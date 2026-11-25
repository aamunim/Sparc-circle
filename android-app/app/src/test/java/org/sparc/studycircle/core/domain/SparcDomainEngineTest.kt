package org.sparc.studycircle.core.domain

import org.junit.Assert.*
import org.junit.Test
import org.sparc.studycircle.core.model.*

class SparcDomainEngineTest {

    @Test
    fun `daily page target estimator uses ceiling division per Section 3_3 and 7_2`() {
        assertEquals(4, SparcDomainEngine.calculateDailyPageTarget(totalPages = 32, targetDays = 8))
        assertEquals(5, SparcDomainEngine.calculateDailyPageTarget(totalPages = 33, targetDays = 8))
        val (days, pages) = SparcDomainEngine.estimateDaysFromDailyMinutes(
            totalPages = 32,
            defaultReadingMinutes = 150,
            dailyAllocatedMinutes = 30
        )
        assertEquals(5, days)
        assertEquals(7, pages)
    }

    @Test
    fun `group activation trigger transitions forming circle to active at threshold`() {
        val forming = StudyCircleGroup(
            id = "c1",
            name = "Sociology Cohort",
            bookId = "b1",
            bookTitle = "CPAR Planner",
            minMembersRequired = 10,
            status = CircleStatus.FORMING,
            memberIds = (1..9).map { "u-$it" }
        )
        assertEquals(CircleStatus.FORMING, SparcDomainEngine.evaluateCircleActivation(forming))

        val reachedThreshold = forming.copy(memberIds = (1..10).map { "u-$it" })
        assertEquals(CircleStatus.ACTIVE, SparcDomainEngine.evaluateCircleActivation(reachedThreshold))
    }

    @Test
    fun `supervisor election respects admin_assigned override and majority vote`() {
        val circle = StudyCircleGroup(
            id = "c1",
            name = "Circle",
            bookId = "b1",
            bookTitle = "Book",
            minMembersRequired = 4,
            status = CircleStatus.ACTIVE,
            memberIds = listOf("u1", "u2", "u3", "u4"),
            supervisorVotes = mapOf("u1" to "u2", "u3" to "u2", "u4" to "u2")
        )
        val (elected, mode) = SparcDomainEngine.resolveElectedSupervisor(circle)
        assertEquals("u2", elected)
        assertEquals(SupervisorAssignmentType.MEMBER_VOTED, mode)

        val adminAssigned = circle.copy(
            supervisorId = "u-admin-pick",
            supervisorAssignmentType = SupervisorAssignmentType.ADMIN_ASSIGNED
        )
        val (finalSup, finalMode) = SparcDomainEngine.resolveElectedSupervisor(adminAssigned)
        assertEquals("u-admin-pick", finalSup)
        assertEquals(SupervisorAssignmentType.ADMIN_ASSIGNED, finalMode)
    }

    @Test
    fun `poke validator enforces rate limits, quiet hours, daily summary, and submitted lock`() {
        val preset = SparcDomainEngine.PRESET_POKE_MESSAGES.first()

        // 1. Block poking someone who already submitted exercises
        val submittedRes = SparcDomainEngine.validatePoke(
            senderId = "s1",
            receiverId = "r1",
            receiverStage = ProgressStage.EXERCISES_SUBMITTED,
            presetMessage = preset,
            existingPokesToday = emptyList(),
            currentHour24Dhaka = 14,
            isReceiverMuted = false
        )
        assertTrue(submittedRes is SparcDomainEngine.PokeValidationResult.Blocked)

        // 2. Quiet hours (11 PM Dhaka) queues notification instead of instant push
        val quietRes = SparcDomainEngine.validatePoke(
            senderId = "s1",
            receiverId = "r1",
            receiverStage = ProgressStage.READING_IN_PROGRESS,
            presetMessage = preset,
            existingPokesToday = emptyList(),
            currentHour24Dhaka = 23,
            isReceiverMuted = false
        )
        assertEquals(
            SparcDomainEngine.PokeValidationResult.Allowed(PokeDeliveryMode.QUIET_HOURS_QUEUED),
            quietRes
        )

        // 3. Sender max 3 pokes/day blocked on 4th attempt
        val threeSent = (1..3).map { idx ->
            PokeItem("p$idx", "c1", 1, "s1", "S1", "r$idx", "R$idx", preset, null, PokeDeliveryMode.INSTANT, 1L)
        }
        val fourthAttempt = SparcDomainEngine.validatePoke(
            senderId = "s1",
            receiverId = "r4",
            receiverStage = ProgressStage.READING_STARTED,
            presetMessage = preset,
            existingPokesToday = threeSent,
            currentHour24Dhaka = 12,
            isReceiverMuted = false
        )
        assertTrue(fourthAttempt is SparcDomainEngine.PokeValidationResult.Blocked)

        // 4. 4th poke to same receiver from different senders goes to DAILY_SUMMARY
        val threeReceived = (1..3).map { idx ->
            PokeItem("pr$idx", "c1", 1, "s$idx", "S$idx", "r-target", "Target", preset, null, PokeDeliveryMode.INSTANT, 1L)
        }
        val summaryRes = SparcDomainEngine.validatePoke(
            senderId = "s4",
            receiverId = "r-target",
            receiverStage = ProgressStage.READING_STARTED,
            presetMessage = preset,
            existingPokesToday = threeReceived,
            currentHour24Dhaka = 12,
            isReceiverMuted = false
        )
        assertEquals(
            SparcDomainEngine.PokeValidationResult.Allowed(PokeDeliveryMode.DAILY_SUMMARY),
            summaryRes
        )
    }

