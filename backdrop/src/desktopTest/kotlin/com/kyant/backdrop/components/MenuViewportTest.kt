package com.kyant.backdrop.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MenuViewportTest {
    private class Fixture(val limit: Float) {
        var offset = 0f
        var flingVelocity = 0f
        private val scroll = ScrollableState { delta ->
            val before = offset
            offset = (before + delta).coerceIn(0f, limit)
            offset - before
        }
        private val bounds = object : ScrollableState by scroll {
            override val canScrollForward get() = offset < limit
            override val canScrollBackward get() = offset > 0f
        }
        private val fling = object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                flingVelocity = initialVelocity
                return 0f
            }
        }
        val menu = MenuViewport(bounds, fling, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test fun reversalUnwindsTheEdgeBeforeScrollingTheRows() {
        val f = Fixture(100f)
        f.menu.drag(Offset(3f, -130f), scrollRows = true)
        assertEquals(100f, f.offset, .001f)
        assertEquals(-100f, f.menu.fingerScroll, .001f)
        assertEquals(Offset(3f, -30f), f.menu.pull)
        f.menu.drag(Offset(0f, 20f), scrollRows = true)
        assertEquals(100f, f.offset, .001f)
        assertEquals(Offset(3f, -10f), f.menu.pull)
        f.menu.drag(Offset(0f, 45f), scrollRows = true)
        assertEquals(65f, f.offset, .001f)
        assertEquals(-65f, f.menu.fingerScroll, .001f)
        assertEquals(Offset(3f, 0f), f.menu.pull)
        f.menu.release(Velocity(0f, 100f))
        assertEquals(-100f, f.flingVelocity, .001f)
        assertFalse(f.menu.dragging)
    }

    @Test fun shortMenuTracksBothAxesAndReleasesWithoutFlinging() {
        val f = Fixture(0f)
        f.menu.drag(Offset(20f, 75f), scrollRows = true)
        assertEquals(Offset(20f, 75f), f.menu.pull)
        f.menu.release(Velocity(120f, 900f))
        assertEquals(Velocity(120f, 900f), f.menu.releaseVelocity)
        assertEquals(Offset(20f, 75f), f.menu.releasedPull)
        assertEquals(Offset.Zero, f.menu.pull)
        assertEquals(0f, f.flingVelocity, .001f)
    }

    @Test fun releasingAnOverscrollDoesNotRestartTheList() {
        val f = Fixture(100f)
        f.menu.drag(Offset(0f, 20f), scrollRows = true)
        f.menu.release(Velocity(0f, -800f))
        assertEquals(0f, f.flingVelocity, .001f)
        assertEquals(Offset.Zero, f.menu.pull)
    }

    @Test fun velocityChangesTheReboundAtTheSameReleasePosition() {
        fun motion(speed: Float): List<MenuPose> {
            val model = MenuElasticity(1f, GlassPressTokens())
            val pull = Offset(30f, 100f)
            repeat(90) { model.advance(pull, 0f, true, 16_000_000L) }
            val held = model.pose
            model.lift(pull, Velocity(speed * .3f, speed))
            assertEquals(held, model.pose)
            return List(91) {
                model.advance(Offset.Zero, 0f, false, 16_000_000L)
                model.pose
            }
        }
        val fast = motion(1600f)
        val slow = motion(100f)
        assertTrue(fast.first().center.y > slow.first().center.y)
        assertTrue(fast.first().stretch.y > slow.first().stretch.y)
        assertTrue(fast.any { it.center.y < 0f && it.stretch.y < 0f })
        assertEquals(MenuPose(), fast.last())
    }

    @Test fun largePullsKeepExtendingAndRecoverFromTheDisplayedPose() {
        val model = MenuElasticity(1f, GlassPressTokens())
        var lastHeight = 0f
        for (distance in listOf(80f, 160f, 260f, 380f, 500f)) {
            repeat(85) { model.advance(Offset(0f, distance), 0f, true, 16_666_667L) }
            val height = 146f * menuStretchScale(model.pose.stretch.y, 146f)
            assertTrue("long pull stopped at $distance", height > lastHeight + 0.5f)
            lastHeight = height
        }
        val before = model.pose
        model.attachScroll(0f)
        assertEquals(before, model.pose)
        model.advance(Offset.Zero, 0f, false, 16_666_667L)
        assertTrue(model.pose.center.y in before.center.y * .8f..before.center.y)
        repeat(130) { model.advance(Offset.Zero, 0f, false, 16_666_667L) }
        assertEquals(MenuPose(), model.pose)
    }

    @Test fun holdingSteadyPullConvergesMonotonicallyWithoutPrematureRebound() {
        val model = MenuElasticity(1f, GlassPressTokens())
        val insidePull = Offset(14f, 18f)
        val insidePoses = List(90) {
            model.advance(insidePull, 0f, dragging = true, nanos = 16_666_667L)
            model.pose
        }
        assertTrue(
            "Held inside pull should not overshoot and bounce back >0.35px (max=${insidePoses.maxOf { it.center.y }}, end=${insidePoses.last().center.y})",
            insidePoses.maxOf { it.center.y } - insidePoses.last().center.y <= 0.35f,
        )

        val outsideModel = MenuElasticity(1f, GlassPressTokens())
        val outsidePull = Offset(0f, 140f)
        val outsidePoses = List(90) {
            outsideModel.advance(outsidePull, 0f, dragging = true, nanos = 16_666_667L)
            outsideModel.pose
        }
        assertTrue(
            "Held outside pull should stay stretched without rebounding mid-hold",
            outsidePoses.maxOf { it.stretch.y } - outsidePoses.last().stretch.y <= 0.35f && outsidePoses.last().stretch.y > 3.5f,
        )
    }

    @Test fun batchedReleaseKeepsMovingVelocityAndAStationaryHoldLosesIt() {
        val measured = DragReleaseVelocity(0, Offset.Zero)
        measured.move(640, Offset(8f, 0f))
        measured.move(690, Offset(58f, -25f))
        measured.move(690, Offset(58f, -25f)) // Up can share a timestamp with the final move.
        val up = measured.atRelease(696)
        assertEquals(1000f, up.x, 2f)
        assertEquals(-500f, up.y, 2f)
        assertEquals(Velocity.Zero, measured.atRelease(840))
    }

    @Test fun verticalPullStretchesHeightAndContractsWidthProportionally() {
        val model = MenuElasticity(1f, GlassPressTokens())
        repeat(80) { model.advance(Offset(0f, 220f), 0f, true, 16_666_667L) }
        val scaleY = menuStretchScale(model.pose.stretch.y, 560f)
        val scaleX = menuStretchScale(model.pose.stretch.x, 250f)
        assertTrue("Expected vertical stretch > 1, got $scaleY", scaleY > 1.01f)
        assertTrue("Expected horizontal contraction < 1, got $scaleX", scaleX in 0.90f..0.998f)
        repeat(120) { model.advance(Offset.Zero, 0f, false, 16_666_667L) }
        assertEquals(1f, menuStretchScale(model.pose.stretch.x, 250f), 0.001f)
        assertEquals(1f, menuStretchScale(model.pose.stretch.y, 560f), 0.001f)
    }

    @Test fun pushingTowardSourceSquashesInsteadOfReverseStretching() {
        val topAnchored = MenuElasticity(1f, GlassPressTokens(), horizontalAway = -1f, verticalAway = 1f)
        repeat(80) { topAnchored.advance(Offset(0f, -220f), 0f, true, 16_666_667L) }
        val squashY = menuStretchScale(topAnchored.pose.stretch.y, 320f)
        val widenX = menuStretchScale(topAnchored.pose.stretch.x, 250f)
        assertTrue("Expected vertical squash toward top anchor < 0.998, got $squashY", squashY < 0.998f)
        assertTrue("Expected slight horizontal widening > 1.001, got $widenX", widenX > 1.001f)

        val bottomAnchored = MenuElasticity(1f, GlassPressTokens(), horizontalAway = 1f, verticalAway = -1f)
        repeat(80) { bottomAnchored.advance(Offset(0f, 220f), 0f, true, 16_666_667L) }
        assertTrue(menuStretchScale(bottomAnchored.pose.stretch.y, 320f) < 0.998f)
        repeat(80) { bottomAnchored.advance(Offset(0f, -220f), 0f, true, 16_666_667L) }
        assertTrue(menuStretchScale(bottomAnchored.pose.stretch.y, 320f) > 1.015f)
    }

    @Test fun holdDragSplitsScrollableStretchFromShortMenuSelection() {
        // Case 1: Scrollable long menu hold-drag only stretches and stays open on release.
        assertFalse(
            shouldCommitHoldDragRelease(
                openedByThisPress = true,
                scrollable = true,
                moved = true,
                insidePanel = true,
                keepOpen = true,
                trackingHistory = false,
                containsPanel = true,
            )
        )
        // Case 2: Non-scrollable short/medium menu hold-drag commits hovered item on release inside panel.
        assertTrue(
            shouldCommitHoldDragRelease(
                openedByThisPress = true,
                scrollable = false,
                moved = true,
                insidePanel = true,
                keepOpen = false,
                trackingHistory = false,
                containsPanel = true,
            )
        )
        // Releasing outside panel or without moving leaves the short menu open.
        assertFalse(
            shouldCommitHoldDragRelease(
                openedByThisPress = true,
                scrollable = false,
                moved = true,
                insidePanel = false,
                keepOpen = false,
                trackingHistory = false,
                containsPanel = false,
            )
        )
        assertFalse(
            shouldCommitHoldDragRelease(
                openedByThisPress = true,
                scrollable = false,
                moved = false,
                insidePanel = true,
                keepOpen = false,
                trackingHistory = false,
                containsPanel = true,
            )
        )
    }

    @Test fun insideDragProducesGentleSteadyStretchAndReboundsCleanlyOnRelease() {
        val rest = androidx.compose.ui.geometry.Rect(100f, 100f, 350f, 360f)
        assertEquals(Offset.Zero, computeMenuBoundaryPull(Offset(220f, 200f), rest))
        assertEquals(Offset(0f, 45f), computeMenuBoundaryPull(Offset(220f, 405f), rest))
        assertEquals(Offset(-30f, -15f), computeMenuBoundaryPull(Offset(70f, 85f), rest))

        val model = MenuElasticity(1f, GlassPressTokens())
        // Fast interior drag produces smooth velocity inertia stretch along the slide direction,
        // then settles with a gentle iOS 27.3-matched stop-bounce while still holding.
        val slideFrames = List(18) {
            model.advance(
                pull = Offset.Zero,
                scrolled = 0f,
                dragging = true,
                nanos = 16_666_667L,
                fingerVelocity = Offset(0f, 900f),
                fingerMoving = true,
            )
            model.pose
        }
        val peakStretchY = slideFrames.maxOf { it.stretch.y }
        assertTrue("Expected dynamic inside stretch in 1.5..5.0px, got $peakStretchY", peakStretchY in 1.5f..5.0f)
        assertTrue("Expected area-conserving cross-axis contraction < -1.0px, got ${model.pose.stretch.x}", model.pose.stretch.x < -1.0f)

        val stopFrames = List(90) {
            model.advance(
                pull = Offset.Zero,
                scrolled = 0f,
                dragging = true,
                nanos = 16_666_667L,
                fingerVelocity = Offset.Zero,
                fingerMoving = true,
            )
            model.pose
        }
        val minStopStretch = stopFrames.minOf { it.stretch.y }
        assertTrue(
            "Expected gentle iOS 27.3 stop-bounce while still holding (-22%..0%), got min=$minStopStretch (peak=$peakStretchY)",
            minStopStretch in (-peakStretchY * 0.22f)..-0.02f,
        )
        assertEquals(MenuPose(), stopFrames.last())

        // Releasing while pull is zero must not fling the panel.
        model.lift(Offset.Zero, Velocity(400f, 1200f))
        model.advance(Offset.Zero, 0f, dragging = false, nanos = 16_666_667L)
        assertEquals(MenuPose(), model.pose)
    }

    @Test fun outsideBoundaryPullHoldsSteadyUntilReleaseThenRebounds() {
        val model = MenuElasticity(1f, GlassPressTokens())
        val outsidePull = Offset(0f, 140f)
        val heldFrames = List(100) {
            model.advance(outsidePull, 0f, dragging = true, nanos = 16_666_667L)
            model.pose
        }
        val peakHeldStretch = heldFrames.maxOf { it.stretch.y }
        assertTrue("Expected sustained stretch > 3.5px while held outside, got ${heldFrames.last().stretch.y}", heldFrames.last().stretch.y > 3.5f)
        assertTrue(
            "Held outside stretch must not bounce back prematurely (peak=$peakHeldStretch, end=${heldFrames.last().stretch.y})",
            peakHeldStretch - heldFrames.last().stretch.y <= 0.35f,
        )
        model.lift(outsidePull, Velocity(0f, 500f))
        val releaseFrames = List(150) {
            model.advance(Offset.Zero, 0f, dragging = false, nanos = 16_666_667L)
            model.pose
        }
        val minStretch = releaseFrames.minOf { it.stretch.y }
        assertTrue(
            "Expected lively iOS-like cushioned overshoot (8%-24% of peak=$peakHeldStretch), got min=$minStretch",
            minStretch < -peakHeldStretch * 0.08f && minStretch > -peakHeldStretch * 0.24f,
        )
        assertEquals(MenuPose(), releaseFrames.last())
    }

    @Test fun internalDragProducesSubtleMonotonicPullAndContinuousBoundaryTransition() {
        val rest = androidx.compose.ui.geometry.Rect(100f, 100f, 350f, 360f)
        val origin = Offset(225f, 120f)
        val ratio = GlassPressTokens().menuInsideDragRatio
        assertEquals(Offset.Zero, computeMenuDragPull(origin, origin, rest, ratio))

        // Inside bounds static drag yields subtle proportional pull, and boundary pull adds on C0-continuously outside restBounds.
        assertEquals(88f * ratio, computeMenuDragPull(Offset(origin.x, origin.y + 88f), origin, rest, ratio).y, 0.001f)
        assertEquals((rest.bottom - origin.y) * ratio + 40f, computeMenuDragPull(Offset(origin.x, rest.bottom + 40f), origin, rest, ratio).y, 0.001f)

        val model = MenuElasticity(1f, GlassPressTokens())
        repeat(20) {
            model.advance(
                Offset(0f, 80f), 0f, dragging = true, nanos = 16_666_667L,
                fingerVelocity = Offset(0f, 750f), fingerAcceleration = Offset(0f, 1200f), fingerMoving = true,
            )
        }
        assertTrue("Expected boundary center shift, got ${model.pose.center.y}", model.pose.center.y > 1f)
        assertTrue("Expected gentle vertical stretch outside boundary, got ${model.pose.stretch.y}", model.pose.stretch.y > 1f)
        assertTrue("Expected mild cross-axis contraction, got ${model.pose.stretch.x}", model.pose.stretch.x < -0.2f)

        // C0 continuity across bottom edge (rest.bottom = 360f)
        val justInside = computeMenuDragPull(Offset(origin.x, rest.bottom - 0.1f), origin, rest, ratio)
        val onEdge = computeMenuDragPull(Offset(origin.x, rest.bottom), origin, rest, ratio)
        val justOutside = computeMenuDragPull(Offset(origin.x, rest.bottom + 0.1f), origin, rest, ratio)
        val farOutside = computeMenuDragPull(Offset(origin.x, rest.bottom + 88f), origin, rest, ratio)
        assertEquals(onEdge.y, justInside.y, 0.05f)
        assertEquals(onEdge.y, justOutside.y, 0.15f)
        assertEquals(onEdge.y + 88f, farOutside.y, 0.001f)

        // Symmetric subtle horizontal inside drag
        val leftPull = computeMenuDragPull(Offset(origin.x - 90f, origin.y), origin, rest, ratio).x
        val rightPull = computeMenuDragPull(Offset(origin.x + 90f, origin.y), origin, rest, ratio).x
        assertEquals(-90f * ratio, leftPull, 0.001f)
        assertEquals(90f * ratio, rightPull, 0.001f)
    }

    @Test fun upwardDragOnTopAnchoredMenuCompressesHeightAndWidensWidth() {
        val model = MenuElasticity(1f, GlassPressTokens(), verticalAway = 1f)
        repeat(24) {
            model.advance(
                pull = Offset(0f, -140f),
                scrolled = 0f,
                dragging = true,
                nanos = 16_666_667L,
                fingerVelocity = Offset(0f, -450f),
                fingerAcceleration = Offset.Zero,
                fingerMoving = true,
            )
        }
        assertTrue("Expected upward squash to compress height, got ${model.pose.stretch.y}", model.pose.stretch.y < -1.5f)
        assertTrue("Expected Poisson cross-contraction to widen width, got ${model.pose.stretch.x}", model.pose.stretch.x > 0.5f)
        assertTrue("Expected subtle upward shift, got ${model.pose.center.y}", model.pose.center.y < -0.5f)

        repeat(150) {
            model.advance(
                pull = Offset.Zero,
                scrolled = 0f,
                dragging = false,
                nanos = 16_666_667L,
            )
        }
        assertEquals(MenuPose(), model.pose)
    }
}
