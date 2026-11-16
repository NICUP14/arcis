package app.arcis.input

import android.content.Context
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.voice.Dictation

/**
 * Speech-to-text for whatever is in front on the external screen: shows the words live
 * in a strip on the glasses and, on release, types them into the selected text field.
 */
class VoiceInput(context: Context) {

    private val dictation = Dictation(
        context,
        onPartial = { text -> overlay()?.showDictation(text.ifEmpty { "Listening…" }) },
        onFinal = ::deliver,
        onError = { message ->
            overlay()?.hideDictation()
            overlay()?.flash(message)
        },
        language = { app.arcis.ArcisApp.instance.controllerPrefs.dictationLanguage.value },
    )

    private fun overlay() = ArcisAccessibilityService.instance?.uiOverlay

    fun startDictation() {
        overlay()?.showDictation("Listening…")
        dictation.start()
    }

    fun stopDictation() = dictation.stop()

    private fun deliver(text: String) {
        overlay()?.hideDictation()
        if (text.isBlank()) return
        val app = app.arcis.ArcisApp.instance
        if (app.hostTarget.active) {
            app.hosts.text(text) // computer: typed through the bridge
            return
        }
        if (app.bluetoothTarget.active) {
            app.hid.type(text) // Bluetooth device: typed as keystrokes
            return
        }
        val inserted = ArcisAccessibilityService.instance?.insertText(text) ?: false
        if (!inserted) overlay()?.flash("Select a text field first")
    }
}
