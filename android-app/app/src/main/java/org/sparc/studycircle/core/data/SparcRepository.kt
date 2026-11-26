package org.sparc.studycircle.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.*
import java.util.UUID

data class SparcAppState(
    val isAuthenticated: Boolean = false,
    val appLanguage: String = "bn", // "bn" or "en"
    val visualTheme: SparcVisualTheme = SparcVisualTheme.COBALT_SAAS,
    val appCustomization: AppCustomizationConfig = AppCustomizationConfig(),
    val uiCustomization: UserUiCustomization = UserUiCustomization(),
    val apiVault: ApiKeyVaultConfig = ApiKeyVaultConfig(),
    val isSupabaseConnected: Boolean = false,
    val isSyncingLive: Boolean = false,
    val activeCircleId: String? = null,
    val activeDebateThreadId: String? = null,
    val currentUser: UserProfile,
    val directoryMembers: List<UserProfile>,
    val messages: List<MemberMessage>,
    val books: List<BookItem>,
    val highlights: List<ReadingHighlight>,
    val bookOrders: List<PhysicalBookOrder>,
    val circles: List<StudyCircleGroup>,
    val pokesToday: List<PokeItem>,
    val activeMuteScope: MuteScope? = null,
    val polls: List<ChapterPoll>,
    val debateComments: List<DebateComment>,
    val memberStageByUserId: Map<String, ProgressStage>,
    val myChapterProgress: Map<Int, ChapterProgressState>,
    val circleMemberProgress: List<ChapterProgressState> = emptyList(),
    val coAuthorUserIds: Set<String> = emptySet(),
    val sessions: List<OnlineSessionItem>,
    val supervisionDeals: List<SupervisionDeal>,
    val walletHistory: List<WalletTransactionItem>,
    val lastAiFeedback: AiWritingFeedback? = null,
    val statusBannerMessage: String? = null
)

