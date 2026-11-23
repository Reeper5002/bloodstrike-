package com.aimbot.project

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AimConfig(
    val general: General,
    val fov: Fov,
    val tracking: Tracking,
    val lock: Lock,
    val sniper: Sniper,
    val anticheat: AntiCheat,
    val targets: Targets,
    val input: Input
) {
    data class General(
        val enabled: Boolean,
        val toggleKey: String,
        val updateRateMs: Int,
        val debugOverlay: Boolean
    )
    data class Fov(
        val radiusPx: Int,
        val shape: String,
        val color: IntArray,
        val lineToTarget: Boolean,
        val lineColor: IntArray,
        val visibilityCheck: Boolean,
        val minDistancePx: Int,
        val maxDistancePx: Int
    )
    data class Tracking(
        val enabled: Boolean,
        val smoothFactor: Float,
        val reverseCorrection: Boolean,
        val reverseMaxOffsetPx: Int,
        val maxCorrectionPerFramePx: Int,
        val velocityWindow: Int,
        val minVelocityToTrack: Float,
        val predictEnabled: Boolean,
        val predictMaxMs: Int,
        val predictSmooth: Float,
        val sprintMultiplier: Float,
        val fly3DPredict: Boolean,
        val airControlCompensation: Float,
        val stopDecayMs: Int
    )
    data class Lock(
        val enabled: Boolean,
        val lockRadiusPx: Int,
        val unlockRadiusPx: Int,
        val lockGraceMs: Long,
        val lockHysteresisMs: Long,
        val maxLostMs: Long,
        val unlockOnKill: Boolean,
        val stickThroughWiggle: Boolean,
        val wiggleTolerancePx: Int,
        val reacquireDelayMs: Long,
        val priority: String
    )
    data class Sniper(
        val enabled: Boolean,
        val fastScopeIn: Boolean,
        val scopeInMs: Int,
        val scopeOutMs: Int,
        val defaultScopeInMs: Int,
        val defaultScopeOutMs: Int,
        val sniperLockEnabled: Boolean,
        val sniperLockRadiusPx: Int,
        val sniperSnugness: Float,
        val closeRangeLock: Boolean,
        val closeRangePx: Int,
        val closeRangeSnugness: Float,
        val noLoseUntilDown: Boolean,
        val maintainLockThroughRecoil: Boolean,
        val postShotReacquireMs: Int
    )
    data class AntiCheat(
        val jitterEnabled: Boolean,
        val jitterMaxPx: Int,
        val timingRandomizeMs: IntArray,
        val batchSizeMin: Int,
        val batchSizeMax: Int,
        val microCorrectionChance: Float,
        val correctionQuantum: Int,
        val idleWiggleMs: Int,
        val idleWiggleAmpPx: Int,
        val useShizukuInject: Boolean,
        val source: String,
        val eventFlag: String,
        val randomizeStartOffsetMs: IntArray
    )
    data class Targets(
        val detectMethod: String,
        val enemyColors: Array<IntArray>,
        val enemyMinSizePx: Int,
        val headDetection: Boolean,
        val headSizeRatio: Float,
        val teamColor: IntArray,
        val ignoreAllies: Boolean,
        val colorTolerance: Int
    )
    data class Input(
        val sensitivity: Float,
        val downsampleFactor: Int,
        val maxTouchX: Int,
        val maxTouchY: Int,
        val screenDpi: Int
    )
}

object ConfigReader {

    /**
     * Load order:
     *   1. /storage/emulated/0/Android/data/com.aimbot.project/files/aimbot.json
     *      (edit this on the phone to tune WITHOUT rebuilding)
     *   2. built-in asset aimbot.json inside the APK (always works out of the box)
     */
    fun load(context: Context): AimConfig {
        val ext = File(context.getExternalFilesDir(null), "aimbot.json")
        if (ext.exists()) return parse(ext.readText())
        val txt = context.assets.open("aimbot.json").bufferedReader().use { it.readText() }
        return parse(txt)
    }

    fun parse(json: String): AimConfig {
        val root = JSONObject(json)
        return AimConfig(
            general = parseGeneral(root.getJSONObject("general")),
            fov = parseFov(root.getJSONObject("fov")),
            tracking = parseTracking(root.getJSONObject("tracking")),
            lock = parseLock(root.getJSONObject("lock")),
            sniper = parseSniper(root.getJSONObject("sniper")),
            anticheat = parseAntiCheat(root.getJSONObject("anticheat")),
            targets = parseTargets(root.getJSONObject("targets")),
            input = parseInput(root.getJSONObject("input"))
        )
    }

    private fun parseGeneral(o: JSONObject) = AimConfig.General(
        o.getBoolean("enabled"),
        o.getString("toggleKey"),
        o.getInt("updateRateMs"),
        o.optBoolean("debugOverlay", false)
    )

