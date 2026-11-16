package app.arcis.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackpadGestureEngineTest {

    private val out = mutableListOf<Gesture>()
    private lateinit var engine: TrackpadGestureEngine
    private var t = 1_000L

    // 2000 x 1000 landscape surface, bottom edge = last 90 px, right edge = last 100 px.
    @Before
    fun setUp() {
        engine = TrackpadGestureEngine(2000f, 1000f, GestureConfig(rightEdge = 100f)) { out += it }
    }

    // ---- scripting helpers -------------------------------------------------

    private val fingers = linkedMapOf<Int, TouchPoint>()

    private fun down(id: Int, x: Float, y: Float, dt: Long = 0) {
        t += dt
        fingers[id] = TouchPoint(id, x, y)
        engine.onTouch(TouchEvent(TouchAction.DOWN, fingers.values.toList(), id, t))
    }

    private fun move(id: Int, x: Float, y: Float, dt: Long = 16) {
        t += dt
        fingers[id] = TouchPoint(id, x, y)
        engine.onTouch(TouchEvent(TouchAction.MOVE, fingers.values.toList(), id, t))
        fireTimerIfDue()
    }

    private fun moveBoth(dx: Float, dy: Float, steps: Int = 5) {
        repeat(steps) {
            t += 16
            fingers.replaceAll { _, p -> p.copy(x = p.x + dx / steps, y = p.y + dy / steps) }
            engine.onTouch(TouchEvent(TouchAction.MOVE, fingers.values.toList(), fingers.keys.first(), t))
        }
    }

    private fun up(id: Int, dt: Long = 0) {
        t += dt
        engine.onTouch(TouchEvent(TouchAction.UP, fingers.values.toList(), id, t))
        fingers.remove(id)
    }

    private fun hold(ms: Long) {
        t += ms
        fireTimerIfDue()
    }

    private fun fireTimerIfDue() {
        engine.nextTimerAt()?.let { if (t >= it) engine.onTimer(t) }
    }

    private inline fun <reified T : Gesture> only(): List<T> = out.filterIsInstance<T>()

    // ---- trackpad ----------------------------------------------------------

    @Test
    fun tapIsLeftClick() {
        down(0, 500f, 500f)
        up(0, dt = 80)
        assertEquals(listOf(Gesture.Click(Button.LEFT)), out)
    }

    @Test
    fun smallJitterStillTaps() {
        down(0, 500f, 500f)
        move(0, 510f, 505f)
        up(0, dt = 60)
        assertEquals(listOf(Gesture.Click(Button.LEFT)), out)
    }

    @Test
    fun dragMovesPointerWithoutClicking() {
        down(0, 500f, 500f)
        move(0, 560f, 500f)
        move(0, 620f, 520f)
        up(0)
        val moves = only<Gesture.PointerMove>()
        assertTrue(moves.isNotEmpty())
        assertTrue(moves.sumOf { it.dx.toDouble() } > 120.0) // accelerated beyond finger travel
        assertTrue(only<Gesture.Click>().isEmpty())
    }

    @Test
    fun fasterFingerGetsMoreGain() {
        assertTrue(engine.pointerGain(3f) > engine.pointerGain(0.2f))
        assertTrue(engine.pointerGain(1000f) <= engine.config.maxPointerGain)
    }

    @Test
    fun longPressIsRightClickOnce() {
        down(0, 500f, 500f)
        hold(400)
        hold(200)
        up(0, dt = 100)
        // The shape "armed" tick comes first (rested still), then the right click.
        assertEquals(listOf(Gesture.ShapeArmed, Gesture.Click(Button.RIGHT)), out)
    }

    @Test
    fun restThenDrawRecognizesShape() {
        down(0, 600f, 300f)
        hold(400)
        assertEquals(listOf(Gesture.ShapeArmed), out)
        // Draw a "V": down-right then up-right.
        for (i in 1..10) move(0, 600f + i * 15f, 300f + i * 30f)
        for (i in 1..10) move(0, 750f + i * 15f, 600f - i * 30f)
        up(0)
        assertEquals(Gesture.ShapeDrawn(ShapeCommand.PASTE), out.last())
        assertTrue(only<Gesture.PointerMove>().isEmpty())
        assertTrue(only<Gesture.Click>().isEmpty())
    }

    @Test
    fun restThenScribbleIsUnrecognized() {
        down(0, 600f, 300f)
        hold(400)
        move(0, 640f, 300f)
        move(0, 600f, 302f)
        move(0, 641f, 304f)
        up(0)
        assertEquals(Gesture.ShapeDrawn(null), out.last())
    }

    @Test
    fun movingWithoutRestingStillMovesPointer() {
        down(0, 600f, 300f)
        move(0, 700f, 300f, dt = 100)
        up(0)
        assertTrue(only<Gesture.PointerMove>().isNotEmpty())
        assertTrue(only<Gesture.ShapeDrawn>().isEmpty())
    }

    @Test
    fun doubleTapHoldDrags() {
        down(0, 500f, 500f)
        up(0, dt = 60)
        down(0, 505f, 500f, dt = 120)
        move(0, 600f, 500f)
        move(0, 700f, 500f)
        up(0)
        assertEquals(Gesture.Click(Button.LEFT), out.first())
        assertTrue(out.contains(Gesture.DragStart))
        assertEquals(Gesture.DragEnd, out.last())
        assertEquals(1, out.count { it == Gesture.DragStart })
        assertEquals(1, out.count { it == Gesture.DragEnd }) // the button is released once
        assertTrue(only<Gesture.PointerMove>().isNotEmpty())
    }

    @Test
    fun twoTapsFarApartAreTwoClicksNotDrag() {
        down(0, 500f, 500f); up(0, dt = 60)
        down(0, 1500f, 500f, dt = 120); up(0, dt = 60)
        assertEquals(listOf(Gesture.Click(Button.LEFT), Gesture.Click(Button.LEFT)), out)
    }

    @Test
    fun twoFingerTapIsBack() {
        down(0, 500f, 500f)
        down(1, 600f, 500f, dt = 20)
        up(1, dt = 80)
        up(0, dt = 10)
        assertEquals(listOf(Gesture.Back), out)
    }

    @Test
    fun twoFingerDragScrollsNaturally() {
        down(0, 500f, 600f)
        down(1, 600f, 600f, dt = 20)
        moveBoth(0f, -200f) // fingers move up
        up(1); up(0)
        val scrolls = only<Gesture.Scroll>()
        assertTrue(scrolls.isNotEmpty())
        assertTrue(scrolls.sumOf { it.dy.toDouble() } > 0) // natural: fingers up = content scrolls down
        assertTrue(only<Gesture.Click>().isEmpty())
        assertTrue(!out.contains(Gesture.Back))
    }

    @Test
    fun lateSecondFingerStillScrolls() {
        down(0, 500f, 600f)
        move(0, 540f, 600f) // first finger already moving
        down(1, 640f, 600f, dt = 40)
        moveBoth(0f, 200f)
        up(1); up(0)
        assertTrue(only<Gesture.Scroll>().isNotEmpty())
    }

    @Test
    fun rightEdgeSlideScrolls() {
        down(0, 1950f, 500f)
        move(0, 1950f, 400f)
        move(0, 1950f, 300f)
        up(0)
        val scrolls = only<Gesture.Scroll>()
        assertTrue(scrolls.isNotEmpty())
        assertTrue(only<Gesture.PointerMove>().isEmpty())
    }

    // ---- bottom edge -------------------------------------------------------

    @Test
    fun bottomEdgeSwipeLeftIsNextApp() {
        down(0, 1000f, 970f)
        move(0, 900f, 968f)
        move(0, 700f, 965f)
        up(0)
        assertEquals(listOf(Gesture.SwitchApp(SwitchDirection.NEXT)), out)
    }

    @Test
    fun bottomEdgeSwipeRightIsPreviousApp() {
        down(0, 1000f, 970f)
        move(0, 1100f, 968f)
        move(0, 1300f, 965f)
        up(0)
        assertEquals(listOf(Gesture.SwitchApp(SwitchDirection.PREVIOUS)), out)
    }

    @Test
    fun shortBottomSwipeDoesNothing() {
        down(0, 1000f, 970f)
        move(0, 1060f, 968f)
        up(0)
        assertTrue(out.isEmpty())
    }

    @Test
    fun quickSwipeUpIsHome() {
        down(0, 1000f, 970f)
        move(0, 1000f, 900f)
        move(0, 1000f, 750f)
        up(0, dt = 10)
        assertEquals(listOf(Gesture.Home), out)
    }

    @Test
    fun swipeUpAndHoldShowsRecentsThenOpens() {
        down(0, 1000f, 970f)
        move(0, 1000f, 900f)
        move(0, 1000f, 750f)
        hold(300) // hold still
        move(0, 1000f, 751f) // tiny move delivers the timer
        assertEquals(listOf(Gesture.RecentsShow), out)
        move(0, 1150f, 750f) // one card right
        move(0, 1300f, 750f) // two cards right
        up(0)
        assertEquals(Gesture.RecentsShow, out[0])
        assertEquals(2, only<Gesture.RecentsMove>().sumOf { it.steps })
        assertEquals(Gesture.RecentsOpenSelected, out.last())
        assertTrue(!out.contains(Gesture.Home))
    }

    @Test
    fun recentsFlickUpClosesCard() {
        down(0, 1000f, 970f)
        move(0, 1000f, 800f)
        hold(300)
        engine.onTimer(t)
        move(0, 1000f, 500f)
        up(0)
        assertEquals(Gesture.RecentsCloseSelected, out.last())
    }

    @Test
    fun bottomEdgeDoesNotMovePointer() {
        down(0, 1000f, 970f)
        move(0, 700f, 965f)
        up(0)
        assertTrue(only<Gesture.PointerMove>().isEmpty())
    }
}