    @Test
    fun `calibration flags detect over-confident and under-confident learners`() {
        val overConfident = SparcDomainEngine.computeCalibrationFlags(
            selfRatings = listOf(4, 5, 4),
            totalScore = 20, // < 50% of 52
            totalMarks = 52
        )
        assertEquals(listOf(CalibrationFlag.OVER_CONFIDENT), overConfident)

        val underConfident = SparcDomainEngine.computeCalibrationFlags(
            selfRatings = listOf(2, 1, 2),
            totalScore = 45, // >= 80% of 52
            totalMarks = 52
        )
        assertEquals(listOf(CalibrationFlag.UNDER_CONFIDENT), underConfident)
    }

    @Test
    fun `red flag engine marks Needs Support at 3 indicators and unlocks Learning Verified when criteria met`() {
        val struggling = ChapterProgressState(
            id = "p1",
            circleId = "c1",
            userId = "u1",
            chapterNo = 1,
            stage = ProgressStage.EXERCISES_SUBMITTED,
            autoScore = 10,
            supervisorScore = 10, // 20/61 < 31 red-flag marks
            genuinenessPassed = false,
            writingSubmitted = false
        )
        val evaluatedStruggling = SparcDomainEngine.evaluateProgressVerification(
            progress = struggling,
            chapterType = ChapterType.LONG,
            missedWritingAfterDeadline = true
        )
        assertTrue(evaluatedStruggling.needsSupport)
        assertEquals(3, evaluatedStruggling.redFlagIndicators.size)
        assertEquals(ProgressStage.EXERCISES_SUBMITTED, evaluatedStruggling.stage)

        val passing = ChapterProgressState(
            id = "p2",
            circleId = "c1",
            userId = "u2",
            chapterNo = 1,
            stage = ProgressStage.EXERCISES_SUBMITTED,
            autoScore = 22,
            supervisorScore = 18, // 40/61 >= 37 pass marks
            selfRatings = listOf(4, 4, 4),
            genuinenessPassed = true,
            writingSubmitted = true
        )
        val evaluatedPassing = SparcDomainEngine.evaluateProgressVerification(
            progress = passing,
            chapterType = ChapterType.LONG
        )
        assertFalse(evaluatedPassing.needsSupport)
        assertEquals(ProgressStage.LEARNING_VERIFIED, evaluatedPassing.stage)
    }

    @Test
    fun `group session trigger fires at 80 percent completion OR 10 days elapsed`() {
        val stages80 = List(8) { ProgressStage.EXERCISES_SUBMITTED } + List(2) { ProgressStage.READING_IN_PROGRESS }
        val byCompletion = SparcDomainEngine.evaluateGroupSessionTrigger(
            totalMembers = 10,
            memberStages = stages80,
            daysSinceLastSession = 3
        )
        assertTrue(byCompletion.shouldAnnounceNextSession)

        val stages40 = List(4) { ProgressStage.EXERCISES_SUBMITTED } + List(6) { ProgressStage.READING_STARTED }
        val byTenDays = SparcDomainEngine.evaluateGroupSessionTrigger(
            totalMembers = 10,
            memberStages = stages40,
            daysSinceLastSession = 10
        )
        assertTrue(byTenDays.shouldAnnounceNextSession)
    }

