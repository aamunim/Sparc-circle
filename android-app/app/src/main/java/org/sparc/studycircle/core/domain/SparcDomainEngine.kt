package org.sparc.studycircle.core.domain

import org.sparc.studycircle.core.model.*
import kotlin.math.ceil
import kotlin.math.max

/**
 * Pure, deterministic domain engine implementing all SPARC Study Circle v2.0 business rules,
 * state transitions, rate limits, grading formulas, calibration flags, and matchmaking logic.
 */
object SparcDomainEngine {

    val PRESET_POKE_MESSAGES = listOf(
        "এগিয়ে চলুন!",
        "পড়া কেমন চলছে?",
        "আমরা আপনার অপেক্ষায়!",
        "চলুন একসাথে অধ্যায়টি শেষ করি।",
        "আপনি পারবেন!"
    )

    val QUICK_POKE_REPLIES = listOf("ধন্যবাদ!", "করছি!")

    val QUICK_INVITE_TEMPLATES = listOf(
        "আমি দ্রুত পড়া শেষ করতে চাই। আপনিও জয়েন করুন এই বইয়ের গ্রুপে।",
        "আমাদের রিসার্চ ইন্টারেস্ট এক! চলুন একসাথে CPAR স্টাডি সার্কেলে যুক্ত হই।",
        "এই বইয়ের ১ম অধ্যায় ফ্রি—চলুন আজই স্টাডি সার্কেল শুরু করি।"
    )

    const val COMPASSIONATE_OVERDUE_MESSAGE = "সময় নিন — আপনার দল আপনার সঙ্গে আছে"

    // ------------------------------------------------------------------------
    // 1. Reading Speed & Daily Page Target Estimator (Section 3.3 & 7.2)
    // ------------------------------------------------------------------------
    fun calculateDailyPageTarget(totalPages: Int, targetDays: Int): Int {
        require(totalPages > 0) { "Total pages must be positive" }
        val safeDays = max(targetDays, 1)
        return ceil(totalPages.toDouble() / safeDays.toDouble()).toInt()
    }

    fun estimateDaysFromDailyMinutes(
        totalPages: Int,
        defaultReadingMinutes: Int,
        dailyAllocatedMinutes: Int
    ): Pair<Int, Int> {
        val safeDailyMinutes = max(dailyAllocatedMinutes, 5)
        val estimatedDays = max(1, ceil(defaultReadingMinutes.toDouble() / safeDailyMinutes.toDouble()).toInt())
        val dailyPages = calculateDailyPageTarget(totalPages, estimatedDays)
        return estimatedDays to dailyPages
    }

    // ------------------------------------------------------------------------
    // 2. Group Activation & Dual Supervisor Voting (Section 4.1 - 4.2 & 7.2)
    // ------------------------------------------------------------------------
    fun evaluateCircleActivation(circle: StudyCircleGroup): CircleStatus {
        if (circle.status == CircleStatus.COMPLETED) return CircleStatus.COMPLETED
        return if (circle.memberIds.distinct().size >= circle.minMembersRequired) {
            CircleStatus.ACTIVE
        } else {
            CircleStatus.FORMING
        }
    }

    fun resolveElectedSupervisor(circle: StudyCircleGroup): Pair<String?, SupervisorAssignmentType?> {
        if (circle.supervisorAssignmentType == SupervisorAssignmentType.ADMIN_ASSIGNED) {
            return circle.supervisorId to SupervisorAssignmentType.ADMIN_ASSIGNED
        }
        val memberCount = circle.memberIds.distinct().size
        if (memberCount == 0 || circle.supervisorVotes.isEmpty()) {
            return circle.supervisorId to circle.supervisorAssignmentType
        }
        val topEntry = circle.supervisorVotes.values
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }

