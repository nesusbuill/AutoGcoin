package com.example.autogcoin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoClickService : AccessibilityService() {

    private var isRunning = false
    private var config: GistConfig? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gamePackage = "" 

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "START_AUTO_CLICK" -> {
                    loadConfig()
                    if (!isRunning) {
                        isRunning = true
                        startSuperAutomation()
                    }
                }
                "STOP_AUTO_CLICK" -> {
                    isRunning = false
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
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

    private fun loadConfig() {
        val prefs = getSharedPreferences("AutoConfig", MODE_PRIVATE)
        gamePackage = prefs.getString("target_package", "") ?: ""
        
        val json = prefs.getString("config_json", null)
        if (json != null) {
            config = Gson().fromJson(json, GistConfig::class.java)
        }
    }

    // 🚀 VÒNG LẶP SIÊU TỰ ĐỘNG
    private fun startSuperAutomation() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    val root = rootInActiveWindow
                    val currentPackage = root?.packageName?.toString() ?: ""

                    // 1. CHẶN CHUYỂN HƯỚNG APP (Anti-Redirect)
                    if (currentPackage == "com.android.vending" || currentPackage.contains("browser") || currentPackage.contains("chrome")) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(1000)
                        continue
                    }

                    // 2. NẾU ĐANG Ở TRONG GAME ĐÃ CHỌN
                    if (currentPackage == gamePackage && gamePackage.isNotEmpty()) {
                        handleGameScreen(root)
                    } else if (gamePackage.isNotEmpty()) {
                        // Nếu đang lạc ở app khác, tự động về Home và mở lại game
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        delay(2000)
                        val launchIntent = packageManager.getLaunchIntentForPackage(gamePackage)
                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                } catch (e: Exception) { }
                delay(1500) // Quét mỗi 1.5s
            }
        }
    }

    // 🌟 HÀM XỬ LÝ MÀN HÌNH GAME (Đã fix lỗi suspend)
    private suspend fun handleGameScreen(root: AccessibilityNodeInfo) {
        // Ưu tiên 1: Tìm nút nhận thưởng
        if (clickNodeByText(root, "10~1000") || clickNodeByText(root, "Phần thưởng từ QC") || clickNodeByText(root, "Xem QC")) {
            delay(2000) // Chờ popup hiện lên
            clickNodeByText(rootInActiveWindow, "Nhận GCoins") || clickNodeByText(rootInActiveWindow, "Xem video")
            return
        }

        // Ưu tiên 2: Tìm nút tắt Quảng Cáo
        val adCloseKeywords = listOf("X", "Skip", "Bỏ qua", "Đóng", "Claim", "Nhận", "Tiếp tục", "Close")
        for (keyword in adCloseKeywords) {
            if (clickNodeByText(rootInActiveWindow, keyword)) {
                return
            }
        }

        // Ưu tiên 3: Tìm nút X "Tàng hình" (Góc trên bên phải)
        if (clickTopRightHiddenButton(rootInActiveWindow)) {
            return
        }
    }

    private fun clickNodeByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (performSmartClick(node)) return true
        }
        return false
    }

    private fun clickTopRightHiddenButton(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        fun traverse(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (rect.right > screenWidth * 0.75 && rect.top < screenHeight * 0.25) {
                if (node.isClickable && (node.className.contains("Button") || node.className.contains("Image"))) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                if (traverse(node.getChild(i))) return true
            }
            return false
        }
        return traverse(root)
    }

    private fun performSmartClick(node: AccessibilityNodeInfo): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            current = current.parent
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { isRunning = false }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        unregisterReceiver(receiver)
    }
}
