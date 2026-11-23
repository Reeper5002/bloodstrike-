package com.aimbot.project

import kotlin.random.Random

/**
 * Anti-cheat evasion layer.
 * BloodStrike (NetEase) checks for:
 *  - Perfectly periodic touch events
 *  - Zero-jitter aim corrections
 *  - Instant snaps (no intermediate movement)
 *  - Accessibility-service injection patterns
 *
 * We counter by:
 *  - Randomizing timing between corrections
 *  - Adding micro-jitter (1-2px) to every correction
 *  - Batching corrections in small groups (1-3 per frame)
 *  - Using Shizuku's injectInputEvent (system-level, not accessibility)
 *  - Simulating idle micro-wiggle when not aiming
 *  - Quantizing corrections to multiples of correctionQuantum
 */
class AntiCheat(private val cfg: AimConfig.AntiCheat) {

    private val rand = Random(System.nanoTime())

    /**
     * Transforms a raw correction (dx, dy) into a "humanized" sequence
     * of micro-moves that get injected one-by-one with random delays.
     */
    fun humanize(dx: Float, dy: Float): List<Pair<Float, Float>> {
        val moves = mutableListOf<Pair<Float, Float>>()

        // Decompose into batches
        val batchSize = rand.nextInt(cfg.batchSizeMin, cfg.batchSizeMax + 1)
        val perMove = batchSize.coerceAtLeast(1)

        var remainingX = dx
        var remainingY = dy

        repeat(perMove) {
            val portionX = remainingX / (perMove - it)
            val portionY = remainingY / (perMove - it)

            // Jitter
            var jx = 0f
            var jy = 0f
            if (cfg.jitterEnabled) {
                jx = rand.nextFloat(-cfg.jitterMaxPx.toFloat(), cfg.jitterMaxPx.toFloat())
                jy = rand.nextFloat(-cfg.jitterMaxPx.toFloat(), cfg.jitterMaxPx.toFloat())
            }

            // Quantize
            val qx = (portionX + jx).roundToQuantum(cfg.correctionQuantum)
            val qy = (portionY + jy).roundToQuantum(cfg.correctionQuantum)

            if (qx != 0f || qy != 0f) {
                moves.add(qx to qy)
            }

            remainingX -= portionX
            remainingY -= portionY
        }

        return moves
    }

    /** Random delay before next injection (ms) */
    fun nextDelay(): Long {
        val min = cfg.timingRandomizeMs[0].toLong()
        val max = cfg.timingRandomizeMs[1].toLong()
        return rand.nextLong(min, max + 1)
    }

    /** Random offset added before the first correction (simulates reaction time) */
    fun startOffset(): Long {
        val min = cfg.randomizeStartOffsetMs[0].toLong()
        val max = cfg.randomizeStartOffsetMs[1].toLong()
        return rand.nextLong(min, max + 1)
    }

    /** Micro-wiggle when idle (prevents "static aim" detection) */
    fun idleWiggle(): Pair<Float, Float> {
        if (rand.nextFloat() > cfg.microCorrectionChance) return 0f to 0f
        val angle = rand.nextFloat(0f, 6.28f)
        val amp = rand.nextFloat(1f, cfg.idleWiggleAmpPx.toFloat())
        return (amp * kotlin.math.cos(angle.toDouble()).toFloat()) to
               (amp * kotlin.math.sin(angle.toDouble()).toFloat())
    }

    private fun Float.roundToQuantum(q: Int): Float {
        if (q <= 1) return this
        val steps = Math.round(this / q)
        return steps * q
    }
}
