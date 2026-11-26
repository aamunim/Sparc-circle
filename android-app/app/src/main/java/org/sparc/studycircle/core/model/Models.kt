package org.sparc.studycircle.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SparcVisualTheme(
    val titleBn: String,
    val subtitleBn: String,
    val subtitleEn: String
) {
    COBALT_SAAS(
        titleBn = "Cobalt SaaS (Light)",
        subtitleBn = "Coursera / Brilliant ধাঁচের আল্ট্রা-ক্লিন সাদা ও ইলেকট্রিক ব্লু",
        subtitleEn = "Ultra-clean white & electric cobalt Coursera/Brilliant style"
    ),
    MIDNIGHT_GLASS(
        titleBn = "Midnight Glass (Dark)",
        subtitleBn = "Linear / Audible ধাঁচের প্রিমিয়াম ডার্ক গ্লাসমরফিজম",
        subtitleEn = "Premium dark glassmorphism Linear/Audible style"
    ),
    OCEAN_HERO(
        titleBn = "Ocean Hero (Layered)",
        subtitleBn = "Blinkist / Headspace ধাঁচের কার্ভড টিল হিরো ও ডার্ক ফ্লোটিং ডক",
        subtitleEn = "Curved ocean-teal hero header & floating dark capsule dock"
    );

    fun subtitle(lang: String): String = if (lang == "en") subtitleEn else subtitleBn
}

@Serializable
enum class SparcAccentPreset(
    val labelBn: String,
    val labelEn: String,
    val primaryHex: Long,
    val primaryEndHex: Long,
    val lightSoftHex: Long,
    val lightSoftTextHex: Long,
    val darkSoftHex: Long,
    val darkSoftTextHex: Long
) {
    COBALT("রয়্যাল কোবাল্ট", "Royal Cobalt", 0xFF2563EB, 0xFF1D4ED8, 0xFFDBEAFE, 0xFF1E40AF, 0xFF1E3A8A, 0xFF93C5FD),
    EMERALD("এমেরাল্ড স্কলার", "Emerald Scholar", 0xFF10B981, 0xFF059669, 0xFFD1FAE5, 0xFF065F46, 0xFF064E3B, 0xFF34D399),
    PURPLE("রয়্যাল পার্পল", "Royal Purple", 0xFF7C3AED, 0xFF6D28D9, 0xFFEDE9FE, 0xFF5B21B6, 0xFF4C1D95, 0xFFC4B5FD),
    AMBER("অ্যাম্বার গোল্ড", "Amber Gold", 0xFFD97706, 0xFFB45309, 0xFFFEF3C7, 0xFF92400E, 0xFF78350F, 0xFFFCD34D),
    CRIMSON("রোজ ক্রিমসন", "Rose Academic", 0xFFE11D48, 0xFFBE123C, 0xFFFFE4E6, 0xFF9F1239, 0xFF881337, 0xFFFDA4AF),
    TEAL("ওশান টিল", "Ocean Teal", 0xFF0D9488, 0xFF0284C7, 0xFFCCFBF1, 0xFF115E59, 0xFF134E4A, 0xFF5EEAD4);

    fun label(lang: String): String = if (lang == "en") labelEn else labelBn
}

@Serializable
enum class ReaderPaperTheme(val labelBn: String, val labelEn: String, val bgHex: Long, val textHex: Long, val mutedHex: Long) {
    PURE_WHITE("সাদা কাগজ", "Pure White", 0xFFFFFFFF, 0xFF0F172A, 0xFF475569),
    WARM_SEPIA("উষ্ণ সেপিয়া", "Warm Sepia", 0xFFF8F1E3, 0xFF2C221E, 0xFF6B5A4E),
    SOFT_MINT("সফট মিন্ট", "Soft Mint", 0xFFECFDF5, 0xFF064E3B, 0xFF047857),
    AMOLED_NIGHT("নাইট রিডিং", "Night Reading", 0xFF0B1120, 0xFFE2E8F0, 0xFF94A3B8);

    fun label(lang: String): String = if (lang == "en") labelEn else labelBn
}

@Serializable
enum class LauncherIconPreset(val labelBn: String, val labelEn: String, val aliasClassSuffix: String) {
    CLASSIC("ক্লাসিক কোবাল্ট", "Classic Cobalt", ".LauncherClassic"),
    EMERALD("এমেরাল্ড স্কলার", "Emerald Scholar", ".LauncherEmerald"),
    GOLD("রয়্যাল গোল্ড", "Royal Gold", ".LauncherGold");

