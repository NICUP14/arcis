package app.arcis.input

import android.content.Context
import android.util.Log
import app.arcis.ArcisApp
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.core.input.Button
import app.arcis.core.input.Gesture
import app.arcis.core.input.ShapeCommand
import app.arcis.glasses.GlassesLauncher

/**
 * Sends trackpad gestures to whatever is in front on the external screen.
 * Phone apps (and Arcis Home itself) are driven through the accessibility service.
 */
class InputRouter(private val context: Context) {

    fun handle(gesture: Gesture) {
        if (gesture !is Gesture.PointerMove && gesture !is Gesture.Scroll) Log.d(TAG, "gesture $gesture")
        if (ArcisApp.instance.switcher.handle(gesture, context)) return
        if (ArcisApp.instance.bluetoothTarget.handle(gesture)) return
        if (ArcisApp.instance.hostTarget.handle(gesture)) return
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
            is Gesture.ShapeDrawn -> shortcut(svc, g)
            else -> Unit
        }
    }

    private fun shortcut(svc: ArcisAccessibilityService, g: Gesture.ShapeDrawn) {
        val overlay = svc.uiOverlay
        val command = g.command ?: run {
            overlay?.flash("Shape not recognized")
            return
        }
        val result = svc.performEdit(command)
        Log.d(TAG, "shortcut $command -> $result")
        val message = when (result) {
            ArcisAccessibilityService.EditResult.DONE -> {
                // Copied in a phone app: also put it on the connected computer's clipboard.
                val hosts = ArcisApp.instance.hosts
                if (command == ShapeCommand.COPY && hosts.isConnected) svc.lastCopiedText?.let(hosts::sendClipboard)
                DONE_LABELS.getValue(command)
            }
            ArcisAccessibilityService.EditResult.NO_FIELD -> "Select a text field first"
            ArcisAccessibilityService.EditResult.UNSUPPORTED ->
                if (command == ShapeCommand.UNDO) "Undo needs Android 13 or newer" else "Not possible here"
        }
        overlay?.flash(message)
    }

    private fun goHome() {
        val screen = ArcisApp.instance.displays.screen.value ?: return
        GlassesLauncher.showHome(context, screen.displayId)
    }

    private companion object {
        const val TAG = "ArcisInput"
        val DONE_LABELS = mapOf(
            ShapeCommand.COPY to "Copied",
            ShapeCommand.PASTE to "Pasted",
            ShapeCommand.UNDO to "Undone",
            ShapeCommand.ENTER to "Enter",
            ShapeCommand.BACKSPACE to "Deleted",
        )
    }
}
