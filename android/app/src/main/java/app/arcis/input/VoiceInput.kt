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
        val inserted = ArcisAccessibilityService.instance?.insertText(text) ?: false
        if (!inserted) overlay()?.flash("Select a text field first")
    }
}
