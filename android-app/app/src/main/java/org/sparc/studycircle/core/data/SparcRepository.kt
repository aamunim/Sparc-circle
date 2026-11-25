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
    private val onPersistTheme: (SparcVisualTheme) -> Unit = {},
    private val onPersistAuth: (Boolean, String, String) -> Unit = { _, _, _ -> },
    private val onPersistLanguage: (String) -> Unit = {},
    private val onPersistApiVault: (ApiKeyVaultConfig) -> Unit = {},
    private val onPersistSession: (SupabaseAuthSession?) -> Unit = {}
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        supabaseClient.vaultConfig = initialApiVault
        supabaseClient.onSessionUpdated = { sess -> onPersistSession(sess) }
        if (initialSession != null) {
            supabaseClient.restoreSession(initialSession)
        }
    }

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
                apiVault = initialApiVault,
                isSupabaseConnected = supabaseClient.isConfigured,
                activeCircleId = seeded.circles.firstOrNull()?.id,
                currentUser = updatedUser
            )
        }
    )
    val state: StateFlow<SparcAppState> = _state.asStateFlow()

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
                    currentUser = liveMe,
                    directoryMembers = mergedPeers,
                    books = snap.books.ifEmpty { s.books },
                    circles = snap.circles.ifEmpty { s.circles },
                    activeCircleId = chosenCircleId,
                    memberStageByUserId = snap.memberStageByUserId.ifEmpty { s.memberStageByUserId },
                    myChapterProgress = if (snap.myChapterProgress.isNotEmpty()) snap.myChapterProgress else s.myChapterProgress,
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
                        if (s.appLanguage == "en") "✓ Synced live with Supabase" else "✓ লাইভ Supabase ডাটা সিঙ্ক হয়েছে"
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

    // Section 2.1: Update Onboarding Profile, Interests, Skills & Optional Referral Code
    fun updateProfileAndReferral(
        name: String,
        department: String,
        interests: List<String>,
        skills: List<String>,
        appliedReferralCode: String
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
                    s.appLanguage == "en" -> "Profile & skill tags saved successfully."
                    else -> "প্রোফাইল ও স্কিল ট্যাগ সফলভাবে সংরক্ষিত হয়েছে।"
                }
            )
        }
        launchRemoteSync {
            supabaseClient.updateMyProfileRemote(name, department, interests, skills, appliedReferralCode).getOrThrow()
            syncFromSupabaseNow(silent = true)
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

    // Section 2.3: Wallet Top-Up (SSLCommerz / bKash PGW / Manual TrxID >= 500 BDT)
    fun topUpWallet(amountBdt: Double, method: PaymentMethod, manualTrxId: String = "") {
        if (amountBdt < 500.0) {
            _state.update {
                it.copy(
                    statusBannerMessage = if (it.appLanguage == "en") "Minimum top-up is ৳500." else "ন্যূনতম ৫০০ টাকা ক্রেডিট লোড করতে হবে।"
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

    // Section 4.1 - 4.2: Create / Join Study Circle & Vote Supervisor
    fun createNewStudyCircle(name: String, book: BookItem, minMembers: Int = 5) {
        if (name.isBlank()) return
        val localId = UUID.randomUUID().toString()
        _state.update { s ->
            val newCircle = StudyCircleGroup(
                id = localId,
                name = name.trim(),
                bookId = book.id,
                bookTitle = book.title,
                minMembersRequired = minMembers.coerceAtLeast(2),
                status = CircleStatus.FORMING,
                memberIds = listOf(s.currentUser.id)
            )
            s.copy(
                circles = s.circles + newCircle,
                activeCircleId = localId,
                statusBannerMessage = if (s.appLanguage == "en") {
                    "Created Study Circle: ${name.trim()}"
                } else {
                    "নতুন স্টাডি সার্কেল তৈরি হয়েছে: ${name.trim()}"
                }
            )
        }
        launchRemoteSync {
            val remoteId = supabaseClient.createCircleRemote(name, book.id, minMembers).getOrThrow()
            if (remoteId.isNotBlank()) {
                _state.update { it.copy(activeCircleId = remoteId) }
            }
            syncFromSupabaseNow(silent = true)
        }
    }

    fun joinStudyCircle(circleId: String) {
        addMemberToCircle(circleId, _state.value.currentUser.id)
        _state.update { it.copy(activeCircleId = circleId) }
        launchRemoteSync {
            supabaseClient.joinCircleRemote(circleId).getOrThrow()
            syncFromSupabaseNow(silent = true)
        }
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
        _state.update { s ->
            val updatedCircles = s.circles.map { c ->
                if (c.id != circleId) c else {
                    val votes = c.supervisorVotes + (s.currentUser.id to candidateId)
                    val withVotes = c.copy(supervisorVotes = votes)
                    val (winner, assignType) = SparcDomainEngine.resolveElectedSupervisor(withVotes)
                    withVotes.copy(supervisorId = winner, supervisorAssignmentType = assignType)
                }
            }
            s.copy(
                circles = updatedCircles,
                statusBannerMessage = if (s.appLanguage == "en") "Supervisor vote recorded." else "সুপারভাইজার নির্বাচনের ভোট রেকর্ড করা হয়েছে।"
            )
        }
        launchRemoteSync {
            supabaseClient.voteSupervisorRemote(circleId, candidateId).getOrThrow()
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
        audioFileName: String?
    ): Result<Unit> {
        if (pdfBytes == null && audioBytes == null) {
            return Result.failure(
                IllegalArgumentException(
                    msg("অনুগ্রহ করে অন্তত একটি PDF বা অডিও ফাইল সিলেক্ট করুন।", "Please select at least a PDF or an audio file.")
                )
            )
        }
        if (isNewBook && newTitle.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    msg("নতুন বইয়ের নাম লিখুন।", "Please enter the new book title.")
                )
            )
        }

        if (!supabaseClient.hasAuthenticatedUser) {
            return Result.failure(
                IllegalStateException(
                    msg(
                        "ক্লাউড স্টোরেজে আপলোড করতে আগে নিজের ইমেইল দিয়ে লগইন/সাইন-আপ করুন।",
                        "Please sign in with your email account to upload files to Supabase Storage."
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

            if (isNewBook) {
                supabaseClient.publishNewBookWithMediaRemote(
                    title = newTitle.trim(),
                    author = newAuthor.trim().ifBlank { "SPARC Author" },
                    priceBdt = newPriceBdt,
                    pdfUrl = uploadedPdfUrl ?: "",
                    audioUrl = uploadedAudioUrl
                ).getOrThrow()
            } else {
                val book = targetBook ?: _state.value.books.first()
                supabaseClient.attachMediaToBookRemote(
                    bookId = book.id,
                    chapterNo = chapterNo.coerceAtLeast(1),
                    pdfUrl = uploadedPdfUrl,
                    audioUrl = uploadedAudioUrl
                ).getOrThrow()
            }

            syncFromSupabaseNow(silent = true)
            _state.update { s ->
                s.copy(
                    statusBannerMessage = if (s.appLanguage == "en") {
                        "✓ Cloud PDF/Audio uploaded to Supabase Storage & synced!"
                    } else {
                        "✓ বইয়ের PDF / অডিও সামারি সফলভাবে Supabase Storage-এ আপলোড ও সিঙ্ক হয়েছে!"
                    }
                )
            }
        }
    }

    suspend fun downloadCloudPdf(pdfUrl: String, targetFile: java.io.File): Result<java.io.File> =
        supabaseClient.downloadCloudFileToCache(pdfUrl, targetFile)

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
                supervisorId = "u-sup-1",
                supervisorAssignmentType = SupervisorAssignmentType.MEMBER_VOTED,
                supervisorVotes = mapOf(me.id to "u-sup-1", "u-2" to "u-sup-1", "u-3" to "u-sup-1", "u-4" to "u-sup-1"),
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
                sessions = listOf(
                    OnlineSessionItem(
                        id = "sess-1",
                        circleId = circle.id,
                        chapterNo = 1,
                        partNo = 1,
                        title = "অধ্যায় ১ (পর্ব ১/২): Practice Architectures অনলাইন সেশন",
                        scheduleLabel = "শুক্রবার, রাত ৮:৩০ • ৪০ মিনিট",
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
