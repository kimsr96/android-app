package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini Request/Response Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content?
)

// --- Structured Output Model ---

@JsonClass(generateAdapter = true)
data class MeetingAnalysis(
    @Json(name = "summary") val summary: String,
    @Json(name = "actionItems") val actionItems: List<String>,
    @Json(name = "keywords") val keywords: List<String>
)

// --- Retrofit Setup ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

class GeminiRepository {
    private val apiService = RetrofitClient.service
    private val adapter = RetrofitClient.moshi.adapter(MeetingAnalysis::class.java)

    suspend fun analyzeMeeting(title: String, content: String): MeetingAnalysis? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is missing or not configured in Secrets panel.")
        }

        val prompt = """
            Analyze the following meeting minutes note. Output a refined summary, action items to be followed, and 3-5 concise keywords summarizing the meeting topics.
            
            Meeting Title: $title
            Meeting Draft Content:
            $content
            
            You must reply ONLY in a valid JSON object matching this schema:
            {
              "summary": "Clear, concise paragraph summarizing what was discussed and decided. (Translate to Korean)",
              "actionItems": ["Action item 1 (Owner/Task) in Korean", "Action item 2 in Korean"],
              "keywords": ["Keyword1", "Keyword2"]
            }
        """.trimIndent()

        val systemPrompt = "You are an expert AI board secretary. You format messy drafts of meeting conversations, summaries, and action items accurately in Korean."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                responseMimeType = "application/json"
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemPrompt))
            )
        )

        val response = apiService.generateContent(apiKey, request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: return null

        return try {
            adapter.fromJson(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
