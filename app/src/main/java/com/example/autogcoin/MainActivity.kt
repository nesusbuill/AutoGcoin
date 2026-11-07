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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
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

    // Link Raw Gist của sếp
    private val gistUrl = "https://gist.githubusercontent.com/nesusbuill/9a5fc7a3d920a0967f9ab77c395876aa/raw/28c79aafcd1c423cab7e5056a555057fe1a07507/rules.json"
    
    private val appNames = mutableListOf<String>()
    private val appPackages = mutableListOf<String>()
    private lateinit var spinnerApps: Spinner

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

        // Quét toàn bộ App (đã lọc rác)
        loadInstalledApps()

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Hãy tìm và bật AutoGcoin!", Toast.LENGTH_LONG).show()
        }

        btnStart.setOnClickListener {
            if (!isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
                Toast.makeText(this, "Sếp phải bật quyền Trợ năng thì Auto mới chạy được!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (spinnerApps.selectedItemPosition == -1) {
                Toast.makeText(this, "Chưa chọn Game!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, 101)
            } else {
                val selectedPackage = appPackages[spinnerApps.selectedItemPosition]
                fetchGistAndStart(selectedPackage)
            }
        }
    }

    // Hàm chỉ lấy các App do người dùng cài, sắp xếp A-Z
    private fun loadInstalledApps() {
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
        
        val appList = mutableListOf<Pair<String, String>>()

        for (app in apps) {
            val packageName = app.activityInfo.packageName
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            
            // FLAG_SYSTEM = 0 nghĩa là app do người dùng tự cài (không phải app mặc định máy)
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val appName = app.loadLabel(packageManager).toString()
                if (packageName != this.packageName) {
                    appList.add(appName to packageName)
                }
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

        // Tự động tìm và chọn "Builda"
        for (i in appNames.indices) {
            if (appNames[i].contains("Builda", ignoreCase = true)) {
                spinnerApps.setSelection(i)
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
            Toast.makeText(this, "Lỗi: Không thể mở App này!", Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        
        finishAffinity()
    }
}
