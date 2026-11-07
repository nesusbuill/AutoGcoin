package com.example.autogcoin // Sếp nhớ đổi lại package cho khớp với project của sếp

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class TempMailManager {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var currentEmail = ""
    var currentPassword = "PassWord123@"
    private var token = ""

    // 1. Tạo Email
    fun createTempEmail(callback: (Boolean, String) -> Unit) {
        val requestDomain = Request.Builder().url("https://api.mail.tm/domains").build()
        client.newCall(requestDomain).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false, "Lỗi mạng") }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = JSONObject(response.body?.string() ?: "{}")
                    val domains = body.optJSONArray("hydra:member")
                    val domain = domains?.getJSONObject(0)?.getString("domain") ?: "dcctrav.com"
                    
                    val randomString = (1..10).map { ('a'..'z').random() }.joinToString("")
                    currentEmail = "$randomString@$domain"

                    val json = JSONObject().apply {
                        put("address", currentEmail)
                        put("password", currentPassword)
                    }
                    val request = Request.Builder()
                        .url("https://api.mail.tm/accounts")
                        .post(json.toString().toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) { callback(false, "Lỗi tạo mail") }
                        override fun onResponse(call: Call, res: Response) {
                            if (res.isSuccessful) loginToGetToken { callback(it, currentEmail) }
                            else callback(false, "Email lỗi")
                        }
                    })
                } else callback(false, "Lỗi lấy domain")
            }
        })
    }

    // 2. Lấy Token
    private fun loginToGetToken(callback: (Boolean) -> Unit) {
        val json = JSONObject().apply {
            put("address", currentEmail)
            put("password", currentPassword)
        }
        val request = Request.Builder().url("https://api.mail.tm/token")
            .post(json.toString().toRequestBody(jsonMediaType)).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    token = JSONObject(response.body?.string() ?: "").getString("token")
                    callback(true)
                } else { callback(false) }
            }
        })
    }

    // 3. Lấy mã OTP
    fun fetchOTP(callback: (String?) -> Unit) {
        val request = Request.Builder().url("https://api.mail.tm/messages")
            .addHeader("Authorization", "Bearer $token").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null) }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = JSONObject(response.body?.string() ?: "{}")
                    val messages = body.optJSONArray("hydra:member")
                    if (messages != null && messages.length() > 0) {
                        getMessageContent(messages.getJSONObject(0).getString("id"), callback)
                    } else { callback(null) }
                } else { callback(null) }
            }
        })
    }

    private fun getMessageContent(id: String, callback: (String?) -> Unit) {
        val request = Request.Builder().url("https://api.mail.tm/messages/$id")
            .addHeader("Authorization", "Bearer $token").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null) }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val text = JSONObject(response.body?.string() ?: "{}").optString("text", "")
                    val otp = Regex("\\b\\d{4,6}\\b").find(text)?.value
                    callback(otp)
                } else { callback(null) }
            }
        })
    }
}
