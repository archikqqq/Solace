package com.v2nox.app

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal class LocalSocks(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
) {
    fun verify() {
        if (username != null) socket(1_000).use {
            check(negotiate(it, null, null) == 0xff) { "SOCKS5 принял подключение без пароля" }
        }
        socket(1_000).use { authenticate(it) }
    }

    private fun socket(timeout: Int): Socket = Socket(Proxy.NO_PROXY).apply {
        try {
            connect(InetSocketAddress(host, this@LocalSocks.port), timeout)
            soTimeout = timeout
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun negotiate(socket: Socket, user: String?, pass: String?): Int {
        val input = DataInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        output.write(byteArrayOf(5, 1, if (user == null) 0 else 2))
        check(input.readUnsignedByte() == 5)
        val method = input.readUnsignedByte()
        if (method == 2 && user != null && pass != null) {
            val login = user.toByteArray(Charsets.UTF_8)
            val secret = pass.toByteArray(Charsets.UTF_8)
            output.write(byteArrayOf(1, login.size.toByte()) + login + byteArrayOf(secret.size.toByte()) + secret)
            check(input.readUnsignedShort() == 0x0100) { "SOCKS5 отклонил учётные данные" }
        }
        return method
    }

    private fun authenticate(socket: Socket) {
        check(negotiate(socket, username, password) == if (username == null) 0 else 2) {
            "SOCKS5 не согласовал авторизацию"
        }
    }

    fun measure(): Long {
        val started = System.nanoTime()
        val destination = "www.gstatic.com"
        socket(4_000).use { socket ->
            authenticate(socket)
            val input = DataInputStream(socket.getInputStream())
            val domain = destination.toByteArray(Charsets.US_ASCII)
            socket.getOutputStream().write(byteArrayOf(5, 1, 0, 3, domain.size.toByte()) + domain + byteArrayOf(1, 0xbb.toByte()))
            check(input.readUnsignedShort() == 0x0500 && input.readUnsignedByte() == 0)
            val length = when (input.readUnsignedByte()) {
                1 -> 4
                3 -> input.readUnsignedByte()
                4 -> 16
                else -> error("Некорректный ответ SOCKS5")
            }
            input.readFully(ByteArray(length + 2))
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(socket, destination, 443, true) as SSLSocket).use { tls ->
                tls.soTimeout = socket.soTimeout
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
                tls.getOutputStream().write(("GET /generate_204 HTTP/1.1\r\nHost: $destination\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                val response = tls.getInputStream().buffered()
                val status = buildString {
                    repeat(128) {
                        val next = response.read()
                        if (next == -1 || next == '\n'.code) return@buildString
                        append(next.toChar())
                    }
                }
                check(status.matches(Regex("HTTP/1\\.[01] 204(?: [^\\r]*)?\\r")))
            }
        }
        return ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1)
    }
}

internal object ActiveProxyLatency {
    private class Session(val serverId: String, val socks: LocalSocks)

    @Volatile private var active: Session? = null

    fun start(serverId: String, socks: LocalSocks) { active = Session(serverId, socks) }
    fun stop() { active = null }

    fun measure(serverId: String): Long {
        val session = active?.takeIf { it.serverId == serverId } ?: return -1
        val delay = try { session.socks.measure() } catch (_: Exception) { -1 }
        return if (active === session) delay else -1
    }
}
