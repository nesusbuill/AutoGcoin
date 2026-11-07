package com.example.autogcoin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoClickService : AccessibilityService() {

    private var isRunning = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val protonVpnPackage = "ch.protonvpn.android"
    
    // Tên Package chính xác của Builda
    private val gamePackage = "com.hortor.juliancysj" 
    private var isProcessing = false 
    
    private var windowManager: WindowManager? = null
    private var debugDotView: View? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "START_AUTO_CLICK" -> {
                    isProcessing = false
                    if (!isRunning) {
                        isRunning = true
                        showToast("🚀 RADAR: Đã bật chế độ Click Mù!")
                        startBlindClicks()
                    }
                }
                "STOP_AUTO_CLICK" -> {
                    isRunning = false
                    isProcessing = false
                    showToast("🛑 ĐÃ DỪNG AUTO!")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction("START_AUTO_CLICK")
            addAction("STOP_AUTO_CLICK")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    private fun startBlindClicks() {
        serviceScope.launch {
            // Vòng lặp click mù liên tục (mỗi 3 giây click 1 lần theo kịch bản)
            while (isRunning) {
                try {
                    // 1. Click vào Avatar (Góc dưới bên phải - theo ảnh sếp)
                    clickCoordinatePercent(0.90f, 0.95f)
                    delay(3000) // Đợi 3s chuyển trang
                    
                    // 2. Click vào nút Số dư (Giữa màn hình)
                    clickCoordinatePercent(0.50f, 0.30f)
                    delay(3000)
                    
                    // 3. Click vào nút Nhận (Bên trái)
                    clickCoordinatePercent(0.25f, 0.25f)
                    delay(3000)

                    // 4. Click vào nút Nhận GCoins sau QC (Giữa màn hình)
                    clickCoordinatePercent(0.50f, 0.80f)
                    delay(5000)

                    // 5. Click vào nút X tắt quảng cáo (Góc trên phải)
                    clickCoordinatePercent(0.95f, 0.05f)
                    delay(10000) // Đợi video chạy
                    
                } catch (e: Exception) {
                    // Bỏ qua lỗi
                }
            }
        }
    }

    // Hàm click theo tỷ lệ % màn hình
    private fun clickCoordinatePercent(xPercent: Float, yPercent: Float) {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val x = width * xPercent
        val y = height * yPercent
        
        showRedDot(x, y) // Hiện chấm đỏ cho sếp thấy
        
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // Vẽ chấm đỏ X
    private fun showRedDot(x: Float, y: Float) {
        if (windowManager == null) return
        try {
            debugDotView?.let { windowManager?.removeView(it) }
            
            debugDotView = TextView(this).apply {
                text = "X"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.RED)
                textSize = 20f
                gravity = Gravity.CENTER
            }
            val params = WindowManager.LayoutParams(
                60, 60,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = x.toInt() - 30
            params.y = y.toInt() - 30
            windowManager?.addView(debugDotView, params)
            
            Handler(Looper.getMainLooper()).postDelayed({
                try { windowManager?.removeView(debugDotView) } catch (e: Exception) {}
                debugDotView = null
            }, 800) // Chấm đỏ hiện 0.8 giây rồi biến mất
        } catch (e: Exception) {}
    }

    // Vẫn giữ lại hàm này để chặn mấy quảng cáo tự bật Google Play
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == "com.android.vending" || pkg.contains("browser")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() { isRunning = false }
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        unregisterReceiver(receiver)
        debugDotView?.let { windowManager?.removeView(it) }
    }
}
