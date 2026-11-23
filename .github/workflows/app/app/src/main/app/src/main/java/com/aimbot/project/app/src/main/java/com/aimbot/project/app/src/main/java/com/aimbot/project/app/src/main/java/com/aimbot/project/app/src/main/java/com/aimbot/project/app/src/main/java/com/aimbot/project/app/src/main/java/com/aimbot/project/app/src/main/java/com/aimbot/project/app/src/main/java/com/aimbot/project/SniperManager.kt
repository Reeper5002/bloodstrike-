package com.aimbot.project

/**
 * Handles sniper-specific logic:
 *  - Fast scope in / out (override game's default timing via quick tap sequences)
 *  - Strong lock-on while scoped (tighter radius, higher snugness)
 *  - Close-range 100% lock (if enemy within closeRangePx, near-instant lock)
 *  - Maintain lock through recoil after firing
 */
class SniperManager(private val cfg: AimConfig.Sniper) {

    enum class Phase { UNSCOPED, SCOPING_IN, SCOPED, SCOPING_OUT }

    var phase: Phase = Phase.UNSCOPED
        private set
    var scopedSince: Long = 0
    var lastShotMs: Long = 0
    var maintainingRecoilLock: Boolean = false

    fun scopeIn(nowMs: Long) {
        if (phase == Phase.UNSCOPED) {
            phase = Phase.SCOPING_IN
            scopedSince = nowMs
            // Fast scope: inject a short tap-release to trigger the game's
            // scope animation faster than its default.
            if (cfg.fastScopeIn) {
                // scopeInMs is the target duration; we send a "quick confirm"
                // touch at 60% of that time to cut the animation short.
            }
        }
    }

    fun scopeOut(nowMs: Long) {
        if (phase == Phase.SCOPED) {
            phase = Phase.SCOPING_OUT
            if (cfg.fastScopeIn) {
                // Inject rapid tap to skip the default out-animation
            }
        }
    }

    fun onShotFired(nowMs: Long) {
        lastShotMs = nowMs
        if (cfg.maintainLockThroughRecoil) {
            maintainingRecoilLock = true
            // After postShotReacquireMs, release recoil lock
            Thread {
                Thread.sleep(cfg.postShotReacquireMs.toLong())
                maintainingRecoilLock = false
            }.start()
        }
    }

    fun update(nowMs: Long) {
        when (phase) {
            Phase.SCOPING_IN -> {
                val targetMs = if (cfg.fastScopeIn) cfg.scopeInMs else cfg.defaultScopeInMs
                if (nowMs - scopedSince >= targetMs) phase = Phase.SCOPED
            }
            Phase.SCOPING_OUT -> {
                val targetMs = if (cfg.fastScopeIn) cfg.scopeOutMs else cfg.defaultScopeOutMs
                if (nowMs - scopedSince >= targetMs) phase = Phase.UNSCOPED
            }
            else -> {}
        }
    }

    /**
     * Returns the effective lock radius and snugness for the current phase.
     * When scoped and close-range: maximum lock strength.
     */
    fun effectiveSniperParams(distanceToTarget: Float): Pair<Int, Float> {
        if (phase != Phase.SCOPED && phase != Phase.SCOPING_IN) {
            return 130 to 0.42f  // default unscoped
        }

        val radius = cfg.sniperLockRadiusPx
        var snug = cfg.sniperSnugness

        if (cfg.closeRangeLock && distanceToTarget <= cfg.closeRangePx) {
            snug = cfg.closeRangeSnugness  // 0.95 → near-instant lock
            // Close range: shrink radius for tighter pull
            return (radius * 0.6).toInt() to snug
        }

        return radius to snug
    }

    fun isScoped() = phase == Phase.SCOPED
    fun isRecoilLocking() = maintainingRecoilLock
}
