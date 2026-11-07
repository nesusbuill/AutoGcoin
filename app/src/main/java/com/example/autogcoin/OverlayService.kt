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
            val serviceChannel = NotificationChannel(CHANNEL_ID, "AutoGcoin Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoGcoin Đang Chạy")
            .setContentText("Nhấn để mở khóa...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    private fun showFloatingMenu() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)
        
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, 
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, 
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300
        windowManager.addView(overlayView, params)

        // Thiết lập kéo thả (Drag & Drop) để di chuyển thanh nổi
        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })

        // XỬ LÝ NÚT CHẠY AUTO
        overlayView?.findViewById<Button>(R.id.btnStartAuto)?.setOnClickListener {
            Toast.makeText(this, "Đã bấm CHẠY AUTO! Đang gửi lệnh...", Toast.LENGTH_SHORT).show()
            
            // Sử dụng setPackage để Android 13/14 không chặn lệnh
            val intent = Intent("START_AUTO_CLICK").setPackage(packageName)
            sendBroadcast(intent)
        }

        // XỬ LÝ NÚT DỪNG
        overlayView?.findViewById<Button>(R.id.btnStopAuto)?.setOnClickListener {
            Toast.makeText(this, "Đã bấm DỪNG!", Toast.LENGTH_SHORT).show()
            
            val intent = Intent("STOP_AUTO_CLICK").setPackage(packageName)
            sendBroadcast(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
