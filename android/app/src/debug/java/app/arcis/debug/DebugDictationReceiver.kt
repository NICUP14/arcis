package app.arcis.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.arcis.ArcisApp

/**
 * Debug-only: presses / releases the "hold volume up" dictation, for testing without
 * touching the phone (adb can't inject keys through the accessibility key filter).
 *
 *   adb shell am broadcast -n app.arcis/.debug.DebugDictationReceiver -a start
 *   adb shell am broadcast -n app.arcis/.debug.DebugDictationReceiver -a stop
 */
class DebugDictationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val voice = ArcisApp.instance.voice
        when (intent.action) {
            "start" -> voice.startDictation()
            "stop" -> voice.stopDictation()
        }
    }
}
