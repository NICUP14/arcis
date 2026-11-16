package app.arcis.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * Streams the phone microphone to the connected computer (its Arcis Bridge plays it into
 * a virtual microphone). 16 kHz mono 16-bit PCM in 20 ms frames, voice-call processing
 * (echo cancellation / noise suppression where the phone offers it).
 */
class MicStreamer(private val context: Context, private val send: (ByteArray) -> Unit) {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission") // checked below
    fun start(): Boolean {
        if (running) return true
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, FRAME_BYTES * 4),
            )
        }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            return false
        }
        running = true
        thread = Thread({
            val frame = ByteArray(FRAME_BYTES)
            try {
                record.startRecording()
                while (running) {
                    val n = record.read(frame, 0, frame.size)
                    if (n > 0) send(frame.copyOf(n))
                }
            } catch (e: Exception) {
                Log.w(TAG, "microphone stopped", e)
            } finally {
                runCatching { record.stop() }
                record.release()
                running = false
            }
        }, "arcis-mic").apply { start() }
        return true
    }

    fun stop() {
        running = false
        thread = null
    }

    private companion object {
        const val TAG = "ArcisMic"
        const val RATE = 16_000
        const val FRAME_BYTES = 640 // 20 ms
    }
}
