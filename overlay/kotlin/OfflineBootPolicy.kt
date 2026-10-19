package com.crownparkcomputing.retrodos.winkit

/** A3 independent launch-time preflight, intentionally stricter than the A2 converter.
 * Pure Kotlin; keep in parity with overlay/native/winkit_offline_gate.h.
 * This validates text structure, NOT disk origin or safety of DOS programs on a guest disk. */
object OfflineBootPolicy {
    fun allows(conf: String, expectedDisk: String): Boolean {
        val suffix = "/machines/YARDOMOB.raw"
        if (conf.isEmpty() || conf.length > 262144 || conf.contains('\u0000') || conf.contains('\r') ||
            !expectedDisk.startsWith('/') || !expectedDisk.endsWith(suffix) ||
            expectedDisk.any { it == '\n' || it == '\r' || it == '"' || it == '\\' }) return false
        var section = ""
        val sections = mutableSetOf<String>()
        var net = 0; var ipx = 0; var mounts = 0; var boots = 0
        for (raw in conf.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(';') || line.startsWith('#')) continue
            if (line.startsWith('[')) {
                if (!line.endsWith(']') || line.length < 3) return false
                section = line.substring(1, line.length - 1).trim().lowercase()
                if (section.isBlank() || !sections.add(section)) return false
                continue
            }
            if (section.isEmpty()) return false
            val low = line.lowercase()
            // A9 parity with native gate: fail closed for alternate host transports.
            if (section in setOf("ethernet", "ethnet", "network", "ipxnet", "modem", "slirp"))
                return false
            if (section == "serial" || section == "parallel") {
                val eq = low.indexOf('=')
                if (eq < 0 || low.substring(0, eq).trim().isEmpty() ||
                    low.substring(eq + 1).trim() !in setOf("disabled", "dummy", "none")) return false
            }
            val eq = low.indexOf('=')
            if (eq >= 0 && low.substring(0, eq).trim() in setOf(
                    "configfile", "config file", "autoexec", "sdl_net", "ethernet", "tunnel", "hostip", "host port"
                )) return false
            if (section == "ne2000") {
                if (low == "ne2000=false") net++
                else if (low.startsWith("ne2000")) return false
            }
            if (section == "ipx") {
                if (low == "ipx=false") ipx++
                else if (low.startsWith("ipx")) return false
            }
            if (section == "autoexec") {
                if (boots != 0) return false
                if (low == "@echo off" || low == "echo off" || low == "cls" || low == "pause" ||
                    low == "rem" || low.startsWith("rem ") || low == "@rem" || low.startsWith("@rem ") ||
                    low.startsWith("echo ") || low.startsWith("@echo ")) {
                    if (line.any { it in "&|<>`" }) return false
                    continue
                }
                if (low.startsWith("imgmount ")) {
                    if (mounts != 0 || !isSafeMount(line, expectedDisk)) return false
                    mounts++
                } else if (low == "boot c:" || low == "boot -l c") {
                    if (mounts != 1) return false
                    boots++
                } else return false
            }
        }
        return sections.contains("autoexec") && net == 1 && ipx == 1 && mounts == 1 && boots == 1
    }

    private fun isSafeMount(line: String, disk: String): Boolean {
        if (line.any { it in "&|<>`\\" }) return false
        val parts = mutableListOf<String>()
        val word = StringBuilder()
        var quoted = false; var active = false
        for (c in line) {
            if (c == '"') { quoted = !quoted; active = true; continue }
            if (!quoted && c.isWhitespace()) {
                if (active) { parts.add(word.toString()); word.clear(); active = false }
            } else { word.append(c); active = true }
        }
        if (quoted) return false
        if (active) parts.add(word.toString())
        if (parts.size != 9 || parts.any { it.isEmpty() } ||
            !parts[0].equals("imgmount", true) ||
            (!parts[1].equals("2", true) && !parts[1].equals("c", true)) ||
            parts[2] != disk) return false
        val options = mutableSetOf<String>()
        for (i in 3 until parts.size step 2) {
            val flag = parts[i].lowercase()
            if (!options.add(flag)) return false
            val value = parts[i+1].lowercase()
            when (flag) {
                "-t" -> if (value != "hdd") return false
                "-fs" -> if (value != "none") return false
                "-size" -> {
                    val geometry = value.split(',')
                    if (geometry.size != 4 || geometry.any { it.length !in 1..12 ||
                            it.any { ch -> ch !in '0'..'9' } || (it.toLongOrNull() ?: 0L) !in 1..Int.MAX_VALUE.toLong() }) return false
                }
                else -> return false
            }
        }
        return options == setOf("-t", "-fs", "-size")
    }
}
