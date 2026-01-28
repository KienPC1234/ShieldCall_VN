package com.sentinel.antiscamvn.network

import android.content.Context
import androidx.preference.PreferenceManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // WARNING: Replace this with your actual backend URL.
    private const val BASE_URL = "http://10.0.2.2:3000/"
    
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val mockInterceptor = Interceptor { chain ->
        val context = appContext
        val isDebug = context != null && PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("debug_mode", false)

        if (isDebug) {
            val uri = chain.request().url.toUri().toString()
            val responseString = when {
                uri.contains("check-phone") -> MockData.CHECK_PHONE_RESPONSE
                uri.contains("chat-ai") -> MockData.CHAT_RESPONSE
                uri.contains("analyze-audio") -> MockData.AUDIO_RESPONSE
                uri.contains("analyze-image") -> MockData.IMAGE_RESPONSE
                else -> "{}"
            }

            Response.Builder()
                .code(200)
                .message("OK")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .body(responseString.toResponseBody("application/json".toMediaType()))
                .addHeader("content-type", "application/json")
                .build()
        } else {
            chain.proceed(chain.request())
        }
    }

    val instance: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(mockInterceptor) // Add Mock Interceptor
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}

object MockData {
    const val CHECK_PHONE_RESPONSE = """
    {
      "risk_level": "RED",
      "risk_label": "Cảnh báo: Giả mạo Công An (TEST MODE)",
      "recommendations": ["Cúp máy ngay", "Chặn số này"]
    }
    """

    const val CHAT_RESPONSE = """
    {
      "ai_response": "Đây là phản hồi từ chế độ DEBUG (Test). Hệ thống hoạt động tốt!",
      "action_suggested": "NONE"
    }
    """

    const val AUDIO_RESPONSE = """
    {
      "risk_score": 88,
      "is_scam": true,
      "transcript": "[DEBUG MODE] Xin chào, đây là cuộc gọi kiểm thử. Nội dung này là giả lập.",
      "warning_message": "Phát hiện nội dung lừa đảo giả lập."
    }
    """

    const val IMAGE_RESPONSE = """
    {
      "ocr_text": "[DEBUG] Số tài khoản: 123456789 - Ngân hàng TEST",
      "risk_analysis": {
        "is_safe": false,
        "risk_level": "YELLOW",
        "details": "Ảnh chứa thông tin tài khoản ngân hàng (Giả lập)."
      }
    }
    """
}