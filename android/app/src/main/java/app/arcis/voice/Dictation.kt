package app.arcis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import app.arcis.core.text.SpokenPunctuation

/**
 * Hold-to-talk speech-to-text using Android's built-in recognizer (no bundled AI).
 * While held, partial text is reported live; if the recognizer stops on a pause
 * while still held, it restarts and keeps adding. [stop] delivers the final text.
 * Main thread only.
 */
class Dictation(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    /** BCP-47 language tag to recognise, or null for the phone's language. Read at each start. */
    private val language: () -> String? = { null },
) {
    private var recognizer: SpeechRecognizer? = null
    private var holding = false
    private var finishing = false
    private val segments = mutableListOf<String>()
    private var partial = ""
    private var tag: String? = null

    /** Offline first; online if the language has no offline pack on this phone. */
    private var allowOnline = false

    val isActive: Boolean get() = holding || finishing

    fun start() {
        if (isActive) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition isn't available on this phone")
            return
        }
        segments.clear()
        partial = ""
        tag = language()
        allowOnline = false
        holding = true
        listen()
    }

    /** Released: wait for the last words, then deliver everything. */
    fun stop() {
        if (!holding) return
        holding = false
        finishing = true
        recognizer?.stopListening() ?: finish()
    }

    fun cancel() {
        holding = false
        finishing = false
        recognizer?.cancel()
        release()
    }

    private fun listen() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !allowOnline)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
        tag?.let { intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        r.startListening(intent)
    }

    private fun current(): String =
        SpokenPunctuation.apply((segments + partial).filter { it.isNotBlank() }.joinToString(" "))

    private fun finish() {
        finishing = false
        release()
        onFinal(current())
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            onPartial(current())
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { segments += it }
            partial = ""
            if (holding) listen() else finish() // paused while still holding: keep listening
        }

        override fun onError(error: Int) {
            Log.d(TAG, "recognizer error $error")
            partial = ""
            when {
                holding && !allowOnline && error in LANGUAGE_ERRORS -> {
                    allowOnline = true // no offline pack for this language: use online recognition
                    listen()
                }
                holding && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) -> listen()
                holding -> {
                    holding = false
                    release()
                    onError(message(error))
                }
                else -> finish()
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun message(error: Int) = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone for Arcis on your phone"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech needs a connection (no offline language pack)"
        in LANGUAGE_ERRORS -> "This phone can't recognise that language (Settings → dictation language)"
        else -> "Speech recognition stopped ($error)"
    }

    private companion object {
        const val TAG = "ArcisVoice"

        /** SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31+). */
        val LANGUAGE_ERRORS = setOf(12, 13)
    }
}
