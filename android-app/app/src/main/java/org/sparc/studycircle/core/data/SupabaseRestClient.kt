package org.sparc.studycircle.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.sparc.studycircle.BuildConfig
import org.sparc.studycircle.core.model.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class SupabaseAuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String = ""
)

data class LiveWorkspaceSnapshot(
    val me: UserProfile?,
    val peers: List<UserProfile>,
    val books: List<BookItem>,
    val circles: List<StudyCircleGroup>,
    val activeCircleId: String?,
    val memberStageByUserId: Map<String, ProgressStage>,
    val myChapterProgress: Map<Int, ChapterProgressState>,
    val pokesToday: List<PokeItem>,
    val activeMuteScope: MuteScope?,
    val polls: List<ChapterPoll>,
    val activeDebateThreadId: String?,
    val debateComments: List<DebateComment>,
    val messages: List<MemberMessage>,
    val highlights: List<ReadingHighlight>,
    val bookOrders: List<PhysicalBookOrder>,
    val walletHistory: List<WalletTransactionItem>,
    val sessions: List<OnlineSessionItem>,
    val supervisionDeals: List<SupervisionDeal>
)

class SupabaseRestClient(
    private val defaultBaseUrl: String = BuildConfig.SUPABASE_URL,
    private val defaultAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    var vaultConfig: ApiKeyVaultConfig = ApiKeyVaultConfig()

    @Volatile
    var currentSession: SupabaseAuthSession? = null
        private set

    var onSessionUpdated: (SupabaseAuthSession?) -> Unit = {}

    val activeSupabaseUrl: String
        get() = vaultConfig.customSupabaseUrl.trim().ifBlank { defaultBaseUrl.trim() }

    val activeAnonKey: String
        get() = vaultConfig.customSupabaseAnonKey.trim().ifBlank { defaultAnonKey.trim() }

    val isConfigured: Boolean
        get() = activeSupabaseUrl.isNotBlank() && activeAnonKey.isNotBlank()

    val hasAuthenticatedUser: Boolean
        get() = isConfigured && !currentSession?.userId.isNullOrBlank() && !currentSession?.accessToken.isNullOrBlank()

    fun restoreSession(session: SupabaseAuthSession?) {
        currentSession = session
    }

    fun clearSession() {
        currentSession = null
        onSessionUpdated(null)
    }

    private fun bearerToken(): String =
        currentSession?.accessToken?.takeIf { it.isNotBlank() } ?: activeAnonKey

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun isUuid(value: String): Boolean =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(value.trim())

    private fun refreshAccessTokenSync(): Boolean {
        val rt = currentSession?.refreshToken?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            val payload = buildJsonObject { put("refresh_token", rt) }.toString()
            val req = Request.Builder()
                .url("${activeSupabaseUrl.trimEnd('/')}/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", activeAnonKey)
                .post(payload.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.contentOrNull ?: return false
                val newRefresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: rt
                val prev = currentSession ?: return false
                val updated = prev.copy(accessToken = newAccess, refreshToken = newRefresh)
                currentSession = updated
                onSessionUpdated(updated)
                true
            }
        }.getOrDefault(false)
    }

    private fun executeAuthed(buildReq: (token: String) -> Request): Pair<Int, String> {
        val firstReq = buildReq(bearerToken())
        http.newCall(firstReq).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (resp.code == 401 && refreshAccessTokenSync()) {
                val retryReq = buildReq(bearerToken())
                http.newCall(retryReq).execute().use { retryResp ->
                    return retryResp.code to (retryResp.body?.string() ?: "")
                }
            }
            return resp.code to raw
        }
    }

    private fun getJsonArray(pathAndQuery: String): JsonArray {
        val url = "${activeSupabaseUrl.trimEnd('/')}/rest/v1/$pathAndQuery"
        val (code, raw) = executeAuthed { token ->
            Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
        }
        if (code !in 200..299 || raw.isBlank()) return JsonArray(emptyList())
        return runCatching { json.parseToJsonElement(raw).jsonArray }.getOrElse { JsonArray(emptyList()) }
    }

    private fun postRpc(rpcName: String, bodyObj: JsonObject): JsonElement {
        val url = "${activeSupabaseUrl.trimEnd('/')}/rest/v1/rpc/$rpcName"
        val (code, raw) = executeAuthed { token ->
            Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $token")
                .post(bodyObj.toString().toRequestBody(jsonMedia))
                .build()
        }
        if (code !in 200..299) {
            val msg = runCatching {
                val o = json.parseToJsonElement(raw).jsonObject
                o["message"]?.jsonPrimitive?.contentOrNull ?: o["details"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "RPC $rpcName HTTP $code"
            error(msg)
        }
        if (raw.isBlank()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(raw) }.getOrElse { JsonObject(emptyMap()) }
    }

    private fun postOrUpsertTable(
        table: String,
        bodyObj: JsonObject,
        mergeDuplicates: Boolean = false,
        onConflict: String? = null
    ) {
        val query = if (onConflict != null) "?on_conflict=${enc(onConflict)}" else ""
        val url = "${activeSupabaseUrl.trimEnd('/')}/rest/v1/$table$query"
        val prefer = if (mergeDuplicates) "resolution=merge-duplicates,return=minimal" else "return=minimal"
        val (code, raw) = executeAuthed { token ->
            Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Prefer", prefer)
                .post(bodyObj.toString().toRequestBody(jsonMedia))
                .build()
        }
        if (code !in 200..299) {
            val msg = runCatching {
                json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "Insert $table HTTP $code"
            error(msg)
        }
    }

    private fun patchTable(tableAndFilter: String, bodyObj: JsonObject) {
        val url = "${activeSupabaseUrl.trimEnd('/')}/rest/v1/$tableAndFilter"
        val (code, raw) = executeAuthed { token ->
            Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Prefer", "return=minimal")
                .patch(bodyObj.toString().toRequestBody(jsonMedia))
                .build()
        }
        if (code !in 200..299) {
            val msg = runCatching {
                json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "Patch $tableAndFilter HTTP $code"
            error(msg)
        }
    }

    private fun deleteTable(tableAndFilter: String) {
        val url = "${activeSupabaseUrl.trimEnd('/')}/rest/v1/$tableAndFilter"
        executeAuthed { token ->
            Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
        }
    }

    // ------------------------------------------------------------------------
    // Authentication (Sign In & Auto-Confirmed Sign Up)
    // ------------------------------------------------------------------------
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
                .post(body.toRequestBody(jsonMedia))
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("ইমেইল বা পাসওয়ার্ড সঠিক নয় (${resp.code})")
                val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
                val userObj = obj["user"]?.jsonObject
                SupabaseAuthSession(
                    userId = userObj?.get("id")?.jsonPrimitive?.content ?: "",
                    email = email.trim(),
                    accessToken = obj["access_token"]?.jsonPrimitive?.content ?: "",
                    refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: ""
                ).also {
                    currentSession = it
                    onSessionUpdated(it)
                }
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
                .post(edgePayload.toRequestBody(jsonMedia))
                .build()

            http.newCall(edgeReq).execute().use { edgeResp ->
                val raw = edgeResp.body?.string() ?: "{}"
                if (edgeResp.isSuccessful) {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    val userObj = obj["user"]?.jsonObject
                    val session = SupabaseAuthSession(
                        userId = userObj?.get("id")?.jsonPrimitive?.content ?: "",
                        email = email.trim(),
                        accessToken = obj["access_token"]?.jsonPrimitive?.content ?: "",
                        refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                    currentSession = session
                    onSessionUpdated(session)
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
                .post(body.toRequestBody(jsonMedia))
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
                    accessToken = obj["access_token"]?.jsonPrimitive?.content ?: "",
                    refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            }
            currentSession = session
            onSessionUpdated(session)
            session
        }
    }

    // ------------------------------------------------------------------------
    // Full Two-Way Live Snapshot Pull (All Tables, Views & Unlocked Answer Keys)
    // ------------------------------------------------------------------------
    suspend fun fetchLiveWorkspaceSnapshot(
        fallbackBooks: List<BookItem>
    ): Result<LiveWorkspaceSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser) { "Not signed into live Supabase" }
            val myUid = currentSession!!.userId

            // Ensure current user is enrolled in default cohort if not yet enrolled
            runCatching {
                postRpc("ensure_user_in_default_cohort", buildJsonObject { put("p_user_id", myUid) })
            }

            // 1. Profiles
            val profilesArr = getJsonArray("profiles?select=*&order=gamification_points.desc&limit=250")
            val allProfiles = profilesArr.map { parseProfile(it.jsonObject) }
            val profilesById = allProfiles.associateBy { it.id }
            val meProfile = profilesById[myUid]
            val peerProfiles = allProfiles.filter { it.id != myUid }

            // 2. Books, Chapters, Questions & RLS-gated Answer Keys
            val booksArr = getJsonArray("books?select=*&order=created_at.asc")
            val chaptersArr = getJsonArray("chapters?select=*&order=chapter_no.asc")
            val questionsArr = getJsonArray("chapter_questions?select=*&order=question_no.asc")
            val answerKeysArr = getJsonArray("chapter_answer_keys?select=*")

            val answerKeysByQId = answerKeysArr.associate { el ->
                val o = el.jsonObject
                val qid = o["question_id"]?.jsonPrimitive?.content ?: ""
                qid to o
            }

            val questionsByChapterId = questionsArr.map { el ->
                val o = el.jsonObject
                val qid = o["id"]?.jsonPrimitive?.content ?: ""
                val chId = o["chapter_id"]?.jsonPrimitive?.content ?: ""
                val keyObj = answerKeysByQId[qid]
                val correctAns = keyObj?.get("correct_answer")?.let { ca ->
                    if (ca is JsonNull) null else ca.jsonPrimitive.contentOrNull
                }
                val modelPts = keyObj?.get("supervisor_model_points")?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                chId to AssessmentQuestion(
                    id = qid,
                    questionNo = o["question_no"]?.jsonPrimitive?.intOrNull ?: 1,
                    sectionCode = o["section_code"]?.jsonPrimitive?.contentOrNull ?: "C",
                    categoryLabel = o["category"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "MCQ",
                    isAutoScored = o["is_auto_scored"]?.jsonPrimitive?.booleanOrNull ?: true,
                    isGenuinenessCheck = o["is_genuineness_check"]?.jsonPrimitive?.booleanOrNull ?: false,
                    maxMarks = o["max_marks"]?.jsonPrimitive?.intOrNull ?: 1,
                    promptBn = o["prompt_bn"]?.jsonPrimitive?.contentOrNull ?: "",
                    options = o["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    lockedCorrectAnswer = correctAns,
                    explanationBn = keyObj?.get("explanation_bn")?.jsonPrimitive?.contentOrNull ?: "",
                    supervisorModelPoints = modelPts
                )
            }.groupBy({ it.first }, { it.second })

            val fallbackCh1Flash = fallbackBooks.firstOrNull()?.chapters?.firstOrNull()?.flashNotes ?: emptyList()
            val chaptersByBookId = chaptersArr.map { el ->
                val o = el.jsonObject
                val chId = o["id"]?.jsonPrimitive?.content ?: ""
                val bId = o["book_id"]?.jsonPrimitive?.content ?: ""
                val chNo = o["chapter_no"]?.jsonPrimitive?.intOrNull ?: 1
                val typeStr = o["type"]?.jsonPrimitive?.contentOrNull ?: "standard"
                val parsedSections = o["sections"]?.jsonArray?.mapIndexed { idx, sEl ->
                    val so = sEl.jsonObject
                    ChapterSection(
                        id = so["id"]?.jsonPrimitive?.contentOrNull ?: "s-${idx + 1}",
                        title = so["title"]?.jsonPrimitive?.contentOrNull ?: "Section ${idx + 1}",
                        pageRange = "",
                        paragraphPreview = emptyList()
                    )
                } ?: emptyList()
                val outcomes = o["learning_outcomes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                bId to ChapterItem(
                    id = chId,
                    bookId = bId,
                    chapterNo = chNo,
                    title = o["title"]?.jsonPrimitive?.contentOrNull ?: "Chapter $chNo",
                    type = if (typeStr == "long") ChapterType.LONG else ChapterType.STANDARD,
                    totalPages = o["total_pages"]?.jsonPrimitive?.intOrNull ?: 28,
                    defaultReadingMinutes = o["default_reading_minutes"]?.jsonPrimitive?.intOrNull ?: 120,
                    audioSummaryTitle = "বাংলা অডিও সামারি (অধ্যায় $chNo)",
                    audioDurationMinutes = ((o["audio_duration_seconds"]?.jsonPrimitive?.intOrNull ?: 360) / 60).coerceAtLeast(1),
                    flashNotes = fallbackCh1Flash,
                    sections = parsedSections,
                    learningOutcomes = outcomes,
                    questions = questionsByChapterId[chId]?.ifEmpty {
                        fallbackBooks.first().chapters.first().questions
                    } ?: fallbackBooks.first().chapters.first().questions,
                    writingTaskPrompt = o["writing_task_prompt"]?.jsonPrimitive?.contentOrNull ?: "",
                    minWords = o["writing_min_words"]?.jsonPrimitive?.intOrNull ?: 500,
                    maxWords = o["writing_max_words"]?.jsonPrimitive?.intOrNull ?: 700
                )
            }.groupBy({ it.first }, { it.second })

            val liveBooks = booksArr.mapNotNull { el ->
                val o = el.jsonObject
                val bId = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val chList = chaptersByBookId[bId]?.ifEmpty {
                    fallbackBooks.first().chapters
                } ?: fallbackBooks.first().chapters
                BookItem(
                    id = bId,
                    title = o["title"]?.jsonPrimitive?.contentOrNull ?: "Book",
                    subtitle = o["subtitle"]?.jsonPrimitive?.contentOrNull ?: "",
                    author = o["author"]?.jsonPrimitive?.contentOrNull ?: "",
                    category = o["category"]?.jsonPrimitive?.contentOrNull ?: "",
                    physicalPriceBdt = o["physical_price_bdt"]?.jsonPrimitive?.doubleOrNull ?: 450.0,
                    totalChapters = o["total_chapters"]?.jsonPrimitive?.intOrNull ?: chList.size,
                    isFirstChapterFree = o["is_first_chapter_free"]?.jsonPrimitive?.booleanOrNull ?: true,
                    minMembersRequired = o["min_members_default"]?.jsonPrimitive?.intOrNull ?: 5,
                    chapters = chList
                )
            }.ifEmpty { fallbackBooks }

            val bookTitleById = liveBooks.associate { it.id to it.title }

            // 3. Study Circles, Members & Supervisor Votes
            val circlesArr = getJsonArray("study_circles?select=*&order=created_at.asc")
            val membersArr = getJsonArray("study_circle_members?select=*&invite_status=eq.joined")
            val supVotesArr = getJsonArray("supervisor_votes?select=*")

            val memberIdsByCircle = membersArr.mapNotNull { el ->
                val o = el.jsonObject
                val cid = o["circle_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val uid = o["user_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                cid to uid
            }.groupBy({ it.first }, { it.second })

            val votesByCircle = supVotesArr.mapNotNull { el ->
                val o = el.jsonObject
                val cid = o["circle_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val voter = o["voter_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val cand = o["candidate_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                cid to (voter to cand)
            }.groupBy({ it.first }, { it.second }).mapValues { (_, list) -> list.toMap() }

            val liveCircles = circlesArr.mapNotNull { el ->
                val o = el.jsonObject
                val cid = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val bid = o["book_id"]?.jsonPrimitive?.contentOrNull ?: liveBooks.first().id
                val statusStr = o["status"]?.jsonPrimitive?.contentOrNull ?: "active"
                val assignStr = o["supervisor_assignment_type"]?.jsonPrimitive?.contentOrNull
                StudyCircleGroup(
                    id = cid,
                    name = o["name"]?.jsonPrimitive?.contentOrNull ?: "Study Circle",
                    bookId = bid,
                    bookTitle = bookTitleById[bid] ?: liveBooks.first().title,
                    minMembersRequired = o["min_members_required"]?.jsonPrimitive?.intOrNull ?: 5,
                    status = when (statusStr) {
                        "forming" -> CircleStatus.FORMING
                        "completed" -> CircleStatus.COMPLETED
                        else -> CircleStatus.ACTIVE
                    },
                    memberIds = memberIdsByCircle[cid] ?: emptyList(),
                    supervisorId = o["supervisor_id"]?.jsonPrimitive?.contentOrNull,
                    supervisorAssignmentType = when (assignStr) {
                        "admin_assigned" -> SupervisorAssignmentType.ADMIN_ASSIGNED
                        "member_voted" -> SupervisorAssignmentType.MEMBER_VOTED
                        else -> null
                    },
                    supervisorVotes = votesByCircle[cid] ?: emptyMap(),
                    currentGroupChapter = o["current_group_chapter"]?.jsonPrimitive?.intOrNull ?: 1,
                    nextSessionAnnounced = o["next_session_announced_at"] != null && o["next_session_announced_at"] !is JsonNull
                )
            }

            val myJoinedCircle = liveCircles.firstOrNull { myUid in it.memberIds } ?: liveCircles.firstOrNull()
            val activeCircleId = myJoinedCircle?.id

            // 4. Public Group Progress Stages + My Detailed Chapter Progress + Submitted Answers + Writing
            val publicProgArr = if (activeCircleId != null) {
                getJsonArray("group_chapter_progress_public?circle_id=eq.${enc(activeCircleId)}")
            } else JsonArray(emptyList())

            val stageMap = publicProgArr.mapNotNull { el ->
                val o = el.jsonObject
                val uid = o["user_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val st = parseProgressStage(o["status"]?.jsonPrimitive?.contentOrNull)
                uid to st
            }.toMap()

            val myProgArr = getJsonArray("chapter_progress?user_id=eq.${enc(myUid)}")
            val myWritingArr = getJsonArray("writing_submissions?user_id=eq.${enc(myUid)}")
            val writingByProgId = myWritingArr.associate { el ->
                val o = el.jsonObject
                (o["progress_id"]?.jsonPrimitive?.contentOrNull ?: "") to (o["content"]?.jsonPrimitive?.contentOrNull ?: "")
            }
            val mySubArr = getJsonArray("exercise_submissions?select=*")
            val answersByProgId = mySubArr.mapNotNull { el ->
                val o = el.jsonObject
                val pid = o["progress_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val qid = o["question_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val ans = o["answer_payload"]?.let { ap ->
                    if (ap is JsonPrimitive) ap.content else ap.toString()
                } ?: ""
                pid to (qid to ans)
            }.groupBy({ it.first }, { it.second }).mapValues { (_, pairs) -> pairs.toMap() }

            val myProgressByCh = myProgArr.mapNotNull { el ->
                val o = el.jsonObject
                val pid = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val cid = o["circle_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (activeCircleId != null && cid != activeCircleId) return@mapNotNull null
                val chNo = o["chapter_no"]?.jsonPrimitive?.intOrNull ?: 1
                chNo to ChapterProgressState(
                    id = pid,
                    circleId = cid,
                    userId = myUid,
                    chapterNo = chNo,
                    stage = parseProgressStage(o["status"]?.jsonPrimitive?.contentOrNull),
                    dateModifiedCount = o["date_modified_count"]?.jsonPrimitive?.intOrNull ?: 0,
                    targetDays = o["target_days"]?.jsonPrimitive?.intOrNull ?: 8,
                    dailyTargetPages = o["daily_target_pages"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 4,
                    checkedSectionIds = o["completed_sections"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
                    submittedAnswers = answersByProgId[pid] ?: emptyMap(),
                    firstAutoScore = o["first_auto_score"]?.jsonPrimitive?.intOrNull,
                    autoScore = o["auto_score"]?.jsonPrimitive?.intOrNull ?: 0,
                    reattemptUsed = o["reattempt_used"]?.jsonPrimitive?.booleanOrNull ?: false,
                    supervisorScore = o["supervisor_score"]?.jsonPrimitive?.intOrNull,
                    supervisorOverride = o["supervisor_override"]?.jsonPrimitive?.booleanOrNull ?: false,
                    selfRatings = o["self_ratings"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList(),
                    genuinenessPassed = o["genuineness_passed"]?.jsonPrimitive?.booleanOrNull ?: true,
                    redFlagIndicators = o["red_flag_indicators"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    needsSupport = o["needs_support"]?.jsonPrimitive?.booleanOrNull ?: false,
                    writingSubmitted = o["writing_task_submitted"]?.jsonPrimitive?.booleanOrNull ?: false,
                    writingContent = writingByProgId[pid] ?: ""
                )
            }.toMap()

            // 5. Pokes & Mute Settings
            val pokesArr = getJsonArray("pokes?select=*&order=created_at.desc&limit=50")
            val livePokes = pokesArr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val sId = o["sender_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val rId = o["receiver_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val modeStr = o["delivery_mode"]?.jsonPrimitive?.contentOrNull ?: "instant"
                PokeItem(
                    id = id,
                    circleId = o["circle_id"]?.jsonPrimitive?.contentOrNull ?: (activeCircleId ?: ""),
                    chapterNo = o["chapter_no"]?.jsonPrimitive?.intOrNull ?: 1,
                    senderId = sId,
                    senderName = profilesById[sId]?.name ?: "Researcher",
                    receiverId = rId,
                    receiverName = profilesById[rId]?.name ?: "Researcher",
                    presetMessage = o["preset_message"]?.jsonPrimitive?.contentOrNull ?: "",
                    quickReply = o["quick_reply"]?.jsonPrimitive?.contentOrNull,
                    deliveryMode = when (modeStr) {
                        "quiet_hours_queued" -> PokeDeliveryMode.QUIET_HOURS_QUEUED
                        "daily_summary" -> PokeDeliveryMode.DAILY_SUMMARY
                        "muted" -> PokeDeliveryMode.MUTED
                        else -> PokeDeliveryMode.INSTANT
                    },
                    dayEpoch = 1L
                )
            }

            val muteArr = if (activeCircleId != null) {
                getJsonArray("poke_mute_settings?user_id=eq.${enc(myUid)}&circle_id=eq.${enc(activeCircleId)}")
            } else JsonArray(emptyList())
            val muteScope = muteArr.firstOrNull()?.jsonObject?.get("mute_scope")?.jsonPrimitive?.contentOrNull?.let {
                when (it) {
                    "24h" -> MuteScope.HOURS_24
                    "7d" -> MuteScope.DAYS_7
                    "chapter" -> MuteScope.ENTIRE_CHAPTER
                    else -> null
                }
            }

            // 6. Polls & Debate Threads / Comments
            val pollsArr = getJsonArray("chapter_polls?select=*&order=created_at.asc")
            val pollVotesArr = getJsonArray("chapter_poll_votes?select=*")
            val votesByPoll = pollVotesArr.mapNotNull { el ->
                val o = el.jsonObject
                val pid = o["poll_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val uid = o["user_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val opt = o["option_index"]?.jsonPrimitive?.intOrNull ?: 0
                pid to (uid to opt)
            }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.toMap() }

            val livePolls = pollsArr.mapNotNull { el ->
                val o = el.jsonObject
                val pid = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ChapterPoll(
                    id = pid,
                    circleId = o["circle_id"]?.jsonPrimitive?.contentOrNull ?: (activeCircleId ?: ""),
                    chapterNo = o["chapter_no"]?.jsonPrimitive?.intOrNull ?: 1,
                    question = o["question"]?.jsonPrimitive?.contentOrNull ?: "",
                    options = o["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    votesByUser = votesByPoll[pid] ?: emptyMap()
                )
            }

            val threadsArr = getJsonArray("debate_threads?select=*&order=created_at.asc&limit=5")
            val activeThreadId = threadsArr.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            val commentsArr = getJsonArray("debate_comments?select=*&order=created_at.desc&limit=40")
            val liveDebateComments = commentsArr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val aid = o["author_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val stanceRaw = o["stance"]?.jsonPrimitive?.contentOrNull ?: "support"
                DebateComment(
                    id = id,
                    authorName = profilesById[aid]?.name ?: "Researcher",
                    stance = stanceRaw.replaceFirstChar { it.uppercase() },
                    body = o["body"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            }

            // 7. Member Messages, Highlights, Book Orders, Wallet Ledger, Sessions & Supervision Deals
            val msgArr = getJsonArray("member_messages?select=*&order=created_at.desc&limit=40")
            val liveMessages = msgArr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val sid = o["sender_id"]?.jsonPrimitive?.contentOrNull ?: ""
                MemberMessage(
                    id = id,
                    senderId = sid,
                    senderName = profilesById[sid]?.name ?: "Researcher",
                    receiverId = o["receiver_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    body = o["body"]?.jsonPrimitive?.contentOrNull ?: "",
                    timestampLabel = o["created_at"]?.jsonPrimitive?.contentOrNull?.take(10) ?: "Live"
                )
            }

            val hlArr = getJsonArray("reading_highlights?select=*&order=created_at.desc&limit=40")
            val liveHighlights = hlArr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val uid = o["user_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val visStr = o["visibility"]?.jsonPrimitive?.contentOrNull ?: "private"
                ReadingHighlight(
                    id = id,
                    userId = uid,
                    userName = profilesById[uid]?.name ?: "Researcher",
                    bookId = o["book_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    chapterNo = 1,
                    pageNo = o["page_no"]?.jsonPrimitive?.intOrNull ?: 1,
                    highlightedText = o["highlighted_text"]?.jsonPrimitive?.contentOrNull ?: "",
                    note = o["note_text"]?.jsonPrimitive?.contentOrNull ?: "",
                    isUnderline = o["style"]?.jsonPrimitive?.contentOrNull == "underline",
                    visibility = when (visStr) {
                        "group" -> HighlightVisibility.GROUP
                        "supervisor" -> HighlightVisibility.SUPERVISOR
                        else -> HighlightVisibility.PRIVATE
                    }
                )
            }

            val ordersArr = getJsonArray("physical_book_orders?select=*&order=created_at.desc&limit=25")
            val liveOrders = ordersArr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val bid = o["book_id"]?.jsonPrimitive?.contentOrNull ?: ""
                PhysicalBookOrder(
                    id = id,
                    userId = o["user_id"]?.jsonPrimitive?.contentOrNull ?: myUid,
                    bookId = bid,
                    bookTitle = bookTitleById[bid] ?: "SPARC Book",
                    orderMode = (o["order_mode"]?.jsonPrimitive?.contentOrNull ?: "purchase").replaceFirstChar { it.uppercase() },
                    address = o["delivery_address"]?.jsonPrimitive?.contentOrNull ?: "",
                    phone = o["contact_phone"]?.jsonPrimitive?.contentOrNull ?: "",
                    amountBdt = o["amount_bdt"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    status = (o["status"]?.jsonPrimitive?.contentOrNull ?: "pending").replaceFirstChar { it.uppercase() }
                )
            }

            val walletArr = getJsonArray("wallet_transactions?select=*&order=created_at.desc&limit=30")
            val liveWallet = walletArr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val txType = o["tx_type"]?.jsonPrimitive?.contentOrNull ?: "topup_bkash_pgw"
                val ref = o["manual_trx_id"]?.jsonPrimitive?.contentOrNull
                    ?: o["gateway_ref"]?.jsonPrimitive?.contentOrNull
                    ?: "SPARC-TX"
                WalletTransactionItem(
                    id = id,
                    method = when {
                        txType.contains("manual") -> PaymentMethod.MANUAL_TRX
                        txType.contains("ssl") -> PaymentMethod.SSLCOMMERZ
                        else -> PaymentMethod.BKASH_PGW
                    },
                    amountBdt = o["amount_bdt"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    status = (o["status"]?.jsonPrimitive?.contentOrNull ?: "pending").replaceFirstChar { it.uppercase() },
                    reference = ref,
                    description = o["notes"]?.jsonPrimitive?.contentOrNull ?: txType
                )
            }

            val sessionsArr = getJsonArray("online_sessions?select=*&order=scheduled_at.asc&limit=10")
            val liveSessions = sessionsArr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val pUid = o["presenter_user_id"]?.jsonPrimitive?.contentOrNull
                val slideUrl = o["slide_upload_url"]?.jsonPrimitive?.contentOrNull
                OnlineSessionItem(
                    id = id,
                    circleId = o["circle_id"]?.jsonPrimitive?.contentOrNull,
                    chapterNo = o["chapter_no"]?.jsonPrimitive?.intOrNull,
                    partNo = o["part_no"]?.jsonPrimitive?.intOrNull ?: 1,
                    isResearchClinic = o["is_research_clinic"]?.jsonPrimitive?.booleanOrNull ?: false,
                    title = o["title"]?.jsonPrimitive?.contentOrNull ?: "Live Session",
                    scheduleLabel = o["scheduled_at"]?.jsonPrimitive?.contentOrNull?.take(16)?.replace('T', ' ') ?: "Scheduled",
                    durationMinutes = o["duration_minutes"]?.jsonPrimitive?.intOrNull ?: 40,
                    presenterName = pUid?.let { profilesById[it]?.name } ?: "SPARC Presenter",
                    hoursUntilSession = 36,
                    slideUploaded = !slideUrl.isNullOrBlank(),
                    slideFileName = slideUrl?.substringAfterLast('/'),
                    videoProvider = VideoProvider.JITSI_IN_APP,
                    jitsiRoomName = o["jitsi_room_name"]?.jsonPrimitive?.contentOrNull ?: "SPARC_Live_Session",
                    externalMeetingUrl = o["external_meeting_url"]?.jsonPrimitive?.contentOrNull ?: "https://meet.google.com"
                )
            }

            val rgArr = getJsonArray("research_groups?select=*&order=created_at.asc&limit=10")
            val liveDeals = rgArr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val supId = o["supervisor_id"]?.jsonPrimitive?.contentOrNull
                SupervisionDeal(
                    id = id,
                    researchGroupName = o["name"]?.jsonPrimitive?.contentOrNull ?: "Research Team",
                    paperTopic = o["paper_topic"]?.jsonPrimitive?.contentOrNull ?: "",
                    supervisorName = supId?.let { profilesById[it]?.name } ?: "Dr. Nusrat Jahan",
                    agreedSessions = o["agreed_sessions"]?.jsonPrimitive?.intOrNull ?: 4,
                    agreedFeeBdt = o["agreed_fee_bdt"]?.jsonPrimitive?.doubleOrNull ?: 800.0,
                    isPaidViaWallet = o["payment_status"]?.jsonPrimitive?.contentOrNull == "paid_via_wallet"
                )
            }

            LiveWorkspaceSnapshot(
                me = meProfile,
                peers = peerProfiles,
                books = liveBooks,
                circles = liveCircles,
                activeCircleId = activeCircleId,
                memberStageByUserId = stageMap,
                myChapterProgress = myProgressByCh,
                pokesToday = livePokes,
                activeMuteScope = muteScope,
                polls = livePolls,
                activeDebateThreadId = activeThreadId,
                debateComments = liveDebateComments,
                messages = liveMessages,
                highlights = liveHighlights,
                bookOrders = liveOrders,
                walletHistory = liveWallet,
                sessions = liveSessions,
                supervisionDeals = liveDeals
            )
        }
    }

    private fun parseProfile(o: JsonObject): UserProfile {
        val roleStr = o["role"]?.jsonPrimitive?.contentOrNull ?: "member"
        return UserProfile(
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

    private fun parseProgressStage(raw: String?): ProgressStage = when (raw) {
        "Reading Started" -> ProgressStage.READING_STARTED
        "Reading in Progress" -> ProgressStage.READING_IN_PROGRESS
        "Reading Completed" -> ProgressStage.READING_COMPLETED
        "Exercises Submitted" -> ProgressStage.EXERCISES_SUBMITTED
        "Learning Verified" -> ProgressStage.LEARNING_VERIFIED
        else -> ProgressStage.NOT_STARTED
    }

    // ------------------------------------------------------------------------
    // Live Write Operations & Atomic Server RPC Calls
    // ------------------------------------------------------------------------
    suspend fun updateMyProfileRemote(
        name: String,
        department: String,
        interests: List<String>,
        skills: List<String>,
        referralCode: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser) { "Not authenticated" }
            val uid = currentSession!!.userId
            patchTable(
                "profiles?id=eq.${enc(uid)}",
                buildJsonObject {
                    put("name", name.trim())
                    put("department", department.trim())
                    put("research_interests", JsonArray(interests.map { JsonPrimitive(it) }))
                    put("skills", JsonArray(skills.map { JsonPrimitive(it) }))
                }
            )
            if (referralCode.isNotBlank()) {
                val res = postRpc(
                    "apply_referral_bonus",
                    buildJsonObject {
                        put("p_new_user_id", uid)
                        put("p_referral_code", referralCode.trim())
                    }
                )
                (res as? JsonPrimitive)?.booleanOrNull ?: false
            } else false
        }
    }

    suspend fun sendInviteMessageRemote(receiverId: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(receiverId))
            val uid = currentSession!!.userId
            postOrUpsertTable(
                "member_messages",
                buildJsonObject {
                    put("sender_id", uid)
                    put("receiver_id", receiverId)
                    put("body", body.trim())
                }
            )
        }
    }

    suspend fun createCircleRemote(name: String, bookId: String, minMembers: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(bookId))
            val res = postRpc(
                "create_or_join_circle_rpc",
                buildJsonObject {
                    put("p_name", name.trim())
                    put("p_book_id", bookId)
                    put("p_min_members", minMembers)
                }
            ).jsonObject
            res["circle_id"]?.jsonPrimitive?.content ?: ""
        }
    }

    suspend fun joinCircleRemote(circleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(circleId))
            postRpc("join_existing_circle_rpc", buildJsonObject { put("p_circle_id", circleId) })
            Unit
        }
    }

    suspend fun voteSupervisorRemote(circleId: String, candidateId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(circleId) && isUuid(candidateId))
            val uid = currentSession!!.userId
            postOrUpsertTable(
                table = "supervisor_votes",
                bodyObj = buildJsonObject {
                    put("circle_id", circleId)
                    put("voter_id", uid)
                    put("candidate_id", candidateId)
                },
                mergeDuplicates = true,
                onConflict = "circle_id,voter_id"
            )
        }
    }

    suspend fun sendPokeRemote(
        circleId: String,
        chapterNo: Int,
        receiverId: String,
        presetMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(circleId) && isUuid(receiverId))
            val res = postRpc(
                "send_poke",
                buildJsonObject {
                    put("p_circle_id", circleId)
                    put("p_chapter_no", chapterNo)
                    put("p_receiver_id", receiverId)
                    put("p_preset_message", presetMessage)
                }
            ).jsonObject
            res["delivery_mode"]?.jsonPrimitive?.contentOrNull ?: "instant"
        }
    }

    suspend fun replyPokeRemote(pokeId: String, quickReply: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(pokeId))
            patchTable(
                "pokes?id=eq.${enc(pokeId)}",
                buildJsonObject {
                    put("quick_reply", quickReply)
                    put("replied_at", java.time.Instant.now().toString())
                }
            )
        }
    }

    suspend fun setPokeMuteRemote(circleId: String, scope: MuteScope?, chapterNo: Int = 1): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(circleId))
            val uid = currentSession!!.userId
            if (scope == null) {
                deleteTable("poke_mute_settings?user_id=eq.${enc(uid)}&circle_id=eq.${enc(circleId)}")
            } else {
                val scopeCode = when (scope) {
                    MuteScope.HOURS_24 -> "24h"
                    MuteScope.DAYS_7 -> "7d"
                    MuteScope.ENTIRE_CHAPTER -> "chapter"
                }
                val until = when (scope) {
                    MuteScope.HOURS_24 -> java.time.Instant.now().plusSeconds(86400).toString()
                    MuteScope.DAYS_7 -> java.time.Instant.now().plusSeconds(86400 * 7).toString()
                    MuteScope.ENTIRE_CHAPTER -> null
                }
                postOrUpsertTable(
                    table = "poke_mute_settings",
                    bodyObj = buildJsonObject {
                        put("user_id", uid)
                        put("circle_id", circleId)
                        put("mute_scope", scopeCode)
                        put("chapter_no", chapterNo)
                        if (until != null) put("muted_until", until)
                    },
                    mergeDuplicates = true,
                    onConflict = "user_id,circle_id"
                )
            }
        }
    }

    suspend fun votePollRemote(pollId: String, optionIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(pollId))
            val uid = currentSession!!.userId
            postOrUpsertTable(
                table = "chapter_poll_votes",
                bodyObj = buildJsonObject {
                    put("poll_id", pollId)
                    put("user_id", uid)
                    put("option_index", optionIndex)
                },
                mergeDuplicates = true,
                onConflict = "poll_id,user_id"
            )
        }
    }

    suspend fun postDebateCommentRemote(threadId: String, stance: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(threadId))
            val uid = currentSession!!.userId
            val dbStance = when {
                stance.lowercase().contains("counter") -> "counter"
                stance.lowercase().contains("synth") -> "synthesis"
                else -> "support"
            }
            postOrUpsertTable(
                table = "debate_comments",
                bodyObj = buildJsonObject {
                    put("thread_id", threadId)
                    put("author_id", uid)
                    put("stance", dbStance)
                    put("body", body.trim())
                }
            )
        }
    }

    suspend fun saveHighlightAndLogReadingRemote(
        bookId: String,
        chapterId: String,
        pageNo: Int,
        selectedText: String,
        note: String,
        isUnderline: Boolean,
        visibility: HighlightVisibility
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(bookId) && isUuid(chapterId))
            val uid = currentSession!!.userId
            val visCode = when (visibility) {
                HighlightVisibility.GROUP -> "group"
                HighlightVisibility.SUPERVISOR -> "supervisor"
                HighlightVisibility.PRIVATE -> "private"
            }
            postOrUpsertTable(
                table = "reading_highlights",
                bodyObj = buildJsonObject {
                    put("user_id", uid)
                    put("book_id", bookId)
                    put("chapter_id", chapterId)
                    put("page_no", pageNo.coerceAtLeast(1))
                    put("highlighted_text", selectedText.trim())
                    put("note_text", note.trim())
                    put("style", if (isUnderline) "underline" else "highlight")
                    put("visibility", visCode)
                }
            )
            // Log daily reading to trigger trg_update_reading_streak on PostgreSQL
            postOrUpsertTable(
                table = "reading_logs",
                bodyObj = buildJsonObject {
                    put("user_id", uid)
                    put("book_id", bookId)
                    put("chapter_id", chapterId)
                    put("read_date", java.time.LocalDate.now().toString())
                    put("pages_read", 2)
                    put("minutes_spent", 15)
                },
                mergeDuplicates = true,
                onConflict = "user_id,chapter_id,read_date"
            )
        }
    }

    suspend fun upsertChapterStageRemote(
        circleId: String,
        chapterId: String,
        chapterNo: Int,
        statusLabel: String,
        dailyPages: Int? = null,
        targetDays: Int? = null,
        completedSections: Set<String>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(circleId) && isUuid(chapterId))
            val uid = currentSession!!.userId
            postOrUpsertTable(
                table = "chapter_progress",
                bodyObj = buildJsonObject {
                    put("circle_id", circleId)
                    put("user_id", uid)
                    put("chapter_id", chapterId)
                    put("chapter_no", chapterNo)
                    put("status", statusLabel)
                    if (dailyPages != null) put("daily_target_pages", dailyPages)
                    if (targetDays != null) put("target_days", targetDays)
                    if (completedSections != null) {
                        put("completed_sections", JsonArray(completedSections.map { JsonPrimitive(it) }))
                    }
                    if (statusLabel == "Reading Completed") {
                        put("reading_completed_at", java.time.Instant.now().toString())
                    }
                },
                mergeDuplicates = true,
                onConflict = "circle_id,user_id,chapter_no"
            )
            val arr = getJsonArray("chapter_progress?circle_id=eq.${enc(circleId)}&user_id=eq.${enc(uid)}&chapter_no=eq.$chapterNo")
            arr.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content ?: ""
        }
    }

    suspend fun submitExercisesRemote(
        progressId: String,
        answers: Map<String, String>,
        isReattempt: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(progressId))
            val payloadArray = buildJsonArray {
                answers.forEach { (qId, ans) ->
                    if (isUuid(qId)) {
                        add(buildJsonObject {
                            put("question_id", qId)
                            put("answer", ans)
                        })
                    }
                }
            }
            val res = postRpc(
                "submit_chapter_exercises",
                buildJsonObject {
                    put("p_progress_id", progressId)
                    put("p_answers", payloadArray)
                    put("p_is_reattempt", isReattempt)
                }
            ).jsonObject
            res["auto_score"]?.jsonPrimitive?.intOrNull ?: 0
        }
    }

    suspend fun updateSelfRatingsRemote(progressId: String, ratings: List<Int>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(progressId))
            patchTable(
                "chapter_progress?id=eq.${enc(progressId)}",
                buildJsonObject {
                    put("self_ratings", JsonArray(ratings.map { JsonPrimitive(it) }))
                }
            )
        }
    }

    suspend fun submitWritingTaskRemote(
        circleId: String,
        chapterNo: Int,
        content: String,
        language: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(circleId))
            val res = postRpc(
                "submit_writing_task_rpc",
                buildJsonObject {
                    put("p_circle_id", circleId)
                    put("p_chapter_no", chapterNo)
                    put("p_content", content)
                    put("p_language", language)
                }
            ).jsonObject
            res["status"]?.jsonPrimitive?.contentOrNull ?: "Exercises Submitted"
        }
    }

    suspend fun uploadSessionSlideRemote(sessionId: String, fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(sessionId))
            patchTable(
                "online_sessions?id=eq.${enc(sessionId)}",
                buildJsonObject {
                    put("slide_upload_url", "https://storage.sparc.org.bd/slides/$fileName")
                    put("slide_uploaded_at", java.time.Instant.now().toString())
                }
            )
        }
    }

    suspend fun topUpWalletRemote(
        method: PaymentMethod,
        amountBdt: Double,
        manualTrxId: String
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser)
            val methodCode = when (method) {
                PaymentMethod.MANUAL_TRX -> "manual_trx"
                PaymentMethod.BKASH_PGW -> "bkash_pgw"
                PaymentMethod.SSLCOMMERZ -> "sslcommerz"
            }
            postRpc(
                "topup_wallet_rpc",
                buildJsonObject {
                    put("p_method", methodCode)
                    put("p_amount_bdt", amountBdt)
                    if (method == PaymentMethod.MANUAL_TRX) {
                        put("p_manual_trx_id", manualTrxId.trim())
                    }
                }
            ).jsonObject
        }
    }

    suspend fun placeBookOrderRemote(
        bookId: String,
        mode: String,
        address: String,
        phone: String
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(bookId))
            postRpc(
                "place_book_order_rpc",
                buildJsonObject {
                    put("p_book_id", bookId)
                    put("p_order_mode", mode.lowercase())
                    put("p_address", address.trim())
                    put("p_phone", phone.trim())
                }
            ).jsonObject
        }
    }

    suspend fun paySupervisionRemote(groupId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasAuthenticatedUser && isUuid(groupId))
            postRpc(
                "pay_supervision_rpc",
                buildJsonObject { put("p_group_id", groupId) }
            ).jsonObject
        }
    }

    // ------------------------------------------------------------------------
    // AI Writing Coach (Direct Gemini 2.5 Flash Vault Key OR Supabase Edge Fn)
    // ------------------------------------------------------------------------
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
                    .post(geminiPayload.toRequestBody(jsonMedia))
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

            check(isConfigured) { "No Gemini key or Supabase URL configured" }
            val payload = buildJsonObject {
                put("chapter_no", chapterNo)
                put("prompt", prompt)
                put("content", content)
                put("language", language)
                put("min_words", minWords)
                put("max_words", maxWords)
            }.toString()

            val (code, raw) = executeAuthed { token ->
                Request.Builder()
                    .url("${activeSupabaseUrl.trimEnd('/')}/functions/v1/ai-writing-assistant")
                    .addHeader("apikey", activeAnonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .post(payload.toRequestBody(jsonMedia))
                    .build()
            }
            if (code !in 200..299) error("Edge function HTTP $code")
            val obj: JsonObject = json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject
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
