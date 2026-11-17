package app.arcis.core.net

import java.net.URI
import java.net.URLDecoder

/**
 * The Arcis Bridge's pairing QR code: everything needed to find a computer (all its
 * addresses, e.g. LAN and Tailscale), check its identity (certificate fingerprint) and
 * pair (PIN), so nothing has to be typed.
 *
 *     arcis://pair?a=192.168.1.5,100.64.1.2&fp=<sha256 hex>&h=<name>&p=41990&pin=123456
 */
data class PairLink(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val pin: String,
    val addresses: List<String>,
) {
    companion object {
        /** The link from a scanned code, or null if it isn't a valid Arcis pairing link. */
        fun parse(text: String): PairLink? {
            val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
            if (!uri.scheme.equals("arcis", ignoreCase = true) || uri.host != "pair") return null
            val q = (uri.rawQuery ?: return null).split("&").mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else decode(part.substring(0, i)) to decode(part.substring(i + 1))
            }.toMap()
            val port = q["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val fingerprint = q["fp"]?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return null
            val pin = q["pin"]?.takeIf { it.matches(Regex("\\d{6}")) } ?: return null
            val addresses = q["a"].orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (addresses.isEmpty()) return null
            return PairLink(q["h"].orEmpty().ifBlank { addresses.first() }, port, fingerprint, pin, addresses)
        }

        private fun decode(s: String): String = URLDecoder.decode(s, Charsets.UTF_8)
    }
}
