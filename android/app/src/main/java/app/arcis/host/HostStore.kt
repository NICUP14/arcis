package app.arcis.host

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A computer paired with this phone. [addresses]: tried in order (LAN first, then remote). */
data class SavedHost(
    val name: String,
    val addresses: List<String>,
    val port: Int,
    val fingerprint: String,
    val token: String,
    val os: String = "",
    /** Network card MAC addresses, for Wake-on-LAN. */
    val macs: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("addresses", JSONArray(addresses))
        .put("port", port)
        .put("fingerprint", fingerprint)
        .put("token", token)
        .put("os", os)
        .put("macs", JSONArray(macs))

    companion object {
        fun fromJson(o: JSONObject) = SavedHost(
            name = o.getString("name"),
            addresses = o.getJSONArray("addresses").let { a -> (0 until a.length()).map { a.getString(it) } },
            port = o.getInt("port"),
            fingerprint = o.getString("fingerprint"),
            token = o.getString("token"),
            os = o.optString("os"),
            macs = o.optJSONArray("macs")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        )
    }
}

/** Paired computers, persisted in app-private storage. */
class HostStore(context: Context) {
    private val prefs = context.getSharedPreferences("hosts", Context.MODE_PRIVATE)
    private val _hosts = MutableStateFlow(load())
    val hosts: StateFlow<List<SavedHost>> = _hosts.asStateFlow()

    /** Adds or replaces (same fingerprint) a host. */
    fun save(host: SavedHost) {
        _hosts.value = _hosts.value.filterNot { it.fingerprint == host.fingerprint } + host
        persist()
    }

    /** Adds an address to a known host (e.g. its Tailscale name) without duplicating. */
    fun addAddress(fingerprint: String, address: String) {
        _hosts.value = _hosts.value.map { h ->
            if (h.fingerprint == fingerprint && address !in h.addresses) h.copy(addresses = h.addresses + address) else h
        }
        persist()
    }

    fun forget(fingerprint: String) {
        _hosts.value = _hosts.value.filterNot { it.fingerprint == fingerprint }
        persist()
    }

    private fun load(): List<SavedHost> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { SavedHost.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun persist() {
        prefs.edit().putString(KEY, JSONArray(_hosts.value.map { it.toJson() }).toString()).apply()
    }

    private companion object {
        const val KEY = "hosts"
    }
}
