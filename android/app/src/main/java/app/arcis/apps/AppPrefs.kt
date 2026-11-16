package app.arcis.apps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Favourite phone apps, persisted. */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(load())

    /** Favourite package names, in the order they were added. */
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    fun toggleFavorite(packageName: String) {
        val current = _favorites.value
        val next = if (packageName in current) current - packageName else current + packageName
        _favorites.value = next
        prefs.edit().putString(KEY_FAVORITES, next.joinToString(SEP)).apply()
    }

    private fun load(): List<String> =
        prefs.getString(KEY_FAVORITES, null)?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val SEP = "|"
    }
}
