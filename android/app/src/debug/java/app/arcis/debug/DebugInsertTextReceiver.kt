package app.arcis.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.core.text.SpokenPunctuation

/**
 * Debug-only: inserts text into the focused field on the external screen exactly like a
 * finished dictation would (spoken punctuation applied). A receiver, not an activity, so it
 * doesn't move window focus away from the external screen.
 *
 *   adb shell am broadcast -n app.arcis/.debug.DebugInsertTextReceiver --es text "hello comma world"
 */
class DebugInsertTextReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = SpokenPunctuation.apply(intent.getStringExtra("text").orEmpty())
        val ok = ArcisAccessibilityService.instance?.insertText(text) ?: false
        Log.i("ArcisDebug", "insert '$text' -> $ok")
    }
}
