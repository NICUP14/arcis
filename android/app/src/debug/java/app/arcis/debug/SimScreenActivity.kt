package app.arcis.debug

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Debug-only: asks for screen-capture consent, then starts [SimScreenService],
 * which creates a public presentation display that behaves like real glasses.
 *
 *   adb shell am start -n app.arcis/.debug.SimScreenActivity
 */
class SimScreenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST)
    }

    @Deprecated("Activity result API is fine for a debug tool")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST && resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, SimScreenService::class.java)
                    .setAction(SimScreenService.ACTION_START)
                    .putExtra(SimScreenService.EXTRA_CODE, resultCode)
                    .putExtra(SimScreenService.EXTRA_DATA, data),
            )
        }
        finish()
    }

    private companion object {
        const val REQUEST = 1
    }
}
