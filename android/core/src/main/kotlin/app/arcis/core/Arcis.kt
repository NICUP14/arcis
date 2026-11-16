package app.arcis.core

/** Project-wide constants shared by the app and its pure-Kotlin logic. */
object Arcis {
    const val NAME = "Arcis"
    const val VERSION = "0.1.0"

    /** Arcis Bridge TCP port (see docs/protocol.md). */
    const val BRIDGE_PORT = 41990

    /** Arcis Bridge localhost status page (shows the pairing PIN). */
    const val BRIDGE_STATUS_PORT = 41991
}
