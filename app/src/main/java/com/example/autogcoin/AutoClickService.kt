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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
    private val gamePackage = "com.hortor.juliancysj"
    private val protonVpnPackage = "ch.protonvpn.android"
    private val mainAccountId = "49718334"
    
    private val mailManager = TempMailManager()
    private var isProcessing = false 

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "START_AUTO_CLICK" -> {
                    // Reset lại trạng thái mỗi khi bấm chạy
                    isProcessing = false
                    if (!isRunning) {
                        isRunning = true
                        showToast("BẮT ĐẦU: Đang tạo Email...")
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
                        showToast("Đã tạo Email: $email. Đang mở VPN...")
                        toggleProtonVPN()
                    } else {
                        showToast("LỖI TẠO EMAIL! Sếp kiểm tra lại mạng.")
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
        val intent = packageManager.getLaunchIntentForPackage(gamePackage)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
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

        if (pkg == gamePackage) {
            handleGameScreen(root)
        }
    }

    private fun handleProtonVPN(root: AccessibilityNodeInfo) {
        if (isProcessing) return
        isProcessing = true
        serviceScope.launch {
            if (clickNodeByText(root, "Ngắt kết nối") || clickNodeByText(root, "Disconnect") || clickNodeByText(root, "Hủy")) {
                showToast("Đang đổi IP VPN...")
                delay(2000)
            }
            
            val connected = clickNodeByText(rootInActiveWindow, "Kết nối") || 
                            clickNodeByText(rootInActiveWindow, "Connect") || 
                            clickNodeByText(rootInActiveWindow, "Kết nối nhanh")
            
            if (connected) {
                showToast("Đang kết nối VPN...")
                delay(5000) 
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(1000)
                openGame()
                showToast("Đã đổi IP, đang vào Game...")
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(1000)
                openGame()
            }
            isProcessing = false
        }
    }

    private fun handleGameScreen(root: AccessibilityNodeInfo) {
        if (isProcessing) return

        if (findNode(root, "Đăng ký") != null || findNode(root, "Register") != null) {
            isProcessing = true
            serviceScope.launch {
                showToast("Đang tự động Đăng Ký...")
                clickNodeByText(root, "Đăng ký")
                delay(1000)
                clickNodeByText(root, "Đăng ký bằng Email")
                delay(1500)
                fillAllEditTexts(rootInActiveWindow)
                clickNodeByText(rootInActiveWindow, "Tạo tài khoản")
                delay(8000)
                isProcessing = false
            }
            return
        }

        if (findNode(root, "Nhập mã") != null || findNode(root, "OTP") != null) {
            isProcessing = true
            serviceScope.launch {
                showToast("Đang chờ mã OTP...")
                var retry = 0
                while (retry < 10) { 
                    mailManager.fetchOTP { otp ->
                        if (otp != null) {
                            serviceScope.launch {
                                showToast("Đã lấy OTP: $otp")
                                val editTexts = mutableListOf<AccessibilityNodeInfo>()
                                findEditTextsRecursively(rootInActiveWindow, editTexts)
                                if(editTexts.isNotEmpty()) fillTextToSpecificNode(editTexts[0], otp)
                                clickNodeByText(rootInActiveWindow, "Xác nhận")
                                isProcessing = false
                            }
                        }
                    }
                    delay(3000)
                    retry++
                }
                isProcessing = false
            }
            return
        }

        if (findNode(root, "Đã đạt giới hạn") != null || findNode(root, "Hết lượt") != null) {
            isProcessing = true
            serviceScope.launch {
                showToast("Hết lượt! Đang chuyển Coin...")
                navigateToTransfer(root)
                delay(3000)
                showToast("Đang Đăng xuất...")
                navigateToLogout()
                delay(3000)
                
                accountCount++
                isProcessing = false
                if (accountCount < maxAccounts) {
                    startMasterCycle()
                } else {
                    showToast("ĐÃ HOÀN THÀNH $maxAccounts ACC!")
                    isRunning = false
                }
            }
            return
        }

        if (clickNodeByText(root, "Nhận GCoins sau QC") || clickNodeByText(root, "Nhận GCoin sau QC")) {
            showToast("Đang vào xem Quảng Cáo...")
            return
        }
        
        if (clickNodeByText(root, "Nhận GCoins")) {
            return
        }
        
        val adCloseKeywords = listOf("X", "Skip", "Bỏ qua", "Đóng", "Claim")
        for (keyword in adCloseKeywords) {
            if (clickNodeByText(root, keyword)) return
        }

        if (clickNodeByText(root, "Nhận") || clickNodeByText(root, "Nhận thưởng")) {
            return
        }
        
        if (findNode(root, "Trang chủ") != null) {
            if (!clickAvatarByCoordinates()) {
                clickNodeByText(root, "Ví")
                clickNodeByText(root, "Số dư")
            }
        }
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

    private fun clickAvatarByCoordinates(): Boolean {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val x = width - 100f
        val y = height - 100f
        
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build(), null, null)
        return true
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
        if (node.className == "android.widget.EditText") {
            list.add(node)
        }
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
            val path = Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
            dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build(), null, null)
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
    }
}