    fun label(lang: String): String = if (lang == "en") labelEn else labelBn
}

@Serializable
data class AppCustomizationConfig(
    val appTitle: String = "SPARC Study Circle",
    val appTaglineBn: String = "বই পড়তে পড়তে লেখা শেখা",
    val appTaglineEn: String = "Learning Academic Writing While Reading",
    val splashQuoteBn: String = "যৌথ পাঠ, গভীর চিন্তা ও একাডেমিক গবেষণার সমন্বিত প্ল্যাটফর্ম",
    val splashQuoteEn: String = "Collaborative reading, critical inquiry & scholarly writing",
    val announcementBannerBn: String = "📢 নতুন কোহর্টে ভর্তি ও ১ম অধ্যায়ের ফ্রি রিডিং চলছে!",
    val announcementBannerEn: String = "📢 New cohort enrollment & free Chapter 1 reading are open!",
    val showAnnouncementBanner: Boolean = true,
    val bkashMerchantNumber: String = "01700-000000",
    val nagadMerchantNumber: String = "01800-000000",
    val rocketMerchantNumber: String = "01900-000000",
    val defaultSupervisionFeeBdt: Double = 300.0,
    val borrowDepositBdt: Double = 150.0,
    val minTopupBdt: Double = 500.0
)

@Serializable
data class UserUiCustomization(
    val accentPreset: SparcAccentPreset? = null,
    val readerFontSizeSp: Int = 16,
    val readerLineSpacingMultiplier: Float = 1.55f,
    val readerPaperTheme: ReaderPaperTheme = ReaderPaperTheme.WARM_SEPIA,
    val defaultLandingTab: String = "CIRCLE",
    val compactHomeCards: Boolean = false,
    val showHomeStatsRow: Boolean = true,
    val showHomePokeCard: Boolean = true,
    val launcherIconPreset: LauncherIconPreset = LauncherIconPreset.CLASSIC
)

@Serializable
data class ApiKeyVaultConfig(
    val geminiApiKey: String = "",
    val sslCommerzStoreId: String = "",
    val bkashMerchantKey: String = "",
    val customJitsiServerUrl: String = "",
    val customSupabaseUrl: String = "",
    val customSupabaseAnonKey: String = ""
)

@Serializable
enum class UserRole { MEMBER, SUPERVISOR, SPARC_REP, ADMIN }

@Serializable
enum class HighlightVisibility(val labelBn: String, val labelEn: String) {
    PRIVATE("ব্যক্তিগত (Private)", "Private"),
    SUPERVISOR("শুধু সুপারভাইজার", "Supervisor Only"),
    GROUP("গ্রুপ সদস্য", "Group Members");

    fun label(lang: String): String = if (lang == "en") labelEn else labelBn
}

@Serializable
enum class ChapterType(
    val totalQuestions: Int,
    val totalMarks: Int,
    val passMarks: Int,
    val redFlagMarks: Int,
    val defaultSessions: Int
) {
    STANDARD(totalQuestions = 30, totalMarks = 52, passMarks = 32, redFlagMarks = 26, defaultSessions = 1),
    LONG(totalQuestions = 36, totalMarks = 61, passMarks = 37, redFlagMarks = 31, defaultSessions = 2)
}

@Serializable
enum class CircleStatus(val labelBn: String, val labelEn: String) {
    FORMING("গঠন প্রক্রিয়াধীন", "Forming"),
    ACTIVE("সক্রিয় (Active)", "Active"),
    COMPLETED("সম্পন্ন", "Completed");

    fun label(lang: String): String = if (lang == "en") labelEn else labelBn
}

@Serializable
enum class SupervisorAssignmentType { MEMBER_VOTED, ADMIN_ASSIGNED }

@Serializable
enum class ProgressStage(val step: Int, val labelBn: String, val labelEn: String) {
    NOT_STARTED(1, "শুরু হয়নি", "Not Started"),
    READING_STARTED(2, "পড়া শুরু", "Reading Started"),
    READING_IN_PROGRESS(3, "পড়া চলছে", "Reading in Progress"),
    READING_COMPLETED(4, "পড়া সম্পন্ন", "Reading Completed"),
    EXERCISES_SUBMITTED(5, "অনুশীলনী জমা", "Exercises Submitted"),
    LEARNING_VERIFIED(6, "শিখন যাচাইকৃত", "Learning Verified");

    fun label(lang: String): String = if (lang == "en") labelEn else labelBn
}

