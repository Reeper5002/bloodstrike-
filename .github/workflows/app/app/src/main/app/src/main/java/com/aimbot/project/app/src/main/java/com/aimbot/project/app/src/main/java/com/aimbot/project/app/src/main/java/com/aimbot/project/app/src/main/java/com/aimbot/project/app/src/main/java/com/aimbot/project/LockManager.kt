package com.aimbot.project

class LockManager(private val cfg: AimConfig.Lock) {

    enum class State { FREE, ACQUIRING, LOCKED, GRACE }

    var state: State = State.FREE
        private set
    var lockTargetId: Int = -1
        private set
    private var lastValidLockTime = 0L
    private var lastVisibleTime = 0L
    private var lockStartTime = 0L

    fun update(
        targetId: Int,
        distanceToTarget: Float,
        isVisible: Boolean,
        isDead: Boolean,
        nowMs: Long
    ) {
        when (state) {
            State.FREE -> {
                if (distanceToTarget <= cfg.lockRadiusPx && isVisible && !isDead) {
                    state = State.ACQUIRING
                    lockTargetId = targetId
                    lockStartTime = nowMs
                }
            }
            State.ACQUIRING -> {
                if (distanceToTarget <= cfg.lockRadiusPx && isVisible) {
                    if (nowMs - lockStartTime >= cfg.lockGraceMs) {
                        state = State.LOCKED
                        lastValidLockTime = nowMs
                        lastVisibleTime = nowMs
                    }
                } else if (distanceToTarget > cfg.unlockRadiusPx) {
                    state = State.FREE
                    lockTargetId = -1
                }
            }
            State.LOCKED -> {
                if (isDead && cfg.unlockOnKill) {
                    state = State.FREE
                    lockTargetId = -1
                    return
                }
                if (targetId != lockTargetId) {
                    state = State.FREE
                    lockTargetId = -1
                    return
                }
                if (isVisible) {
                    lastVisibleTime = nowMs
                    lastValidLockTime = nowMs
                }
                val effectiveUnlock = if (cfg.stickThroughWiggle)
                    cfg.unlockRadiusPx + cfg.wiggleTolerancePx
                else
                    cfg.unlockRadiusPx
                if (distanceToTarget > effectiveUnlock) {
                    if (nowMs - lastValidLockTime > cfg.maxLostMs) state = State.GRACE
                } else {
                    if (nowMs - lastValidLockTime <= cfg.maxLostMs) state = State.LOCKED
                }
            }
            State.GRACE -> {
                if (distanceToTarget <= cfg.lockRadiusPx && isVisible) {
                    state = State.LOCKED
                    lastValidLockTime = nowMs
                } else if (nowMs - lastValidLockTime > cfg.maxLostMs) {
                    state = State.FREE
                    lockTargetId = -1
                }
            }
        }
    }

    fun isLocked() = state == State.LOCKED
    fun isAcquiring() = state == State.ACQUIRING || state == State.GRACE
    fun getTargetId() = lockTargetId

    fun release() {
        state = State.FREE
        lockTargetId = -1
    }
}
