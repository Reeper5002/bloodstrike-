package com.aimbot.project

import kotlin.math.hypot

/**
 * Tracks enemy position over time, estimates velocity,
 * and predicts future position for leading.
 */
class PredictionEngine(
    private val windowSize: Int,
    private val predictMaxMs: Int,
    private val predictSmooth: Float,
    private val sprintMultiplier: Float,
    private val fly3D: Boolean,
    private val airComp: Float,
    private val stopDecayMs: Int,
    private val minVel: Float
) {
    data class Sample(val x: Float, val y: Float, val z: Float, val t: Long)
    data class PredictedPoint(val x: Float, val y: Float, val z: Float)

    private val history = ArrayDeque<Sample>()

    fun addSample(x: Float, y: Float, z: Float = 0f, timestamp: Long = System.currentTimeMillis()) {
        history.addLast(Sample(x, y, z, timestamp))
        while (history.size > windowSize) history.removeFirst()
    }

    fun clear() = history.clear()

    /**
     * Returns predicted position `leadMs` in the future, or null if
     * there's not enough data / velocity is too low.
     */
    fun predict(leadMs: Int = predictMaxMs): PredictedPoint? {
        if (history.size < 2) return null

        val newest = history.last()
        val oldest = history.first()
        val dtMs = (newest.t - oldest.t).coerceAtLeast(1)
        val dtSec = dtMs / 1000f

        // Base velocity from full window
        var vx = (newest.x - oldest.x) / dtSec
        var vy = (newest.y - oldest.y) / dtSec
        var vz = (newest.z - oldest.z) / dtSec

        // Check if target recently stopped (velocity decay)
        val lastTwo = history.takeLast(2)
        if (lastTwo.size == 2) {
            val recentDt = ((lastTwo[1].t - lastTwo[0].t).coerceAtLeast(1)) / 1000f
            val recentVx = (lastTwo[1].x - lastTwo[0].x) / recentDt
            val recentVy = (lastTwo[1].y - lastTwo[0].y) / recentDt

            if (hypot(recentVx, recentVy) < minVel * 0.3f) {
                // Target is stopping – decay prediction
                val decayFactor = (stopDecayMs / dtMs.toFloat()).coerceIn(0f, 1f)
                vx *= decayFactor
                vy *= decayFactor
                vz *= decayFactor
            }
        }

        // Sprint detection: if velocity exceeds normal by sprintMultiplier, amplify
        val speed = hypot(vx, vy, vz)
        val normalSpeed = minVel * 2f
        if (speed > normalSpeed) {
            val multiplier = (speed / normalSpeed).coerceIn(1f, sprintMultiplier)
            vx *= multiplier
            vy *= multiplier
            vz *= multiplier
        }

        // Air / fly compensation – reduce prediction reliability in air
        if (fly3D && vz != 0f) {
            val airFactor = 1f - airComp
            vx *= airFactor
            vy *= airFactor
            vz *= airFactor
        }

        val leadSec = leadMs / 1000f

        val predX = newest.x + vx * leadSec
        val predY = newest.y + vy * leadSec
        val predZ = newest.z + vz * leadSec

        // Smooth the prediction (blend with current to avoid overshooting)
        val s = predictSmooth
        val finalX = predX * s + newest.x * (1f - s)
        val finalY = predY * s + newest.y * (1f - s)
        val finalZ = predZ * s + newest.z * (1f - s)

        return PredictedPoint(finalX, finalY, finalZ)
    }

    fun currentVelocity(): Pair<Float, Float> {
        if (history.size < 2) return 0f to 0f
        val a = history[history.size - 2]
        val b = history.last()
        val dt = ((b.t - a.t).coerceAtLeast(1)) / 1000f
        return ((b.x - a.x) / dt) to ((b.y - a.y) / dt)
    }
}
