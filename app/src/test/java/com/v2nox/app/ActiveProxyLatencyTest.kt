package com.v2nox.app

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class ActiveProxyLatencyTest {
    @Test fun targetsOnlyActiveProfileAndUsesSocksRemoteDns() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 2_000
            val observed = FutureTask {
                listener.accept().use { socket ->
                    socket.soTimeout = 2_000
                    val input = DataInputStream(socket.getInputStream())
                    assertArrayEquals(byteArrayOf(5, 1, 0), ByteArray(3).also(input::readFully))
                    socket.getOutputStream().write(byteArrayOf(5, 0))
                    assertArrayEquals(byteArrayOf(5, 1, 0, 3), ByteArray(4).also(input::readFully))
                    val host = ByteArray(input.readUnsignedByte()).also(input::readFully)
                    assertEquals(443, input.readUnsignedShort())
                    socket.getOutputStream().write(byteArrayOf(5, 4))
                    String(host, Charsets.US_ASCII)
                }
            }
            Thread(observed).apply { isDaemon = true }.start()
            ActiveProxyLatency.start("active", LocalSocks("127.0.0.1", listener.localPort, null, null))
            try {
                assertEquals(-1L, ActiveProxyLatency.measure("other"))
                assertEquals(-1L, ActiveProxyLatency.measure("active"))
                assertEquals("www.gstatic.com", observed.get(3, TimeUnit.SECONDS))
            } finally {
                ActiveProxyLatency.stop()
            }
        }
    }
}