@Serializable
enum class CalibrationFlag(val labelBn: String) {
    OVER_CONFIDENT("Over-confident (Self-rating >= 4, Score < 50%)"),
    UNDER_CONFIDENT("Under-confident (Self-rating <= 2, Score >= 80%)")
}

@Serializable
enum class PokeDeliveryMode { INSTANT, DAILY_SUMMARY, QUIET_HOURS_QUEUED, MUTED }

@Serializable
enum class MuteScope(val labelBn: String, val labelEn: String) {
    HOURS_24("২৪ ঘণ্টা", "24h"),
    DAYS_7("৭ দিন", "7d"),
    ENTIRE_CHAPTER("পুরো অধ্যায়", "Chapter");

    fun label(lang: String): String = if (lang == "en") labelEn else labelBn
}

@Serializable
enum class PaymentMethod(val labelBn: String, val labelEn: String) {
    SSLCOMMERZ("SSLCommerz গেটওয়ে", "SSLCommerz Gateway"),
    BKASH_PGW("bKash পেমেন্ট গেটওয়ে", "bKash Payment Gateway"),
    MANUAL_TRX("ম্যানুয়াল bKash/Nagad TrxID", "Manual bKash/Nagad TrxID");

    fun label(lang: String): String = if (lang == "en") labelEn else labelBn
}

@Serializable
enum class VideoProvider(val labelBn: String) {
    JITSI_IN_APP("ইন-অ্যাপ লাইভ ভিডিও (Jitsi)"),
    GOOGLE_MEET("Google Meet লিংক"),
    ZOOM("Zoom লিংক")
}

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val department: String,
    val researchInterests: List<String>,
    val skills: List<String>,
    val role: UserRole = UserRole.MEMBER,
    val rating: Double = 4.8,
    val gamificationPoints: Int = 0,
    val readingStreakDays: Int = 0,
    val referralCode: String,
    val walletBalanceBdt: Double = 0.0,
    val earnedBadges: List<String> = emptyList(),
    val avatarUrl: String = "",
    val institution: String = "",
    val bio: String = "",
    val dailyReadingGoalMinutes: Int = 30
)

@Serializable
data class MemberMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val receiverId: String,
    val body: String,
    val timestampLabel: String
)

@Serializable
data class ReadingHighlight(
    val id: String,
    val userId: String,
    val userName: String,
    val bookId: String,
    val chapterNo: Int,
    val pageNo: Int,
    val highlightedText: String,
    val note: String = "",
    val isUnderline: Boolean = false,
    val visibility: HighlightVisibility = HighlightVisibility.PRIVATE
)

@Serializable
data class AssessmentQuestion(
    val id: String,
    val questionNo: Int,
    val sectionCode: String, // A..E auto, F..J open
    val categoryLabel: String,
    val isAutoScored: Boolean,
    val isGenuinenessCheck: Boolean = false,
    val maxMarks: Int,
    val promptBn: String,
    val options: List<String> = emptyList(),
    val lockedCorrectAnswer: String? = null,
    val explanationBn: String = "",
    val supervisorModelPoints: List<String> = emptyList()
)

@Serializable
data class ChapterSection(
    val id: String,
    val title: String,
    val pageRange: String,
    val paragraphPreview: List<String>
)

@Serializable
data class ChapterItem(
    val id: String,
    val bookId: String,
    val chapterNo: Int,
    val title: String,
    val type: ChapterType,
    val totalPages: Int,
    val defaultReadingMinutes: Int,
    val audioSummaryTitle: String,
    val audioSummaryUrl: String = "",
    val audioDurationMinutes: Int,
    val flashNotes: List<String>,
    val sections: List<ChapterSection>,
    val learningOutcomes: List<String>,
    val questions: List<AssessmentQuestion>,
    val writingTaskPrompt: String,
    val minWords: Int = 500,
    val maxWords: Int = 700
)

@Serializable
data class BookItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val author: String,
    val category: String,
    val pdfUrl: String = "",
    val physicalPriceBdt: Double,
    val totalChapters: Int,
    val isFirstChapterFree: Boolean = true,
    val minMembersRequired: Int = 10,
    val chapters: List<ChapterItem>,
    val sourceAttribution: String = ""
)

@Serializable
data class PhysicalBookOrder(
    val id: String,
    val userId: String,
    val bookId: String,
    val bookTitle: String,
    val orderMode: String, // Purchase or Borrow
    val address: String,
    val phone: String,
    val amountBdt: Double,
    val status: String
)

