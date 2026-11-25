package org.sparc.studycircle.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.sparc.studycircle.BuildConfig
import org.sparc.studycircle.core.model.AiWritingFeedback
import java.util.concurrent.TimeUnit

/**
 * Lightweight Supabase REST, RPC & Edge Function client.
 * Uses BuildConfig.SUPABASE_URL and BuildConfig.SUPABASE_ANON_KEY when configured,
 * falling back gracefully to local domain execution during offline/sandbox development.
 */
class SupabaseRestClient(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    suspend fun requestAiWritingFeedback(
        chapterNo: Int,
        prompt: String,
        content: String,
        minWords: Int,
        maxWords: Int
    ): Result<AiWritingFeedback> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "Supabase URL not configured" }
            val payload = buildJsonObject {
                put("chapter_no", chapterNo)
                put("prompt", prompt)
                put("content", content)
                put("language", "bn")
                put("min_words", minWords)
                put("max_words", maxWords)
            }.toString()

            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/functions/v1/ai-writing-assistant")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Edge function HTTP ${resp.code}")
                val obj: JsonObject = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                AiWritingFeedback(
                    wordCount = obj["word_count"]?.jsonPrimitive?.int ?: 0,
                    academicToneScore = obj["academic_tone_score"]?.jsonPrimitive?.int ?: 80,
                    toneFeedback = obj["tone_feedback"]?.jsonPrimitive?.content ?: "",
                    grammarSuggestions = obj["grammar_feedback"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList(),
                    structureSuggestions = obj["structure_feedback"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList()
                )
            }
        }
    }
}
