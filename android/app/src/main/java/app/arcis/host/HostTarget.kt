package app.arcis.host

import android.content.ComponentName
import app.arcis.core.input.Button
import app.arcis.core.input.Gesture
import app.arcis.core.input.ShapeCommand
import kotlin.math.abs

/**
 * Sends trackpad gestures to the connected computer (through its Arcis Bridge) while
 * Moonlight's stream is in front on the glasses. In Moonlight's own menus (computer and app
 * lists) the trackpad drives them like any phone app.
 */
class HostTarget(private val hosts: HostManager) {

    /** Set from the foreground app on the external screen. */
    @Volatile
    var streamInFront = false

    /** Debug builds only: act as if Moonlight were in front (testing without Moonlight). */
    @Volatile
    var forceActiveForTesting = false

    val active: Boolean get() = (streamInFront || forceActiveForTesting) && hosts.isConnected

    fun handle(g: Gesture): Boolean {
        if (!active) return false
        when (g) {
            is Gesture.PointerMove -> hosts.move(g.dx * POINTER_SCALE, g.dy * POINTER_SCALE)
            is Gesture.Click -> hosts.click(if (g.button == Button.LEFT) "left" else "right")
            Gesture.DragStart -> hosts.button("left", true)
            Gesture.DragEnd -> hosts.button("left", false)
            is Gesture.Scroll -> hosts.scroll(g.dx / SCROLL_STEP_PX, g.dy / SCROLL_STEP_PX)
            Gesture.Back -> hosts.key("Escape")
            is Gesture.ShapeDrawn -> shortcut(g.command)
            is Gesture.Zoom -> repeat(abs(g.steps)) { hosts.key(if (g.steps > 0) "Equal" else "Minus", primary()) }
            else -> return false
        }
        return true
    }

    /** Ctrl, or Cmd on a Mac. */
    private fun primary() = listOf(if (hosts.isMac) "meta" else "ctrl")

    private fun shortcut(command: ShapeCommand?) {
        val mod = primary()
        val mac = hosts.isMac
        when (command) {
            ShapeCommand.COPY -> hosts.key("C", mod)
            ShapeCommand.PASTE -> hosts.key("V", mod)
            ShapeCommand.CUT -> hosts.key("X", mod)
            ShapeCommand.UNDO -> hosts.key("Z", mod)
            ShapeCommand.REDO -> if (mac) hosts.key("Z", listOf("meta", "shift")) else hosts.key("Y", mod)
            ShapeCommand.SELECT_ALL -> hosts.key("A", mod)
            ShapeCommand.SAVE -> hosts.key("S", mod)
            ShapeCommand.FIND -> hosts.key("F", mod)
            ShapeCommand.ENTER -> hosts.key("Enter")
            ShapeCommand.BACKSPACE -> hosts.key("Backspace")
            ShapeCommand.TAB -> hosts.key("Tab")
            ShapeCommand.SPACE -> hosts.key("Space")
            ShapeCommand.DELETE -> hosts.key("Delete")
            ShapeCommand.LEFT -> hosts.key("Left")
            ShapeCommand.RIGHT -> hosts.key("Right")
            ShapeCommand.UP -> hosts.key("Up")
            ShapeCommand.DOWN -> hosts.key("Down")
            ShapeCommand.SWITCH_WINDOW -> hosts.key("Tab", listOf(if (mac) "meta" else "alt"))
            // Windows key (Start menu); Spotlight on a Mac.
            ShapeCommand.START_MENU -> if (mac) hosts.key("Space", listOf("meta")) else hosts.key("Meta")
            null -> Unit
        }
    }

    companion object {
        const val POINTER_SCALE = 1.0f
        const val SCROLL_STEP_PX = 40f

        /** Moonlight and its forks (Artemis etc.) share this package prefix. */
        fun isMoonlight(pkg: String?): Boolean = pkg?.startsWith("com.limelight") == true

        /** True while Moonlight's stream (not its menus) is in front. */
        fun isMoonlightStream(pkg: String?, activity: ComponentName?): Boolean =
            activity != null && isMoonlight(pkg) && activity.packageName == pkg && activity.className == Moonlight.STREAM_ACTIVITY
    }
}
