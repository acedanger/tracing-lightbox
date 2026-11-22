package com.tracinglightbox.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockGestureDetectorTest {

    private val config = UnlockConfig(
        zoneSizePx = 100f,
        maxTouchMajorPx = 50f,
        confirmButton = Box(400f, 400f, 600f, 500f),
    )

    private val d = UnlockGestureDetector(config)

    private fun down(id: Int, x: Float, y: Float, t: Long, major: Float = 10f) =
        d.onPointer(PointerSample(PointerAction.DOWN, id, x, y, major, t))

    private fun move(id: Int, x: Float, y: Float, t: Long, major: Float = 10f) =
        d.onPointer(PointerSample(PointerAction.MOVE, id, x, y, major, t))

    private fun up(id: Int, x: Float, y: Float, t: Long, major: Float = 10f) =
        d.onPointer(PointerSample(PointerAction.UP, id, x, y, major, t))

    private fun reachConfirming() {
        down(0, 10f, 10f, 0)
        d.onTick(3000)
        assertTrue(d.state is UnlockState.Confirming)
    }

    @Test
    fun holdInCornerForThreeSecondsShowsConfirm() {
        down(0, 10f, 10f, 0)
        d.onTick(2999)
        assertTrue(d.state is UnlockState.Holding)
        d.onTick(3000)
        assertEquals(UnlockState.Confirming(5000), d.state)
    }

    @Test
    fun ringAppearsAfterOneSecond() {
        down(0, 10f, 10f, 0)
        d.onTick(999)
        assertFalse((d.state as UnlockState.Holding).showRing)
        d.onTick(1000)
        val s = d.state as UnlockState.Holding
        assertTrue(s.showRing)
        assertEquals(0.333f, s.progress, 0.001f)
    }

    @Test
    fun releaseEarlyCancels() {
        down(0, 10f, 10f, 0)
        up(0, 10f, 10f, 2000)
        assertEquals(UnlockState.Idle, d.state)
        d.onTick(3500)
        assertEquals(UnlockState.Idle, d.state)
    }

    @Test
    fun leavingZoneCancels() {
        down(0, 10f, 10f, 0)
        move(0, 150f, 10f, 500)
        assertEquals(UnlockState.Idle, d.state)
    }

    @Test
    fun palmContactIgnored() {
        down(0, 10f, 10f, 0, major = 80f)
        d.onTick(4000)
        assertEquals(UnlockState.Idle, d.state)
    }

    @Test
    fun palmGrowingDuringHoldCancels() {
        down(0, 10f, 10f, 0, major = 10f)
        move(0, 12f, 12f, 500, major = 80f)
        assertEquals(UnlockState.Idle, d.state)
    }

    @Test
    fun pointersOutsideZoneNeverStartHold() {
        down(0, 500f, 500f, 0)
        down(1, 50f, 300f, 10)
        down(2, 300f, 50f, 20)
        d.onTick(10_000)
        assertEquals(UnlockState.Idle, d.state)
    }

    @Test
    fun extraPointersDoNotDisturbHold() {
        down(0, 10f, 10f, 0)
        down(1, 500f, 500f, 500)
        move(1, 520f, 540f, 1000)
        up(1, 520f, 540f, 1500)
        d.onTick(3000)
        assertTrue(d.state is UnlockState.Confirming)
    }

    @Test
    fun tapOnConfirmUnlocks() {
        reachConfirming()
        down(1, 500f, 450f, 3100)
        up(1, 500f, 450f, 3200)
        assertEquals(UnlockState.Unlocked, d.state)
    }

    @Test
    fun tapOutsideConfirmDoesNothing() {
        reachConfirming()
        down(1, 100f, 900f, 3100)
        up(1, 100f, 900f, 3200)
        assertTrue(d.state is UnlockState.Confirming)
    }

    @Test
    fun palmTapOnConfirmDoesNotUnlock() {
        reachConfirming()
        down(1, 500f, 450f, 3100, major = 80f)
        up(1, 500f, 450f, 3200, major = 80f)
        assertTrue(d.state is UnlockState.Confirming)
    }

    @Test
    fun longPressOnConfirmDoesNotUnlock() {
        reachConfirming()
        down(1, 500f, 450f, 3100)
        up(1, 500f, 450f, 3700)
        assertTrue(d.state is UnlockState.Confirming)
    }

    @Test
    fun strokeAcrossConfirmDoesNotUnlock() {
        reachConfirming()
        down(1, 420f, 420f, 3100)
        move(1, 580f, 480f, 3200)
        up(1, 580f, 480f, 3300)
        assertTrue(d.state is UnlockState.Confirming)
    }

    @Test
    fun tapThatGrowsIntoPalmDoesNotUnlock() {
        reachConfirming()
        down(1, 500f, 450f, 3100)
        move(1, 500f, 450f, 3150, major = 80f)
        up(1, 500f, 450f, 3200)
        assertTrue(d.state is UnlockState.Confirming)
    }

    @Test
    fun confirmingTimesOutToIdle() {
        reachConfirming()
        d.onTick(8000)
        assertEquals(UnlockState.Idle, d.state)
    }

    @Test
    fun resetReturnsToIdle() {
        reachConfirming()
        down(1, 500f, 450f, 3100)
        up(1, 500f, 450f, 3200)
        assertEquals(UnlockState.Unlocked, d.state)
        d.reset()
        assertEquals(UnlockState.Idle, d.state)
    }
}
