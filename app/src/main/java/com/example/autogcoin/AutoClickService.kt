package com.example.autogcoin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoClickService : AccessibilityService() {

    private var isRunning = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gamePackage = "com.hortor.juliancysj" // Đã cập nhật đúng package Builda
    
    private var windowManager: WindowManager? = null
    private var debugDotView: View? = null
    private var lastClickTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showToast("✅ Trợ năng đã kết nối! Đang chờ lệnh...")
        startPollingMemory()
    }

    // Hàm liên tục kiểm tra bộ nhớ xem có lệnh CHẠY không
    private fun startPollingMemory() {
        serviceScope.launch {
            while (true) {
                val prefs = getSharedPreferences("AutoConfig", MODE_PRIVATE)
                isRunning = prefs.getBoolean("is_auto_running", false)
                
                if (isRunning) {
                    val root = rootInActiveWindow
                    val pkg = root?.packageName?.toString() ?: ""
                    
                    if (pkg == gamePackage) {
                        // Chỉ thực hiện click nếu đã qua 2.5 giây (tránh click quá nhanh)
                        if (System.currentTimeMillis() - lastClickTime > 2500) {
                            handleGameScreen(root)
                            lastClickTime = System.currentTimeMillis()
                        }
                    }
                }
                delay(500) // Cứ 0.5s kiểm tra bộ nhớ 1 lần
            }
        }
    }

    private fun handleGameScreen(root: AccessibilityNodeInfo?) {
        if (root == null) return
        
        val allTexts = mutableListOf<String>()
        dumpAllTexts(root, allTexts)
        val textString = allTexts.joinToString(" ")

        // 1. TRANG CHỦ -> CLICK AVATAR
        if (textString.contains("Trang chủ") || textString.contains("Đề xuất")) {
            showToast("Radar: Trang Chủ -> Bấm Avatar")
            clickCoordinatePercent(0.90f, 0.92f)
            return
        }

        // 2. TRANG CÁ NHÂN -> CLICK SỐ DƯ
        if (textString.contains("Người theo dõi") || textString.contains("Bài đăng") || textString.contains("Dự án")) {
            showToast("Radar: Trang Cá Nhân -> Bấm Số Dư")
            if (!clickNodeByText(root, "Số dư")) {
                clickCoordinatePercent(0.50f, 0.25f) // Tọa độ nút số dư
            }
            return
        }

        // 3. TRANG SỐ DƯ -> CLICK NHẬN
        if (textString.contains("GCoins") && textString.contains("Beans")) {
            showToast("Radar: Trang Số Dư -> Bấm Nhận")
            if (!clickNodeByText(root, "Nhận")) {
                clickCoordinatePercent(0.20f, 0.20f) // Tọa độ nút nhận
            }
            return
        }

        // 4. CÀY COIN & TẮT QC
        if (clickNodeByText(root, "Nhận GCoins sau QC") || clickNodeByText(root, "Nhận GCoins")) {
            showToast("Radar: Đang xem quảng cáo...")
            return
        }
        for (kw in listOf("X", "Skip", "Bỏ qua", "Đóng", "Claim")) {
            if (clickNodeByText(root, kw)) return
        }
    }

    private fun clickCoordinatePercent(xPercent: Float, yPercent: Float) {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val x = width * xPercent
        val y = height * yPercent
        
        showRedDot(x, y)
        
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build()
        dispatchGesture(gesture, null, null)
    }

    private fun showRedDot(x: Float, y: Float) {
        if (windowManager == null) return
        try {
            debugDotView?.let { windowManager?.removeView(it) }
            debugDotView = TextView(this).apply {
                text = "X"; setTextColor(Color.WHITE); setBackgroundColor(Color.RED); textSize = 20f; gravity = Gravity.CENTER
            }
            val params = WindowManager.LayoutParams(
                60, 60,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = x.toInt() - 30; params.y = y.toInt() - 30
            windowManager?.addView(debugDotView, params)
            
            Handler(Looper.getMainLooper()).postDelayed({
                try { windowManager?.removeView(debugDotView) } catch (e: Exception) {}
                debugDotView = null
            }, 800)
        } catch (e: Exception) {}
    }

    private fun dumpAllTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        if (node.text != null) list.add(node.text.toString())
        for (i in 0 until node.childCount) dumpAllTexts(node.getChild(i), list)
    }

    private fun clickNodeByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val node = root?.findAccessibilityNodeInfosByText(text)?.firstOrNull() ?: return false
        var clickable = node
        while (clickable != null && !clickable.isClickable) { clickable = clickable.parent }
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0) { clickCoordinatePercent(rect.centerX().toFloat() / resources.displayMetrics.widthPixels, rect.centerY().toFloat() / resources.displayMetrics.heightPixels); return true }
        return false
    }

    // Chặn quảng cáo tự chuyển sang CH Play
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
        debugDotView?.let { windowManager?.removeView(it) }
    }
}
