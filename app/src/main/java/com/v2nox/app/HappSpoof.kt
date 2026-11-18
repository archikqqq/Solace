// Ported from ChumpyXray/Chumpy (MIT)
package com.v2nox.app

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.Locale

internal val Context.subscriptionPreferences by preferencesDataStore(name = "subscriptions")

object HappSpoof {
    const val DEFAULT_APP_VERSION = "3.21.1"
    const val USER_AGENT = "V2Nox/1.0 (Android) v2rayNG/1.10"

    private val enabledKey = booleanPreferencesKey("happ_spoof")
    private val userIdKey = stringPreferencesKey("user_id")
    private val hwidKey = stringPreferencesKey("hwid")
    private val rng = SecureRandom()

    data class Fingerprint(
        val appVersion: String,
        val os: String,
        val osVersion: String,
        val deviceModel: String,
        val locale: String,
        val userId: String,
        val hwid: String,
    )

    fun randomUserId(): String = buildString {
        append(rng.nextInt(9) + 1)
        repeat(19) { append(rng.nextInt(10)) }
    }

    fun randomHwid(): String = ByteArray(8).also(rng::nextBytes)
        .joinToString("") { "%02x".format(it) }

    fun enabled(context: Context): Flow<Boolean> = context.applicationContext.subscriptionPreferences.data
        .map { it[enabledKey] ?: true }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.subscriptionPreferences.edit { it[enabledKey] = enabled }
    }

    suspend fun fingerprint(context: Context): Fingerprint? {
        // One transaction prevents concurrent fetches from generating different identities.
        val preferences = context.applicationContext.subscriptionPreferences.edit {
            if (it[enabledKey] == null) it[enabledKey] = true
            if (it[userIdKey].isNullOrBlank()) it[userIdKey] = randomUserId()
            if (it[hwidKey].isNullOrBlank()) it[hwidKey] = randomHwid()
        }
        if (preferences[enabledKey] == false) return null
        return Fingerprint(
            appVersion = DEFAULT_APP_VERSION,
            os = "Android",
            osVersion = Build.VERSION.RELEASE.ifBlank { "16" },
            deviceModel = Build.MODEL.ifBlank { "Pixel 7 Pro" },
            locale = Locale.getDefault().language.take(2).ifBlank { "en" },
            userId = checkNotNull(preferences[userIdKey]),
            hwid = checkNotNull(preferences[hwidKey]),
        )
    }

    fun headers(fingerprint: Fingerprint?): Map<String, String> = if (fingerprint == null) {
        mapOf("User-Agent" to USER_AGENT, "Accept" to "*/*")
    } else {
        mapOf(
            "User-Agent" to "Happ/${fingerprint.appVersion}/${fingerprint.os}/${fingerprint.userId}",
            "x-hwid" to fingerprint.hwid,
            "x-device-os" to fingerprint.os,
            "x-ver-os" to fingerprint.osVersion,
            "x-device-model" to fingerprint.deviceModel,
            "x-device-locale" to fingerprint.locale,
            "accept-encoding" to "gzip",
        )
    }
}
