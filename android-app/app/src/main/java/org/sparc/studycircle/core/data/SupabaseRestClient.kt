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
import org.sparc.studycircle.core.model.ApiKeyVaultConfig
import org.sparc.studycircle.core.model.UserProfile
import org.sparc.studycircle.core.model.UserRole
import java.util.concurrent.TimeUnit

data class SupabaseAuthSession(
    val userId: String,
    val email: String,
    val accessToken: String
)

class SupabaseRestClient(
    private val defaultBaseUrl: String = BuildConfig.SUPABASE_URL,
    private val defaultAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    var vaultConfig: ApiKeyVaultConfig = ApiKeyVaultConfig()

    @Volatile
    var currentSession: SupabaseAuthSession? = null
        private set

    val activeSupabaseUrl: String
        get() = vaultConfig.customSupabaseUrl.trim().ifBlank { defaultBaseUrl.trim() }

    val activeAnonKey: String
        get() = vaultConfig.customSupabaseAnonKey.trim().ifBlank { defaultAnonKey.trim() }

    val isConfigured: Boolean
        get() = activeSupabaseUrl.isNotBlank() && activeAnonKey.isNotBlank()

    private fun bearerToken(): String =
        currentSession?.accessToken?.takeIf { it.isNotBlank() } ?: activeAnonKey

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
                .url("${activeSupabaseUrl.trimEnd('/')}/auth/v1/token?grant_type=password")
                .addHeader("apikey", activeAnonKey)
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
            if (password.length < 6) {
                error("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে (Password must be at least 6 characters)")
            }

            // Primary path: Call auth-register Edge Function (auto-confirms user via Admin API, ZERO 429 email limits!)
            val edgePayload = buildJsonObject {
                put("email", email.trim())
                put("password", password)
                put("name", fullName)
                put("department", department)
                put("skills", JsonArray(skills.map { JsonPrimitive(it) }))
                put("referral_code", referralCode.trim())
            }.toString()

            val edgeReq = Request.Builder()
                .url("${activeSupabaseUrl.trimEnd('/')}/functions/v1/auth-register")
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .post(edgePayload.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(edgeReq).execute().use { edgeResp ->
                val raw = edgeResp.body?.string() ?: "{}"
                if (edgeResp.isSuccessful) {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    val userObj = obj["user"]?.jsonObject
                    val session = SupabaseAuthSession(
                        userId = userObj?.get("id")?.jsonPrimitive?.content ?: "",
                        email = email.trim(),
                        accessToken = obj["access_token"]?.jsonPrimitive?.content ?: ""
                    )
                    currentSession = session
                    return@runCatching session
                }
                if (edgeResp.code in 400..499 && edgeResp.code != 404) {
                    val edgeErr = runCatching {
                        json.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull() ?: "HTTP ${edgeResp.code}"
                    if (edgeErr.contains("Invalid login credentials", ignoreCase = true) ||
                        edgeErr.contains("already", ignoreCase = true)
                    ) {
                        error("এই ইমেইল দিয়ে আগেই অ্যাকাউন্ট খোলা আছে — অনুগ্রহ করে সঠিক পাসওয়ার্ড দিয়ে Sign In করুন (Email already registered).")
                    }
                    error(edgeErr)
                }
            }

            // Fallback path (only if Edge Function not deployed yet): Standard GoTrue /auth/v1/signup (mailer_autoconfirm = true)
            val body = buildJsonObject {
                put("email", email.trim())
                put("password", password)
                put("data", buildJsonObject {
                    put("name", fullName)
                    put("department", department)
                })
            }.toString()

            val req = Request.Builder()
                .url("${activeSupabaseUrl.trimEnd('/')}/auth/v1/signup")
                .addHeader("apikey", activeAnonKey)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val session = http.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        val errObj = json.parseToJsonElement(respBody).jsonObject
                        errObj["msg"]?.jsonPrimitive?.contentOrNull
                            ?: errObj["message"]?.jsonPrimitive?.contentOrNull
                            ?: errObj["error_description"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull() ?: "HTTP ${resp.code}"
                    error("Sign-up failed: $msg")
                }
                val obj = json.parseToJsonElement(respBody).jsonObject
                val userObj = obj["user"]?.jsonObject
                SupabaseAuthSession(
                    userId = userObj?.get("id")?.jsonPrimitive?.content ?: "",
                    email = email.trim(),
                    accessToken = obj["access_token"]?.jsonPrimitive?.content ?: ""
                )
            }
            currentSession = session
            session
        }
    }

    suspend fun fetchDirectoryProfiles(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "Supabase URL not configured" }
            val req = Request.Builder()
                .url("${activeSupabaseUrl.trimEnd('/')}/rest/v1/profiles?select=*&order=gamification_points.desc&limit=200")
                .addHeader("apikey", activeAnonKey)
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

    suspend fun requestAiWritingFeedback(
        chapterNo: Int,
        prompt: String,
        content: String,
        minWords: Int,
        maxWords: Int,
        language: String = "bn"
    ): Result<AiWritingFeedback> = withContext(Dispatchers.IO) {
        runCatching {
            val words = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val wordCount = words.size
            val userGeminiKey = vaultConfig.geminiApiKey.trim()
            val langName = if (language == "en") "English" else "Bengali"

            // 1. If Gemini API Key is added in App Settings, call Gemini 2.5 Flash directly!
            if (userGeminiKey.isNotBlank()) {
                val sysText = """You are SPARC Study Circle's Academic Writing Coach.
Do NOT rewrite the student's paper. Give constructive feedback in $langName strictly as JSON with keys:
- grammar_feedback (string[])
- academic_tone_score (number 0-100)
- tone_feedback (string)
- structure_feedback (string[])
Chapter $chapterNo Task: $prompt
Target Word Range: $minWords-$maxWords words (Current: $wordCount words)."""

                val geminiPayload = buildJsonObject {
                    put("contents", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", "$sysText\n\nStudent Draft:\n$content") })
                            })
                        })
                    })
                    put("generationConfig", buildJsonObject {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.3)
                    })
                }.toString()

                val directReq = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$userGeminiKey")
                    .post(geminiPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                http.newCall(directReq).execute().use { resp ->
                    if (!resp.isSuccessful) error("Gemini API error (${resp.code})")
                    val root = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                    val rawJson = root["candidates"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "{}"
                    val parsed = json.parseToJsonElement(rawJson).jsonObject
                    return@runCatching AiWritingFeedback(
                        wordCount = wordCount,
                        academicToneScore = parsed["academic_tone_score"]?.jsonPrimitive?.intOrNull ?: 85,
                        toneFeedback = parsed["tone_feedback"]?.jsonPrimitive?.contentOrNull ?: "",
                        grammarSuggestions = parsed["grammar_feedback"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                        structureSuggestions = parsed["structure_feedback"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    )
                }
            }

            // 2. Otherwise call Supabase Edge Function ai-writing-assistant
            check(isConfigured) { "No Gemini key or Supabase URL configured" }
            val payload = buildJsonObject {
                put("chapter_no", chapterNo)
                put("prompt", prompt)
                put("content", content)
                put("language", language)
                put("min_words", minWords)
                put("max_words", maxWords)
            }.toString()

            val req = Request.Builder()
                .url("${activeSupabaseUrl.trimEnd('/')}/functions/v1/ai-writing-assistant")
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer ${bearerToken()}")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Edge function HTTP ${resp.code}")
                val obj: JsonObject = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                AiWritingFeedback(
                    wordCount = obj["word_count"]?.jsonPrimitive?.intOrNull ?: wordCount,
                    academicToneScore = obj["academic_tone_score"]?.jsonPrimitive?.intOrNull ?: 82,
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
