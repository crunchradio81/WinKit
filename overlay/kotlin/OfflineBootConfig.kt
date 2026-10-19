package com.crownparkcomputing.retrodos.winkit

/**
 * Strict, offline-first conversion of ONE known-working DOSBox-X guest conf.
 * Pure Kotlin: host-testable without an Android SDK.  This is deliberately not
 * a generic DOSBox config converter; reject ambiguous boot chains and media.
 */
object OfflineBootConfig {
    class UnsafeConfig(message: String) : IllegalArgumentException(message)

    /** Android-private path resolved at runtime from Activity.filesDir. */
    fun convert(macConfig: String, originalImagePath: String, privateDiskPath: String): String {
        fun reject(s: String): Nothing = throw UnsafeConfig(s)
        if (macConfig.length > 262144 || macConfig.contains('\u0000')) reject("Config too large or contains NUL")
        if (originalImagePath.isBlank() || originalImagePath.any { it == '\r' || it == '\n' || it == '"' })
            reject("Specify the exact, unquoted original Mac disk path")
        if (!originalImagePath.startsWith('/')) reject("Original path must be absolute")
        if (!privateDiskPath.startsWith('/') || privateDiskPath.any { it == '\r' || it == '\n' || it == '"' })
            reject("Private disk path is invalid")
        if (!privateDiskPath.endsWith("/machines/YARDOMOB.raw") || privateDiskPath == originalImagePath)
            reject("Expected a separate app-private YARDOMOB disk")
        if (macConfig.split(originalImagePath).size != 2) reject("Original disk path must occur exactly once")
        val original = macConfig.replace("\r\n", "\n").replace('\r', '\n')
        if (original.lines().any { it.trim().startsWith("!") }) reject("Unrecognized shell command in config")
        val sectionRe = Regex("^\\s*\\[([a-zA-Z0-9 _-]+)]\\s*(?:[;#].*)?$")
        val sectionCounts = mutableMapOf<String, Int>()
        var section = ""
        val lines = mutableListOf<String>()
        val matchedDisk = Regex("(?i)^\\s*imgmount\\s+(?:2|[cC])\\s+.*")
        val allMounts = Regex("(?i)^\\s*@?(?:mount|imgmount|boot|keyb|ethnet|ipxnet|serial|parallel)\\b.*")
        val ethCommand = Regex("(?i)^\\s*@?(?:ethnet|ipxnet|net|ipconfig|ping|route|netbeui)\\b")
        val bootCommand = Regex("(?i)^\\s*@?boot\\s+(?:c:|[-/]l\\s+c)\\s*$")
        var diskLineSeen = false
        var bootSeen = false
        for (oldLine in original.lines()) {
            val trimmed = oldLine.trim()
            val sectionMatch = sectionRe.matchEntire(oldLine)
            if (sectionMatch != null) {
                section = sectionMatch.groupValues[1].lowercase().trim()
                sectionCounts[section] = (sectionCounts[section] ?: 0) + 1
                if (sectionCounts[section]!! > 1) reject("Repeated [$section] section; manually review first")
                lines.add(oldLine)
                continue
            }
            if (trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.isEmpty()) {
                lines.add(oldLine)
                continue
            }
            // Preserve the actual controller and CHS boot line; change only the image's path.
            if (section == "autoexec") {
                if (Regex("(?i)^\\s*@?(?:ethnet|ipxnet)\\b").containsMatchIn(oldLine)) continue
                if (ethCommand.containsMatchIn(oldLine)) reject("Guest network command requires manual review: $trimmed")
                if (allMounts.containsMatchIn(oldLine)) {
                    if (matchedDisk.matches(oldLine) && oldLine.contains(originalImagePath)) {
                        if (diskLineSeen) reject("Multiple HDD mounts")
                        diskLineSeen = true
                        lines.add(oldLine.replace(originalImagePath, privateDiskPath))
                        continue
                    }
                    if (bootCommand.matches(oldLine)) {
                        if (bootSeen) reject("Multiple guest BOOT commands")
                        bootSeen = true
                        lines.add(oldLine)
                        continue
                    }
                    reject("Additional host media/boot/network commands require manual review: $trimmed")
                }
                // No CALL or RUN of unknown external scripts; they could mount media or network.
                if (Regex("(?i)^\\s*@?(?:call|if|goto|config|c:|[a-z]:\\\\|[^;# ]+\\.(?:bat|com|exe))\\b").containsMatchIn(oldLine))
                    reject("Additional autoexec command requires manual review: $trimmed")
                if (!Regex("(?i)^\\s*@?(?:echo\\b.*|rem\\b.*|@?echo\\s+off|pause|cls)\\s*$").matches(oldLine))
                    reject("Unsupported autoexec line; do not guess DOS boot chain: $trimmed")
            }
            if (section == "ne2000" && Regex("(?i)^\\s*ne2000\\s*=").containsMatchIn(oldLine)) continue
            if (section == "ipx" && Regex("(?i)^\\s*ipx\\s*=").containsMatchIn(oldLine)) continue
            if (section == "dosbox" && Regex("(?i)^\\s*working directory option\\s*=").containsMatchIn(oldLine)) continue
            if (oldLine.contains(originalImagePath)) reject("Original image referenced outside the primary autoexec IMGMOUNT")
            lines.add(oldLine)
        }
        if ((sectionCounts["autoexec"] ?: 0) != 1 || !diskLineSeen || !bootSeen)
            reject("Need exactly one HDD IMGMOUNT and BOOT C: chain in [autoexec]")
        // The offline profile never opens a virtual NIC or IPX transport.
        if (sectionCounts.containsKey("ne2000")) {
            val idx = lines.indexOfFirst { it.trim().equals("[ne2000]", true) }
            lines.add(idx + 1, "ne2000=false")
        } else lines.addAll(listOf("[ne2000]", "ne2000=false"))
        if (sectionCounts.containsKey("ipx")) {
            val idx = lines.indexOfFirst { it.trim().equals("[ipx]", true) }
            lines.add(idx + 1, "ipx=false")
        } else lines.addAll(listOf("[ipx]", "ipx=false"))
        if (sectionCounts.containsKey("dosbox")) {
            val idx = lines.indexOfFirst { it.trim().equals("[dosbox]", true) }
            lines.add(idx + 1, "working directory option=noprompt")
        } else lines.addAll(listOf("[dosbox]", "working directory option=noprompt"))
        val converted = "; WinKit A2: OFFLINE. Not yet renamed or LAN approved.\n" + lines.joinToString("\n").trimEnd() + "\n"
        if (!OfflineBootPolicy.allows(converted, privateDiskPath)) reject("Offline profile failed independent launch-time policy")
        return converted
    }
}