    private fun parseFov(o: JSONObject) = AimConfig.Fov(
        o.getInt("radiusPx"),
        o.getString("shape"),
        intArray(o),
        o.optBoolean("lineToTarget", true),
        intArray(o, "lineColor"),
        o.optBoolean("visibilityCheck", true),
        o.getInt("minDistancePx"),
        o.getInt("maxDistancePx")
    )

    private fun parseTracking(o: JSONObject) = AimConfig.Tracking(
        o.optBoolean("enabled", true),
        o.optFloat("smoothFactor", 0.4f),
        o.optBoolean("reverseCorrection", true),
        o.optInt("reverseMaxOffsetPx", 180),
        o.optInt("maxCorrectionPerFramePx", 14),
        o.optInt("velocityWindow", 6),
        o.optFloat("minVelocityToTrack", 30f),
        o.optBoolean("predictEnabled", true),
        o.optInt("predictMaxMs", 45),
        o.optFloat("predictSmooth", 0.75f),
        o.optFloat("sprintMultiplier", 1.6f),
        o.optBoolean("fly3DPredict", true),
        o.optFloat("airControlCompensation", 0.3f),
        o.optInt("stopDecayMs", 200)
    )

    private fun parseLock(o: JSONObject) = AimConfig.Lock(
        o.optBoolean("enabled", true),
        o.optInt("lockRadiusPx", 130),
        o.optInt("unlockRadiusPx", 280),
        o.optLong("lockGraceMs", 400),
        o.optLong("lockHysteresisMs", 600),
        o.optLong("maxLostMs", 1200),
        o.optBoolean("unlockOnKill", true),
        o.optBoolean("stickThroughWiggle", true),
        o.optInt("wiggleTolerancePx", 40),
        o.optLong("reacquireDelayMs", 150),
        o.optString("priority", "head > chest > body")
    )

    private fun parseSniper(o: JSONObject) = AimConfig.Sniper(
        o.optBoolean("enabled", true),
        o.optBoolean("fastScopeIn", true),
        o.optInt("scopeInMs", 80),
        o.optInt("scopeOutMs", 55),
        o.optInt("defaultScopeInMs", 350),
        o.optInt("defaultScopeOutMs", 250),
        o.optBoolean("sniperLockEnabled", true),
        o.optInt("sniperLockRadiusPx", 55),
        o.optFloat("sniperSnugness", 0.85f),
        o.optBoolean("closeRangeLock", true),
        o.optInt("closeRangePx", 400),
        o.optFloat("closeRangeSnugness", 0.95f),
        o.optBoolean("noLoseUntilDown", true),
        o.optBoolean("maintainLockThroughRecoil", true),
        o.optInt("postShotReacquireMs", 100)
    )

    private fun parseAntiCheat(o: JSONObject) = AimConfig.AntiCheat(
        o.optBoolean("jitterEnabled", true),
        o.optInt("jitterMaxPx", 2),
        intArray(o, "timingRandomizeMs"),
        o.optInt("batchSizeMin", 1),
        o.optInt("batchSizeMax", 3),
        o.optFloat("microCorrectionChance", 0.35f),
        o.optInt("correctionQuantum", 2),
        o.optInt("idleWiggleMs", 800),
        o.optInt("idleWiggleAmpPx", 3),
        o.optBoolean("useShizukuInject", true),
        o.optString("source", "TOUCH"),
        o.optString("eventFlag", "TRACKING"),
        intArray(o, "randomizeStartOffsetMs")
    )

    private fun parseTargets(o: JSONObject) = AimConfig.Targets(
        o.optString("detectMethod", "color_shape"),
        colorsArray(o, "enemyColors"),
        o.optInt("enemyMinSizePx", 8),
        o.optBoolean("headDetection", true),
        o.optFloat("headSizeRatio", 0.3f),
        colorsArrayOne(o, "teamColor"),
        o.optBoolean("ignoreAllies", true),
        o.optInt("colorTolerance", 40)      // NEW: tune detection strictness
    )

    private fun parseInput(o: JSONObject) = AimConfig.Input(
        o.optFloat("sensitivity", 1.0f),
        o.optInt("downsampleFactor", 1),
        o.optInt("maxTouchX", 1080),
        o.optInt("maxTouchY", 2340),
        o.optInt("screenDpi", 480)
    )

    private fun intArray(o: JSONObject, key: String = "color"): IntArray {
        val arr = o.getJSONArray(key)
        return IntArray(arr.length()) { arr.getInt(it) }
    }

    private fun colorsArray(o: JSONObject, key: String): Array<IntArray> {
        val arr = o.getJSONArray(key)
        return Array(arr.length()) {
            val sub = arr.getJSONArray(it)
            IntArray(sub.length()) { sub.getInt(it) }
        }
    }

    private fun colorsArrayOne(o: JSONObject, key: String): IntArray {
        return if (o.has(key)) {
            val arr = o.getJSONArray(key)
            IntArray(arr.length()) { arr.getInt(it) }
        } else intArrayOf(255, 100, 50)
    }
}
