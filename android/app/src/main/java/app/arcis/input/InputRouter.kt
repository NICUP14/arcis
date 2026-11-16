package app.arcis.input

import android.content.Context
import app.arcis.ArcisApp
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.core.input.Button
import app.arcis.core.input.Gesture
import app.arcis.glasses.GlassesLauncher

/**
 * Sends trackpad gestures to whatever is in front on the external screen.
 * Phone apps (and Arcis Home itself) are driven through the accessibility service.
 */
class InputRouter(private val context: Context) {

    fun handle(gesture: Gesture) {
        if (ArcisApp.instance.switcher.handle(gesture, context)) return
        when (gesture) {
            Gesture.Home -> goHome()
            else -> toAccessibility(gesture)
        }
    }

    private fun toAccessibility(g: Gesture) {
        val svc = ArcisAccessibilityService.instance ?: return
        when (g) {
            is Gesture.PointerMove -> svc.movePointer(g.dx, g.dy)
            is Gesture.Click -> if (g.button == Button.LEFT) svc.click() else svc.longPress()
            Gesture.DragStart -> svc.dragStart()
            Gesture.DragEnd -> svc.dragEnd()
            is Gesture.Scroll -> svc.scroll(g.dx, g.dy)
            Gesture.Back -> svc.back()
            else -> Unit
        }
    }

    private fun goHome() {
        val screen = ArcisApp.instance.displays.screen.value ?: return
        GlassesLauncher.showHome(context, screen.displayId)
    }
}