class SparcRepository(
    private val supabaseClient: SupabaseRestClient = SupabaseRestClient(),
    initialTheme: SparcVisualTheme = SparcVisualTheme.COBALT_SAAS,
    initialAuthenticated: Boolean = false,
    initialUserName: String? = null,
    initialUserDept: String? = null,
    initialLanguage: String = "bn",
    initialApiVault: ApiKeyVaultConfig = ApiKeyVaultConfig(),
    initialSession: SupabaseAuthSession? = null,
    initialAppCustomization: AppCustomizationConfig = AppCustomizationConfig(),
    initialUiCustomization: UserUiCustomization = UserUiCustomization(),
    private val onPersistTheme: (SparcVisualTheme) -> Unit = {},
    private val onPersistAuth: (Boolean, String, String) -> Unit = { _, _, _ -> },
    private val onPersistLanguage: (String) -> Unit = {},
    private val onPersistApiVault: (ApiKeyVaultConfig) -> Unit = {},
    private val onPersistSession: (SupabaseAuthSession?) -> Unit = {},
    private val onPersistAppCustomization: (AppCustomizationConfig) -> Unit = {},
    private val onPersistUiCustomization: (UserUiCustomization) -> Unit = {}
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(
        createInitialSeededState().let { seeded ->
            val updatedUser = seeded.currentUser.copy(
                id = initialSession?.userId?.takeIf { it.isNotBlank() } ?: seeded.currentUser.id,
                name = initialUserName?.takeIf { it.isNotBlank() } ?: seeded.currentUser.name,
                department = initialUserDept?.takeIf { it.isNotBlank() } ?: seeded.currentUser.department
            )
            seeded.copy(
                isAuthenticated = initialAuthenticated,
                appLanguage = initialLanguage,
                visualTheme = initialTheme,
                appCustomization = initialAppCustomization,
                uiCustomization = initialUiCustomization,
                apiVault = initialApiVault,
                isSupabaseConnected = supabaseClient.isConfigured,
                activeCircleId = seeded.circles.firstOrNull()?.id,
                currentUser = updatedUser
            )
        }
    )
    val state: StateFlow<SparcAppState> = _state.asStateFlow()

    init {
        supabaseClient.vaultConfig = initialApiVault
        supabaseClient.onSessionUpdated = { sess -> onPersistSession(sess) }
        if (initialSession != null) {
            supabaseClient.restoreSession(initialSession)
        }
        if (supabaseClient.isConfigured) {
            repoScope.launch {
                supabaseClient.fetchPublicAppCustomization().onSuccess { liveCustom ->
                    onPersistAppCustomization(liveCustom)
                    _state.update { it.copy(appCustomization = liveCustom) }
                }
            }
        }
    }

    private fun msg(bn: String, en: String): String =
        if (_state.value.appLanguage == "en") en else bn

    private fun launchRemoteSync(block: suspend () -> Unit) {
        if (!supabaseClient.hasAuthenticatedUser) return
        repoScope.launch {
            runCatching { block() }.onFailure { err ->
                val detail = err.message?.takeIf { it.isNotBlank() }
                if (detail != null) {
                    _state.update { it.copy(statusBannerMessage = detail) }
                }
            }
        }
    }

    suspend fun syncFromSupabaseNow(silent: Boolean = true) {
        if (!supabaseClient.hasAuthenticatedUser) return
        if (!silent) {
            _state.update { it.copy(isSyncingLive = true) }
        }
        val fallbackBooks = _state.value.books
        val snapRes = supabaseClient.fetchLiveWorkspaceSnapshot(fallbackBooks)
        snapRes.onSuccess { snap ->
            _state.update { s ->
                val liveMe = snap.me ?: s.currentUser
                onPersistAuth(s.isAuthenticated, liveMe.name, liveMe.department)
                snap.appCustomization?.let { onPersistAppCustomization(it) }
                val mergedPeers = if (snap.peers.isNotEmpty()) {
                    snap.peers
                } else {
                    s.directoryMembers
                }
                val chosenCircleId = s.activeCircleId?.takeIf { id -> snap.circles.any { it.id == id } }
                    ?: snap.activeCircleId
                    ?: s.circles.firstOrNull()?.id

                s.copy(
                    isSyncingLive = false,
                    isSupabaseConnected = supabaseClient.isConfigured,
                    appCustomization = snap.appCustomization ?: s.appCustomization,
                    currentUser = liveMe,
                    directoryMembers = mergedPeers,
                    books = snap.books.ifEmpty { s.books },
                    circles = snap.circles.ifEmpty { s.circles },
                    activeCircleId = chosenCircleId,
                    memberStageByUserId = snap.memberStageByUserId.ifEmpty { s.memberStageByUserId },
                    myChapterProgress = if (snap.myChapterProgress.isNotEmpty()) snap.myChapterProgress else s.myChapterProgress,
                    circleMemberProgress = snap.circleMemberProgress.ifEmpty { s.circleMemberProgress },
                    coAuthorUserIds = snap.coAuthorUserIds.ifEmpty { s.coAuthorUserIds },
                    pokesToday = if (snap.pokesToday.isNotEmpty() || snap.peers.isNotEmpty()) snap.pokesToday else s.pokesToday,
                    activeMuteScope = snap.activeMuteScope,
                    polls = snap.polls.ifEmpty { s.polls },
                    activeDebateThreadId = snap.activeDebateThreadId ?: s.activeDebateThreadId,
                    debateComments = snap.debateComments.ifEmpty { s.debateComments },
                    messages = snap.messages.ifEmpty { s.messages },
                    highlights = snap.highlights.ifEmpty { s.highlights },
                    bookOrders = snap.bookOrders,
                    walletHistory = snap.walletHistory.ifEmpty { s.walletHistory },
                    sessions = snap.sessions.ifEmpty { s.sessions },
                    supervisionDeals = snap.supervisionDeals.ifEmpty { s.supervisionDeals },
                    statusBannerMessage = if (!silent) {
                        if (s.appLanguage == "en") "✓ Synced with Server" else "✓ সার্ভারের সাথে সিঙ্ক সম্পন্ন হয়েছে"
                    } else s.statusBannerMessage
                )
            }
        }.onFailure {
            _state.update { it.copy(isSyncingLive = false) }
        }
    }

    fun selectActiveCircle(circleId: String) {
        _state.update { it.copy(activeCircleId = circleId) }
        launchRemoteSync { syncFromSupabaseNow(silent = true) }
    }

    fun updateApiVault(newVault: ApiKeyVaultConfig, bannerMsg: String = msg("API Key সংরক্ষিত হয়েছে!", "API Key saved!")) {
        supabaseClient.vaultConfig = newVault
        onPersistApiVault(newVault)
        _state.update {
            it.copy(
                apiVault = newVault,
                isSupabaseConnected = supabaseClient.isConfigured,
                statusBannerMessage = bannerMsg
            )
        }
        launchRemoteSync { syncFromSupabaseNow(silent = true) }
    }

    fun setVisualTheme(theme: SparcVisualTheme) {
        onPersistTheme(theme)
        _state.update {
            it.copy(
                visualTheme = theme,
                statusBannerMessage = if (it.appLanguage == "en") {
                    "Theme switched to: ${theme.titleBn}"
                } else {
                    "থিম পরিবর্তন করা হয়েছে: ${theme.titleBn}"
                }
            )
        }
    }

    fun setAppLanguage(lang: String) {
        onPersistLanguage(lang)
        _state.update {
            it.copy(
                appLanguage = lang,
                statusBannerMessage = if (lang == "en") "Language switched to English" else "ভাষা বাংলা করা হয়েছে"
            )
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        if (email.isBlank() || password.length < 6) {
            return Result.failure(
                IllegalArgumentException(
                    msg(
                        "সঠিক ইমেইল ও কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন।",
                        "Please enter a valid email and a password of at least 6 characters."
                    )
                )
            )
        }
        var remoteUserId: String? = null
        if (supabaseClient.isConfigured) {
            val remote = supabaseClient.signInWithEmailPassword(email, password)
            if (remote.isFailure) return Result.failure(remote.exceptionOrNull()!!)
            remoteUserId = remote.getOrNull()?.userId
        }
        val derivedName = email.substringBefore('@')
            .replace('.', ' ')
            .replaceFirstChar { it.uppercase() }
            .ifBlank { _state.value.currentUser.name }

        _state.update { s ->
            val user = s.currentUser.copy(
                id = remoteUserId ?: s.currentUser.id,
                name = derivedName
            )
            onPersistAuth(true, user.name, user.department)
            s.copy(
                isAuthenticated = true,
                currentUser = user,
                statusBannerMessage = if (s.appLanguage == "en") "Welcome back, ${user.name}!" else "স্বাগতম, ${user.name}!"
            )
        }
        if (supabaseClient.hasAuthenticatedUser) {
            syncFromSupabaseNow(silent = true)
        }
        return Result.success(Unit)
    }

    suspend fun registerResearcher(
        name: String,
        email: String,
        password: String,
        department: String,
        skills: List<String>,
        referralCode: String
    ): Result<Unit> {
        if (name.isBlank() || email.isBlank() || password.length < 6) {
            return Result.failure(
                IllegalArgumentException(
                    msg(
                        "নাম, ইমেইল ও কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন।",
                        "Please enter your name, email, and a password of at least 6 characters."
                    )
                )
            )
        }
        val cleanName = name.trim()
        val cleanDept = department.ifBlank { "Sociology" }
        var remoteUserId: String? = null

        if (supabaseClient.isConfigured) {
            val remote = supabaseClient.signUpWithEmailPassword(
                email = email,
                password = password,
                fullName = cleanName,
                department = cleanDept,
                skills = skills,
                referralCode = referralCode
            )
            if (remote.isFailure) return Result.failure(remote.exceptionOrNull()!!)
            remoteUserId = remote.getOrNull()?.userId
        }

        val refBonus = if (referralCode.trim().isNotEmpty()) 50.0 else 0.0
        val newCode = "SPARC-" + cleanName.filter { it.isLetterOrDigit() }.take(6).uppercase().ifBlank { "USER" } + "26"

        _state.update { s ->
            val newUser = s.currentUser.copy(
                id = remoteUserId ?: s.currentUser.id,
                name = cleanName,
                department = cleanDept,
                skills = skills.ifEmpty { s.currentUser.skills },
                referralCode = newCode,
                walletBalanceBdt = if (supabaseClient.isConfigured) refBonus else s.currentUser.walletBalanceBdt + refBonus
            )
            onPersistAuth(true, newUser.name, newUser.department)
            s.copy(
                isAuthenticated = true,
                currentUser = newUser,
                statusBannerMessage = when {
                    refBonus > 0 && s.appLanguage == "en" -> "Account created & +৳50 referral bonus credited!"
                    refBonus > 0 -> "অ্যাকাউন্ট তৈরি হয়েছে এবং +৳৫০ রেফারেল বোনাস যোগ হয়েছে!"
                    s.appLanguage == "en" -> "Welcome $cleanName! Your researcher profile is ready."
                    else -> "স্বাগতম $cleanName! আপনার গবেষক প্রোফাইল প্রস্তুত।"
                }
            )
        }
        if (supabaseClient.hasAuthenticatedUser) {
            syncFromSupabaseNow(silent = true)
        }
        return Result.success(Unit)
    }

    fun loginInstantDemo() {
        supabaseClient.clearSession()
        val u = _state.value.currentUser
        onPersistAuth(true, u.name, u.department)
        _state.update {
            it.copy(
                isAuthenticated = true,
                statusBannerMessage = if (it.appLanguage == "en") "Entered Demo Researcher Mode." else "ডেমো গবেষক মোডে প্রবেশ করেছেন।"
            )
        }
    }

    fun logout() {
        supabaseClient.clearSession()
        onPersistAuth(false, "", "")
        _state.update { it.copy(isAuthenticated = false, statusBannerMessage = null) }
    }

    fun dismissBanner() {
        _state.update { it.copy(statusBannerMessage = null) }
    }

    // Pack 1 & Pack 5: Save Organization Brand/Payment Config & Personal UI Preferences
    fun saveAppCustomization(config: AppCustomizationConfig) {
        onPersistAppCustomization(config)
        _state.update { s ->
            s.copy(
                appCustomization = config,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "✓ Brand, notice & payment customization saved!"
                } else {
                    "✓ অ্যাপ ব্র্যান্ডিং, নোটিশ ও পেমেন্ট কনফিগারেশন সংরক্ষিত হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.saveAppCustomizationRemote(config).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun updateUiCustomization(
        newUi: UserUiCustomization,
        bannerBn: String? = null,
        bannerEn: String? = null
    ) {
        onPersistUiCustomization(newUi)
        _state.update { s ->
            val msgText = when {
                s.appLanguage == "en" && bannerEn != null -> bannerEn
                s.appLanguage != "en" && bannerBn != null -> bannerBn
                else -> s.statusBannerMessage
            }
            s.copy(uiCustomization = newUi, statusBannerMessage = msgText)
        }
    }

    // Section 2.1 & Pack 2: Update Onboarding Profile, Bio, Institution, Goal, Interests, Skills & Referral
    fun updateProfileAndReferral(
        name: String,
        department: String,
        interests: List<String>,
        skills: List<String>,
        appliedReferralCode: String,
        institution: String? = null,
        bio: String? = null,
        dailyReadingGoalMinutes: Int? = null,
        avatarUrl: String? = null
    ) {
        _state.update { s ->
            val validReferral = appliedReferralCode.isNotBlank() &&
                !appliedReferralCode.equals(s.currentUser.referralCode, ignoreCase = true) &&
                s.directoryMembers.any { it.referralCode.equals(appliedReferralCode.trim(), ignoreCase = true) }

            val bonusBdt = if (validReferral) 50.0 else 0.0
            val bonusPoints = if (validReferral) 25 else 0
            val updatedUser = s.currentUser.copy(
                name = name.ifBlank { s.currentUser.name },
                department = department.ifBlank { s.currentUser.department },
                researchInterests = interests.ifEmpty { s.currentUser.researchInterests },
                skills = skills.ifEmpty { s.currentUser.skills },
                institution = institution ?: s.currentUser.institution,
                bio = bio ?: s.currentUser.bio,
                dailyReadingGoalMinutes = dailyReadingGoalMinutes ?: s.currentUser.dailyReadingGoalMinutes,
                avatarUrl = avatarUrl ?: s.currentUser.avatarUrl,
                walletBalanceBdt = s.currentUser.walletBalanceBdt + bonusBdt,
                gamificationPoints = s.currentUser.gamificationPoints + bonusPoints
            )
            onPersistAuth(s.isAuthenticated, updatedUser.name, updatedUser.department)
            val updatedHistory = if (validReferral) {
                listOf(
                    WalletTransactionItem(
                        id = UUID.randomUUID().toString(),
                        method = PaymentMethod.MANUAL_TRX,
                        amountBdt = 50.0,
                        status = "Verified",
                        reference = "REF-${appliedReferralCode.uppercase()}",
                        description = if (s.appLanguage == "en") "Referral Bonus (Invite & Earn)" else "রেফারেল বোনাস (Invite & Earn)"
                    )
                ) + s.walletHistory
            } else s.walletHistory

            s.copy(
                currentUser = updatedUser,
                walletHistory = updatedHistory,
                statusBannerMessage = when {
                    validReferral && s.appLanguage == "en" -> "Profile updated & +৳50 referral bonus credited!"
                    validReferral -> "প্রোফাইল আপডেট হয়েছে এবং ৫০ টাকা রেফারেল বোনাস যোগ হয়েছে!"
                    s.appLanguage == "en" -> "Profile, bio & preferences saved successfully."
                    else -> "প্রোফাইল, বায়ো ও দক্ষতা সফলভাবে সংরক্ষিত হয়েছে।"
                }
            )
        }
        launchRemoteSync {
            val u = _state.value.currentUser
            supabaseClient.updateMyProfileRemote(
                name = u.name,
                department = u.department,
                interests = u.researchInterests,
                skills = u.skills,
                referralCode = appliedReferralCode,
                avatarUrl = u.avatarUrl,
                institution = u.institution,
                bio = u.bio,
                dailyReadingGoalMinutes = u.dailyReadingGoalMinutes
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    suspend fun uploadProfileAvatar(
        imageBytes: ByteArray,
        fileName: String,
        localCacheFallbackPath: String = ""
    ): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException(msg("ছবি পাওয়া যায়নি।", "Empty image selected.")))
        }
        return runCatching {
            val finalUrl = if (supabaseClient.hasAuthenticatedUser) {
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "avatar.jpg" }
                val mime = when {
                    safeName.endsWith(".png", true) -> "image/png"
                    safeName.endsWith(".webp", true) -> "image/webp"
                    else -> "image/jpeg"
                }
                val ts = System.currentTimeMillis()
                val cloudUrl = supabaseClient.uploadBytesToSupabaseStorage(
                    bucket = "sparc-avatars",
                    objectPath = "profiles/${_state.value.currentUser.id}_${ts}_$safeName",
                    bytes = imageBytes,
                    contentType = mime
                ).getOrThrow()
                val u = _state.value.currentUser
                supabaseClient.updateMyProfileRemote(
                    name = u.name,
                    department = u.department,
                    interests = u.researchInterests,
                    skills = u.skills,
                    referralCode = "",
                    avatarUrl = cloudUrl,
                    institution = u.institution,
                    bio = u.bio,
                    dailyReadingGoalMinutes = u.dailyReadingGoalMinutes
                ).getOrThrow()
                cloudUrl
            } else {
                localCacheFallbackPath
            }

            _state.update { s ->
                s.copy(
                    currentUser = s.currentUser.copy(avatarUrl = finalUrl),
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "✓ Profile photo updated!"
                    } else {
                        "✓ প্রোফাইল ছবি সফলভাবে আপডেট হয়েছে!"
                    }
                )
            }
            finalUrl
        }
    }

    // Section 2.2: Send Quick Invite / In-App Message (+Gamification Points)
    fun sendInviteMessage(receiver: UserProfile, body: String) {
        if (body.isBlank()) return
        _state.update { s ->
            val msgItem = MemberMessage(
                id = UUID.randomUUID().toString(),
                senderId = s.currentUser.id,
                senderName = s.currentUser.name,
                receiverId = receiver.id,
                body = body.trim(),
                timestampLabel = if (s.appLanguage == "en") "Just now" else "এইমাত্র"
            )
            s.copy(
                messages = listOf(msgItem) + s.messages,
                currentUser = s.currentUser.copy(
                    gamificationPoints = s.currentUser.gamificationPoints + 5
                ),
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Invitation sent to ${receiver.name} (+5 pts)!"
                } else {
                    "${receiver.name}-কে আমন্ত্রণ বার্তা পাঠানো হয়েছে (+৫ পয়েন্ট)!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.sendInviteMessageRemote(receiver.id, body).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    // Section 2.3: Wallet Top-Up (Uses Dynamic Customized Min Top-Up)
    fun topUpWallet(amountBdt: Double, method: PaymentMethod, manualTrxId: String = "") {
        val minTopup = _state.value.appCustomization.minTopupBdt
        if (amountBdt < minTopup) {
            _state.update {
                it.copy(
                    statusBannerMessage = if (it.appLanguage == "en") {
                        "Minimum top-up is ৳${minTopup.toInt()}."
                    } else {
                        "ন্যূনতম ৳${minTopup.toInt()} টাকা ক্রেডিট লোড করতে হবে।"
                    }
                )
            }
            return
        }
        if (method == PaymentMethod.MANUAL_TRX && manualTrxId.trim().length < 6) {
            _state.update {
                it.copy(
                    statusBannerMessage = if (it.appLanguage == "en") "Please enter a valid bKash/Nagad TrxID." else "সঠিক bKash/Nagad TrxID লিখুন।"
                )
            }
            return
        }
        _state.update { s ->
            val isInstantGateway = method != PaymentMethod.MANUAL_TRX
            val ref = if (isInstantGateway) {
                "PGW-${System.currentTimeMillis().toString().takeLast(6)}"
            } else {
                manualTrxId.trim().uppercase()
            }
            val tx = WalletTransactionItem(
                id = UUID.randomUUID().toString(),
                method = method,
                amountBdt = amountBdt,
                status = if (isInstantGateway) "Verified" else "Pending",
                reference = ref,
                description = "${method.label(s.appLanguage)} Top-Up"
            )
            s.copy(
                currentUser = if (isInstantGateway) {
                    s.currentUser.copy(walletBalanceBdt = s.currentUser.walletBalanceBdt + amountBdt)
                } else s.currentUser,
                walletHistory = listOf(tx) + s.walletHistory,
                statusBannerMessage = when {
                    isInstantGateway && s.appLanguage == "en" -> "৳${amountBdt.toInt()} added to your wallet!"
                    isInstantGateway -> "৳${amountBdt.toInt()} ওয়ালেটে যুক্ত হয়েছে!"
                    s.appLanguage == "en" -> "Manual TrxID ($ref) submitted; pending admin verification."
                    else -> "ম্যানুয়াল TrxID ($ref) জমা হয়েছে; অ্যাডমিন যাচাই করলে ব্যালেন্স যোগ হবে।"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.topUpWalletRemote(method, amountBdt, manualTrxId).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    // Section 2.4 & 3.2: Log Reading + Highlight + Streak Update
    fun addHighlightAndLogReading(
        bookId: String,
        chapterNo: Int,
        pageNo: Int,
        selectedText: String,
        note: String,
        isUnderline: Boolean,
        visibility: HighlightVisibility
    ) {
        if (selectedText.isBlank()) return
        val chapterId = _state.value.books.find { it.id == bookId }
            ?.chapters?.find { it.chapterNo == chapterNo }?.id
            ?: _state.value.books.first().chapters.first().id

        _state.update { s ->
            val hl = ReadingHighlight(
                id = UUID.randomUUID().toString(),
                userId = s.currentUser.id,
                userName = s.currentUser.name,
                bookId = bookId,
                chapterNo = chapterNo,
                pageNo = pageNo,
                highlightedText = selectedText.trim(),
                note = note.trim(),
                isUnderline = isUnderline,
                visibility = visibility
            )
            val (nextStreak, bonus) = SparcDomainEngine.calculateNextStreakAndBonus(
                currentStreakDays = s.currentUser.readingStreakDays,
                readYesterday = true,
                alreadyReadToday = false
            )
            s.copy(
                highlights = listOf(hl) + s.highlights,
                currentUser = s.currentUser.copy(
                    readingStreakDays = nextStreak,
                    gamificationPoints = s.currentUser.gamificationPoints + bonus
                ),
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Highlight saved (${visibility.labelEn}) & 🔥 Reading Streak: $nextStreak days!"
                } else {
                    "হাইলাইট সংরক্ষিত (${visibility.labelBn}) এবং 🔥 রিডিং স্ট্রিক $nextStreak দিন!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.saveHighlightAndLogReadingRemote(
                bookId = bookId,
                chapterId = chapterId,
                pageNo = pageNo,
                selectedText = selectedText,
                note = note,
                isUnderline = isUnderline,
                visibility = visibility
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    // Section 3.5: Physical Book Order (Restricted to Admin, Teacher/Supervisor, SPARC Rep)
    fun placePhysicalBookOrder(book: BookItem, mode: String, address: String, phone: String) {
        if (address.isBlank() || phone.isBlank()) {
            _state.update {
                it.copy(
                    statusBannerMessage = if (it.appLanguage == "en") "Please provide your delivery address and phone number." else "ঠিকানা ও ফোন নম্বর প্রদান করুন।"
                )
            }
            return
        }
        val cost = if (mode == "Purchase") book.physicalPriceBdt else 0.0
        if (cost > 0 && _state.value.currentUser.walletBalanceBdt < cost) {
            _state.update { s ->
                s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "Insufficient wallet balance (requires ৳${cost.toInt()})."
                    } else {
                        "ওয়ালেটে পর্যাপ্ত ক্রেডিট নেই (প্রয়োজন ৳${cost.toInt()})।"
                    }
                )
            }
            return
        }
        _state.update { s ->
            val order = PhysicalBookOrder(
                id = UUID.randomUUID().toString(),
                userId = s.currentUser.id,
                bookId = book.id,
                bookTitle = book.title,
                orderMode = mode,
                address = address.trim(),
                phone = phone.trim(),
                amountBdt = cost,
                status = "Pending"
            )
            s.copy(
                currentUser = s.currentUser.copy(
                    walletBalanceBdt = s.currentUser.walletBalanceBdt - cost
                ),
                bookOrders = listOf(order) + s.bookOrders,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Hardcopy order placed securely (visible only to SPARC Admin/Rep)."
                } else {
                    "হার্ডকপি অর্ডারের অনুরোধ সফলভাবে প্লেস হয়েছে (গোপনীয়তা সুরক্ষিত)।"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.placeBookOrderRemote(book.id, mode, address, phone).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    // Section 4.1 - 4.2 & Pack 4: Create / Customize / Join Study Circle & Vote Supervisor
    fun createNewStudyCircle(
        name: String,
        book: BookItem,
        minMembers: Int = 5,
        maxMembers: Int = 25,
        isPrivate: Boolean = false,
        inviteCode: String? = null,
        scheduleLabel: String = "শুক্রবার রাত ৮:৩০",
        meetingUrl: String = "https://meet.jit.si/SPARC_Circle_Live",
        rules: String = "নিয়মিত অধ্যায় পড়া ও সহপাঠীদের প্রতি শ্রদ্ধাশীল থেকে আলোচনায় অংশ নেওয়া।"
    ) {
        if (name.isBlank()) return
        val localId = UUID.randomUUID().toString()
        val resolvedCode = if (isPrivate) {
            inviteCode?.trim()?.uppercase()?.ifBlank { null }
                ?: ("SPARC-" + UUID.randomUUID().toString().take(5).uppercase())
        } else null

        _state.update { s ->
            val newCircle = StudyCircleGroup(
                id = localId,
                name = name.trim(),
                bookId = book.id,
                bookTitle = book.title,
                minMembersRequired = minMembers.coerceIn(2, 30),
                maxMembers = maxMembers.coerceAtLeast(minMembers.coerceAtLeast(2)),
                status = CircleStatus.FORMING,
                memberIds = listOf(s.currentUser.id),
                initiatorId = s.currentUser.id,
                circleLeadId = s.currentUser.id,
                isPrivate = isPrivate,
                inviteCode = resolvedCode,
                sessionScheduleLabel = scheduleLabel.ifBlank { "শুক্রবার রাত ৮:৩০" },
                defaultMeetingUrl = meetingUrl.ifBlank { "https://meet.jit.si/SPARC_Circle_Live" },
                circleRules = rules.ifBlank { "নিয়মিত অধ্যায় পড়া ও সহপাঠীদের প্রতি শ্রদ্ধাশীল থেকে আলোচনায় অংশ নেওয়া।" }
            )
            val updatedBadges = if ("Circle Initiator" in s.currentUser.earnedBadges) {
                s.currentUser.earnedBadges
            } else {
                s.currentUser.earnedBadges + "Circle Initiator"
            }
            s.copy(
                circles = s.circles + newCircle,
                activeCircleId = localId,
                currentUser = s.currentUser.copy(
                    gamificationPoints = s.currentUser.gamificationPoints + 50,
                    earnedBadges = updatedBadges
                ),
                statusBannerMessage = if (s.appLanguage == "en") {
                    "🚀 Created Study Circle: ${name.trim()} (+50 Host Points!)" + (if (resolvedCode != null) " • Code: $resolvedCode" else "")
                } else {
                    "🚀 নতুন স্টাডি সার্কেল তৈরি হয়েছে: ${name.trim()} (+৫০ উদ্যোক্তা পয়েন্ট!)" + (if (resolvedCode != null) " • কোড: $resolvedCode" else "")
                }
            )
        }
        launchRemoteSync {
            val remoteId = supabaseClient.createCircleRemote(
                name = name,
                bookId = book.id,
                minMembers = minMembers,
                maxMembers = maxMembers,
                isPrivate = isPrivate,
                inviteCode = resolvedCode,
                scheduleLabel = scheduleLabel,
                meetingUrl = meetingUrl,
                rules = rules
            ).getOrThrow()
            if (remoteId.isNotBlank()) {
                _state.update { it.copy(activeCircleId = remoteId) }
            }
            syncFromSupabaseNow(silent = true)
        }
    }

    fun updateStudyCircleCustomization(
        circleId: String,
        name: String,
        minMembers: Int,
        maxMembers: Int,
        isPrivate: Boolean,
        inviteCode: String?,
        scheduleLabel: String,
        meetingUrl: String,
        rules: String,
        currentGroupChapter: Int
    ) {
        val resolvedCode = if (isPrivate) {
            inviteCode?.trim()?.uppercase()?.ifBlank { null }
                ?: ("SPARC-" + UUID.randomUUID().toString().take(5).uppercase())
        } else null

        var finalMin = minMembers.coerceIn(2, 30)
        _state.update { s ->
            val updated = s.circles.map { c ->
                if (c.id != circleId) c else {
                    // Only Admin (SPARC Team) can modify minimum required members; preserve existing otherwise
                    val allowedMin = if (s.currentUser.role.isAdminLike) finalMin else c.minMembersRequired
                    finalMin = allowedMin
                    c.copy(
                        name = name.trim().ifBlank { c.name },
                        minMembersRequired = allowedMin,
                        maxMembers = maxMembers.coerceAtLeast(allowedMin.coerceAtLeast(2)),
                        isPrivate = isPrivate,
                        inviteCode = resolvedCode,
                        sessionScheduleLabel = scheduleLabel.trim().ifBlank { c.sessionScheduleLabel },
                        defaultMeetingUrl = meetingUrl.trim().ifBlank { c.defaultMeetingUrl },
                        circleRules = rules.trim().ifBlank { c.circleRules },
                        currentGroupChapter = currentGroupChapter.coerceAtLeast(1)
                    )
                }
            }
            s.copy(
                circles = updated,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "✓ Study circle rules & schedule updated!"
                } else {
                    "✓ স্টাডি সার্কেলের রুলস, শিডিউল ও প্রাইভেসি আপডেট হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.updateCircleCustomizationRemote(
                circleId = circleId,
                name = name,
                minMembers = finalMin,
                maxMembers = maxMembers,
                isPrivate = isPrivate,
                inviteCode = resolvedCode,
                scheduleLabel = scheduleLabel,
                meetingUrl = meetingUrl,
                rules = rules,
                currentChapter = currentGroupChapter
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun joinStudyCircle(circleId: String, enteredInviteCode: String? = null): Boolean {
        val target = _state.value.circles.find { it.id == circleId }
        if (target != null && _state.value.currentUser.id !in target.memberIds) {
            if (target.isPrivate && !target.inviteCode.isNullOrBlank() &&
                !target.inviteCode.equals(enteredInviteCode?.trim(), ignoreCase = true)
            ) {
                _state.update { s ->
                    s.copy(
                        statusBannerMessage = if (s.appLanguage == "en") {
                            "Invalid Invite Code for private circle."
                        } else {
                            "প্রাইভেট সার্কেলে জয়েন করার সঠিক Invite Code দিন।"
                        }
                    )
                }
                return false
            }
            if (target.memberIds.size >= target.maxMembers) {
                _state.update { s ->
                    s.copy(
                        statusBannerMessage = if (s.appLanguage == "en") {
                            "This circle has reached its maximum member limit."
                        } else {
                            "এই সার্কেলের সর্বোচ্চ সদস্য সীমা পূর্ণ হয়ে গেছে।"
                        }
                    )
                }
                return false
            }
        }
        addMemberToCircle(circleId, _state.value.currentUser.id)
        _state.update { it.copy(activeCircleId = circleId) }
        launchRemoteSync {
            supabaseClient.joinCircleRemote(circleId, enteredInviteCode).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
        return true
    }

    fun addMemberToCircle(circleId: String, userId: String) {
        _state.update { s ->
            val updatedCircles = s.circles.map { c ->
                if (c.id != circleId || userId in c.memberIds) c else {
                    val grown = c.copy(memberIds = c.memberIds + userId)
                    grown.copy(status = SparcDomainEngine.evaluateCircleActivation(grown))
                }
            }
            s.copy(
                circles = updatedCircles,
                statusBannerMessage = if (s.appLanguage == "en") "Joined the study circle!" else "স্টাডি সার্কেলে যুক্ত হয়েছেন!"
            )
        }
    }

    fun voteForSupervisor(circleId: String, candidateId: String) {
        voteForCircleLead(circleId, candidateId)
    }

    fun voteForCircleLead(circleId: String, candidateId: String) {
        _state.update { s ->
            val updatedCircles = s.circles.map { c ->
                if (c.id != circleId) c else {
                    val votes = c.circleLeadVotes + (s.currentUser.id to candidateId)
                    val withVotes = c.copy(circleLeadVotes = votes)
                    val winner = SparcDomainEngine.resolveElectedCircleLead(withVotes)
                    withVotes.copy(circleLeadId = winner)
                }
            }
            s.copy(
                circles = updatedCircles,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "⭐ Circle Lead vote recorded!"
                } else {
                    "⭐ সার্কেল লিড নির্বাচনের ভোট সংরক্ষিত হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.voteCircleLeadRemote(circleId, candidateId).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun assignSessionPresenter(sessionId: String, presenter: UserProfile) {
        _state.update { s ->
            s.copy(
                sessions = s.sessions.map { sess ->
                    if (sess.id == sessionId) {
                        sess.copy(presenterUserId = presenter.id, presenterName = presenter.name)
                    } else sess
                },
                statusBannerMessage = if (s.appLanguage == "en") {
                    "🎤 ${presenter.name} assigned as Chapter Presenter!"
                } else {
                    "🎤 ${presenter.name}-কে অধ্যায়ের উপস্থাপক (Presenter) নির্ধারণ করা হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.assignSessionPresenterRemote(sessionId, presenter.id).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun switchMyRolePreview(newRole: UserRole) {
        _state.update { s ->
            val lang = s.appLanguage
            s.copy(
                currentUser = s.currentUser.copy(role = newRole),
                statusBannerMessage = if (lang == "en") {
                    "${newRole.badgeEmoji} Switched active role to: ${newRole.labelEn}"
                } else {
                    "${newRole.badgeEmoji} সক্রিয় রোল পরিবর্তন করা হয়েছে: ${newRole.labelBn}"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.switchMyRolePreviewRemote(newRole).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun mentorEvaluateAndSupport(
        circleId: String,
        targetUserId: String,
        chapter: ChapterItem,
        openScores: Map<String, Int>? = null,
        peerBuddyId: String? = null,
        extendDays: Int = 0,
        manualUnlock: Boolean? = null,
        approveWriting: Boolean? = null
    ) {
        val computedSupScore = openScores?.values?.sum()
        _state.update { s ->
            if (!s.currentUser.role.isMentorOrAdmin &&
                s.circles.find { it.id == circleId }?.supervisorId != s.currentUser.id
            ) {
                return@update s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "Only Mentor or Admin can grade open questions and assign Peer Buddies."
                    } else {
                        "শুধুমাত্র মেন্টর বা অ্যাডমিন নম্বর প্রদান ও Peer Buddy যুক্ত করতে পারেন।"
                    }
                )
            }

            fun updateProg(p: ChapterProgressState): ChapterProgressState {
                val mergedOpen = if (openScores != null) p.openQuestionScores + openScores else p.openQuestionScores
                val nextSupScore = computedSupScore ?: p.supervisorScore ?: 0
                val nextBase = p.copy(
                    supervisorScore = nextSupScore,
                    openQuestionScores = mergedOpen,
                    peerBuddyId = peerBuddyId ?: p.peerBuddyId,
                    expectedCompletionDaysFromToday = p.expectedCompletionDaysFromToday + extendDays,
                    supervisorOverride = manualUnlock ?: p.supervisorOverride,
                    writingSubmitted = if (approveWriting == true) true else p.writingSubmitted,
                    writingSupervisorApproved = approveWriting ?: p.writingSupervisorApproved
                )
                return SparcDomainEngine.evaluateProgressVerification(nextBase, chapter.type)
            }

            val existingPeerList = s.circleMemberProgress
            val foundTarget = existingPeerList.find { it.userId == targetUserId && it.chapterNo == chapter.chapterNo }
            val updatedTarget = updateProg(
                foundTarget ?: ChapterProgressState(
                    id = UUID.randomUUID().toString(),
                    circleId = circleId,
                    userId = targetUserId,
                    chapterNo = chapter.chapterNo,
                    stage = s.memberStageByUserId[targetUserId] ?: ProgressStage.EXERCISES_SUBMITTED,
                    autoScore = 20
                )
            )
            val nextCircleList = existingPeerList
                .filterNot { it.userId == targetUserId && it.chapterNo == chapter.chapterNo } + updatedTarget

            val nextMyMap = if (targetUserId == s.currentUser.id) {
                s.myChapterProgress + (chapter.chapterNo to updatedTarget)
            } else s.myChapterProgress

            s.copy(
                circleMemberProgress = nextCircleList,
                myChapterProgress = nextMyMap,
                memberStageByUserId = s.memberStageByUserId + (targetUserId to updatedTarget.stage),
                statusBannerMessage = if (s.appLanguage == "en") {
                    "🎓 Mentor evaluation & Peer Buddy support saved!"
                } else {
                    "🎓 মেন্টর মূল্যায়ন ও সহপাঠী সাপোর্ট সফলভাবে সংরক্ষিত হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.mentorEvaluateAndSupportRemote(
                circleId = circleId,
                targetUserId = targetUserId,
                chapterNo = chapter.chapterNo,
                supervisorScore = computedSupScore,
                openScores = openScores,
                peerBuddyId = peerBuddyId,
                extendDays = extendDays,
                supervisorOverride = manualUnlock,
                approveWriting = approveWriting
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun adminAssignUserRole(targetUserId: String, newRole: UserRole) {
        _state.update { s ->
            if (!s.currentUser.role.isAdminLike) {
                return@update s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") "Admin permission required." else "শুধুমাত্র অ্যাডমিন রোল পরিবর্তন করতে পারেন।"
                )
            }
            val updatedMe = if (targetUserId == s.currentUser.id) s.currentUser.copy(role = newRole) else s.currentUser
            val updatedDir = s.directoryMembers.map { if (it.id == targetUserId) it.copy(role = newRole) else it }
            s.copy(
                currentUser = updatedMe,
                directoryMembers = updatedDir,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "🛡️ Updated member role to ${newRole.labelEn}!"
                } else {
                    "🛡️ সদস্যের রোল '${newRole.labelBn}' হিসেবে আপডেট করা হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.adminManageRolesAndOrdersRemote(targetUserId = targetUserId, newRole = newRole).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun adminAssignCircleMentorAndMinMembers(circleId: String, mentorId: String?, minMembers: Int?) {
        _state.update { s ->
            if (!s.currentUser.role.isAdminLike) return@update s
            val updatedCircles = s.circles.map { c ->
                if (c.id != circleId) c else {
                    val grown = c.copy(
                        supervisorId = mentorId ?: c.supervisorId,
                        supervisorAssignmentType = if (mentorId != null) SupervisorAssignmentType.ADMIN_ASSIGNED else c.supervisorAssignmentType,
                        minMembersRequired = minMembers?.coerceIn(2, 30) ?: c.minMembersRequired
                    )
                    grown.copy(status = SparcDomainEngine.evaluateCircleActivation(grown))
                }
            }
            s.copy(
                circles = updatedCircles,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "🛡️ Circle Mentor & minimum member threshold updated by Admin!"
                } else {
                    "🛡️ সার্কেলের মেন্টর ও সর্বনিম্ন সদস্য সংখ্যা অ্যাডমিন কর্তৃক আপডেট হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.adminManageRolesAndOrdersRemote(
                circleId = circleId,
                mentorId = mentorId,
                minMembers = minMembers
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun adminUpdateBookOrderStatus(orderId: String, newStatus: String) {
        _state.update { s ->
            if (!s.currentUser.role.isAdminLike) return@update s
            s.copy(
                bookOrders = s.bookOrders.map {
                    if (it.id == orderId) it.copy(status = newStatus.replaceFirstChar { ch -> ch.uppercase() }) else it
                },
                statusBannerMessage = if (s.appLanguage == "en") {
                    "📦 Physical book order marked as $newStatus!"
                } else {
                    "📦 ফিজিক্যাল বইয়ের অর্ডার স্ট্যাটাস '$newStatus' করা হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.adminManageRolesAndOrdersRemote(
                bookOrderId = orderId,
                bookOrderStatus = newStatus
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun adminVerifyWalletTx(txId: String, newStatus: String) {
        _state.update { s ->
            if (!s.currentUser.role.isAdminLike) return@update s
            s.copy(
                walletHistory = s.walletHistory.map {
                    if (it.id == txId) it.copy(status = newStatus.replaceFirstChar { ch -> ch.uppercase() }) else it
                },
                statusBannerMessage = if (s.appLanguage == "en") {
                    "💳 Wallet transaction marked as $newStatus!"
                } else {
                    "💳 ওয়ালেট ট্রানজেকশন '$newStatus' করা হয়েছে!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.adminManageRolesAndOrdersRemote(
                walletTxId = txId,
                walletTxStatus = newStatus
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }


    // Section 4.3: Rate-Limited Poke, Mute & 1-Tap Quick Reply + Poll & Debate
    fun sendPokeToMember(circleId: String, receiver: UserProfile, presetMessage: String, currentDhakaHour: Int = 15) {
        var allowed = false
        _state.update { s ->
            val receiverStage = s.memberStageByUserId[receiver.id] ?: ProgressStage.READING_IN_PROGRESS
            val validation = SparcDomainEngine.validatePoke(
                senderId = s.currentUser.id,
                receiverId = receiver.id,
                receiverStage = receiverStage,
                presetMessage = presetMessage,
                existingPokesToday = s.pokesToday,
                currentHour24Dhaka = currentDhakaHour,
                isReceiverMuted = false
            )
            when (validation) {
                is SparcDomainEngine.PokeValidationResult.Blocked ->
                    s.copy(statusBannerMessage = validation.reasonBn)
                is SparcDomainEngine.PokeValidationResult.Allowed -> {
                    allowed = true
                    val poke = PokeItem(
                        id = UUID.randomUUID().toString(),
                        circleId = circleId,
                        chapterNo = 1,
                        senderId = s.currentUser.id,
                        senderName = s.currentUser.name,
                        receiverId = receiver.id,
                        receiverName = receiver.name,
                        presetMessage = presetMessage,
                        deliveryMode = validation.deliveryMode,
                        dayEpoch = 1L
                    )
                    s.copy(
                        pokesToday = listOf(poke) + s.pokesToday,
                        currentUser = s.currentUser.copy(
                            gamificationPoints = s.currentUser.gamificationPoints + 2
                        ),
                        statusBannerMessage = if (s.appLanguage == "en") {
                            "Motivation sent to ${receiver.name} (${validation.deliveryMode.name})!"
                        } else {
                            "${receiver.name}-কে উৎসাহ বার্তা পাঠানো হয়েছে (${validation.deliveryMode.name})!"
                        }
                    )
                }
            }
        }
        if (allowed) {
            launchRemoteSync {
                supabaseClient.sendPokeRemote(circleId, 1, receiver.id, presetMessage).getOrThrow()
                syncFromSupabaseNow(silent = true)
            }
        }
    }

    fun replyToPoke(pokeId: String, quickReply: String) {
        if (quickReply !in SparcDomainEngine.QUICK_POKE_REPLIES) return
        _state.update { s ->
            s.copy(
                pokesToday = s.pokesToday.map { if (it.id == pokeId) it.copy(quickReply = quickReply) else it },
                statusBannerMessage = if (s.appLanguage == "en") "Quick reply sent!" else "এক ট্যাপে রিপ্লাই পাঠানো হয়েছে: \"$quickReply\""
            )
        }
        launchRemoteSync {
            supabaseClient.replyPokeRemote(pokeId, quickReply).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun setPokeMuteScope(scope: MuteScope?) {
        val circleId = _state.value.activeCircleId ?: _state.value.circles.firstOrNull()?.id ?: ""
        _state.update {
            it.copy(
                activeMuteScope = scope,
                statusBannerMessage = when {
                    scope == null && it.appLanguage == "en" -> "Poke notifications unmuted."
                    scope == null -> "Poke নোটিফিকেশন আনমিউট করা হয়েছে।"
                    it.appLanguage == "en" -> "Poke muted for ${scope.labelEn}."
                    else -> "Poke ${scope.labelBn}-এর জন্য মিউট করা হয়েছে।"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.setPokeMuteRemote(circleId, scope, 1).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun voteInPoll(pollId: String, optionIndex: Int) {
        _state.update { s ->
            s.copy(
                polls = s.polls.map { p ->
                    if (p.id == pollId) p.copy(votesByUser = p.votesByUser + (s.currentUser.id to optionIndex)) else p
                }
            )
        }
        launchRemoteSync {
            supabaseClient.votePollRemote(pollId, optionIndex).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun postDebateComment(stance: String, body: String) {
        if (body.isBlank()) return
        val threadId = _state.value.activeDebateThreadId ?: "66666666-6666-6666-6666-666666666601"
        _state.update { s ->
            val comment = DebateComment(
                id = UUID.randomUUID().toString(),
                authorName = s.currentUser.name,
                stance = stance,
                body = body.trim()
            )
            s.copy(debateComments = listOf(comment) + s.debateComments)
        }
        launchRemoteSync {
            supabaseClient.postDebateCommentRemote(threadId, stance, body).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    // Section 5: 6-Stage Chapter State Machine Actions
    fun startChapterReading(chapter: ChapterItem, targetDays: Int) {
        val circleId = _state.value.activeCircleId ?: _state.value.circles.first().id
        val dailyPages = SparcDomainEngine.calculateDailyPageTarget(chapter.totalPages, targetDays)
        _state.update { s ->
            val existing = s.myChapterProgress[chapter.chapterNo] ?: ChapterProgressState(
                id = UUID.randomUUID().toString(),
                circleId = circleId,
                userId = s.currentUser.id,
                chapterNo = chapter.chapterNo
            )
            val updated = existing.copy(
                stage = ProgressStage.READING_STARTED,
                targetDays = targetDays,
                dailyTargetPages = dailyPages,
                expectedCompletionDaysFromToday = targetDays
            )
            s.copy(
                myChapterProgress = s.myChapterProgress + (chapter.chapterNo to updated),
                memberStageByUserId = s.memberStageByUserId + (s.currentUser.id to updated.stage),
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Reading started! $dailyPages pages/day finishes in $targetDays days."
                } else {
                    "পড়া শুরু হয়েছে! প্রতিদিন $dailyPages পৃষ্ঠা পড়লে $targetDays দিনে শেষ হবে।"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.upsertChapterStageRemote(
                circleId = circleId,
                chapterId = chapter.id,
                chapterNo = chapter.chapterNo,
                statusLabel = "Reading Started",
                dailyPages = dailyPages,
                targetDays = targetDays
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun modifyExpectedDateOnce(chapterNo: Int, newDaysFromToday: Int) {
        _state.update { s ->
            val existing = s.myChapterProgress[chapterNo] ?: return@update s
            if (!SparcDomainEngine.canModifyExpectedDate(existing)) {
                return@update s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "Expected completion date can only be modified once."
                    } else {
                        "সম্ভাব্য শেষের তারিখ সর্বোচ্চ একবারই পরিবর্তন করা যায়।"
                    }
                )
            }
            val updated = existing.copy(
                expectedCompletionDaysFromToday = newDaysFromToday,
                dateModifiedCount = existing.dateModifiedCount + 1
            )
            s.copy(
                myChapterProgress = s.myChapterProgress + (chapterNo to updated),
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Expected completion date updated (1/1 change used)."
                } else {
                    "সম্ভাব্য শেষের তারিখ আপডেট করা হয়েছে (১/১ পরিবর্তন ব্যবহৃত)।"
                }
            )
        }
    }

    fun toggleChapterSection(chapter: ChapterItem, sectionId: String) {
        val circleId = _state.value.activeCircleId ?: _state.value.circles.first().id
        var nextSections: Set<String> = emptySet()
        _state.update { s ->
            val existing = s.myChapterProgress[chapter.chapterNo] ?: return@update s
            val nextSet = if (sectionId in existing.checkedSectionIds) {
                existing.checkedSectionIds - sectionId
            } else {
                existing.checkedSectionIds + sectionId
            }
            nextSections = nextSet
            val nextStage = if (existing.stage.step < ProgressStage.READING_IN_PROGRESS.step && nextSet.isNotEmpty()) {
                ProgressStage.READING_IN_PROGRESS
            } else existing.stage

            val updated = existing.copy(checkedSectionIds = nextSet, stage = nextStage)
            s.copy(
                myChapterProgress = s.myChapterProgress + (chapter.chapterNo to updated),
                memberStageByUserId = s.memberStageByUserId + (s.currentUser.id to updated.stage)
            )
        }
        launchRemoteSync {
            supabaseClient.upsertChapterStageRemote(
                circleId = circleId,
                chapterId = chapter.id,
                chapterNo = chapter.chapterNo,
                statusLabel = "Reading in Progress",
                completedSections = nextSections
            ).getOrThrow()
        }
    }

    fun declareReadingCompleted(chapter: ChapterItem) {
        val circleId = _state.value.activeCircleId ?: _state.value.circles.first().id
        _state.update { s ->
            val existing = s.myChapterProgress[chapter.chapterNo] ?: return@update s
            val updated = existing.copy(stage = ProgressStage.READING_COMPLETED)
            s.copy(
                myChapterProgress = s.myChapterProgress + (chapter.chapterNo to updated),
                memberStageByUserId = s.memberStageByUserId + (s.currentUser.id to updated.stage),
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Congratulations! Chapter exercises are now unlocked."
                } else {
                    "অভিনন্দন! অনুশীলনী (Exercises) এখন আনলক হয়েছে।"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.upsertChapterStageRemote(
                circleId = circleId,
                chapterId = chapter.id,
                chapterNo = chapter.chapterNo,
                statusLabel = "Reading Completed"
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun submitExercises(
        chapter: ChapterItem,
        answers: Map<String, String>,
        isReattempt: Boolean = false
    ) {
        val circleId = _state.value.activeCircleId ?: _state.value.circles.first().id
        var proceedRemote = true
        _state.update { s ->
            val existing = s.myChapterProgress[chapter.chapterNo] ?: ChapterProgressState(
                id = UUID.randomUUID().toString(),
                circleId = circleId,
                userId = s.currentUser.id,
                chapterNo = chapter.chapterNo
            )
            if (isReattempt && !SparcDomainEngine.canUseAutoScoreReattempt(existing)) {
                proceedRemote = false
                return@update s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "A single auto-scored reattempt is allowed 48 hours after first submission."
                    } else {
                        "প্রথম জমার ৪৮ ঘণ্টা পর শুধুমাত্র ১ বার অটো-স্কোরড অংশে রি-অ্যাটেম্পট করা যাবে।"
                    }
                )
            }
            val autoOutcome = SparcDomainEngine.gradeAutoQuestions(chapter.questions, answers)
            val scaledAuto = (autoOutcome.autoScore * 11).coerceAtMost(25)
            val baseUpdated = existing.copy(
                stage = ProgressStage.EXERCISES_SUBMITTED,
                submittedAnswers = answers,
                firstAutoScore = existing.firstAutoScore ?: scaledAuto,
                autoScore = scaledAuto,
                reattemptUsed = if (isReattempt) true else existing.reattemptUsed,
                supervisorScore = existing.supervisorScore ?: 18,
                genuinenessPassed = autoOutcome.genuinenessPassed
            )
            val evaluated = SparcDomainEngine.evaluateProgressVerification(baseUpdated, chapter.type)
            s.copy(
                myChapterProgress = s.myChapterProgress + (chapter.chapterNo to evaluated),
                memberStageByUserId = s.memberStageByUserId + (s.currentUser.id to evaluated.stage),
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Exercises submitted! Answer Key & Self-Rating are now unlocked."
                } else {
                    "অনুশীলনী লক হয়েছে! উত্তরপত্র (Answer Key) ও আত্মমূল্যায়ন (Self-Rating) আনলক হয়েছে।"
                }
            )
        }
        if (proceedRemote) {
            launchRemoteSync {
                val progId = supabaseClient.upsertChapterStageRemote(
                    circleId = circleId,
                    chapterId = chapter.id,
                    chapterNo = chapter.chapterNo,
                    statusLabel = "Reading Completed"
                ).getOrThrow()
                if (progId.isNotBlank()) {
                    supabaseClient.submitExercisesRemote(progId, answers, isReattempt).getOrThrow()
                    // Re-sync pulls the newly RLS-unlocked chapter_answer_keys from PostgreSQL!
                    syncFromSupabaseNow(silent = true)
                }
            }
        }
    }

    fun submitSelfRatings(chapter: ChapterItem, ratings: List<Int>) {
        var progId = ""
        _state.update { s ->
            val existing = s.myChapterProgress[chapter.chapterNo] ?: return@update s
            progId = existing.id
            val evaluated = SparcDomainEngine.evaluateProgressVerification(
                existing.copy(selfRatings = ratings),
                chapter.type
            )
            s.copy(
                myChapterProgress = s.myChapterProgress + (chapter.chapterNo to evaluated),
                memberStageByUserId = s.memberStageByUserId + (s.currentUser.id to evaluated.stage),
                statusBannerMessage = when {
                    evaluated.stage == ProgressStage.LEARNING_VERIFIED && s.appLanguage == "en" ->
                        "🎉 Learning Verified! Next chapter & shareable card unlocked!"
                    evaluated.stage == ProgressStage.LEARNING_VERIFIED ->
                        "🎉 Learning Verified! পরবর্তী অধ্যায় ও শেয়ারেবল সোশ্যাল কার্ড আনলক হয়েছে!"
                    s.appLanguage == "en" ->
                        "Self-rating saved. Complete the writing task to reach Learning Verified."
                    else ->
                        "আত্মমূল্যায়ন সংরক্ষিত হয়েছে। Learning Verified হতে রাইটিং টাস্ক সম্পন্ন করুন।"
                }
            )
        }
        launchRemoteSync {
            if (progId.isNotBlank()) {
                supabaseClient.updateSelfRatingsRemote(progId, ratings).getOrThrow()
                syncFromSupabaseNow(silent = true)
            }
        }
    }

    fun uploadSessionSlide(sessionId: String, fileName: String) {
        var ok = false
        _state.update { s ->
            val target = s.sessions.find { it.id == sessionId } ?: return@update s
            if (target.hoursUntilSession < 24) {
                return@update s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "Slides must be uploaded at least 24 hours before the session."
                    } else {
                        "সেশনের অন্তত ২৪ ঘণ্টা আগে স্লাইড আপলোড করতে হয়।"
                    }
                )
            }
            ok = true
            s.copy(
                sessions = s.sessions.map {
                    if (it.id == sessionId) it.copy(slideUploaded = true, slideFileName = fileName) else it
                },
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Slides ($fileName) uploaded successfully!"
                } else {
                    "স্লাইড ($fileName) সফলভাবে আপলোড হয়েছে!"
                }
            )
        }
        if (ok) {
            launchRemoteSync {
                supabaseClient.uploadSessionSlideRemote(sessionId, fileName).getOrThrow()
                syncFromSupabaseNow(silent = true)
            }
        }
    }

    // Section 6.1: Chapter Writing Task + AI Writing Assistant
    suspend fun analyzeWritingWithAi(chapter: ChapterItem, draft: String) {
        val lang = _state.value.appLanguage
        val feedback = supabaseClient.requestAiWritingFeedback(
            chapterNo = chapter.chapterNo,
            prompt = chapter.writingTaskPrompt,
            content = draft,
            minWords = chapter.minWords,
            maxWords = chapter.maxWords,
            language = lang
        ).getOrElse {
            SparcDomainEngine.generateLocalAiWritingFeedback(draft, chapter.minWords, chapter.maxWords)
        }
        _state.update { it.copy(lastAiFeedback = feedback) }
    }

    fun submitChapterWritingTask(chapter: ChapterItem, content: String) {
        val wordCount = content.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        if (wordCount < 20) {
            _state.update {
                it.copy(
                    statusBannerMessage = if (it.appLanguage == "en") {
                        "Please write a detailed academic paragraph (at least 20 words)."
                    } else {
                        "অনুগ্রহ করে বিস্তারিত একাডেমিক অনুচ্ছেদ লিখুন।"
                    }
                )
            }
            return
        }
        val circleId = _state.value.activeCircleId ?: _state.value.circles.first().id
        val lang = _state.value.appLanguage
        _state.update { s ->
            val existing = s.myChapterProgress[chapter.chapterNo] ?: ChapterProgressState(
                id = UUID.randomUUID().toString(),
                circleId = circleId,
                userId = s.currentUser.id,
                chapterNo = chapter.chapterNo
            )
            val updatedBase = existing.copy(
                writingSubmitted = true,
                writingContent = content.trim()
            )
            val evaluated = SparcDomainEngine.evaluateProgressVerification(updatedBase, chapter.type)
            val newBadges = if ("Research Proposal Writer" !in s.currentUser.earnedBadges) {
                s.currentUser.earnedBadges + "Research Proposal Writer"
            } else s.currentUser.earnedBadges

            s.copy(
                myChapterProgress = s.myChapterProgress + (chapter.chapterNo to evaluated),
                memberStageByUserId = s.memberStageByUserId + (s.currentUser.id to evaluated.stage),
                currentUser = s.currentUser.copy(
                    gamificationPoints = s.currentUser.gamificationPoints + 30,
                    earnedBadges = newBadges
                ),
                statusBannerMessage = when {
                    evaluated.stage == ProgressStage.LEARNING_VERIFIED && s.appLanguage == "en" ->
                        "Writing task submitted & chapter promoted to Learning Verified!"
                    evaluated.stage == ProgressStage.LEARNING_VERIFIED ->
                        "রাইটিং টাস্ক জমা হয়েছে এবং অধ্যায়টি 'Learning Verified' হিসেবে উন্নীত হয়েছে!"
                    s.appLanguage == "en" ->
                        "Writing task saved (+30 points)!"
                    else ->
                        "রাইটিং টাস্ক সংরক্ষিত হয়েছে (+৩০ পয়েন্ট)!"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.submitWritingTaskRemote(circleId, chapter.chapterNo, content, lang).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    // Section 6.3: Paid Supervision Wallet Settlement
    fun paySupervisionViaWallet(dealId: String) {
        var proceed = false
        _state.update { s ->
            val deal = s.supervisionDeals.find { it.id == dealId } ?: return@update s
            if (deal.isPaidViaWallet) return@update s
            if (s.currentUser.walletBalanceBdt < deal.agreedFeeBdt) {
                return@update s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "Insufficient wallet balance (requires ৳${deal.agreedFeeBdt.toInt()}). Please top up."
                    } else {
                        "ওয়ালেটে পর্যাপ্ত ব্যালেন্স নেই (প্রয়োজন ৳${deal.agreedFeeBdt.toInt()})। ওয়ালেট টপ-আপ করুন।"
                    }
                )
            }
            proceed = true
            val tx = WalletTransactionItem(
                id = UUID.randomUUID().toString(),
                method = PaymentMethod.SSLCOMMERZ,
                amountBdt = -deal.agreedFeeBdt,
                status = "Verified",
                reference = "SUP-${deal.id.take(6).uppercase()}",
                description = if (s.appLanguage == "en") "Paid Supervision (${deal.agreedSessions} sessions)" else "পেইড সুপারভিশন ফি (${deal.agreedSessions} সেশন)"
            )
            s.copy(
                currentUser = s.currentUser.copy(
                    walletBalanceBdt = s.currentUser.walletBalanceBdt - deal.agreedFeeBdt
                ),
                supervisionDeals = s.supervisionDeals.map {
                    if (it.id == dealId) it.copy(isPaidViaWallet = true) else it
                },
                walletHistory = listOf(tx) + s.walletHistory,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Supervision agreement settled via wallet!"
                } else {
                    "পেইড সুপারভিশন চুক্তি চূড়ান্ত এবং ওয়ালেট থেকে পরিশোধ সম্পন্ন!"
                }
            )
        }
        if (proceed) {
            launchRemoteSync {
                supabaseClient.paySupervisionRemote(dealId).getOrThrow()
                syncFromSupabaseNow(silent = true)
            }
        }
    }

    // Section 3.1 - 3.4: Upload Real Book PDF & Bangla MP3 Audio Summary to Supabase Storage
    suspend fun uploadBookPdfAndAudio(
        targetBook: BookItem?,
        isNewBook: Boolean,
        newTitle: String,
        newAuthor: String,
        newPriceBdt: Double,
        chapterNo: Int = 1,
        pdfBytes: ByteArray?,
        pdfFileName: String?,
        audioBytes: ByteArray?,
        audioFileName: String?,
        sourceAttribution: String = "",
        permissionDeclared: Boolean = true,
        exerciseTemplateRaw: String? = null
    ): Result<Unit> {
        if (pdfBytes == null && audioBytes == null && exerciseTemplateRaw.isNullOrBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    msg("অনুগ্রহ করে অন্তত একটি PDF, অডিও বা অনুশীলনী টেমপ্লেট সিলেক্ট করুন।", "Please select at least a PDF, audio file, or exercise template.")
                )
            )
        }
        if (isNewBook && newTitle.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    msg("নতুন বই বা প্রবন্ধের শিরোনাম লিখুন।", "Please enter the book or paper title.")
                )
            )
        }
        if (isNewBook && !permissionDeclared) {
            return Result.failure(
                IllegalArgumentException(
                    msg("কপিরাইট / ওপেন-অ্যাক্সেস পাঠচক্র সম্মতিতে টিক দিন।", "Please confirm copyright / open-access educational permission.")
                )
            )
        }

        val parsedTemplate = if (!exerciseTemplateRaw.isNullOrBlank()) {
            val parseRes = org.sparc.studycircle.core.domain.ExerciseTemplateParser.parse(exerciseTemplateRaw)
            if (parseRes.isFailure) return Result.failure(parseRes.exceptionOrNull()!!)
            parseRes.getOrNull()
        } else null

        if (!supabaseClient.hasAuthenticatedUser && (pdfBytes != null || audioBytes != null)) {
            return Result.failure(
                IllegalStateException(
                    msg(
                        "সার্ভারে ফাইল আপলোড করতে আগে নিজের ইমেইল দিয়ে লগইন/সাইন-আপ করুন।",
                        "Please sign in with your email account to upload files to the Server."
                    )
                )
            )
        }

        return runCatching {
            val ts = System.currentTimeMillis()
            var uploadedPdfUrl: String? = null
            var uploadedAudioUrl: String? = null

            if (pdfBytes != null && pdfBytes.isNotEmpty()) {
                val safePdf = (pdfFileName ?: "book.pdf")
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
                uploadedPdfUrl = supabaseClient.uploadBytesToSupabaseStorage(
                    bucket = "sparc-books",
                    objectPath = "pdfs/${ts}_$safePdf",
                    bytes = pdfBytes,
                    contentType = "application/pdf"
                ).getOrThrow()
            }

            if (audioBytes != null && audioBytes.isNotEmpty()) {
                val rawName = audioFileName ?: "summary.mp3"
                val safeAudio = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val mime = when {
                    safeAudio.endsWith(".wav", true) -> "audio/wav"
                    safeAudio.endsWith(".ogg", true) -> "audio/ogg"
                    safeAudio.endsWith(".m4a", true) -> "audio/mp4"
                    else -> "audio/mpeg"
                }
                uploadedAudioUrl = supabaseClient.uploadBytesToSupabaseStorage(
                    bucket = "sparc-audio",
                    objectPath = "summaries/${ts}_$safeAudio",
                    bytes = audioBytes,
                    contentType = mime
                ).getOrThrow()
            }

            val resolvedBookId = if (isNewBook) {
                supabaseClient.publishNewBookWithMediaRemote(
                    title = newTitle.trim(),
                    author = newAuthor.trim().ifBlank { "Guest Researcher" },
                    priceBdt = newPriceBdt,
                    pdfUrl = uploadedPdfUrl ?: "",
                    audioUrl = uploadedAudioUrl,
                    sourceAttribution = sourceAttribution,
                    permissionDeclared = permissionDeclared
                ).getOrThrow()
            } else {
                val book = targetBook ?: _state.value.books.first()
                if (uploadedPdfUrl != null || uploadedAudioUrl != null) {
                    supabaseClient.attachMediaToBookRemote(
                        bookId = book.id,
                        chapterNo = chapterNo.coerceAtLeast(1),
                        pdfUrl = uploadedPdfUrl,
                        audioUrl = uploadedAudioUrl
                    ).getOrThrow()
                }
                book.id
            }

            if (parsedTemplate != null && resolvedBookId.isNotBlank()) {
                val adjusted = parsedTemplate.copy(chapterNo = chapterNo.coerceAtLeast(parsedTemplate.chapterNo))
                supabaseClient.importExerciseTemplateRemote(
                    bookId = resolvedBookId,
                    parsed = adjusted,
                    replaceQuestions = isNewBook
                ).getOrThrow()
            }

            syncFromSupabaseNow(silent = true)
            _state.update { s ->
                s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "✓ PDF/Audio & Exercise Template uploaded to Server & synced!"
                    } else {
                        "✓ PDF / অডিও এবং অনুশীলনী টেমপ্লেট সার্ভারে আপলোড ও সিঙ্ক হয়েছে!"
                    }
                )
            }
        }
    }

    fun importExerciseTemplate(
        bookId: String,
        overrideChapterNo: Int?,
        rawTemplate: String,
        replaceExistingQuestions: Boolean = false
    ): Result<Int> {
        val parsedRes = org.sparc.studycircle.core.domain.ExerciseTemplateParser.parse(rawTemplate)
        if (parsedRes.isFailure) return Result.failure(parsedRes.exceptionOrNull()!!)
        val baseParsed = parsedRes.getOrThrow()
        val parsed = if (overrideChapterNo != null && overrideChapterNo >= 1) {
            baseParsed.copy(chapterNo = overrideChapterNo)
        } else baseParsed

        _state.update { s ->
            val updatedBooks = s.books.map { b ->
                if (b.id != bookId) b else {
                    val existing = b.chapters.find { it.chapterNo == parsed.chapterNo }
                    val startNo = if (replaceExistingQuestions || existing == null) 0
                    else (existing.questions.maxOfOrNull { it.questionNo } ?: 0)
                    val mappedQs = parsed.questions.mapIndexed { idx, q ->
                        AssessmentQuestion(
                            id = UUID.randomUUID().toString(),
                            questionNo = startNo + idx + 1,
                            sectionCode = q.sectionCode.uppercase().take(1).ifBlank { if (q.isAutoScored) "C" else "H" },
                            categoryLabel = if (q.isAutoScored) "MCQ" else "SCENARIO",
                            isAutoScored = q.isAutoScored,
                            maxMarks = if (q.isAutoScored) 1 else 3,
                            promptBn = q.promptBn,
                            options = q.options,
                            lockedCorrectAnswer = if (q.isAutoScored) q.correctAnswer else null,
                            explanationBn = q.explanationBn,
                            supervisorModelPoints = q.modelPoints
                        )
                    }
                    val finalChapter = if (existing != null) {
                        existing.copy(
                            title = parsed.chapterTitle.ifBlank { existing.title },
                            type = parsed.chapterType,
                            totalPages = parsed.totalPages,
                            flashNotes = parsed.flashNotes.ifEmpty { existing.flashNotes },
                            learningOutcomes = parsed.learningOutcomes.ifEmpty { existing.learningOutcomes },
                            writingTaskPrompt = parsed.writingPrompt.ifBlank { existing.writingTaskPrompt },
                            minWords = parsed.minWords,
                            maxWords = parsed.maxWords,
                            questions = if (replaceExistingQuestions) mappedQs.ifEmpty { existing.questions }
                            else existing.questions + mappedQs
                        )
                    } else {
                        ChapterItem(
                            id = UUID.randomUUID().toString(),
                            bookId = b.id,
                            chapterNo = parsed.chapterNo,
                            title = parsed.chapterTitle.ifBlank { "অধ্যায় ${parsed.chapterNo}" },
                            type = parsed.chapterType,
                            totalPages = parsed.totalPages,
                            defaultReadingMinutes = parsed.totalPages * 4,
                            audioSummaryTitle = "বাংলা অডিও সামারি (অধ্যায় ${parsed.chapterNo})",
                            audioDurationMinutes = 6,
                            flashNotes = parsed.flashNotes.ifEmpty { listOf("পাঠের মূল নোট") },
                            sections = listOf(ChapterSection("s-${parsed.chapterNo}-1", "মূল আলোচনা", "1-${parsed.totalPages}", emptyList())),
                            learningOutcomes = parsed.learningOutcomes.ifEmpty { listOf("পাঠের মূল ধারণা বিশ্লেষণ") },
                            questions = mappedQs.ifEmpty { b.chapters.first().questions },
                            writingTaskPrompt = parsed.writingPrompt.ifBlank { "এই পাঠের আলোকে বিশ্লেষণমূলক অনুচ্ছেদ লিখুন।" },
                            minWords = parsed.minWords,
                            maxWords = parsed.maxWords
                        )
                    }
                    val mergedChapters = (b.chapters.filter { it.chapterNo != parsed.chapterNo } + finalChapter)
                        .sortedBy { it.chapterNo }
                    b.copy(
                        totalChapters = maxOf(b.totalChapters, parsed.chapterNo),
                        chapters = mergedChapters
                    )
                }
            }
            s.copy(
                books = updatedBooks,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "✓ Template imported (${parsed.questions.size} exercises & ${parsed.flashNotes.size} notes)!"
                } else {
                    "✓ টেমপ্লেট থেকে ${parsed.questions.size}টি অনুশীলনী ও ${parsed.flashNotes.size}টি ফ্ল্যাশ নোট যুক্ত হয়েছে!"
                }
            )
        }

        launchRemoteSync {
            supabaseClient.importExerciseTemplateRemote(
                bookId = bookId,
                parsed = parsed,
                replaceQuestions = replaceExistingQuestions
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
        return Result.success(parsed.questions.size)
    }

    fun submitWritingForHumanEvaluation(chapter: ChapterItem, content: String) {
        submitChapterWritingTask(chapter, content)
        if (content.trim().split(Regex("\\s+")).count { it.isNotBlank() } >= 20) {
            _state.update { s ->
                s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "✓ Submitted to Human Supervisor/Mentor queue for authentic evaluation!"
                    } else {
                        "✓ আপনার লেখাটি মানব সুপারভাইজার/মেন্টরের কাছে Evaluate করার জন্য জমা হয়েছে!"
                    }
                )
            }
        }
    }

    suspend fun downloadCloudPdf(pdfUrl: String, targetFile: java.io.File): Result<java.io.File> =
        supabaseClient.downloadCloudFileToCache(pdfUrl, targetFile)

    // Pack 3: In-App Chapter, Flash Notes, Writing Prompt & Quiz Customizer
    fun saveChapterCurriculum(
        bookId: String,
        chapterNo: Int,
        title: String,
        chapterType: ChapterType,
        totalPages: Int,
        flashNotes: List<String>,
        learningOutcomes: List<String>,
        writingPrompt: String,
        minWords: Int,
        maxWords: Int
    ) {
        val safeNo = chapterNo.coerceAtLeast(1)
        val safePages = totalPages.coerceAtLeast(1)
        val safeMin = minWords.coerceAtLeast(50)
        val safeMax = maxWords.coerceAtLeast(safeMin + 50)
        val cleanFlash = flashNotes.map { it.trim() }.filter { it.isNotBlank() }
        val cleanOutcomes = learningOutcomes.map { it.trim() }.filter { it.isNotBlank() }

        _state.update { s ->
            val updatedBooks = s.books.map { b ->
                if (b.id != bookId) b else {
                    val existing = b.chapters.find { it.chapterNo == safeNo }
                    val newChapter = if (existing != null) {
                        existing.copy(
                            title = title.trim().ifBlank { existing.title },
                            type = chapterType,
                            totalPages = safePages,
                            flashNotes = cleanFlash.ifEmpty { existing.flashNotes },
                            learningOutcomes = cleanOutcomes.ifEmpty { existing.learningOutcomes },
                            writingTaskPrompt = writingPrompt.trim().ifBlank { existing.writingTaskPrompt },
                            minWords = safeMin,
                            maxWords = safeMax
                        )
                    } else {
                        ChapterItem(
                            id = UUID.randomUUID().toString(),
                            bookId = b.id,
                            chapterNo = safeNo,
                            title = title.trim().ifBlank { "অধ্যায় $safeNo" },
                            type = chapterType,
                            totalPages = safePages,
                            defaultReadingMinutes = safePages * 4,
                            audioSummaryTitle = "বাংলা অডিও সামারি (অধ্যায় $safeNo)",
                            audioDurationMinutes = 6,
                            flashNotes = cleanFlash.ifEmpty { listOf("নতুন অধ্যায়ের মূল নোট") },
                            sections = listOf(ChapterSection("s-$safeNo-1", "মূল আলোচনা", "1-$safePages", emptyList())),
                            learningOutcomes = cleanOutcomes.ifEmpty { listOf("অধ্যায়ের মূল ধারণা বিশ্লেষণ") },
                            questions = b.chapters.first().questions,
                            writingTaskPrompt = writingPrompt.trim().ifBlank { "এই অধ্যায়ের আলোকে একাডেমিক অনুচ্ছেদ লিখুন।" },
                            minWords = safeMin,
                            maxWords = safeMax
                        )
                    }
                    val mergedChapters = (b.chapters.filter { it.chapterNo != safeNo } + newChapter)
                        .sortedBy { it.chapterNo }
                    b.copy(
                        totalChapters = maxOf(b.totalChapters, safeNo),
                        chapters = mergedChapters
                    )
                }
            }
            s.copy(
                books = updatedBooks,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "✓ Chapter $safeNo curriculum, flash notes & writing task saved!"
                } else {
                    "✓ অধ্যায় $safeNo-এর কারিকুলাম, ফ্ল্যাশ নোটস ও রাইটিং প্রম্পট সংরক্ষিত হয়েছে!"
                }
            )
        }

        launchRemoteSync {
            supabaseClient.upsertChapterCurriculumRemote(
                bookId = bookId,
                chapterNo = safeNo,
                title = title,
                typeCode = chapterType.name.lowercase(),
                totalPages = safePages,
                flashNotes = cleanFlash,
                learningOutcomes = cleanOutcomes,
                writingPrompt = writingPrompt,
                minWords = safeMin,
                maxWords = safeMax
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    fun addCustomChapterQuestion(
        bookId: String,
        chapterNo: Int,
        isAutoScored: Boolean,
        sectionCode: String,
        promptBn: String,
        options: List<String>,
        correctAnswer: String,
        explanationBn: String,
        modelPoints: List<String>
    ) {
        if (promptBn.isBlank()) return
        val safeNo = chapterNo.coerceAtLeast(1)
        val cleanOpts = options.map { it.trim() }.filter { it.isNotBlank() }
        val cleanPts = modelPoints.map { it.trim() }.filter { it.isNotBlank() }
        val resolvedSec = if (isAutoScored) {
            sectionCode.uppercase().take(1).takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "C"
        } else {
            sectionCode.uppercase().take(1).takeIf { it in listOf("F", "G", "H", "I", "J") } ?: "H"
        }

        _state.update { s ->
            val updatedBooks = s.books.map { b ->
                if (b.id != bookId) b else {
                    val updatedChs = b.chapters.map { ch ->
                        if (ch.chapterNo != safeNo) ch else {
                            val nextNo = (ch.questions.maxOfOrNull { it.questionNo } ?: 0) + 1
                            val q = AssessmentQuestion(
                                id = UUID.randomUUID().toString(),
                                questionNo = nextNo,
                                sectionCode = resolvedSec,
                                categoryLabel = if (isAutoScored) "MCQ" else "SCENARIO",
                                isAutoScored = isAutoScored,
                                maxMarks = if (isAutoScored) 1 else 3,
                                promptBn = promptBn.trim(),
                                options = if (isAutoScored) cleanOpts else emptyList(),
                                lockedCorrectAnswer = if (isAutoScored) correctAnswer.trim() else null,
                                explanationBn = explanationBn.trim(),
                                supervisorModelPoints = if (!isAutoScored) cleanPts else emptyList()
                            )
                            ch.copy(questions = ch.questions + q)
                        }
                    }
                    b.copy(chapters = updatedChs)
                }
            }
            s.copy(
                books = updatedBooks,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "✓ Custom quiz question [$resolvedSec] added to Chapter $safeNo!"
                } else {
                    "✓ অধ্যায় $safeNo-এ নতুন কাস্টম কুইজ প্রশ্ন [$resolvedSec] যুক্ত হয়েছে!"
                }
            )
        }

        launchRemoteSync {
            supabaseClient.upsertChapterQuestionRemote(
                bookId = bookId,
                chapterNo = safeNo,
                isAutoScored = isAutoScored,
                sectionCode = resolvedSec,
                promptBn = promptBn,
                options = cleanOpts,
                correctAnswer = correctAnswer,
                explanationBn = explanationBn,
                modelPoints = cleanPts
            ).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
    }

    companion object {
        fun createInitialSeededState(): SparcAppState {
            val me = UserProfile(
                id = "u-me",
                name = "তানভীর আহমেদ",
                department = "Sociology",
                researchInterests = listOf("Action Research", "Higher Education", "Rural Development"),
                skills = listOf("Academic Writing", "Literature Review"),
                role = UserRole.MEMBER,
                rating = 4.9,
                gamificationPoints = 245,
                readingStreakDays = 7,
                referralCode = "SPARC-TANVIR07",
                walletBalanceBdt = 1250.0,
                earnedBadges = listOf("Literature Review Pro", "7-Day Streak Scholar")
            )

            val peers = listOf(
                UserProfile(
                    id = "u-sup-1",
                    name = "ড. নুসরাত জাহান",
                    department = "Education & Research (IER)",
                    researchInterests = listOf("Action Research", "Pedagogy", "Higher Education"),
                    skills = listOf("Data Analysis", "Qualitative Coding", "Academic Writing"),
                    role = UserRole.SUPERVISOR,
                    rating = 5.0,
                    gamificationPoints = 620,
                    readingStreakDays = 15,
                    referralCode = "SPARC-NUSRAT15",
                    earnedBadges = listOf("Certified Supervisor", "Critical Thinker")
                ),
                UserProfile(
                    id = "u-2",
                    name = "ফারহান সাদিক",
                    department = "Sociology",
                    researchInterests = listOf("Action Research", "Rural Development"),
                    skills = listOf("Data Analysis", "Field Survey"),
                    rating = 4.8,
                    gamificationPoints = 310,
                    readingStreakDays = 9,
                    referralCode = "SPARC-FARHAN09",
                    earnedBadges = listOf("Critical Thinker")
                ),
                UserProfile(
                    id = "u-3",
                    name = "মালিহা তাবাসসুম",
                    department = "Anthropology",
                    researchInterests = listOf("Higher Education", "Gender Studies"),
                    skills = listOf("Field Survey", "Ethnography"),
                    rating = 4.7,
                    gamificationPoints = 195,
                    readingStreakDays = 3,
                    referralCode = "SPARC-MALIHA03"
                ),
                UserProfile(
                    id = "u-4",
                    name = "রাফিউজ্জামান খান",
                    department = "Economics",
                    researchInterests = listOf("Rural Development", "Public Policy"),
                    skills = listOf("Data Analysis", "Econometrics"),
                    rating = 4.85,
                    gamificationPoints = 275,
                    readingStreakDays = 6,
                    referralCode = "SPARC-RAFI06"
                ),
                UserProfile(
                    id = "u-5",
                    name = "সাদিয়া ইসলাম",
                    department = "Sociology",
                    researchInterests = listOf("Action Research", "Youth Participation"),
                    skills = listOf("Literature Review", "Presentation"),
                    rating = 4.6,
                    gamificationPoints = 160,
                    readingStreakDays = 2,
                    referralCode = "SPARC-SADIA02"
                )
            )

            val ch1 = ChapterItem(
                id = "ch-1",
                bookId = "book-cpar",
                chapterNo = 1,
                title = "অধ্যায় ১: CPAR-এর পরিচয় ও Practice Architectures",
                type = ChapterType.LONG,
                totalPages = 32,
                defaultReadingMinutes = 150,
                audioSummaryTitle = "বাংলা অডিও সামারি — প্র্যাকটিস আর্কিটেকচার ও CPAR (৬ মিনিট ৩০ সেকেন্ড)",
                audioDurationMinutes = 6,
                flashNotes = listOf(
                    "CPAR-এ গবেষক বাইরে থেকে দর্শক নন; অংশগ্রহণকারীরাই নিজেদের প্র্যাকটিস রূপান্তর করেন।",
                    "Practice Architectures তিনটি মাত্রায় গঠিত: Sayings (ভাষা/চিন্তা), Doings (কাজ/উপকরণ), Relatings (ক্ষমতা/সম্পর্ক)।",
                    "Communicative Space এমন একটি উন্মুক্ত পরিসর যেখানে সবাই নির্ভয়ে সমালোচনামূলক মতামত দিতে পারেন।"
                ),
                sections = listOf(
                    ChapterSection(
                        id = "s1",
                        title = "1.1 Action Research বনাম CPAR",
                        pageRange = "পৃষ্ঠা ১–৮",
                        paragraphPreview = listOf(
                            "Critical Participatory Action Research (CPAR) কেবল কৌশলগত দক্ষতা বাড়ানোর প্রক্রিয়া নয়; বরং শিক্ষা ও সামাজিক অনুশীলনের অন্যায্য, অযৌক্তিক ও অটেকসই দিকগুলো চিহ্নিত করে যৌথভাবে পরিবর্তন আনার একটি গবেষণা দর্শন।",
                            "প্রথাগত গবেষণায় অংশগ্রহণকারীদের 'Subject' হিসেবে দেখা হলেও CPAR-এ তারা খোদ সহ-গবেষক (Co-researchers)।"
                        )
                    ),
                    ChapterSection(
                        id = "s2",
                        title = "1.2 Public Spheres ও Communicative Space",
                        pageRange = "পৃষ্ঠা ৯–১৬",
                        paragraphPreview = listOf(
                            "হাবেরমাসের Communicative Action তত্ত্ব অনুসরণ করে Kemmis দেখিয়েছেন—যেকোনো অর্থবহ পরিবর্তনের শুরু হয় একটি গণতান্ত্রিক সংলাপের পরিসর (Public Sphere) তৈরির মাধ্যমে।"
                        )
                    ),
                    ChapterSection(
                        id = "s3",
                        title = "1.3 Sayings, Doings এবং Relatings",
                        pageRange = "পৃষ্ঠা ১৭–২৪",
                        paragraphPreview = listOf(
                            "যেকোনো সামাজিক বা শ্রেণিকক্ষের অনুশীলন টিকে থাকে তিনটি আন্তঃসম্পর্কিত বিন্যাসের ওপর: সাংস্কৃতিক-ডিসকার্সিভ (Sayings), বস্তুগত-অর্থনৈতিক (Doings) এবং সামাজিক-রাজনৈতিক (Relatings)।"
                        )
                    ),
                    ChapterSection(
                        id = "s4",
                        title = "1.4 Felt-Concern ও Research Site নির্বাচন",
                        pageRange = "পৃষ্ঠা ২৫–৩২",
                        paragraphPreview = listOf(
                            "প্রপোজাল লেখার প্রথম ধাপ হলো নিজের কর্মক্ষেত্র বা পরিচিত প্রতিষ্ঠান থেকে একটি বাস্তব 'Felt-concern' বেছে নেওয়া এবং কারা এতে প্রভাবিত হচ্ছেন তা সুনির্দিষ্ট করা।"
                        )
                    )
                ),
                learningOutcomes = listOf(
                    "LO1: CPAR ও সাধারণ অ্যাকশন রিসার্চের পার্থক্য বিশ্লেষণ করতে পারা",
                    "LO2: Sayings, Doings ও Relatings দিয়ে প্র্যাকটিস ব্যাখ্যা করতে পারা",
                    "LO3: নিজের সাইটের জন্য স্পষ্ট Felt-concern বিবৃতি লিখতে পারা"
                ),
                questions = listOf(
                    AssessmentQuestion(
                        id = "q1",
                        questionNo = 1,
                        sectionCode = "C",
                        categoryLabel = "MCQ (Auto-Scored)",
                        isAutoScored = true,
                        maxMarks = 1,
                        promptBn = "Kemmis et al.-এর মতে প্র্যাকটিসকে ধরে রাখে এমন তিনটি মাত্রা কোনটি?",
                        options = listOf(
                            "Sayings, Doings, and Relatings",
                            "Inputs, Outputs, and Outcomes",
                            "Sampling, Coding, and Regression"
                        ),
                        lockedCorrectAnswer = "Sayings, Doings, and Relatings",
                        explanationBn = "সাংস্কৃতিক-ডিসকার্সিভ (Sayings), বস্তুগত-অর্থনৈতিক (Doings) এবং সামাজিক-রাজনৈতিক (Relatings) একত্রে প্র্যাকটিস আর্কিটেকচার গঠন করে।"
                    ),
                    AssessmentQuestion(
                        id = "q2",
                        questionNo = 2,
                        sectionCode = "D",
                        categoryLabel = "True/False (Genuineness Check)",
                        isAutoScored = true,
                        isGenuinenessCheck = true,
                        maxMarks = 1,
                        promptBn = "বইয়ের ১ম অধ্যায় অনুযায়ী, CPAR শুধুমাত্র বহিরাগত বিশেষজ্ঞদের দ্বারা পরিচালিত হয়।",
                        options = listOf("সত্য (True)", "মিথ্যা (False)"),
                        lockedCorrectAnswer = "মিথ্যা (False)",
                        explanationBn = "CPAR-এ স্থানীয় অংশগ্রহণকারীরাই (Insiders) যৌথভাবে গবেষণা পরিচালনা করেন।"
                    ),
                    AssessmentQuestion(
                        id = "q3",
                        questionNo = 3,
                        sectionCode = "H",
                        categoryLabel = "Scenario Analysis (Supervisor Scored 0–3)",
                        isAutoScored = false,
                        maxMarks = 3,
                        promptBn = "একটি বিভাগে শিক্ষার্থীদের সেমিনারে অংশগ্রহণ কমছে। Sayings, Doings ও Relatings ব্যবহার করে কীভাবে সমস্যাটি বিশ্লেষণ করবেন?",
                        supervisorModelPoints = listOf(
                            "১ নম্বর: সেমিনার নিয়ে প্রচলিত ধারণা বা ভাষা (Sayings) চিহ্নিত করা",
                            "১ নম্বর: সময়সূচি ও কক্ষের পরিবেশ (Doings) মূল্যায়ন করা",
                            "১ নম্বর: শিক্ষক-শিক্ষার্থী সংলাপের সংস্কৃতি (Relatings) ব্যাখ্যা করা"
                        )
                    )
                ),
                writingTaskPrompt = "আপনার পরিচিত কোনো শিক্ষা প্রতিষ্ঠান বা সামাজিক ক্ষেত্র থেকে ৫০০–৭০০ শব্দে একটি 'Felt-concern and site statement' লিখুন (৭ম অধ্যায়ে এটি পূর্ণাঙ্গ CPAR প্রপোজালে যুক্ত হবে)।"
            )

            val ch2 = ch1.copy(
                id = "ch-2",
                chapterNo = 2,
                title = "অধ্যায় ২: Reconnaissance ও Communicative Space গঠন",
                type = ChapterType.STANDARD,
                totalPages = 24,
                defaultReadingMinutes = 110,
                writingTaskPrompt = "আপনার নির্বাচিত সাইটের অংশগ্রহণকারীদের নিয়ে ৫০০–৭০০ শব্দে 'Reconnaissance & Public Sphere Plan' তৈরি করুন।"
            )

            val flagshipBook = BookItem(
                id = "book-cpar",
                title = "The Action Research Planner (CPAR)",
                subtitle = "বই পড়তে পড়তে রিসার্চ প্রপোজাল লেখা শিখুন (১ম অধ্যায় ফ্রি)",
                author = "Kemmis, McTaggart & Nixon",
                category = "Action Research & Pedagogy",
                physicalPriceBdt = 450.0,
                totalChapters = 7,
                isFirstChapterFree = true,
                minMembersRequired = 5,
                chapters = listOf(ch1, ch2)
            )

            val secondBook = BookItem(
                id = "book-creswell",
                title = "Research Design: Qualitative, Quantitative & Mixed Methods",
                subtitle = "সামাজিক ও শিক্ষা গবেষণার মেথডোলজি গাইড (১ম অধ্যায় ফ্রি)",
                author = "John W. Creswell",
                category = "Research Methodology",
                physicalPriceBdt = 520.0,
                totalChapters = 10,
                isFirstChapterFree = true,
                minMembersRequired = 10,
                chapters = listOf(
                    ch2.copy(
                        id = "cres-ch1",
                        bookId = "book-creswell",
                        chapterNo = 1,
                        title = "অধ্যায় ১: গবেষণা অ্যাপ্রোচ নির্বাচন ও দার্শনিক ভিত্তি"
                    )
                )
            )

            val circle = StudyCircleGroup(
                id = "circle-cpar-01",
                name = "CPAR কোহর্ট-০১ (সমাজবিজ্ঞান ও শিক্ষা গবেষণা)",
                bookId = flagshipBook.id,
                bookTitle = flagshipBook.title,
                minMembersRequired = 5,
                status = CircleStatus.ACTIVE,
                memberIds = listOf(me.id, "u-sup-1", "u-2", "u-3", "u-4", "u-5"),
                initiatorId = me.id,
                supervisorId = "u-sup-1",
                supervisorAssignmentType = SupervisorAssignmentType.ADMIN_ASSIGNED,
                supervisorVotes = mapOf(me.id to "u-2", "u-3" to "u-2", "u-4" to "u-2"),
                circleLeadId = "u-2",
                circleLeadVotes = mapOf(me.id to "u-2", "u-3" to "u-2", "u-4" to "u-2"),
                currentGroupChapter = 1,
                daysSinceLastSession = 6
            )

            val stageMap = mapOf(
                me.id to ProgressStage.READING_IN_PROGRESS,
                "u-sup-1" to ProgressStage.LEARNING_VERIFIED,
                "u-2" to ProgressStage.EXERCISES_SUBMITTED,
                "u-3" to ProgressStage.EXERCISES_SUBMITTED,
                "u-4" to ProgressStage.LEARNING_VERIFIED,
                "u-5" to ProgressStage.READING_STARTED
            )

            val myCh1Progress = ChapterProgressState(
                id = "prog-ch1-me",
                circleId = circle.id,
                userId = me.id,
                chapterNo = 1,
                stage = ProgressStage.READING_IN_PROGRESS,
                expectedCompletionDaysFromToday = 5,
                targetDays = 8,
                dailyTargetPages = 4,
                checkedSectionIds = setOf("s1", "s2")
            )

            val peerProgressSeed = listOf(
                myCh1Progress,
                ChapterProgressState(
                    id = "prog-ch1-u2",
                    circleId = circle.id,
                    userId = "u-2",
                    chapterNo = 1,
                    stage = ProgressStage.EXERCISES_SUBMITTED,
                    autoScore = 22,
                    submittedAnswers = mapOf(
                        "q3" to "Sayings দিয়ে সেমিনার সংস্কৃতি, Doings দিয়ে সময়সূচি এবং Relatings দিয়ে শিক্ষক-শিক্ষার্থী সমতা বিশ্লেষণ করা যায়।"
                    ),
                    selfRatings = listOf(4, 4, 4),
                    writingSubmitted = true,
                    writingContent = "আমাদের সমাজবিজ্ঞান বিভাগে শিক্ষার্থীদের সেমিনারে অংশগ্রহণ বৃদ্ধির জন্য একটি Communicative Space গঠন জরুরি..."
                ),
                ChapterProgressState(
                    id = "prog-ch1-u5",
                    circleId = circle.id,
                    userId = "u-5",
                    chapterNo = 1,
                    stage = ProgressStage.READING_STARTED,
                    expectedCompletionDaysFromToday = -8,
                    autoScore = 10,
                    supervisorScore = 8,
                    genuinenessPassed = false,
                    redFlagIndicators = listOf(
                        "নির্ধারিত সময়ের চেয়ে ৭ দিনের বেশি দেরি",
                        "মূল্যায়নে ৫০%-এর কম বা রেড-ফ্ল্যাগ নম্বর",
                        "রিডিং-জেনুইননেস প্রশ্নে ভুল উত্তর"
                    ),
                    needsSupport = true,
                    peerBuddyId = "u-4"
                )
            )

            return SparcAppState(
                currentUser = me,
                directoryMembers = peers,
                messages = listOf(
                    MemberMessage(
                        id = "m1",
                        senderId = "u-2",
                        senderName = "ফারহান সাদিক",
                        receiverId = me.id,
                        body = "ভাইয়া, অধ্যায় ১-এর ১.৩ সেকশনটা দারুণ! চলুন শুক্রবারের আগে অনুশীলনী শেষ করি।",
                        timestampLabel = "১০ মিনিট আগে"
                    )
                ),
                books = listOf(flagshipBook, secondBook),
                highlights = listOf(
                    ReadingHighlight(
                        id = "hl-1",
                        userId = me.id,
                        userName = me.name,
                        bookId = flagshipBook.id,
                        chapterNo = 1,
                        pageNo = 18,
                        highlightedText = "Sayings, doings and relatings hang together in the project of a practice.",
                        note = "প্রপোজালের তাত্ত্বিক ফ্রেমওয়ার্কে এটি উদ্ধৃত করব",
                        visibility = HighlightVisibility.GROUP
                    )
                ),
                bookOrders = emptyList(),
                circles = listOf(circle),
                pokesToday = listOf(
                    PokeItem(
                        id = "pk-1",
                        circleId = circle.id,
                        chapterNo = 1,
                        senderId = "u-2",
                        senderName = "ফারহান সাদিক",
                        receiverId = me.id,
                        receiverName = me.name,
                        presetMessage = "চলুন একসাথে অধ্যায়টি শেষ করি।",
                        deliveryMode = PokeDeliveryMode.INSTANT,
                        dayEpoch = 1L
                    )
                ),
                polls = listOf(
                    ChapterPoll(
                        id = "poll-1",
                        circleId = circle.id,
                        chapterNo = 1,
                        question = "বাংলাদেশের বিশ্ববিদ্যালয়ে CPAR বাস্তবায়নে সবচেয়ে বড় বাধা কোনটি বলে মনে করেন?",
                        options = listOf(
                            "শিক্ষক-শিক্ষার্থী ক্ষমতার দূরত্ব (Relatings)",
                            "সময় ও ফান্ডিং সীমাবদ্ধতা (Doings)",
                            "গবেষণা ভীতি ও প্রচলিত মুখস্থ সংস্কৃতি (Sayings)"
                        ),
                        votesByUser = mapOf("u-2" to 0, "u-3" to 2, "u-4" to 1)
                    )
                ),
                debateComments = listOf(
                    DebateComment(
                        id = "deb-1",
                        authorName = "মালিহা তাবাসসুম",
                        stance = "পক্ষে (Support)",
                        body = "ক্লাসরুমে আগে নিরাপদ Communicative Space তৈরি না করলে শিক্ষার্থীরা মন খুলে কথা বলবে না।"
                    )
                ),
                memberStageByUserId = stageMap,
                myChapterProgress = mapOf(1 to myCh1Progress),
                circleMemberProgress = peerProgressSeed,
                coAuthorUserIds = setOf(me.id, "u-2", "u-4"),
                sessions = listOf(
                    OnlineSessionItem(
                        id = "sess-1",
                        circleId = circle.id,
                        chapterNo = 1,
                        partNo = 1,
                        title = "অধ্যায় ১ (পর্ব ১/২): Practice Architectures অনলাইন সেশন",
                        scheduleLabel = "শুক্রবার, রাত ৮:৩০ • ৪০ মিনিট",
                        presenterUserId = me.id,
                        presenterName = me.name,
                        hoursUntilSession = 36,
                        videoProvider = VideoProvider.JITSI_IN_APP,
                        jitsiRoomName = "SPARC_CPAR_Cohort01_Ch1_Part1",
                        externalMeetingUrl = "https://meet.google.com/sparc-cpar-ch1"
                    ),
                    OnlineSessionItem(
                        id = "sess-clinic",
                        circleId = null,
                        chapterNo = null,
                        isResearchClinic = true,
                        title = "মাসিক লাইভ রিসার্চ ক্লিনিক (Live Q&A with Senior Researchers)",
                        scheduleLabel = "মাসের শেষ শনিবার, রাত ৯:০০ • ৬০ মিনিট",
                        durationMinutes = 60,
                        presenterName = "ড. নুসরাত জাহান ও SPARC প্যানেল",
                        hoursUntilSession = 120,
                        videoProvider = VideoProvider.JITSI_IN_APP,
                        jitsiRoomName = "SPARC_Monthly_Research_Clinic",
                        externalMeetingUrl = "https://zoom.us/j/9988776655"
                    )
                ),
                supervisionDeals = listOf(
                    SupervisionDeal(
                        id = "deal-1",
                        researchGroupName = "CPAR Higher-Ed Co-Author Team",
                        paperTopic = "বিশ্ববিদ্যালয় শ্রেণিকক্ষে অংশগ্রহণমূলক মূল্যায়ন সংস্কার",
                        supervisorName = "ড. নুসরাত জাহান",
                        agreedSessions = 4,
                        agreedFeeBdt = 800.0,
                        isPaidViaWallet = false
                    )
                ),
                walletHistory = listOf(
                    WalletTransactionItem(
                        id = "tx-1",
                        method = PaymentMethod.BKASH_PGW,
                        amountBdt = 1000.0,
                        status = "Verified",
                        reference = "BKASH-984120",
                        description = "bKash PGW ক্রেডিট লোড"
                    ),
                    WalletTransactionItem(
                        id = "tx-2",
                        method = PaymentMethod.MANUAL_TRX,
                        amountBdt = 250.0,
                        status = "Verified",
                        reference = "REF-BONUS",
                        description = "রেফারেল ও ওয়েলকাম ক্রেডিট"
                    )
                )
            )
        }
    }
}
