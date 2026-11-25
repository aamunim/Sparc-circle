package org.sparc.studycircle.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.sparc.studycircle.BuildConfig
import org.sparc.studycircle.core.model.AiWritingFeedback
import org.sparc.studycircle.core.model.UserProfile
import org.sparc.studycircle.core.model.UserRole
import java.util.concurrent.TimeUnit

data class SupabaseAuthSession(
    val userId: String,
    val email: String,
    val accessToken: String
)

class SupabaseRestClient(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    var currentSession: SupabaseAuthSession? = null
        private set

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private fun bearerToken(): String =
        currentSession?.accessToken?.takeIf { it.isNotBlank() } ?: anonKey

    fun clearSession() {
        currentSession = null
    }

    suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "Supabase URL not configured" }
            val body = buildJsonObject {
                put("email", email.trim())
                put("password", password)
            }.toString()

            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("ইমেইল বা পাসওয়ার্ড সঠিক নয় (${resp.code})")
                val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                val userObj = obj["user"]?.jsonObject
                SupabaseAuthSession(
                    userId = userObj?.get("id")?.jsonPrimitive?.content ?: "",
                    email = email.trim(),
                    accessToken = obj["access_token"]?.jsonPrimitive?.content ?: ""
                ).also { currentSession = it }
            }
        }
    }

    suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        fullName: String,
        department: String,
        skills: List<String>,
        referralCode: String
    ): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "Supabase URL not configured" }
            val body = buildJsonObject {
                put("email", email.trim())
                put("password", password)
                put("data", buildJsonObject {
                    put("name", fullName)
                    put("department", department)
                })
            }.toString()

            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/auth/v1/signup")
                .addHeader("apikey", anonKey)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val session = http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("সাইন-আপ সম্পন্ন হয়নি (${resp.code})")
                val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                val userObj = obj["user"]?.jsonObject
                SupabaseAuthSession(
                    userId = userObj?.get("id")?.jsonPrimitive?.content ?: "",
                    email = email.trim(),
                    accessToken = obj["access_token"]?.jsonPrimitive?.content ?: ""
                )
            }
            currentSession = session

            // Update skills & optional referral bonus if access token was issued immediately
            if (session.accessToken.isNotBlank() && session.userId.isNotBlank()) {
                runCatching {
                    val patchJson = buildJsonObject {
                        put("skills", JsonArray(skills.map { JsonPrimitive(it) }))
                        put("department", department)
                    }.toString()
                    val patchReq = Request.Builder()
                        .url("${baseUrl.trimEnd('/')}/rest/v1/profiles?id=eq.${session.userId}")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer ${session.accessToken}")
                        .patch(patchJson.toRequestBody("application/json".toMediaType()))
                        .build()
                    http.newCall(patchReq).execute().close()

                    if (referralCode.isNotBlank()) {
                        val rpcBody = buildJsonObject {
                            put("p_new_user_id", session.userId)
                            put("p_referral_code", referralCode.trim())
                        }.toString()
                        val rpcReq = Request.Builder()
                            .url("${baseUrl.trimEnd('/')}/rest/v1/rpc/apply_referral_bonus")
                            .addHeader("apikey", anonKey)
                            .addHeader("Authorization", "Bearer ${session.accessToken}")
                            .post(rpcBody.toRequestBody("application/json".toMediaType()))
                            .build()
                        http.newCall(rpcReq).execute().close()
                    }
                }
            }
            session
        }
    }

    suspend fun fetchDirectoryProfiles(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "Supabase URL not configured" }
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/rest/v1/profiles?select=*&order=gamification_points.desc&limit=200")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer ${bearerToken()}")
                .get()
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Profiles fetch HTTP ${resp.code}")
                val arr = json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
                arr.map { el ->
                    val o = el.jsonObject
                    val roleStr = o["role"]?.jsonPrimitive?.contentOrNull ?: "member"
                    UserProfile(
                        id = o["id"]?.jsonPrimitive?.content ?: "",
                        name = o["name"]?.jsonPrimitive?.content ?: "Researcher",
                        department = o["department"]?.jsonPrimitive?.content ?: "General",
                        researchInterests = o["research_interests"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                        skills = o["skills"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                        role = runCatching { UserRole.valueOf(roleStr.uppercase()) }.getOrDefault(UserRole.MEMBER),
                        rating = o["rating"]?.jsonPrimitive?.doubleOrNull ?: 5.0,
                        gamificationPoints = o["gamification_points"]?.jsonPrimitive?.intOrNull ?: 0,
                        readingStreakDays = o["reading_streak_days"]?.jsonPrimitive?.intOrNull ?: 0,
                        referralCode = o["referral_code"]?.jsonPrimitive?.content ?: "",
                        walletBalanceBdt = o["wallet_balance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        earnedBadges = o["earned_badges"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    )
                }
            }
        }
    }

    suspend fun sendDirectMessage(receiverId: String, bodyText: String) = withContext(Dispatchers.IO) {
        val senderId = currentSession?.userId ?: return@withContext
        if (!isConfigured) return@withContext
        runCatching {
            val payload = buildJsonObject {
                put("sender_id", senderId)
                put("receiver_id", receiverId)
                put("body", bodyText)
            }.toString()
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/rest/v1/member_messages")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer ${bearerToken()}")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().close()
        }
    }

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
                .addHeader("Authorization", "Bearer ${bearerToken()}")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Edge function HTTP ${resp.code}")
                val obj: JsonObject = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                AiWritingFeedback(
                    wordCount = obj["word_count"]?.jsonPrimitive?.intOrNull ?: 0,
                    academicToneScore = obj["academic_tone_score"]?.jsonPrimitive?.intOrNull ?: 80,
                    toneFeedback = obj["tone_feedback"]?.jsonPrimitive?.contentOrNull ?: "",
                    grammarSuggestions = obj["grammar_feedback"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    structureSuggestions = obj["structure_feedback"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                )
            }
        }
    }
}
