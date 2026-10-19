package org.winkit.android

/** NOT the final emulator's config. Render template offline with caller's validated Mac config. */
object BootProfile {
    /** File not interpreted as command or an autoexec yet; never construct emulator shell commands from URI. */
    fun auditImageName(name: String): Boolean = name == "YARDOMOB.raw"
    @Suppress("UNUSED_PARAMETER")
    fun networkingPermitted(identityConfirmed: Boolean, coreHasNativeNet: Boolean): Boolean = false
    const val REQUIREMENTS = "Obtain validated Mac CHS/IMGMOUNT/BOOT chain; network DISABLED; rename WFW computer to YARDOMOB before LAN join."
}
