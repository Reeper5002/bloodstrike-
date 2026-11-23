package com.aimbot.project

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private var projectionData: Intent? = null

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == Shizuku.PERMISSION_GRANTED) {
            ensureShizukuInjector()
            askScreen()
        } else {
            Toast.makeText(this, "Shizuku permission denied - open the Shizuku app and allow AimBot", Toast.LENGTH_LONG).show()
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { ensureShizukuInjector() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addOnBinderReceivedListener(binderListener)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(this).apply {
            text = "AimBot v1\n1. Start Shizuku first\n2. Tap the button below\n3. Allow screen recording\n4. Open the game"
        }
        val btn = Button(this).apply { text = "START AIMBOT" }

        btn.setOnClickListener {
            when {
                Shizuku.isPreV11() ->
                    Toast.makeText(this, "Shizuku app too old - update it", Toast.LENGTH_LONG).show()
                Shizuku.checkSelfPermission() == Shizuku.PERMISSION_GRANTED -> {
                    ensureShizukuInjector()
                    askScreen()
                }
                else -> Shizuku.requestPermission(114)
            }
        }
        layout.addView(status)
        layout.addView(btn)
        setContentView(layout)
    }

    private fun ensureShizukuInjector() {
        Thread {
            try { ShizukuInjector.init() } catch (_: Throwable) {}
        }.start()
    }

    private fun askScreen() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            projectionData = data
            startBot()
        }
    }

    private fun startBot() {
        val d = projectionData ?: return askScreen()
        startForegroundService(Intent(this, AimBotService::class.java).putExtra("projection", d))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeOnBinderReceivedListener(binderListener)
    }
}
