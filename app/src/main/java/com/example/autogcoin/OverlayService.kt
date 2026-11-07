package com.example.autogcoin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val CHANNEL_ID = "AutoGcoinChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingMenu()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "AutoGcoin", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoGcoin Đang Chạy")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    private fun showFloatingMenu() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100; params.y = 300
        windowManager.addView(overlayView, params)

        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> { initialX = params.x; initialY = params.y; initialTouchX = event.rawX; initialTouchY = event.rawY; return true }
                    MotionEvent.ACTION_MOVE -> { params.x = initialX + (event.rawX - initialTouchX).toInt(); params.y = initialY + (event.rawY - initialTouchY).toInt(); windowManager.updateViewLayout(overlayView, params); return true }
                }
                return false
            }
        })

        // GHI LỆNH VÀO BỘ NHỚ
        overlayView?.findViewById<Button>(R.id.btnStartAuto)?.setOnClickListener {
            val prefs = getSharedPreferences("AutoConfig", MODE_PRIVATE)
            prefs.edit().putBoolean("is_auto_running", true).apply()
            Toast.makeText(this, "✅ Đã Bật Auto! Vui lòng chờ 1-2s...", Toast.LENGTH_SHORT).show()
        }

        overlayView?.findViewById<Button>(R.id.btnStopAuto)?.setOnClickListener {
            val prefs = getSharedPreferences("AutoConfig", MODE_PRIVATE)
            prefs.edit().putBoolean("is_auto_running", false).apply()
            Toast.makeText(this, "🛑 Đã Dừng Auto!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
