package com.example.autogcoin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.*
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

    private val gistUrl = "https://gist.githubusercontent.com/nesusbuill/9a5fc7a3d920a0967f9ab77c395876aa/raw/28c79aafcd1c423cab7e5056a555057fe1a07507/rules.json"
    
    private val appNames = mutableListOf<String>()
    private val appPackages = mutableListOf<String>()
    private lateinit var spinnerApps: Spinner
    private lateinit var edtPackage: EditText

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
        spinnerApps = findViewById(R.id.spinnerApps)
        edtPackage = findViewById(R.id.edtPackage)

        loadAllInstalledApps()

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Hãy tìm và bật AutoGcoin!", Toast.LENGTH_LONG).show()
        }

        btnStart.setOnClickListener {
            if (!isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
                Toast.makeText(this, "Sếp phải bật quyền Trợ năng!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Ưu tiên lấy từ ô nhập tay, nếu trống thì lấy từ Spinner
            var selectedPackage = edtPackage.text.toString().trim()
            if (selectedPackage.isEmpty()) {
                if (spinnerApps.selectedItemPosition != -1) {
                    selectedPackage = appPackages[spinnerApps.selectedItemPosition]
                } else {
                    Toast.makeText(this, "Chưa chọn hoặc nhập Game!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, 101)
            } else {
                fetchGistAndStart(selectedPackage)
            }
        }
    }

    // Hàm quét TẤT CẢ các App (kể cả app ẩn)
    private fun loadAllInstalledApps() {
        val packages = packageManager.getInstalledPackages(0)
        val appList = mutableListOf<Pair<String, String>>()

        for (pkgInfo in packages) {
            val packageName = pkgInfo.packageName
            
            // Bỏ qua app của chính mình
            if (packageName == this.packageName) continue

            // Chỉ lấy app không phải hệ thống (bên thứ 3) để danh sách bớt rác
            if ((pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val appName = packageManager.getApplicationLabel(pkgInfo.applicationInfo).toString()
                appList.add("$appName\n($packageName)" to packageName)
            }
        }

        appList.sortBy { it.first }
        
        appNames.clear()
        appPackages.clear()
        for (app in appList) {
            appNames.add(app.first)
            appPackages.add(app.second)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, appNames)
        spinnerApps.adapter = adapter

        // Tự động điền Builda nếu tìm thấy trong danh sách
        for (i in appNames.indices) {
            if (appNames[i].contains("Builda", ignoreCase = true) || appPackages[i].contains("hortor", ignoreCase = true)) {
                spinnerApps.setSelection(i)
                edtPackage.setText(appPackages[i]) // Tự điền vào ô text
                break
            }
        }
    }

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

    private fun fetchGistAndStart(selectedPackage: String) {
        Toast.makeText(this, "Đang tải cấu hình...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = OkHttpClient().newCall(Request.Builder().url(gistUrl).build()).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val config = Gson().fromJson(json, GistConfig::class.java)
                    
                    val prefs = getSharedPreferences("AutoConfig", MODE_PRIVATE)
                    prefs.edit().putString("config_json", json).apply()
                    prefs.edit().putString("target_package", selectedPackage).apply()
                    
                    withContext(Dispatchers.Main) { startOverlayAndGame(selectedPackage) }
                } else { withContext(Dispatchers.Main) { fallbackStart(selectedPackage) } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { fallbackStart(selectedPackage) } }
        }
    }

    private fun fallbackStart(selectedPackage: String) {
        Toast.makeText(this, "Lỗi mạng! Dùng cấu hình mặc định.", Toast.LENGTH_SHORT).show()
        getSharedPreferences("AutoConfig", MODE_PRIVATE).edit().putString("target_package", selectedPackage).apply()
        startOverlayAndGame(selectedPackage)
    }

    private fun startOverlayAndGame(gamePackage: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(gamePackage)
        
        if (launchIntent == null) {
            // Thử mở bằng lệnh của hệ thống nếu getLaunchIntentForPackage thất bại (Dành cho app ẩn)
            val intent = Intent().apply {
                setClassName(gamePackage, "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Package không tồn tại hoặc không thể mở!", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }

        val serviceIntent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        
        finishAffinity()
    }
}
