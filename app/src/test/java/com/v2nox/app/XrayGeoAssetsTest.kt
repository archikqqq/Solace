package com.v2nox.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

class XrayGeoAssetsTest {
    @Test fun readsRoutingDnsHostsAndExternalReferences() {
        val config = JSONObject("""{
            "remarks":"geoip:ignored",
            "dns":{"hosts":{"geosite:private":"127.0.0.1"},
                "servers":[{"expectedIPs":["!geoip:ru","ext-ip:geoip.dat:!CN"]}]},
            "routing":{"rules":[{"ip":"geoip:private,!geoip:us",
                "domain":["geosite:google@ads","ext-site:custom.dat:lan"]}]}
        }""")
        assertEquals(mapOf(
            "geosite.dat" to setOf("PRIVATE", "GOOGLE"),
            "geoip.dat" to setOf("RU", "CN", "PRIVATE", "US"),
            "custom.dat" to setOf("LAN"),
        ), XrayGeoAssets.references(config))
    }

    @Test fun bundledAssetsContainPrivateSectionsAndCanBeReduced() {
        val aar = listOf(File("libs/libXray.aar"), File("app/libs/libXray.aar")).first { it.isFile }
        ZipFile(aar).use { zip ->
            for (name in listOf("geoip.dat", "geosite.dat")) {
                val entry = zip.getEntry("assets/$name")
                val out = ByteArrayOutputStream()
                zip.getInputStream(entry).use {
                    assertTrue(XrayGeoAssets.slice(it, out, setOf("PRIVATE")).isEmpty())
                }
                assertTrue(out.size() in 1 until (entry.size / 10).toInt())
                val verify = ByteArrayOutputStream()
                assertEquals(setOf("CN"), XrayGeoAssets.slice(out.toByteArray().inputStream(), verify, setOf("PRIVATE", "CN")))
                assertArrayEquals(out.toByteArray(), verify.toByteArray())
            }
        }
    }
}
