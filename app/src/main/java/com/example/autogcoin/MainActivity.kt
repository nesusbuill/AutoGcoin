package com.example.autogcoin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : AppCompatActivity() {

    private val gistUrl = "https://gist.githubusercontent.com/nesusbuill/9a5fc7a3d920a0967f9ab77c395876aa/raw"
    private val defaultGamePackage = "com.hortor.juliancysj"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }

        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnStart = findViewById<Button>(R.id.btnStart)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Hãy tìm và bật AutoGcoin!", Toast.LENGTH_LONG).show()
        }

        btnStart.setOnClickListener {
            // 1. Kiểm tra quyền Trợ năng trước (Nếu chưa bật thì không chạy được)
            if (!isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
                Toast.makeText(this, "Sếp chưa bật quyền Trợ năng cho App!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 2. Kiểm tra quyền Cửa sổ nổi
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, 101)
            } else {
                fetchGistAndStart()
            }
        }
    }

    // Hàm kiểm tra xem quyền Trợ năng đã được bật chưa
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val serviceId = context.packageName + "/" + service.name
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(serviceId, ignoreCase = true)) return true
        }
        return false
    }

    private fun fetchGistAndStart() {
        Toast.makeText(this, "Đang tải cấu hình...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = OkHttpClient().newCall(Request.Builder().url(gistUrl).build()).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val config = Gson().fromJson(json, GistConfig::class.java)
                    getSharedPreferences("AutoConfig", MODE_PRIVATE).edit().putString("config_json", json).apply()
                    withContext(Dispatchers.Main) { startOverlayAndGame(config.game_package.ifEmpty { defaultGamePackage }) }
                } else { withContext(Dispatchers.Main) { fallbackStart() } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { fallbackStart() } }
        }
    }

    private fun fallbackStart() {
        Toast.makeText(this, "Lỗi mạng! Dùng cấu hình mặc định.", Toast.LENGTH_SHORT).show()
        startOverlayAndGame(defaultGamePackage)
    }

    private fun startOverlayAndGame(gamePackage: String) {
        // Kiểm tra xem Game có thực sự được cài trên máy không
        val launchIntent = packageManager.getLaunchIntentForPackage(gamePackage)
        
        if (launchIntent == null) {
            Toast.makeText(this, "Không tìm thấy Game: $gamePackage", Toast.LENGTH_LONG).show()
            return // Thoát luôn, không bật màn hình đen nữa để tránh bị kẹt
        }

        // Nếu có game thì bật màn hình đen
        val serviceIntent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        
        finishAffinity()
    }
}