    @Test
    fun `48h auto-score reattempt rule blocks early or repeated reattempts`() {
        val base = ChapterProgressState(
            id = "p1",
            circleId = "c1",
            userId = "u1",
            chapterNo = 1,
            stage = ProgressStage.EXERCISES_SUBMITTED,
            hoursSinceFirstSubmission = 24,
            reattemptUsed = false
        )
        assertFalse(SparcDomainEngine.canUseAutoScoreReattempt(base))
        assertTrue(SparcDomainEngine.canUseAutoScoreReattempt(base.copy(hoursSinceFirstSubmission = 48)))
        assertFalse(
            SparcDomainEngine.canUseAutoScoreReattempt(
                base.copy(hoursSinceFirstSubmission = 72, reattemptUsed = true)
            )
        )
    }

    @Test
    fun `repository supports switching and persisting all 3 modern visual UI themes`() {
        var persisted: SparcVisualTheme? = null
        val repo = org.sparc.studycircle.core.data.SparcRepository(
            initialTheme = SparcVisualTheme.COBALT_SAAS,
            onPersistTheme = { persisted = it }
        )
        assertEquals(SparcVisualTheme.COBALT_SAAS, repo.state.value.visualTheme)

        repo.setVisualTheme(SparcVisualTheme.MIDNIGHT_GLASS)
        assertEquals(SparcVisualTheme.MIDNIGHT_GLASS, repo.state.value.visualTheme)
        assertEquals(SparcVisualTheme.MIDNIGHT_GLASS, persisted)

        repo.setVisualTheme(SparcVisualTheme.OCEAN_HERO)
        assertEquals(SparcVisualTheme.OCEAN_HERO, repo.state.value.visualTheme)
        assertEquals(SparcVisualTheme.OCEAN_HERO, persisted)
    }

    @Test
    fun `repository supports researcher registration with referral bonus, language toggle, and logout`() = kotlinx.coroutines.test.runTest {
        var authFlag = false
        val repo = org.sparc.studycircle.core.data.SparcRepository(
            onPersistAuth = { authed, _, _ -> authFlag = authed }
        )
        assertFalse(repo.state.value.isAuthenticated)

        val baseWallet = repo.state.value.currentUser.walletBalanceBdt
        val result = repo.registerResearcher(
            name = "আরিফুল ইসলাম",
            email = "ariful@du.ac.bd",
            password = "secret123",
            department = "Sociology",
            skills = listOf("Data Analysis"),
            referralCode = "SPARC-FARHAN09"
        )
        assertTrue(result.isSuccess)
        assertTrue(repo.state.value.isAuthenticated)
        assertTrue(authFlag)
        assertEquals(baseWallet + 50.0, repo.state.value.currentUser.walletBalanceBdt, 0.01)

        repo.setAppLanguage("en")
        assertEquals("en", repo.state.value.appLanguage)

        repo.logout()
        assertFalse(repo.state.value.isAuthenticated)
        assertFalse(authFlag)
    }

    @Test
    fun `api key vault supports adding, persisting, and removing dynamic api keys`() {
        var savedVault = ApiKeyVaultConfig()
        val repo = org.sparc.studycircle.core.data.SparcRepository(
            onPersistApiVault = { savedVault = it }
        )
        assertEquals("", repo.state.value.apiVault.geminiApiKey)

        repo.updateApiVault(
            ApiKeyVaultConfig(
                geminiApiKey = "AIzaSyTestKey12345",
                bkashMerchantKey = "bkash_test_99"
            )
        )
        assertEquals("AIzaSyTestKey12345", repo.state.value.apiVault.geminiApiKey)
        assertEquals("bkash_test_99", savedVault.bkashMerchantKey)

        // Remove Gemini key while keeping bKash key
        repo.updateApiVault(repo.state.value.apiVault.copy(geminiApiKey = ""))
        assertEquals("", repo.state.value.apiVault.geminiApiKey)
        assertEquals("bkash_test_99", savedVault.bkashMerchantKey)
    }
}
