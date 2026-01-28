package com.sentinel.antiscamvn.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

// --- Data Models ---

// 1. Phone Check Response
data class RiskResponse(
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("risk_label") val riskLabel: String,
    @SerializedName("recommendations") val recommendations: List<String>
)

// 2. Chat Request & Response
data class ChatRequest(
    @SerializedName("user_message") val userMessage: String,
    @SerializedName("context") val context: String = "general"
)

data class ChatResponse(
    @SerializedName("ai_response") val aiResponse: String,
    @SerializedName("action_suggested") val actionSuggested: String?
)

// 3. Audio Analysis Response
data class AudioAnalysisResponse(
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("is_scam") val isScam: Boolean,
    @SerializedName("transcript") val transcript: String,
    @SerializedName("warning_message") val warningMessage: String
)

// 4. Image Analysis Response
data class ImageAnalysisResponse(
    @SerializedName("ocr_text") val ocrText: String,
    @SerializedName("risk_analysis") val riskAnalysis: RiskDetail
)

data class RiskDetail(
    @SerializedName("is_safe") val isSafe: Boolean,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("details") val details: String
)

// --- API Interface ---

interface ApiService {
    // 1. Check Phone Number
    @GET("check-phone")
    fun checkPhoneNumber(@Query("phone") phoneNumber: String): Call<RiskResponse>

    // 2. Chat with AI
    @POST("chat-ai")
    fun chatWithAI(@Body request: ChatRequest): Call<ChatResponse>

    // 3. Upload Audio for Analysis (Multipart)
    @Multipart
    @POST("analyze-audio")
    fun analyzeAudio(
        @Part audio: MultipartBody.Part,
        @Part("phone_number") phoneNumber: RequestBody
    ): Call<AudioAnalysisResponse>

    // 4. Upload Image for Analysis (Multipart)
    @Multipart
    @POST("analyze-image")
    fun analyzeImage(
        @Part image: MultipartBody.Part
    ): Call<ImageAnalysisResponse>
}