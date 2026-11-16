package app.arcis.bt

import app.arcis.core.hid.HidKeys
import app.arcis.core.input.Button
import app.arcis.core.input.Gesture
import app.arcis.core.input.ShapeCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Sends trackpad gestures to the connected Bluetooth device while its "controlling"
 * screen is in front on the glasses.
 */
class BluetoothTarget(private val hid: HidPeripheral) {

    private val _active = MutableStateFlow(false)

    /** True while the Bluetooth screen is in front and a device is connected. */
    val activeState: StateFlow<Boolean> = _active.asStateFlow()

    var active: Boolean
        get() = _active.value
        set(value) {
            _active.value = value
        }

    private var scrollRemainder = 0f
    private var moveRemainderX = 0f
    private var moveRemainderY = 0f

    /** Returns true if the gesture was sent to the Bluetooth device. */
    fun handle(g: Gesture): Boolean {
        if (!active || hid.state.value.connected == null) return false
        when (g) {
            is Gesture.PointerMove -> {
                // The remote cursor moves in device pixels; keep sub-pixel remainders.
                moveRemainderX += g.dx * POINTER_SCALE
                moveRemainderY += g.dy * POINTER_SCALE
                val dx = moveRemainderX.roundToInt()
                val dy = moveRemainderY.roundToInt()
                moveRemainderX -= dx
                moveRemainderY -= dy
                if (dx != 0 || dy != 0) hid.move(dx, dy)
            }
            is Gesture.Click -> hid.click(if (g.button == Button.LEFT) HidPeripheral.BUTTON_LEFT else HidPeripheral.BUTTON_RIGHT)
            Gesture.DragStart -> hid.press(HidPeripheral.BUTTON_LEFT)
            Gesture.DragEnd -> hid.release(HidPeripheral.BUTTON_LEFT)
            is Gesture.Scroll -> {
                // Our dy > 0 = show content further down = wheel down (negative).
                scrollRemainder -= g.dy / SCROLL_STEP_PX
                val steps = scrollRemainder.toInt()
                scrollRemainder -= steps
                if (steps != 0) hid.scroll(steps)
            }
            Gesture.Back -> hid.key(HidKeys.Stroke(HidKeys.ESCAPE))
            is Gesture.ShapeDrawn -> shortcut(g.command)
            else -> return false
        }
        return true
    }

    private fun shortcut(command: ShapeCommand?) {
        val ctrl = HidKeys.MOD_CTRL
        when (command) {
            ShapeCommand.COPY -> hid.key(HidKeys.Stroke(0x06, ctrl)) // C
            ShapeCommand.PASTE -> hid.key(HidKeys.Stroke(0x19, ctrl)) // V
            ShapeCommand.UNDO -> hid.key(HidKeys.Stroke(0x1D, ctrl)) // Z
            ShapeCommand.ENTER -> hid.key(HidKeys.Stroke(HidKeys.ENTER))
            ShapeCommand.BACKSPACE -> hid.key(HidKeys.Stroke(HidKeys.BACKSPACE))
            null -> Unit
        }
    }

    private companion object {
        const val POINTER_SCALE = 0.8f
        const val SCROLL_STEP_PX = 40f
    }
}
