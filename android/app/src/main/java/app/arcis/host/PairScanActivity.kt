package app.arcis.host

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.widget.Toast
import androidx.activity.ComponentActivity
import app.arcis.ArcisApp
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.core.net.PairLink
import app.arcis.glasses.phoneName
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Pairs with a computer from the Arcis Bridge's QR code (tray › Pair a phone…): either
 * scans it with the phone camera, or receives the arcis://pair link when the phone's own
 * camera app scanned it. Always on the phone screen (the camera is there); the result is
 * shown on both screens.
 */
class PairScanActivity : ComponentActivity() {

    private val scan = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) finish() else handle(result.contents)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.dataString
        when {
            link != null -> handle(link)
            savedInstanceState == null -> scan.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Scan the code on the computer\n(Arcis Bridge in the tray › Pair a phone…)")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        }
    }

    private fun handle(text: String) {
        val link = PairLink.parse(text)
        if (link == null) {
            say("That's not an Arcis Bridge pairing code")
            finish()
            return
        }
        say("Pairing with ${link.host}…")
        // Stay (invisible) until the answer: Android hides toasts from finished activities.
        ArcisApp.instance.hosts.pairInBackground(link, phoneName(this)) { result ->
            result
                .onSuccess { say("Paired with ${it.name}") }
                .onFailure { say(it.message ?: "Pairing failed") }
            finish()
        }
    }

    private fun say(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        ArcisAccessibilityService.instance?.uiOverlay?.flash(text)
    }

    companion object {
        /** Opens the scanner on the phone screen, also when asked from the glasses. */
        fun start(context: Context) {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            context.startActivity(
                Intent(context, PairScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                options.toBundle(),
            )
        }
    }
}
