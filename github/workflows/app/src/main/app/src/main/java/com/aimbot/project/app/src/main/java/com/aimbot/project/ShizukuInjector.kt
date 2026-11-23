package com.aimbot.project

import android.os.IBinder
import android.view.InputDevice
import android.view.MotionEvent
import dev.rikka.shizuku.SystemServiceHelper
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.sui.Sui

object ShizukuInjector {

    @Volatile private var injectMethod: java.lang.reflect.Method? = null
    @Volatile private var inputManager: Any? = null
    @Volatile private var ready = false

    fun init(): Boolean {
        return try {
            Sui.init("com.aimbot.project")
            if (Shizuku.checkSelfPermission() != Shizuku.PERMISSION_GRANTED) return false
            val binder: IBinder = SystemServiceHelper.getSystemService("input")
            val wrapper = ShizukuBinderWrapper(binder)
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            inputManager = stub.getMethod("asInterface", IBinder::class.java).invoke(null, wrapper)
            injectMethod = inputManager!!.javaClass.methods.first {
                it.name == "injectInputEvent" &&
                it.parameterCount == 2 &&
                it.parameterTypes[1] == Integer.TYPE
            }
            ready = true
            android.util.Log.i("ShizukuInjector", "Injector ready")
            true
        } catch (t: Throwable) {
            android.util.Log.e("ShizukuInjector", "init failed", t)
            ready = false
            false
        }
    }

    private fun inject(event: MotionEvent): Boolean {
        val im = inputManager ?: return false
        val m = injectMethod ?: return false
        return try {
            // mode 2 = INJECT_INPUT_EVENT_MODE_ASYNC (fastest, best for aim)
            m.invoke(im, event, 2)
            true
        } catch (t: Throwable) {
            false
        }
    }

    // ---- persistent camera-drag gesture ----
    private var gestureActive = false
    private var fingerX = 0f
    private var fingerY = 0f
    private var pointerId = 0

    fun startGesture(x: Float, y: Float, downTime: Long) {
        if (gestureActive) return
        gestureActive = true
        fingerX = x
        fingerY = y
        val props = MotionEvent.PointerProperties().apply {
            id = pointerId; toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = fingerX; this.y = fingerY; pressure = 1f; size = 1f
        }
        val ev = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
            1, arrayOf(props), arrayOf(coords), 0, 1f, 1f, 0, 0, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN)
        inject(ev)
        ev.recycle()
    }

    fun moveBy(dx: Float, dy: Float, downTime: Long, now: Long) {
        if (!gestureActive) return
        fingerX += dx
        fingerY += dy
        val props = MotionEvent.PointerProperties().apply {
            id = pointerId; toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = fingerX; this.y = fingerY; pressure = 1f; size = 1f
        }
        val ev = MotionEvent.obtain(downTime, now, MotionEvent.ACTION_MOVE,
            1, arrayOf(props), arrayOf(coords), 0, 1f, 1f, 0, 0, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN)
        inject(ev)
        ev.recycle()
    }

    fun endGesture(downTime: Long, now: Long) {
        if (!gestureActive) return
        gestureActive = false
        val props = MotionEvent.PointerProperties().apply {
            id = pointerId; toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = fingerX; this.y = fingerY; pressure = 0f; size = 1f
        }
        val ev = MotionEvent.obtain(downTime, now, MotionEvent.ACTION_UP,
            1, arrayOf(props), arrayOf(coords), 0, 1f, 1f, 0, 0, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN)
        inject(ev)
        ev.recycle()
        // use a different finger id for the next gesture (avoids id-reuse patterns)
        pointerId = (pointerId + 1) % 8
    }

    fun isGestureActive() = gestureActive
}
