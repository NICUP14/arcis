package app.arcis.host

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingQueue
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * One TLS connection to an Arcis Bridge (see docs/protocol.md). The bridge's self-signed
 * certificate is checked against the fingerprint pinned at pairing time.
 */
class BridgeConnection private constructor(
    private val socket: SSLSocket,
    private val reader: BufferedReader,
    val fingerprint: String,
) {
    private val out: OutputStream = socket.outputStream
    private val queue = LinkedBlockingQueue<JSONObject>()
    @Volatile
    private var closed = false

    /** Sends messages in order without blocking the caller. */
    fun send(message: JSONObject) {
        if (!closed) queue.offer(message)
    }

    /** Starts the writer and reader threads; [onMessage] / [onClosed] run on the reader thread. */
    fun start(onMessage: (JSONObject) -> Unit, onClosed: (Throwable?) -> Unit) {
        Thread({
            try {
                while (!closed) {
                    val m = queue.take()
                    if (closed) break
                    out.write((m.toString() + "\n").toByteArray())
                    if (queue.isEmpty()) out.flush()
                }
            } catch (e: Exception) {
                close()
            }
        }, "arcis-bridge-writer").start()
        Thread({
            var error: Throwable? = null
            try {
                while (!closed) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) onMessage(JSONObject(line))
                }
            } catch (e: Exception) {
                error = e
            } finally {
                close()
                onClosed(error)
            }
        }, "arcis-bridge-reader").start()
    }

    fun close() {
        if (closed) return
        closed = true
        queue.offer(JSONObject()) // wake the writer
        runCatching { socket.close() }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val HANDSHAKE_TIMEOUT_MS = 6_000

        /**
         * Opens a TLS connection. With [pinnedFingerprint] null, any certificate is accepted
         * (pairing) and its fingerprint is reported; otherwise it must match exactly.
         */
        fun open(address: String, port: Int, pinnedFingerprint: String?): BridgeConnection {
            var seen = ""
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
                    seen = sha256(chain[0].encoded)
                    if (pinnedFingerprint != null && !seen.equals(pinnedFingerprint, ignoreCase = true)) {
                        throw java.security.cert.CertificateException("This computer's identity changed. Pair it again.")
                    }
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            val socket = ctx.socketFactory.createSocket() as SSLSocket
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.startHandshake()
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            return BridgeConnection(socket, reader, seen)
        }

        /** Reads one message synchronously (handshake phase). */
        fun BridgeConnection.request(message: JSONObject): JSONObject {
            out.write((message.toString() + "\n").toByteArray())
            out.flush()
            val line = reader.readLine() ?: throw java.io.IOException("The computer closed the connection")
            socket.soTimeout = 0
            return JSONObject(line)
        }

        private fun sha256(der: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }

        @SuppressLint("DefaultLocale")
        fun msg(type: String, vararg fields: Pair<String, Any?>): JSONObject = JSONObject().apply {
            put("t", type)
            for ((k, v) in fields) {
                when (v) {
                    null -> Unit
                    is List<*> -> put(k, JSONArray(v))
                    else -> put(k, v)
                }
            }
        }
    }
}
