// Ported from ChumpyXray/Chumpy (MIT)
package com.v2nox.app

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SubscriptionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.subscriptionPreferences
    private val servers = ServerRepository(appContext)
    private val fetcher = SubscriptionFetcher(appContext)

    val subscriptions: Flow<List<SubscriptionConfig>> = dataStore.data.map {
        decodeSubscriptions(it[SUBSCRIPTION_LIST_KEY].orEmpty())
    }

    suspend fun subscriptionById(id: String): SubscriptionConfig? =
        subscriptions.first().firstOrNull { it.id == id }

    suspend fun add(url: String, name: String? = null): Result<SubscriptionConfig> =
        withContext(Dispatchers.IO) {
            resultOf {
                val normalizedUrl = fetcher.normalizeUrl(url)
                val response = fetchWithNestedExpansion(normalizedUrl)
                val parsed = parseResponse(response)
                val now = System.currentTimeMillis()
                val subscription = SubscriptionConfig(
                    id = UUID.randomUUID().toString(),
                    name = name?.trim()?.takeIf { it.isNotBlank() }
                        ?: response.metadata.title ?: fetcher.hostNameOf(normalizedUrl),
                    url = normalizedUrl,
                ).withMetadata(response.metadata, now)
                lockFor(subscription.id).withLock {
                    servers.replaceSubscriptionServers(subscription.id, parsed)
                    save(subscription)
                    scheduleAutoUpdate(subscription)
                }
                subscription
            }
        }

    suspend fun update(id: String): Result<SubscriptionConfig> = withContext(Dispatchers.IO) {
        lockFor(id).withLock {
            resultOf {
                val current = subscriptionById(id) ?: error("Подписка не найдена")
                val attemptAt = System.currentTimeMillis()
                try {
                    val response = fetchWithNestedExpansion(current.url)
                    val parsed = parseResponse(response)
                    val updated = current.withMetadata(response.metadata, attemptAt).copy(
                        name = response.metadata.title ?: current.name,
                    )
                    servers.replaceSubscriptionServers(id, parsed)
                    save(updated)
                    if (current.updateIntervalHours != updated.updateIntervalHours) {
                        scheduleAutoUpdate(updated)
                    }
                    updated
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    save(current.copy(
                        lastUpdateAttempt = attemptAt,
                        lastError = e.message ?: e.javaClass.simpleName,
                    ))
                    throw e
                }
            }
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        lockFor(id).withLock {
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_PREFIX + id)
            servers.removeSubscriptionServers(id)
            dataStore.edit { preferences ->
                preferences[SUBSCRIPTION_LIST_KEY] = encodeSubscriptions(
                    decodeSubscriptions(preferences[SUBSCRIPTION_LIST_KEY].orEmpty()).filterNot { it.id == id },
                )
            }
        }
    }

    private suspend fun save(subscription: SubscriptionConfig) {
        dataStore.edit { preferences ->
            val current = decodeSubscriptions(preferences[SUBSCRIPTION_LIST_KEY].orEmpty()).toMutableList()
            val index = current.indexOfFirst { it.id == subscription.id }
            if (index >= 0) current[index] = subscription else current.add(subscription)
            preferences[SUBSCRIPTION_LIST_KEY] = encodeSubscriptions(current)
        }
    }

    private suspend fun fetchWithNestedExpansion(url: String): SubscriptionFetcher.FetchResult {
        val visited = mutableSetOf(fetcher.normalizeUrl(url))
        var response = fetcher.fetch(url)
        var depth = 0
        while (true) {
            val nested = SubscriptionParser.isNestedSubscriptionUrl(response.preparedBody) ?: return response
            require(depth < 2) { "Вложенность подписки превышает 2 уровня" }
            val normalized = fetcher.normalizeUrl(nested)
            require(visited.add(normalized)) { "Обнаружен цикл вложенных подписок" }
            response = fetcher.fetch(normalized)
            depth++
        }
    }

    private fun parseResponse(response: SubscriptionFetcher.FetchResult): List<ServerConfig> {
        val analysis = SubscriptionParser.analyze(response.preparedBody) { scheme, type ->
            Log.w("V2NoxSub", "rejected scheme=$scheme exception=$type")
        }
        Log.w("V2NoxSub", "host=${response.host} format=${analysis.format} decodedFormat=${analysis.decodedFormat} servers=${analysis.servers.size}")
        require(analysis.servers.isNotEmpty()) { analysis.failureMessage() }
        return analysis.servers
    }

    private fun scheduleAutoUpdate(subscription: SubscriptionConfig) {
        val manager = WorkManager.getInstance(appContext)
        if (!subscription.autoUpdate) {
            manager.cancelUniqueWork(WORK_PREFIX + subscription.id)
            return
        }
        val hours = (subscription.updateIntervalHours ?: 24).coerceIn(1, 336).toLong()
        val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(hours, TimeUnit.HOURS)
            .setInputData(workDataOf(SubscriptionUpdateWorker.KEY_SUBSCRIPTION_ID to subscription.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_PREFIX + subscription.id, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun SubscriptionConfig.withMetadata(metadata: SubscriptionMetadata, attemptAt: Long) = copy(
        lastUpdateAttempt = attemptAt,
        lastUpdateSuccess = System.currentTimeMillis(),
        updateIntervalHours = metadata.updateIntervalHours,
        uploadBytes = metadata.uploadBytes,
        downloadBytes = metadata.downloadBytes,
        totalBytes = metadata.totalBytes,
        expireEpochSeconds = metadata.expireEpochSeconds,
        lastError = null,
    )

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    private fun decodeSubscriptions(value: String): List<SubscriptionConfig> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching {
                        val item = array.getJSONObject(index)
                        SubscriptionConfig(
                            id = item.getString("id"),
                            name = item.optString("name"),
                            url = item.getString("url"),
                            createdAt = item.optLong("createdAt"),
                            lastUpdateAttempt = item.optLong("lastUpdateAttempt"),
                            lastUpdateSuccess = item.optLong("lastUpdateSuccess"),
                            updateIntervalHours = if (item.isNull("updateIntervalHours")) null
                                else item.getInt("updateIntervalHours"),
                            autoUpdate = item.optBoolean("autoUpdate", true),
                            uploadBytes = item.optLong("uploadBytes"),
                            downloadBytes = item.optLong("downloadBytes"),
                            totalBytes = item.optLong("totalBytes"),
                            expireEpochSeconds = item.optLong("expireEpochSeconds"),
                            lastError = if (item.isNull("lastError")) null else item.optString("lastError"),
                        )
                    }.getOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSubscriptions(subscriptions: List<SubscriptionConfig>): String = JSONArray().apply {
        subscriptions.forEach { subscription ->
            put(JSONObject().apply {
                put("id", subscription.id)
                put("name", subscription.name)
                put("url", subscription.url)
                put("createdAt", subscription.createdAt)
                put("lastUpdateAttempt", subscription.lastUpdateAttempt)
                put("lastUpdateSuccess", subscription.lastUpdateSuccess)
                put("updateIntervalHours", subscription.updateIntervalHours ?: JSONObject.NULL)
                put("autoUpdate", subscription.autoUpdate)
                put("uploadBytes", subscription.uploadBytes)
                put("downloadBytes", subscription.downloadBytes)
                put("totalBytes", subscription.totalBytes)
                put("expireEpochSeconds", subscription.expireEpochSeconds)
                put("lastError", subscription.lastError ?: JSONObject.NULL)
            })
        }
    }.toString()

    private companion object {
        val SUBSCRIPTION_LIST_KEY = stringPreferencesKey("subscription_list")
        const val WORK_PREFIX = "subscription-update-"
        // Shared by UI and worker repository instances; retained to avoid lock replacement races.
        val locks = ConcurrentHashMap<String, Mutex>()
        fun lockFor(id: String): Mutex = locks.getOrPut(id) { Mutex() }
    }
}
