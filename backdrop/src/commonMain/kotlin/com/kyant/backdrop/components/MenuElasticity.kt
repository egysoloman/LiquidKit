package com.kyant.backdrop.components

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.tanh

internal data class MenuPose(val center: Offset = Offset.Zero, val stretch: Offset = Offset.Zero)

/** Integrate from the last displayed position and velocity, even when a gesture changes owners. */
internal class MenuElasticity(
    private val density: Float,
    private val tokens: GlassPressTokens,
    private val horizontalAway: Float = 1f,
    private val verticalAway: Float = 1f,
) {
    private class Axis {
        var value = 0f
        var velocity = 0f

        fun advance(target: Float, spring: FloatSpringSpec, nanos: Long): Boolean {
            if (nanos > 0 && (abs(value - target) > .025f || abs(velocity) > .35f)) {
                val next = spring.getValueFromNanos(nanos, value, target, velocity)
                velocity = spring.getVelocityFromNanos(nanos, value, target, velocity)
                value = next
                return true
            }
            value = target
            velocity = 0f
            return false
        }
    }

    private fun GlassSpring.axisSpec() = FloatSpringSpec(damping, stiffness)
    private val tracking = tokens.tracking.axisSpec()
    private val release = tokens.centerRelease.axisSpec()
    private val aspectRelease = tokens.release.axisSpec()
    private val x = Axis()
    private val y = Axis()
    private val width = Axis()
    private val height = Axis()
    private val scrollFollower = Axis()
    val pose get() = MenuPose(Offset(x.value, y.value), Offset(width.value, height.value))

    fun attachScroll(position: Float) {
        scrollFollower.value = position
        scrollFollower.velocity = 0f
    }

    fun applyAcceleration(deltaVelocity: Offset) {
        // Velocity/acceleration is integrated smoothly into advance() so active movement tracks without jitter
        // and rebounds elastically once finger movement stops.
    }

    fun lift(pull: Offset, velocity: Velocity) {
        if (pull == Offset.Zero) return
        val limit = tokens.menuPullLimit.value * density * 14f
        if (limit <= 0f) return
        fun transfer(v: Float) = limit * tanh(v * tokens.menuReleaseTransfer / limit)
        val vx = transfer(velocity.x)
        val vy = transfer(velocity.y)
        // The surface already has momentum. Transfer measured finger speed without resetting that motion.
        x.velocity += (vx - x.velocity) * .45f
        y.velocity += (vy - y.velocity) * .45f
        val horizontal = horizontalAway * vx
        val vertical = verticalAway * vy
        width.velocity += (strain(horizontal, vertical) - width.velocity) * .45f
        height.velocity += (strain(vertical, horizontal) - height.velocity) * .45f
    }

    private fun strain(along: Float, across: Float, crossScale: Float = 1f) =
        tokens.directionalStretch * (along - tokens.menuCrossContraction * crossScale * across)

    private fun directed(value: Float, away: Float): Float {
        val signed = value * away
        return if (signed >= 0f) signed else signed * .72f
    }

    private fun directedVel(value: Float, away: Float): Float {
        val signed = value * away
        return if (signed >= 0f) signed else signed * .85f
    }

    fun advance(
        pull: Offset,
        scrolled: Float,
        dragging: Boolean,
        nanos: Long,
        fingerVelocity: Offset = Offset.Zero,
        fingerAcceleration: Offset = Offset.Zero,
        fingerMoving: Boolean = dragging,
        aspectRatio: Float = 1f,
    ): Boolean {
        val listMoving = scrollFollower.advance(scrolled, tracking, nanos)
        val flex = tokens.scrollFlex.value * density * tanh((scrolled - scrollFollower.value) / (16f * density))
        val knee = (tokens.menuPullLimit.value * density).coerceAtLeast(.01f)
        // The force becomes harder to overcome, but a longer pull still moves the edge.
        fun displacement(distance: Float) = knee * asinh(distance * tokens.menuDragGain / knee)
        val baseCap = 6.5f * density
        fun velPull(v: Float, pullAlong: Float): Float {
            val outBoost = 1f + 0.85f * ((abs(pullAlong) - 24f * density) / (50f * density)).coerceIn(0f, 1f)
            return (baseCap * outBoost) * tanh(v * .0105f / baseCap)
        }
        val dispX = displacement(pull.x)
        val dispY = displacement(pull.y) + flex
        val velX = if (dragging) velPull(fingerVelocity.x, pull.x) else 0f
        val velY = if (dragging) velPull(fingerVelocity.y, pull.y) else 0f
        val targetX = (if (dispX * horizontalAway < 0f) dispX * .80f else dispX) + velX
        val targetY = (if (dispY * verticalAway < 0f) dispY * .80f else dispY) + velY
        val directedX = directed(dispX, horizontalAway) + directedVel(velX, horizontalAway)
        val directedY = directed(dispY, verticalAway) + directedVel(velY, verticalAway)
        val widthAspect = aspectRatio.coerceIn(0.55f, 2.2f)
        val centerSpring = if (dragging) tracking else release
        val movingX = x.advance(targetX, centerSpring, nanos)
        val movingY = y.advance(targetY, centerSpring, nanos)
        val shapeSpring = if (dragging) tracking else aspectRelease
        val resizingX = width.advance(strain(directedX, directedY, crossScale = widthAspect), shapeSpring, nanos)
        val resizingY = height.advance(strain(directedY, directedX, crossScale = 1f / widthAspect), shapeSpring, nanos)
        return listMoving || movingX || movingY || resizingX || resizingY
    }
}
