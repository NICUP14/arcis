package app.arcis.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Debug-only simulated external screen (1920x1080) for testing without glasses.
 *
 * Unlike the developer-option "overlay" display, this public virtual display is
 * accepted by the accessibility system, so injected gestures can be tested.
 *
 *   dump a frame:  adb shell am start-foreground-service -n app.arcis/.debug.SimScreenService -a dump
 *                  then adb pull /sdcard/Android/data/app.arcis/files/sim.png
 *   stop:          adb shell am start-foreground-service -n app.arcis/.debug.SimScreenService -a stop
 */
class SimScreenService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_DUMP -> dump()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        if (display != null) return
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA) ?: return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(code, data) ?: return
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf()
        }, Handler(Looper.getMainLooper()))
        val r = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2)
        display = mp.createVirtualDisplay(
            "Arcis simulated screen",
            WIDTH,
            HEIGHT,
            DPI,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            r.surface,
            null,
            null,
        )
        projection = mp
        reader = r
        Log.i(TAG, "simulated screen created: displayId=${display?.display?.displayId}")
    }

    private fun dump() {
        val image = reader?.acquireLatestImage() ?: run {
            Log.w(TAG, "no frame available")
            return
        }
        image.use { img ->
            val plane = img.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * WIDTH
            val bmp = Bitmap.createBitmap(WIDTH + rowPadding / plane.pixelStride, HEIGHT, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            val out = File(getExternalFilesDir(null), "sim.png")
            out.outputStream().use { Bitmap.createBitmap(bmp, 0, 0, WIDTH, HEIGHT).compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i(TAG, "frame written to $out")
        }
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Simulated screen (debug)", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Arcis simulated screen")
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .build()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_DUMP = "dump"
        const val ACTION_STOP = "stop"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val TAG = "ArcisSim"
        private const val CHANNEL = "sim"
        private const val WIDTH = 1920
        private const val HEIGHT = 1080
        private const val DPI = 240
    }
}
