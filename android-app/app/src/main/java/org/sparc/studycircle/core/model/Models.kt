package org.sparc.studycircle.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SparcVisualTheme(
    val titleBn: String,
    val subtitleBn: String
) {
    COBALT_SAAS(
        titleBn = "Cobalt SaaS (Light)",
        subtitleBn = "Coursera / Brilliant ধাঁচের আল্ট্রা-ক্লিন সাদা ও ইলেকট্রিক ব্লু"
    ),
    MIDNIGHT_GLASS(
        titleBn = "Midnight Glass (Dark)",
        subtitleBn = "Linear / Audible ধাঁচের প্রিমিয়াম ডার্ক গ্লাসমরফিজম"
    ),
    OCEAN_HERO(
        titleBn = "Ocean Hero (Layered)",
        subtitleBn = "Blinkist / Headspace ধাঁচের কার্ভড টিল হিরো ও ডার্ক ফ্লোটিং ডক"
    )
}

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
enum class HighlightVisibility(val labelBn: String) {
    PRIVATE("ব্যক্তিগত (Private)"),
    SUPERVISOR("শুধু সুপারভাইজার"),
    GROUP("গ্রুপ সদস্য")
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
enum class CircleStatus(val labelBn: String) {
    FORMING("গঠন প্রক্রিয়াধীন"),
    ACTIVE("সক্রিয় (Active)"),
    COMPLETED("সম্পন্ন")
}

@Serializable
enum class SupervisorAssignmentType { MEMBER_VOTED, ADMIN_ASSIGNED }

@Serializable
enum class ProgressStage(val step: Int, val labelBn: String) {
    NOT_STARTED(1, "শুরু হয়নি (Not Started)"),
    READING_STARTED(2, "পড়া শুরু (Reading Started)"),
    READING_IN_PROGRESS(3, "পড়া চলছে (In Progress)"),
    READING_COMPLETED(4, "পড়া সম্পন্ন (Completed)"),
    EXERCISES_SUBMITTED(5, "অনুশীলনী জমা (Submitted)"),
    LEARNING_VERIFIED(6, "শিখন যাচাইকৃত (Verified)")
}

@Serializable
enum class CalibrationFlag(val labelBn: String) {
    OVER_CONFIDENT("Over-confident (আত্মমূল্যায়ন বেশি, নম্বর < ৫০%)"),
    UNDER_CONFIDENT("Under-confident (আত্মমূল্যায়ন কম, নম্বর >= ৮০%)")
}

@Serializable
enum class PokeDeliveryMode { INSTANT, DAILY_SUMMARY, QUIET_HOURS_QUEUED, MUTED }

@Serializable
enum class MuteScope(val labelBn: String) {
    HOURS_24("২৪ ঘণ্টা"),
    DAYS_7("৭ দিন"),
    ENTIRE_CHAPTER("পুরো অধ্যায়")
}

@Serializable
enum class PaymentMethod(val labelBn: String) {
    SSLCOMMERZ("SSLCommerz গেটওয়ে"),
    BKASH_PGW("bKash পেমেন্ট গেটওয়ে"),
    MANUAL_TRX("ম্যানুয়াল bKash/Nagad TrxID")
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
    val earnedBadges: List<String> = emptyList()
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
    val physicalPriceBdt: Double,
    val totalChapters: Int,
    val isFirstChapterFree: Boolean = true,
    val minMembersRequired: Int = 10,
    val chapters: List<ChapterItem>
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
    val nextSessionAnnounced: Boolean = false
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
