package com.aimbot.project

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import kotlin.math.hypot

class AimBotService : Service() {

    companion object { private const val TAG = "AimBot" }

    private var config: AimConfig? = null
    private var prediction: PredictionEngine? = null
    private var lockManager: LockManager? = null
    private var sniper: SniperManager? = null
    private var antiCheat: AntiCheat? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null

    var screenW = 0
    var screenH = 0
    var capScale = 2

    // current detected enemy
    var enemyX = 0f
    var enemyY = 0f
    var enemyVisible = false
    var enemyDead = false
    var enemyId = -1
    var lastSeenMs = 0L
    var gestureStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        loadConfig()
    }

    private fun loadConfig() {
        config = try {
            ConfigReader.load(this)
        } catch (e: Exception) {
            Log.e(TAG, "Config load failed: ${e.message}")
            null
        }
        config?.let { c ->
            capScale = c.input.downsampleFactor.coerceAtLeast(1)
            prediction = PredictionEngine(
                c.tracking.velocityWindow, c.tracking.predictMaxMs, c.tracking.predictSmooth,
                c.tracking.sprintMultiplier, c.tracking.fly3DPredict,
                c.tracking.airControlCompensation, c.tracking.stopDecayMs,
                c.tracking.minVelocityToTrack)
            lockManager = LockManager(c.lock)
            sniper = SniperManager(c.sniper)
            antiCheat = AntiCheat(c.anticheat)
        }
    }

    fun start(resultData: Intent) {
        if (running) return
        val c = config ?: run {
            Log.e(TAG, "No config - cannot start"); return
        }

        startAsForeground()

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(RESULT_OK, resultData)

        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        screenW = dm.widthPixels
        screenH = dm.heightPixels

        val cw = (screenW / capScale).coerceAtLeast(1)
        val ch = (screenH / capScale).coerceAtLeast(1)
        imageReader = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 3)
        virtualDisplay = projection!!.createVirtualDisplay(
            "aimbot", cw, ch, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler)

        running = true
        Log.i(TAG, "AimBot started. Screen ${screenW}x$screenH capture ${cw}x$ch scale=$capScale")

        val rate = c.general.updateRateMs.toLong().coerceAtLeast(8)
        val loop = object : Runnable {
            override fun run() {
                if (!running) return
                try { tick() } catch (t: Throwable) { Log.w(TAG, "tick: ${t.message}") }
                handler.postDelayed(this, rate)
            }
        }
        handler.post(loop)
    }

    private fun startAsForeground() {
        val ch = NotificationChannel("aimbot", "AimBot", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val notif = Notification.Builder(this, "aimbot")
            .setContentTitle("AimBot running")
            .setContentText("Screen capture + touch active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else
            startForeground(1, notif)
    }

    private fun tick() {
        val c = config ?: return
        val pred = prediction ?: return
        val lock = lockManager ?: return
        val snp = sniper ?: return
        val ac = antiCheat ?: return
        val reader = imageReader ?: return
        val now = SystemClock.uptimeMillis()

        snp.update(now)

        // crosshair = screen CENTER (the camera crosshair, not the finger)
        val cx = screenW / 2f
        val cy = screenH / 2f

        // ---- 1. SEE: detect enemy on the newest frame ----
        val image = reader.acquireLatestImage() ?: return  // no new frame yet -> skip tick
        val det = ScreenDetector.detect(
            image, c.targets.enemyColors,
            tolerance = c.targets.colorTolerance,
            minPixels = c.targets.enemyMinSizePx * 3,
            minBlobHeightPx = (c.targets.enemyMinSizePx / capScale).coerceAtLeast(2)
        )
        image.close()

        if (det != null) {
            enemyX = det.x * capScale
            enemyY = det.y * capScale
            if (c.targets.headDetection) {
                // blob height -> head sits above centroid
                enemyY -= det.sizePx * capScale * c.targets.headSizeRatio * 0.5f
            }
            enemyVisible = true
            enemyId = 1
            lastSeenMs = now
        } else {
            enemyVisible = false
        }

        // hold the touch only while we actually have something to aim at
        if (!enemyVisible || enemyDead) {
            pred.clear()
            lock.update(enemyId, Float.MAX_VALUE, false, enemyDead, now)
            if (now - lastSeenMs > 800) endGestureIfNeeded(now)
            return
        }

        // ---- 2. PREDICT ----
        pred.addSample(enemyX, enemyY, 0f, now)
        var targetX = enemyX
        var targetY = enemyY
        if (c.tracking.predictEnabled && c.tracking.enabled) {
            pred.predict()?.let { targetX = it.x; targetY = it.y }
        }

        // ---- 3. FOV gate ----
        val dist = hypot(targetX - cx, targetY - cy).toFloat()
        if (dist > c.fov.maxDistancePx || dist < c.fov.minDistancePx) {
            lock.update(enemyId, dist, false, false, now)
            endGestureIfNeeded(now)
            return
        }

        // ---- 4. LOCK ----
        lock.update(enemyId, dist, enemyVisible, enemyDead, now)
        if (!lock.isLocked() && !lock.isAcquiring()) {
            endGestureIfNeeded(now)
            return
        }

        // ---- 5. correction ----
        val (_, snug) = snp.effectiveSniperParams(dist)
        val smooth =
            if (!c.tracking.enabled) 1f
            else if (snp.isScoped()) snug
            else c.tracking.smoothFactor

        var dx = (targetX - cx) * smooth
        var dy = (targetY - cy) * smooth

        if (c.tracking.reverseCorrection && dist > c.tracking.reverseMaxOffsetPx) {
            val pullBack = (dist - c.tracking.reverseMaxOffsetPx) * 0.5f
            dx -= (dx / dist) * pullBack
            dy -= (dy / dist) * pullBack
        }

        val maxPerFrame = c.tracking.maxCorrectionPerFramePx.toFloat()
        val mag = hypot(dx, dy)
        if (mag > maxPerFrame) { dx *= maxPerFrame / mag; dy *= maxPerFrame / mag }

        if (snp.isRecoilLocking()) { dx *= 0.5f; dy *= 0.5f }

        // correction in TOUCH pixels (game camera moves more than 1:1 per finger px)
        val tdx = dx * c.input.sensitivity
        val tdy = dy * c.input.sensitivity

        // ---- 6. HUMANIZE + INJECT as a persistent camera-drag gesture ----
        if (!ShizukuInjector.isGestureActive() && now - gestureStartTime > 250) {
            // start the drag on an empty screen area (left-lower quadrant)
            ShizukuInjector.startGesture(screenW * 0.15f, screenH * 0.75f, now)
            gestureStartTime = now
        }

        val humanized = ac.humanize(tdx, tdy)
        if (humanized.isNotEmpty()) {
            for ((mx, my) in humanized) {
                ShizukuInjector.moveBy(mx, my, gestureStartTime, now)
            }
        } else {
            val (wx, wy) = ac.idleWiggle()
            if (wx != 0f || wy != 0f) ShizukuInjector.moveBy(wx, wy, gestureStartTime, now)
        }

        if (dist > c.fov.maxDistancePx * 1.2f) endGestureIfNeeded(now)
    }

    private fun endGestureIfNeeded(now: Long) {
        if (ShizukuInjector.isGestureActive()) {
            ShizukuInjector.endGesture(gestureStartTime, now)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "stop" -> stop()
            else -> intent?.getParcelableExtra<Intent>("projection")?.let { start(it) }
        }
        return START_STICKY
    }

    fun stop() { running = false }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stop()
        lockManager?.release()
        endGestureIfNeeded(SystemClock.uptimeMillis())
        virtualDisplay?.release()
        projection?.stop()
    }
}
