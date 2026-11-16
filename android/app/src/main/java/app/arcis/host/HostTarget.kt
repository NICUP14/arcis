package app.arcis.host

import app.arcis.core.input.Button
import app.arcis.core.input.Gesture
import app.arcis.core.input.ShapeCommand

/**
 * Sends trackpad gestures to the connected computer (through its Arcis Bridge) while the
 * Moonlight app is in front on the glasses.
 */
class HostTarget(private val hosts: HostManager) {

    /** Set from the foreground app on the external screen. */
    @Volatile
    var moonlightInFront = false

    /** Debug builds only: act as if Moonlight were in front (testing without Moonlight). */
    @Volatile
    var forceActiveForTesting = false

    val active: Boolean get() = (moonlightInFront || forceActiveForTesting) && hosts.isConnected

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
            else -> return false
        }
        return true
    }

    private fun shortcut(command: ShapeCommand?) {
        val mod = listOf(if (hosts.isMac) "meta" else "ctrl")
        when (command) {
            ShapeCommand.COPY -> hosts.key("C", mod)
            ShapeCommand.PASTE -> hosts.key("V", mod)
            ShapeCommand.UNDO -> hosts.key("Z", mod)
            ShapeCommand.ENTER -> hosts.key("Enter")
            ShapeCommand.BACKSPACE -> hosts.key("Backspace")
            null -> Unit
        }
    }

    companion object {
        const val POINTER_SCALE = 1.0f
        const val SCROLL_STEP_PX = 40f

        /** Moonlight and its forks (Artemis etc.) share this package prefix. */
        fun isMoonlight(pkg: String?): Boolean = pkg?.startsWith("com.limelight") == true
    }
}