@Serializable
data class StudyCircleGroup(
    val id: String,
    val name: String,
    val bookId: String,
    val bookTitle: String,
    val minMembersRequired: Int,
    val status: CircleStatus,
    val memberIds: List<String>,
    val supervisorId: String? = null,
    val supervisorAssignmentType: SupervisorAssignmentType? = null,
    val supervisorVotes: Map<String, String> = emptyMap(), // voterId -> candidateId
    val currentGroupChapter: Int = 1,
    val daysSinceLastSession: Int = 4,
    val nextSessionAnnounced: Boolean = false,
    val maxMembers: Int = 25,
    val isPrivate: Boolean = false,
    val inviteCode: String? = null,
    val sessionScheduleLabel: String = "শুক্রবার রাত ৮:৩০",
    val defaultMeetingUrl: String = "https://meet.jit.si/SPARC_Circle_Live",
    val circleRules: String = "নিয়মিত অধ্যায় পড়া ও সহপাঠীদের প্রতি শ্রদ্ধাশীল থেকে আলোচনায় অংশ নেওয়া।"
)

@Serializable
data class PokeItem(
    val id: String,
    val circleId: String,
    val chapterNo: Int,
    val senderId: String,
    val senderName: String,
    val receiverId: String,
    val receiverName: String,
    val presetMessage: String,
    val quickReply: String? = null,
    val deliveryMode: PokeDeliveryMode,
    val dayEpoch: Long
)

@Serializable
data class ChapterPoll(
    val id: String,
    val circleId: String,
    val chapterNo: Int,
    val question: String,
    val options: List<String>,
    val votesByUser: Map<String, Int> = emptyMap()
)

@Serializable
data class DebateComment(
    val id: String,
    val authorName: String,
    val stance: String,
    val body: String
)

@Serializable
data class ChapterProgressState(
    val id: String,
    val circleId: String,
    val userId: String,
    val chapterNo: Int,
    val stage: ProgressStage = ProgressStage.NOT_STARTED,
    val expectedCompletionDaysFromToday: Int = 6,
    val dateModifiedCount: Int = 0,
    val targetDays: Int = 8,
    val dailyTargetPages: Int = 4,
    val checkedSectionIds: Set<String> = emptySet(),
    val submittedAnswers: Map<String, String> = emptyMap(),
    val firstAutoScore: Int? = null,
    val autoScore: Int = 0,
    val hoursSinceFirstSubmission: Int = 0,
    val reattemptUsed: Boolean = false,
    val supervisorScore: Int? = null,
    val supervisorOverride: Boolean = false,
    val selfRatings: List<Int> = emptyList(),
    val genuinenessPassed: Boolean = true,
    val calibrationFlags: List<CalibrationFlag> = emptyList(),
    val redFlagIndicators: List<String> = emptyList(),
    val needsSupport: Boolean = false,
    val writingSubmitted: Boolean = false,
    val writingContent: String = "",
    val writingSupervisorApproved: Boolean = false
)

@Serializable
data class OnlineSessionItem(
    val id: String,
    val circleId: String?,
    val chapterNo: Int?,
    val partNo: Int = 1,
    val isResearchClinic: Boolean = false,
    val title: String,
    val scheduleLabel: String,
    val durationMinutes: Int = 40,
    val presenterName: String,
    val hoursUntilSession: Int,
    val slideUploaded: Boolean = false,
    val slideFileName: String? = null,
    val videoProvider: VideoProvider = VideoProvider.JITSI_IN_APP,
    val jitsiRoomName: String,
    val externalMeetingUrl: String
)

@Serializable
data class AiWritingFeedback(
    val wordCount: Int,
    val academicToneScore: Int,
    val toneFeedback: String,
    val grammarSuggestions: List<String>,
    val structureSuggestions: List<String>
)

@Serializable
data class CoAuthorCandidate(
    val user: UserProfile,
    val sharedInterests: List<String>,
    val complementarySkills: List<String>,
    val matchScore: Double
)

@Serializable
data class SupervisionDeal(
    val id: String,
    val researchGroupName: String,
    val paperTopic: String,
    val supervisorName: String,
    val agreedSessions: Int,
    val agreedFeeBdt: Double,
    val isPaidViaWallet: Boolean
)

@Serializable
data class WalletTransactionItem(
    val id: String,
    val method: PaymentMethod,
    val amountBdt: Double,
    val status: String, // Verified / Pending
    val reference: String,
    val description: String
)
