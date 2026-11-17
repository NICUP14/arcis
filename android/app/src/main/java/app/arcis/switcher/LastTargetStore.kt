package app.arcis.switcher

import android.content.Context
import app.arcis.core.switcher.LastTarget
import app.arcis.core.switcher.Target
import org.json.JSONObject

/**
 * Remembers what was last in front on the glasses ([last]) and the last PC stream
 * ([lastStream]), across restarts, for [app.arcis.core.switcher.ResumePolicy].
 */
class LastTargetStore(context: Context) {

    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)

    var last: LastTarget?
        get() = read(KEY_LAST)
        set(value) = write(KEY_LAST, value)

    var lastStream: LastTarget?
        get() = read(KEY_STREAM)
        set(value) = write(KEY_STREAM, value)

    /** The glasses left: what was in front counts as seen until now. */
    fun touch(nowMs: Long) {
        last?.let { last = it.copy(atMs = nowMs) }
        val stream = lastStream
        if (stream != null && last?.target == stream.target) lastStream = stream.copy(atMs = nowMs)
    }

    private fun read(key: String): LastTarget? = runCatching {
        val o = JSONObject(prefs.getString(key, null) ?: return null)
        val target = when (o.getString("type")) {
            "app" -> Target.PhoneApp(o.getString("id"))
            "pc" -> Target.Pc(o.getString("id"), o.optString("name"))
            "bt" -> Target.Bluetooth(o.optString("id"))
            else -> return null
        }
        LastTarget(target, o.getLong("at"))
    }.getOrNull()

    private fun write(key: String, value: LastTarget?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val (type, id, name) = when (val t = value.target) {
            is Target.PhoneApp -> Triple("app", t.pkg, "")
            is Target.Pc -> Triple("pc", t.hostId, t.name)
            is Target.Bluetooth -> Triple("bt", t.address, "")
        }
        val o = JSONObject().put("type", type).put("id", id).put("name", name).put("at", value.atMs)
        prefs.edit().putString(key, o.toString()).apply()
    }

    private companion object {
        const val KEY_LAST = "last"
        const val KEY_STREAM = "last_stream"
    }
}
