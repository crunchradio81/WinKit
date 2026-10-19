package org.winkit.android

/** A1 native disk *probe*, NOT an emulator. A call only locks/validates an image. */
object NativeDisk {
    init { System.loadLibrary("winkit_disk") }
    @JvmStatic external fun probe(path: String): Long
}
