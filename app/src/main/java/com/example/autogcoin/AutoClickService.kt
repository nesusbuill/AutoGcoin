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
import android.os.Bundle
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
    
    private var accountCount = 0
    private val maxAccounts = 5
    // Tên package dự phòng, nhưng code sẽ tự nhận diện Package thật
    private val protonVpnPackage = "ch.protonvpn.android"
    private val mainAccountId = "49718334"
    
    private val mailManager = TempMailManager()
    private var isProcessing = false 
    
    // Dùng để vẽ chấm đỏ debug
    private var windowManager: WindowManager? = null
    private var debugDotView: View? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "START_AUTO_CLICK" -> {
                    isProcessing = false
                    if (!isRunning) {
                        isRunning = true
                        showToast("RADAR: Đã bật chế độ Debug!")
                        startMasterCycle()
                    }
                }
                "STOP_AUTO_CLICK" -> {
                    isRunning = false
                    isProcessing = false
                    showToast("ĐÃ DỪNG AUTO!")
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

    private fun startMasterCycle() {
        serviceScope.launch {
            if (accountCount < maxAccounts) {
                showToast("Đang kết nối tới server tạo mail...")
                mailManager.createTempEmail { success, email ->
                    if (success) {
                        showToast("Đã tạo Email: $email. Mở VPN...")
                        toggleProtonVPN()
                    } else {
                        showToast("LỖI TẠO EMAIL!")
                        isRunning = false
                    }
                }
            } else {
                showToast("Đã cày xong $maxAccounts Acc!")
                isRunning = false
            }
        }
    }

    private fun toggleProtonVPN() {
        val intent = packageManager.getLaunchIntentForPackage(protonVpnPackage)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            showToast("Không tìm thấy Proton VPN! Vào Game luôn...")
            openGame()
        }
    }

    private fun openGame() {
        // Thử mở bằng tên Package sếp đã cung cấp
        val intent = packageManager.getLaunchIntentForPackage("com.hortor.juliancysj")
        if(intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            showToast("Không tìm thấy Game Builda để mở!")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return
        val root = rootInActiveWindow ?: return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == "com.android.vending" || pkg.contains("browser")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        if (pkg == protonVpnPackage) {
            handleProtonVPN(root)
            return
        }

        // 🚨 DEBUG: Hiển thị tên Package đang chạy để kiểm tra
        // Bỏ điều kiện if (pkg == gamePackage) để nó chạy trên mọi app
        if (pkg != "com.example.autogcoin" && pkg != protonVpnPackage) {
            handleGameScreen(root, pkg)
        }
    }

    private fun handleProtonVPN(root: AccessibilityNodeInfo) {
        if (isProcessing) return
        isProcessing = true
        serviceScope.launch {
            if (clickNodeByText(root, "Ngắt kết nối") || clickNodeByText(root, "Disconnect") || clickNodeByText(root, "Hủy")) {
                delay(2000)
            }
            val connected = clickNodeByText(rootInActiveWindow, "Kết nối") || 
                            clickNodeByText(rootInActiveWindow, "Connect") || 
                            clickNodeByText(rootInActiveWindow, "Kết nối nhanh")
            if (connected) {
                delay(5000) 
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(1000)
                openGame()
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(1000)
                openGame()
            }
            isProcessing = false
        }
    }

    private fun handleGameScreen(root: AccessibilityNodeInfo, currentPkg: String) {
        if (isProcessing) return

        // Đọc toàn bộ chữ trên màn hình để xem Bot có "mù" không
        val allTexts = mutableListOf<String>()
        dumpAllTexts(root, allTexts)
        val textString = allTexts.joinToString(", ")

        // Nếu tìm thấy chữ "Trang chủ", chứng tỏ đang ở màn hình chính Builda
        if (textString.contains("Trang chủ") || textString.contains("Đề xuất") || currentPkg.contains("hortor")) {
            
            // 1. Tìm nút "Nhận GCoins sau QC" (Nếu đã vào đúng màn hình)
            if (clickNodeByText(root, "Nhận GCoins sau QC") || clickNodeByText(root, "Nhận GCoin sau QC")) {
                showToast("Đã tìm thấy nút Nhận QC (Text)!")
                return
            }
            
            if (clickNodeByText(root, "Nhận GCoins")) {
                return
            }

            // 2. Tắt quảng cáo (Nếu đang xem)
            val adCloseKeywords = listOf("X", "Skip", "Bỏ qua", "Đóng", "Claim")
            for (keyword in adCloseKeywords) {
                if (clickNodeByText(root, keyword)) return
            }

            // 3. ĐIỀU HƯỚNG MÙ (Vì game có thể giấu text)
            // Nếu đang ở màn hình chính (có chữ Trang chủ) -> Click góc dưới phải (Avatar)
            if (textString.contains("Trang chủ")) {
                showToast("Màn hình chính -> Click Avatar góc phải")
                clickCoordinates(resources.displayMetrics.widthPixels - 100f, resources.displayMetrics.heightPixels - 100f)
                Thread.sleep(2000) // Đợi chuyển trang
                return
            }

            // Nếu ở màn hình cá nhân (có chữ "Người theo dõi" hoặc "Bài đăng")
            if (textString.contains("Người theo dõi") || textString.contains("Bài đăng") || textString.contains("kcc")) {
                showToast("Màn hình cá nhân -> Tìm Ví/Số dư")
                if (!clickNodeByText(root, "Ví") && !clickNodeByText(root, "Số dư")) {
                    // Click mù vào khu vực giữa màn hình (nơi có nút Số dư)
                    clickCoordinates(resources.displayMetrics.widthPixels / 2f, resources.displayMetrics.heightPixels * 0.3f)
                }
                Thread.sleep(2000)
                return
            }

            // Nếu đang ở màn hình Số dư (có chữ "GCoins" và "Beans")
            if (textString.contains("GCoins") && textString.contains("Beans")) {
                showToast("Màn hình Số dư -> Bấm nút Nhận màu xanh")
                if (!clickNodeByText(root, "Nhận")) {
                    // Click mù vào nút Nhận (bên trái)
                    clickCoordinates(resources.displayMetrics.widthPixels * 0.25f, resources.displayMetrics.heightPixels * 0.25f)
                }
                Thread.sleep(2000)
                return
            }

            // 4. CHUYỂN COIN VÀ ĐĂNG XUẤT
            if (textString.contains("Đã đạt giới hạn") || textString.contains("Hết lượt")) {
                isProcessing = true
                serviceScope.launch {
                    showToast("Hết lượt! Chuyển Coin...")
                    navigateToTransfer(root)
                    delay(3000)
                    navigateToLogout()
                    delay(3000)
                    accountCount++
                    isProcessing = false
                    if (accountCount < maxAccounts) startMasterCycle()
                }
                return
            }
        }
    }

    // Hàm in ra tất cả các Text đang hiển thị (để debug)
    private fun dumpAllTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        if (node.text != null) list.add(node.text.toString())
        for (i in 0 until node.childCount) {
            dumpAllTexts(node.getChild(i), list)
        }
    }

    // Hàm click tọa độ mù (kèm hiệu ứng chấm đỏ)
    private fun clickCoordinates(x: Float, y: Float) {
        showRedDot(x, y) // Hiện chấm đỏ để sếp biết bot đang bấm ở đâu
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // Vẽ chấm đỏ debug
    private fun showRedDot(x: Float, y: Float) {
        if (windowManager == null) return
        try {
            if (debugDotView == null) {
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
                
                // Xóa chấm đỏ sau 1s
                Handler(Looper.getMainLooper()).postDelayed({
                    windowManager?.removeView(debugDotView)
                    debugDotView = null
                }, 1000)
            }
        } catch (e: Exception) {}
    }

    private suspend fun navigateToTransfer(root: AccessibilityNodeInfo) {
        if (clickNodeByText(root, "Số dư") || clickNodeByText(root, "Ví")) {
            delay(2000)
            if (clickNodeByText(rootInActiveWindow, "Chuyển")) {
                delay(2000)
                val currentRoot = rootInActiveWindow
                if (currentRoot != null) {
                    inputTextToField(currentRoot, "ID", mainAccountId)
                    inputTextToField(currentRoot, "Số lượng", "ALL")
                    delay(1000)
                    clickNodeByText(currentRoot, "Xác nhận")
                    clickNodeByText(currentRoot, "Chuyển")
                    delay(3000)
                }
            }
        }
    }

    private suspend fun navigateToLogout() {
        if (clickNodeByText(rootInActiveWindow, "Cài đặt")) {
            delay(2000)
            if (clickNodeByText(rootInActiveWindow, "Đăng xuất")) {
                delay(1000)
                clickNodeByText(rootInActiveWindow, "Đồng ý")
                clickNodeByText(rootInActiveWindow, "Xác nhận")
                delay(2000)
            }
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fillAllEditTexts(root: AccessibilityNodeInfo?) {
        if (root == null) return
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        findEditTextsRecursively(root, editTexts)
        
        if (editTexts.isNotEmpty()) fillTextToSpecificNode(editTexts[0], mailManager.currentEmail)
        if (editTexts.size > 1) fillTextToSpecificNode(editTexts[1], mailManager.currentPassword)
        if (editTexts.size > 2) fillTextToSpecificNode(editTexts[2], mailManager.currentPassword)
    }

    private fun findEditTextsRecursively(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.className == "android.widget.EditText") list.add(node)
        for (i in 0 until node.childCount) {
            findEditTextsRecursively(node.getChild(i), list)
        }
    }

    private fun fillTextToSpecificNode(node: AccessibilityNodeInfo?, text: String) {
        if (node == null) return
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun inputTextToField(root: AccessibilityNodeInfo?, hintText: String, textToType: String) {
        if (root == null) return
        val nodes = root.findAccessibilityNodeInfosByText(hintText)
        for (node in nodes) {
            if (node.className == "android.widget.EditText") {
                fillTextToSpecificNode(node, textToType)
                return
            }
        }
    }

    private fun findNode(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        return root?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }

    private fun clickNodeByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val node = findNode(root, text) ?: return false
        var clickable = node
        while (clickable != null && !clickable.isClickable) { clickable = clickable.parent }
        
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            clickCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
            return true
        }
        return false
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