        return if (topEntry != null && topEntry.value > memberCount / 2) {
            topEntry.key to SupervisorAssignmentType.MEMBER_VOTED
        } else {
            circle.supervisorId to circle.supervisorAssignmentType
        }
    }

    // ------------------------------------------------------------------------
    // 3. Peer Motivation ('Poke') Rate-Limiter & Quiet Hours (Section 4.3)
    // ------------------------------------------------------------------------
    sealed class PokeValidationResult {
        data class Allowed(val deliveryMode: PokeDeliveryMode) : PokeValidationResult()
        data class Blocked(val reasonBn: String) : PokeValidationResult()
    }

    fun validatePoke(
        senderId: String,
        receiverId: String,
        receiverStage: ProgressStage,
        presetMessage: String,
        existingPokesToday: List<PokeItem>,
        currentHour24Dhaka: Int,
        isReceiverMuted: Boolean
    ): PokeValidationResult {
        if (senderId == receiverId) {
            return PokeValidationResult.Blocked("নিজেকে Poke পাঠানো যাবে না।")
        }
        if (presetMessage !in PRESET_POKE_MESSAGES) {
            return PokeValidationResult.Blocked("শুধুমাত্র নির্ধারিত উৎসাহমূলক বার্তা পাঠানো যাবে।")
        }
        if (receiverStage.step >= ProgressStage.EXERCISES_SUBMITTED.step) {
            return PokeValidationResult.Blocked("এই সদস্য ইতোমধ্যে অনুশীলনী জমা দিয়েছেন।")
        }
        val senderCountToday = existingPokesToday.count { it.senderId == senderId }
        if (senderCountToday >= 3) {
            return PokeValidationResult.Blocked("আপনি আজ সর্বোচ্চ ৩টি Poke পাঠিয়ে ফেলেছেন।")
        }
        val alreadyPokedSamePerson = existingPokesToday.any {
            it.senderId == senderId && it.receiverId == receiverId
        }
        if (alreadyPokedSamePerson) {
            return PokeValidationResult.Blocked("একজন সদস্যকে দিনে সর্বোচ্চ ১টি Poke পাঠানো যায়।")
        }

        val receiverInstantCount = existingPokesToday.count {
            it.receiverId == receiverId && it.deliveryMode == PokeDeliveryMode.INSTANT
        }
        val isQuietHours = currentHour24Dhaka >= 22 || currentHour24Dhaka < 7

        val delivery = when {
            isReceiverMuted -> PokeDeliveryMode.MUTED
            isQuietHours -> PokeDeliveryMode.QUIET_HOURS_QUEUED
            receiverInstantCount >= 3 -> PokeDeliveryMode.DAILY_SUMMARY
            else -> PokeDeliveryMode.INSTANT
        }
        return PokeValidationResult.Allowed(delivery)
    }

    // ------------------------------------------------------------------------
    // 4. 6-Stage State Machine, Auto-Grading & 48h Re-Attempt (Section 5)
    // ------------------------------------------------------------------------
    fun canModifyExpectedDate(progress: ChapterProgressState): Boolean =
        progress.dateModifiedCount < 1 && progress.stage.step < ProgressStage.EXERCISES_SUBMITTED.step

    fun isChapterUnlockedForUser(
        book: BookItem,
        chapterNo: Int,
        previousChapterProgress: ChapterProgressState?
    ): Boolean {
        if (chapterNo == 1 && book.isFirstChapterFree) return true
        if (chapterNo == 1) return true
        return previousChapterProgress?.stage == ProgressStage.LEARNING_VERIFIED
    }

    fun canUseAutoScoreReattempt(progress: ChapterProgressState): Boolean {
        return progress.stage == ProgressStage.EXERCISES_SUBMITTED &&
            !progress.reattemptUsed &&
            progress.hoursSinceFirstSubmission >= 48
    }

    data class AutoGradeOutcome(
        val autoScore: Int,
        val genuinenessPassed: Boolean
    )

    fun gradeAutoQuestions(
        questions: List<AssessmentQuestion>,
        submittedAnswers: Map<String, String>
    ): AutoGradeOutcome {
        var score = 0
        var genuinenessOk = true
        for (q in questions.filter { it.isAutoScored }) {
            val given = submittedAnswers[q.id]?.trim()
            val expected = q.lockedCorrectAnswer?.trim()
            if (expected != null && given == expected) {
                score += q.maxMarks
            } else if (q.isGenuinenessCheck) {
                genuinenessOk = false
            }
        }
        return AutoGradeOutcome(autoScore = score, genuinenessPassed = genuinenessOk)
    }

    // ------------------------------------------------------------------------
    // 5. Self-Rating Calibration, Red-Flags & Learning Verification (5.3-5.4)
    // ------------------------------------------------------------------------
    fun computeCalibrationFlags(
        selfRatings: List<Int>,
        totalScore: Int,
        totalMarks: Int
    ): List<CalibrationFlag> {
        if (selfRatings.isEmpty() || totalMarks <= 0) return emptyList()
        val avgRating = selfRatings.average()
        val scorePct = (totalScore.toDouble() / totalMarks.toDouble()) * 100.0
        return buildList {
            if (avgRating >= 4.0 && scorePct < 50.0) add(CalibrationFlag.OVER_CONFIDENT)
            if (avgRating <= 2.0 && scorePct >= 80.0) add(CalibrationFlag.UNDER_CONFIDENT)
        }
    }

    fun evaluateProgressVerification(
        progress: ChapterProgressState,
        chapterType: ChapterType,
        missedWritingAfterDeadline: Boolean = false
    ): ChapterProgressState {
        val totalScore = progress.autoScore + (progress.supervisorScore ?: 0)
        val scorePct = (totalScore.toDouble() / chapterType.totalMarks.toDouble()) * 100.0

        val calibration = computeCalibrationFlags(
            selfRatings = progress.selfRatings,
            totalScore = totalScore,
            totalMarks = chapterType.totalMarks
        )

        val redFlags = buildList {
            if (progress.stage == ProgressStage.NOT_STARTED) {
                add("পড়ার ঘোষণা দেওয়া হয়নি")
            }
            if (progress.expectedCompletionDaysFromToday < -7 &&
                progress.stage.step < ProgressStage.EXERCISES_SUBMITTED.step
            ) {
                add("নির্ধারিত সময়ের চেয়ে ৭ দিনের বেশি দেরি")
            }
            if (progress.stage.step >= ProgressStage.EXERCISES_SUBMITTED.step &&
                progress.supervisorScore != null &&
                (totalScore < chapterType.redFlagMarks || scorePct < 50.0)
            ) {
                add("মূল্যায়নে ৫০%-এর কম বা রেড-ফ্ল্যাগ নম্বর")
            }
            if (!progress.genuinenessPassed) {
                add("রিডিং-জেনুইননেস প্রশ্নে ভুল উত্তর")
            }
            if (missedWritingAfterDeadline && !progress.writingSubmitted) {
                add("রাইটিং টাস্ক মিস হয়েছে")
            }
        }

        val passedMarks = totalScore >= chapterType.passMarks || progress.supervisorOverride
        val nextStage = if (
            progress.stage.step >= ProgressStage.EXERCISES_SUBMITTED.step &&
            passedMarks &&
            progress.selfRatings.isNotEmpty() &&
            progress.writingSubmitted
        ) {
            ProgressStage.LEARNING_VERIFIED
        } else {
            progress.stage
        }

        return progress.copy(
            stage = nextStage,
            calibrationFlags = calibration,
            redFlagIndicators = redFlags,
            needsSupport = redFlags.size >= 3
        )
    }

    // ------------------------------------------------------------------------
    // 6. Group Session Threshold Trigger (>= 80% Submitted OR >= 10 Days)
    // ------------------------------------------------------------------------
    data class GroupSessionThresholdStatus(
        val completionRatePercent: Double,
        val shouldAnnounceNextSession: Boolean,
        val triggerReasonBn: String?
    )

    fun evaluateGroupSessionTrigger(
        totalMembers: Int,
        memberStages: List<ProgressStage>,
        daysSinceLastSession: Int
    ): GroupSessionThresholdStatus {
        if (totalMembers <= 0) {
            return GroupSessionThresholdStatus(0.0, false, null)
        }
        val submittedCount = memberStages.count { it.step >= ProgressStage.EXERCISES_SUBMITTED.step }
        val completionRate = (submittedCount.toDouble() / totalMembers.toDouble()) * 100.0

        return when {
            completionRate >= 80.0 -> GroupSessionThresholdStatus(
                completionRatePercent = completionRate,
                shouldAnnounceNextSession = true,
                triggerReasonBn = "৮০%+ সদস্য অনুশীলনী জমা দিয়েছেন — পরবর্তী সেশন ঘোষিত!"
            )
            daysSinceLastSession >= 10 -> GroupSessionThresholdStatus(
                completionRatePercent = completionRate,
                shouldAnnounceNextSession = true,
                triggerReasonBn = "আগের সেশনের ১০ দিন পূর্ণ হয়েছে — পরবর্তী সেশন ঘোষিত!"
            )
            else -> GroupSessionThresholdStatus(
                completionRatePercent = completionRate,
                shouldAnnounceNextSession = false,
                triggerReasonBn = null
            )
        }
    }

    // ------------------------------------------------------------------------
    // 7. Co-Author Matchmaking & Reading Streak (Section 2.4 & 6.2)
    // ------------------------------------------------------------------------
    fun suggestCoAuthorTeam(
        currentUser: UserProfile,
        allMembers: List<UserProfile>,
        desiredTeamSize: Int = 4
    ): List<CoAuthorCandidate> {
        val myInterests = currentUser.researchInterests.map { it.lowercase() }.toSet()
        val mySkills = currentUser.skills.map { it.lowercase() }.toSet()

        return allMembers
            .filter { it.id != currentUser.id }
            .map { candidate ->
                val shared = candidate.researchInterests.filter { it.lowercase() in myInterests }
                val complementary = candidate.skills.filter { it.lowercase() !in mySkills }
                val score = (shared.size * 15.0) + (complementary.size * 20.0) + (candidate.rating * 5.0)
                CoAuthorCandidate(
                    user = candidate,
                    sharedInterests = shared,
                    complementarySkills = complementary,
                    matchScore = score
                )
            }
            .sortedByDescending { it.matchScore }
            .take(max(desiredTeamSize - 1, 3))
    }

    fun calculateNextStreakAndBonus(
        currentStreakDays: Int,
        readYesterday: Boolean,
        alreadyReadToday: Boolean
    ): Pair<Int, Int> {
        if (alreadyReadToday) return currentStreakDays to 0
        val newStreak = if (readYesterday) currentStreakDays + 1 else 1
        val milestoneBonus = if (newStreak in setOf(3, 7, 15, 30)) newStreak * 5 else 0
        return newStreak to (5 + milestoneBonus)
    }

    fun generateLocalAiWritingFeedback(
        content: String,
        minWords: Int = 500,
        maxWords: Int = 700
    ): AiWritingFeedback {
        val words = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val count = words.size
        val hasAcademicMarkers = listOf(
            "গবেষণা", "প্রেক্ষাপট", "অংশগ্রহণকারী", "অনুশীলন", "প্রমাণ",
            "practice", "participant", "evidence", "concern", "site"
        ).count { marker -> content.contains(marker, ignoreCase = true) }

        val toneScore = (65 + minOf(hasAcademicMarkers * 6, 28) + if (count >= minWords) 5 else 0)
            .coerceAtMost(98)

        val lengthSuggestion = when {
            count < minWords -> "বর্তমান শব্দসংখ্যা $count; লক্ষ্যমাত্রা পূরণে আরও অন্তত ${minWords - count} শব্দ যোগ করুন।"
            count > maxWords -> "বর্তমান শব্দসংখ্যা $count; নির্ধারিত $maxWords শব্দের সীমায় আনতে পুনরাবৃত্তি কমান।"
            else -> "শব্দসংখ্যা ($count) আদর্শ সীমার ($minWords–$maxWords শব্দ) মধ্যে রয়েছে।"
        }

        return AiWritingFeedback(
            wordCount = count,
            academicToneScore = toneScore,
            toneFeedback = "একাডেমিক ও অংশগ্রহণমূলক গবেষণার (CPAR) ভাষা বজায় রাখুন এবং ব্যক্তিগত অনুমানের বদলে বাস্তব পর্যবেক্ষণ যুক্ত করুন।",
            grammarSuggestions = listOf(
                "দীর্ঘ যৌগিক বাক্যগুলো ছোট ও সুস্পষ্ট বাক্যে ভাগ করুন।",
                "পরিভাষা ব্যবহারের ক্ষেত্রে প্রথমবার পূর্ণরূপ উল্লেখ করুন (যেমন: Critical Participatory Action Research - CPAR)।"
            ),
            structureSuggestions = listOf(
                lengthSuggestion,
                "১ম অংশে Research Site-এর পটভূমি, ২য় অংশে Felt-concern এবং শেষ অংশে স্টেকহোল্ডারদের ভূমিকা সাজিয়ে লিখুন।"
            )
        )
    }
}
