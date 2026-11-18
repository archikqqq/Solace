// Ported from ChumpyXray/Chumpy (MIT)
package com.v2nox.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.InterruptedIOException
import java.io.InputStream
import javax.net.ssl.SSLException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SubscriptionFetcher(
    private val happSpoof: suspend () -> HappSpoof.Fingerprint?,
) {
    constructor(context: Context) : this(
        happSpoof = context.applicationContext.let { appContext ->
            { HappSpoof.fingerprint(appContext) }
        },
    )

    data class FetchResult(val body: String, val metadata: SubscriptionMetadata, val host: String = "unknown") {
        internal val preparedBody = SubscriptionParser.prepareBody(body)
    }

    suspend fun fetch(rawUrl: String): FetchResult {
        val request = buildRequest(rawUrl)
        return suspendCancellableCoroutine { continuation ->
            val call = directClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w("V2NoxSub", "host=${request.url.host} exception=${e.javaClass.simpleName}")
                    if (continuation.isActive) continuation.resumeWithException(
                        IOException(networkErrorMessage(e), e),
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            val info = "host=${it.request.url.host} HTTP=${it.code} Content-Type=${it.body?.contentType()?.let { type -> "${type.type}/${type.subtype}" } ?: "unknown"} Content-Encoding=${safeEncoding(it.header("Content-Encoding"))}"
                            Log.w("V2NoxSub", "$info wireBytes=${it.body?.contentLength() ?: -1}")
                            if (!it.isSuccessful) throw ResponseException("HTTP ${it.code}")
                            val input = it.body?.byteStream() ?: throw ResponseException("Пустой ответ")
                            val bytes = readBody(input, it.header("Content-Encoding")) { continuation.isActive }
                            val body = String(bytes, Charsets.UTF_8)
                            if (body.trim().removePrefix("\uFEFF").isBlank()) throw ResponseException("Пустой ответ")
                            FetchResult(body, SubscriptionHeaders.parse(it.headers.toMultimap()), it.request.url.host).also { fetched ->
                                val prepared = fetched.preparedBody
                                Log.w("V2NoxSub", "$info bodyBytes=${bytes.size} format=${prepared.format} decodedFormat=${prepared.decodedFormat}")
                            }
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        Log.w("V2NoxSub", "host=${response.request.url.host} exception=${e.javaClass.simpleName}")
                        if (continuation.isActive) continuation.resumeWithException(
                            if (e is ResponseException) e
                            else IOException(if (e is InterruptedIOException) networkErrorMessage(e) else "Не удалось распаковать или прочитать ответ подписки", e),
                        )
                    }
                }
            })
        }
    }

    internal suspend fun buildRequest(rawUrl: String): Request {
        val target = normalizeUrl(rawUrl).toHttpUrl()
        val headers = HappSpoof.headers(happSpoof())
        return try {
            Request.Builder().url(target).get().apply {
                headers.forEach { (name, value) -> header(name, value) }
                if (target.host == "selevk.com") header("User-Agent", "Happ/1.0")
            }.build()
        } catch (e: IllegalArgumentException) {
            throw IOException("Некорректный URL или заголовки запроса подписки", e)
        }
    }

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) throw IOException("URL подписки пуст")
        val normalized = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            "://" in trimmed -> trimmed
            else -> "https://$trimmed"
        }
        return normalized.toHttpUrlOrNull()?.toString()
            ?: throw IOException("Некорректный URL подписки: требуется адрес HTTP или HTTPS")
    }

    fun hostNameOf(rawUrl: String): String = try {
        normalizeUrl(rawUrl).toHttpUrlOrNull()?.host ?: "unknown"
    } catch (_: IOException) {
        "unknown"
    }

    internal companion object {
        private class ResponseException(message: String) : IOException(message)
        const val MAX_BODY_BYTES = 8 * 1024 * 1024
        fun networkErrorMessage(error: IOException): String = when (error) {
            is SocketTimeoutException, is InterruptedIOException -> "Таймаут соединения"
            is UnknownHostException -> "Не удалось найти хост подписки"
            is SSLException -> "Ошибка защищённого соединения (TLS)"
            else -> "Ошибка подключения к серверу подписки"
        }

        private fun safeEncoding(value: String?): String = when (value?.trim()?.lowercase()) {
            null -> "identity"
            "gzip" -> "gzip"
            "identity" -> "identity"
            else -> "other"
        }

        // Explicit Accept-Encoding disables OkHttp's transparent gzip handling.
        fun readBody(input: InputStream, contentEncoding: String?, active: () -> Boolean = { true }): ByteArray {
            val stream = if (contentEncoding?.trim().equals("gzip", true)) GZIPInputStream(input) else input
            return stream.use { source ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    if (!active()) throw ResponseException("Загрузка подписки отменена")
                    val count = source.read(chunk)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BODY_BYTES) throw ResponseException("Размер ответа превышает 8 МБ")
                    output.write(chunk, 0, count)
                }
                output.toByteArray()
            }
        }

        val directClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
