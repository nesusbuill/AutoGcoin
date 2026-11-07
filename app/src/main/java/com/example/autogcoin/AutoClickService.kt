package com.example.autogcoin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "START_AUTO_CLICK") {
                loadConfig()
                if (!isRunning) {
                    isRunning = true
                    startAutoFlow()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("START_AUTO_CLICK")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    private fun loadConfig() {
        val json = getSharedPreferences("AutoConfig", MODE_PRIVATE).getString("config_json", null)
        config = if (json != null) Gson().fromJson(json, GistConfig::class.java) else {
            GistConfig(
                flow = listOf(
                    FlowStep("click_text", "10~1000", delay_ms = 2000),
                    FlowStep("click_text", "Nhận GCoins", delay_ms = 2000),
                    FlowStep("wait_ad", delay_ms = 25000),
                    FlowStep("close_ad", texts = listOf("X", "Skip", "Bỏ qua"))
                )
            )
        }
    }

    private fun startAutoFlow() {
        serviceScope.launch {
            while (isRunning) { 
                try {
                    config?.flow?.forEach { step ->
                        if (!isRunning) return@launch
                        when (step.action) {
                            "click_text" -> step.text?.let { clickNodeByText(it) }
                            "wait_ad" -> delay(step.delay_ms)
                            "close_ad" -> {
                                var closed = false
                                step.texts?.forEach { if (clickNodeByText(it)) closed = true }
                                if (!closed) clickTopRightCorner()
                            }
                        }
                        if (step.delay_ms > 0) delay(step.delay_ms)
                    }
                } catch (e: Exception) { /* Bỏ qua lỗi để vòng lặp chạy tiếp */ }
                delay(2000) 
            }
        }
    }

    private fun clickNodeByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            var clickable = node
            while (clickable != null && !clickable.isClickable) { clickable = clickable.parent }
            if (clickable != null) {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    private fun clickTopRightCorner() {
        val width = resources.displayMetrics.widthPixels
        val path = Path().apply { moveTo(width - 60f, 60f) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return
        val pkg = event.packageName?.toString() ?: return

        // 🛑 CHẶN ĐỨNG CHUYỂN HƯỚNG (Google Play, Trình duyệt)
        if (pkg == "com.android.vending" || pkg.contains("chrome") || pkg.contains("browser")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
        }
    }

    override fun onInterrupt() { isRunning = false }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        unregisterReceiver(receiver)
    }
}
