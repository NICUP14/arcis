package app.arcis.core.net

/**
 * Orders a computer's addresses for connecting: home network first (fast, direct),
 * names and public addresses next, Tailscale (100.64.0.0/10, *.ts.net) last as the
 * "away from home" fallback. Loopback stays first (USB-forwarded testing).
 */
object AddressOrder {

    enum class Kind { LOOPBACK, LAN, OTHER, TAILSCALE }

    fun kind(address: String): Kind {
        val a = address.trim().lowercase()
        if (a == "localhost" || a.startsWith("127.") || a == "::1") return Kind.LOOPBACK
        if (a.endsWith(".ts.net")) return Kind.TAILSCALE
        val octets = a.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size == 4 && a.count { it == '.' } == 3) {
            val (o1, o2) = octets[0] to octets[1]
            return when {
                o1 == 100 && o2 in 64..127 -> Kind.TAILSCALE // CGNAT range used by Tailscale
                o1 == 10 -> Kind.LAN
                o1 == 192 && o2 == 168 -> Kind.LAN
                o1 == 172 && o2 in 16..31 -> Kind.LAN
                else -> Kind.OTHER
            }
        }
        if (a.endsWith(".local") || a.endsWith(".lan") || a.endsWith(".home")) return Kind.LAN
        return Kind.OTHER
    }

    fun isRemote(address: String): Boolean = kind(address).let { it == Kind.TAILSCALE || it == Kind.OTHER }

    /** Merges [known] and [reported] addresses (deduplicated) in connection order. */
    fun order(known: List<String>, reported: List<String> = emptyList()): List<String> =
        (known + reported)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { kind(it).ordinal } // stable: keeps the given order within a kind

    /** How long to wait for a connection: short on the home network, longer remotely. */
    fun connectTimeoutMs(address: String): Int = when (kind(address)) {
        Kind.LOOPBACK, Kind.LAN -> 1_500
        Kind.TAILSCALE, Kind.OTHER -> 5_000
    }
}
