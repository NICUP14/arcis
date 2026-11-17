package app.arcis.core.media

import app.arcis.core.input.Button
import app.arcis.core.input.Gesture
import app.arcis.core.input.ShapeCommand
import app.arcis.core.input.SwitchDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaGestureMapTest {

    private val map = MediaGestureMap()

    private fun swipe(dx: Float, dy: Float = 0f, parts: Int = 8): MediaAction? {
        repeat(parts) { assertEquals(MediaAction.Nothing, map.map(Gesture.PointerMove(dx / parts, dy / parts))) }
        return map.map(Gesture.MoveEnd)
    }

    @Test
    fun tapPlaysAndPauses() {
        assertEquals(MediaAction.PlayPause, map.map(Gesture.Click(Button.LEFT)))
    }

    @Test
    fun longPressLeavesMediaMode() {
        assertEquals(MediaAction.Exit, map.map(Gesture.Click(Button.RIGHT)))
    }

    @Test
    fun horizontalSwipesSeekMoreForLongerSwipes() {
        assertEquals(MediaAction.Seek(10), swipe(200f))
        assertEquals(MediaAction.Seek(-10), swipe(-200f))
        assertEquals(MediaAction.Seek(20), swipe(600f))
        assertEquals(MediaAction.Seek(60), swipe(5000f)) // capped
    }

    @Test
    fun shortOrVerticalSwipesDoNothing() {
        assertEquals(MediaAction.Nothing, swipe(60f))
        assertEquals(MediaAction.Nothing, swipe(200f, dy = 300f))
        // Each swipe starts from zero.
        assertEquals(MediaAction.Seek(10), swipe(150f))
    }

    @Test
    fun edgeOrTwoFingerScrollChangesVolume() {
        assertEquals(MediaAction.Nothing, map.map(Gesture.Scroll(0f, 80f)))
        assertEquals(MediaAction.Volume(1), map.map(Gesture.Scroll(0f, 80f))) // 160 px: one step, 40 left
        assertEquals(MediaAction.Volume(-2), map.map(Gesture.Scroll(0f, -300f)))
    }

    @Test
    fun drawnLinesSeekAndChangeVolume() {
        assertEquals(MediaAction.Seek(10), map.map(Gesture.ShapeDrawn(ShapeCommand.RIGHT)))
        assertEquals(MediaAction.Seek(-10), map.map(Gesture.ShapeDrawn(ShapeCommand.LEFT)))
        assertEquals(MediaAction.Volume(1), map.map(Gesture.ShapeDrawn(ShapeCommand.UP)))
        assertEquals(MediaAction.Nothing, map.map(Gesture.ShapeDrawn(ShapeCommand.COPY)))
    }

    @Test
    fun switchingAndBackStayAsTheyAre() {
        assertEquals(MediaAction.Back, map.map(Gesture.Back))
        assertNull(map.map(Gesture.SwitchApp(SwitchDirection.NEXT)))
        assertNull(map.map(Gesture.Home))
        assertNull(map.map(Gesture.RecentsShow))
    }
}
