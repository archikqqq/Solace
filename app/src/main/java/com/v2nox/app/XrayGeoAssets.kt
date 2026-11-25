package com.v2nox.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.Locale

internal object XrayGeoAssets {
    fun prepare(context: Context, config: String): File? {
        val files = references(JSONObject(config))
        if (files.isEmpty()) return null
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "xray-geo-").toFile()
        try {
            for ((name, sections) in files) {
                val installed = File(context.filesDir, name)
                val source = if (installed.isFile) installed.inputStream() else context.assets.open(name)
                source.use { input ->
                    File(directory, name).outputStream().buffered().use { output ->
                        val missing = slice(input, output, sections)
                        require(missing.isEmpty()) { "В $name не найдены секции: ${missing.joinToString()}" }
                    }
                }
            }
            return directory
        } catch (e: Exception) {
            directory.deleteRecursively()
            throw e
        }
    }

    /** True if the geodata file is installed in filesDir or bundled in assets (same lookup as [prepare]). */
    fun isInstalled(context: Context, name: String): Boolean =
        File(context.filesDir, name).isFile || runCatching { context.assets.open(name).close() }.isSuccess

    internal fun references(root: JSONObject): Map<String, Set<String>> {
        val result = linkedMapOf<String, MutableSet<String>>()
        val listKeys = setOf("domain", "domains", "ip", "source", "sourceip", "localip", "expectedips",
            "expectips", "unexpectedips", "ipsblocked", "allowedsourceips", "blockedsourceips")
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { key ->
                    visit(key) // DNS hosts can use geosite rules as keys.
                    val child = value.opt(key)
                    if (child is String && key.lowercase(Locale.ROOT) in listKeys) {
                        child.split(',').forEach(::visit)
                    } else visit(child)
                }
                is JSONArray -> (0 until value.length()).forEach { visit(value.opt(it)) }
                is String -> {
                    val parts = value.trimStart('!').split(':', limit = 3)
                    val file = when (parts.firstOrNull()) {
                        "geoip" -> "geoip.dat"
                        "geosite" -> "geosite.dat"
                        "ext", "ext-ip", "ext-domain", "ext-site" -> parts.getOrNull(1)
                        else -> null
                    } ?: return
                    val code = parts.getOrNull(if (parts[0].startsWith("ext")) 2 else 1)
                        ?.trimStart('!')?.substringBefore('@') ?: return
                    require(file.matches(Regex("[A-Za-z0-9_.-]+")) && file !in setOf(".", "..")) {
                        "Недопустимое имя файла геобазы"
                    }
                    if (code.isNotEmpty()) result.getOrPut(file, ::linkedSetOf).add(code.uppercase(Locale.ROOT))
                }
            }
        }
        // These sections contain Xray's geodata rules. Avoid treating remarks or credentials as rules.
        listOf("routing", "dns").forEach { visit(root.opt(it)) }
        fun visitMasks(stream: JSONObject?) {
            val mask = stream?.optJSONObject("finalmask") ?: return
            listOf("tcp", "udp").forEach { network ->
                val masks = mask.optJSONArray(network) ?: return@forEach
                for (index in 0 until masks.length()) {
                    val entry = masks.optJSONObject(index) ?: continue
                    if (entry.optString("type") == "filter") visit(entry.opt("settings"))
                }
            }
        }
        root.optJSONArray("inbounds")?.let { inbounds ->
            for (index in 0 until inbounds.length()) visitMasks(inbounds.optJSONObject(index)?.optJSONObject("streamSettings"))
        }
        root.optJSONArray("outbounds")?.let { outbounds ->
            for (index in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(index) ?: continue
                visitMasks(outbound.optJSONObject("streamSettings"))
                val settings = outbound.optJSONObject("settings")
                when (outbound.optString("protocol")) {
                    "freedom" -> listOf("finalRules", "firewall", "ipsBlocked").forEach { visit(settings?.opt(it)) }
                    "dns" -> visit(settings?.opt("rules"))
                }
            }
        }
        return result
    }

    // Both files are protobuf lists of field-1 entries whose first field is the section code.
    // Copy entries verbatim, including GeoSite attributes, using a fixed-size buffer.
    internal fun slice(input: InputStream, output: OutputStream, sections: Set<String>): Set<String> {
        val source = DataInputStream(input.buffered())
        val remaining = sections.toMutableSet()
        val buffer = ByteArray(32 * 1024)
        while (remaining.isNotEmpty()) {
            val tag = source.read()
            if (tag < 0) break
            require(tag == 0x0a) { "Неверный формат списка геоданных" }
            val size = source.readLength()
            require(size >= 3 && source.readUnsignedByte() == 0x0a) { "Неверный формат секции геоданных" }
            // Xray's loader requires an uppercase code with a single-byte length.
            val codeSize = source.readUnsignedByte()
            require(codeSize in 1..127 && codeSize <= size - 2) { "Неверный код секции геоданных" }
            val code = ByteArray(codeSize).also(source::readFully)
            var left = size - 2 - codeSize
            if (!remaining.remove(String(code, Charsets.UTF_8))) {
                source.skipNBytesCompat(left, buffer)
                continue
            }
            output.write(tag)
            output.writeLength(size)
            output.write(0x0a)
            output.write(codeSize)
            output.write(code)
            while (left > 0) {
                val bytes = minOf(left, buffer.size)
                source.readFully(buffer, 0, bytes)
                output.write(buffer, 0, bytes)
                left -= bytes
            }
        }
        return remaining
    }

    private fun DataInputStream.skipNBytesCompat(size: Int, buffer: ByteArray) {
        var left = size
        while (left > 0) {
            val bytes = minOf(left, buffer.size)
            readFully(buffer, 0, bytes)
            left -= bytes
        }
    }

    private fun DataInputStream.readLength(): Int {
        var value = 0
        for (shift in 0..28 step 7) {
            val byte = readUnsignedByte()
            require(shift != 28 || byte <= 7) { "Некорректный размер секции геоданных" }
            value = value or ((byte and 0x7f) shl shift)
            if (byte < 128) return value
        }
        error("Некорректный размер секции геоданных")
    }

    private fun OutputStream.writeLength(size: Int) {
        var left = size
        while (left >= 128) {
            write(left and 0x7f or 0x80)
            left = left ushr 7
        }
        write(left)
    }
}
