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
            supabaseClient = org.sparc.studycircle.core.data.SupabaseRestClient("", ""),
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
            supabaseClient = org.sparc.studycircle.core.data.SupabaseRestClient("", ""),
            onPersistAuth = { authed, _, _ -> authFlag = authed }
        )
        assertFalse(repo.state.value.isAuthenticated)

        val shortPass = repo.registerResearcher(
            name = "আরিফুল ইসলাম",
            email = "ariful@du.ac.bd",
            password = "123",
            department = "Sociology",
            skills = listOf("Data Analysis"),
            referralCode = ""
        )
        assertTrue(shortPass.isFailure)

        repo.setAppLanguage("en")
        assertEquals("en", repo.state.value.appLanguage)

        val baseWallet = repo.state.value.currentUser.walletBalanceBdt
        val result = repo.registerResearcher(
            name = "Ariful Islam",
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
        assertTrue(repo.state.value.statusBannerMessage?.contains("Account created") == true)

        repo.logout()
        assertFalse(repo.state.value.isAuthenticated)
        assertFalse(authFlag)
    }

    @Test
    fun `api key vault supports adding, persisting, and removing dynamic api keys`() {
        var savedVault = ApiKeyVaultConfig()
        val repo = org.sparc.studycircle.core.data.SparcRepository(
            supabaseClient = org.sparc.studycircle.core.data.SupabaseRestClient("", ""),
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

    @Test
    fun `repository supports creating and switching live study circles and book orders`() {
        val repo = org.sparc.studycircle.core.data.SparcRepository(
            supabaseClient = org.sparc.studycircle.core.data.SupabaseRestClient("", "")
        )
        val initialCount = repo.state.value.circles.size
        val book = repo.state.value.books.first()

        repo.createNewStudyCircle("IER Cohort-02", book, minMembers = 5)
        assertEquals(initialCount + 1, repo.state.value.circles.size)
        val created = repo.state.value.circles.last()
        assertEquals(created.id, repo.state.value.activeCircleId)

        repo.placePhysicalBookOrder(book, "Purchase", "Nilkhet, Dhaka", "01711000000")
        assertEquals(1, repo.state.value.bookOrders.size)
    }

    @Test
    fun `customization suite supports brand config, accent and reader prefs, rich profile, curriculum quiz builder, and private circle codes`() {
        var persistedBrand: AppCustomizationConfig? = null
        var persistedUi: UserUiCustomization? = null
        val repo = org.sparc.studycircle.core.data.SparcRepository(
            supabaseClient = org.sparc.studycircle.core.data.SupabaseRestClient("", ""),
            onPersistAppCustomization = { persistedBrand = it },
            onPersistUiCustomization = { persistedUi = it }
        )

        // Pack 1: Brand & Payment Config + Dynamic Min Topup
        repo.saveAppCustomization(
            AppCustomizationConfig(
                appTitle = "DU Research Circle",
                bkashMerchantNumber = "01799-888777",
                minTopupBdt = 300.0
            )
        )
        assertEquals("DU Research Circle", repo.state.value.appCustomization.appTitle)
        assertEquals("01799-888777", persistedBrand?.bkashMerchantNumber)
        val walletBefore = repo.state.value.currentUser.walletBalanceBdt
        repo.topUpWallet(300.0, PaymentMethod.BKASH_PGW)
        assertEquals(walletBefore + 300.0, repo.state.value.currentUser.walletBalanceBdt, 0.01)

        // Pack 1, 2 & 5: Accent Preset, Reader Comfort & Launcher Icon
        repo.updateUiCustomization(
            UserUiCustomization(
                accentPreset = SparcAccentPreset.PURPLE,
                readerFontSizeSp = 20,
                readerPaperTheme = ReaderPaperTheme.AMOLED_NIGHT,
                defaultLandingTab = "LIBRARY",
                launcherIconPreset = LauncherIconPreset.GOLD
            )
        )
        assertEquals(SparcAccentPreset.PURPLE, repo.state.value.uiCustomization.accentPreset)
        assertEquals(20, persistedUi?.readerFontSizeSp)
        assertEquals(LauncherIconPreset.GOLD, persistedUi?.launcherIconPreset)

        // Pack 2: Rich Profile Bio, Institution & Daily Goal
        repo.updateProfileAndReferral(
            name = "তানভীর আহমেদ",
            department = "Sociology",
            interests = listOf("Action Research"),
            skills = listOf("Academic Writing", "NVivo"),
            appliedReferralCode = "",
            institution = "University of Dhaka",
            bio = "Qualitative Action Researcher",
            dailyReadingGoalMinutes = 45
        )
        assertEquals("University of Dhaka", repo.state.value.currentUser.institution)
        assertEquals(45, repo.state.value.currentUser.dailyReadingGoalMinutes)
        assertTrue("NVivo" in repo.state.value.currentUser.skills)

        // Pack 3: In-App Chapter Curriculum & Custom Quiz Question Builder
        val book = repo.state.value.books.first()
        repo.saveChapterCurriculum(
            bookId = book.id,
            chapterNo = 3,
            title = "অধ্যায় ৩: ডেটা সংগ্রহ ও ফিল্ড ডায়েরি",
            chapterType = ChapterType.LONG,
            totalPages = 34,
            flashNotes = listOf("ফিল্ড নোট প্রতিদিন লিখতে হবে"),
            learningOutcomes = listOf("ফিল্ড ডায়েরি বিশ্লেষণ"),
            writingPrompt = "৪০০–৬০০ শব্দে আপনার ফিল্ড ডায়েরি প্ল্যান লিখুন।",
            minWords = 400,
            maxWords = 600
        )
        val updatedBook = repo.state.value.books.first { it.id == book.id }
        val ch3 = updatedBook.chapters.find { it.chapterNo == 3 }
        assertNotNull(ch3)
        assertEquals(ChapterType.LONG, ch3!!.type)
        assertEquals(600, ch3.maxWords)

        val qCountBefore = ch3.questions.size
        repo.addCustomChapterQuestion(
            bookId = book.id,
            chapterNo = 3,
            isAutoScored = true,
            sectionCode = "C",
            promptBn = "ফিল্ড নোট কখন লেখা সবচেয়ে কার্যকর?",
            options = listOf("মাঠপর্যায়ের কাজ শেষ হওয়ার সাথে সাথেই", "১ মাস পরে"),
            correctAnswer = "মাঠপর্যায়ের কাজ শেষ হওয়ার সাথে সাথেই",
            explanationBn = "তাৎক্ষণিক নোটে পর্যবেক্ষণের সূক্ষ্মতা বজায় থাকে।",
            modelPoints = emptyList()
        )
        val ch3After = repo.state.value.books.first { it.id == book.id }.chapters.first { it.chapterNo == 3 }
        assertEquals(qCountBefore + 1, ch3After.questions.size)

        // Pack 4: Private Circle Invite Code Enforcement & Rules Customization
        repo.createNewStudyCircle(
            name = "Private PhD Lab",
            book = book,
            minMembers = 4,
            maxMembers = 12,
            isPrivate = true,
            inviteCode = "PHD-2026",
            scheduleLabel = "শনিবার রাত ৯:০০"
        )
        val privCircle = repo.state.value.circles.last()
        assertTrue(privCircle.isPrivate)
        assertEquals("PHD-2026", privCircle.inviteCode)

        // Simulate peercreated private circle that currentUser has not joined yet
        repo.updateStudyCircleCustomization(
            circleId = privCircle.id,
            name = "Private PhD Lab Updated",
            minMembers = 4,
            maxMembers = 15,
            isPrivate = true,
            inviteCode = "SECRET-77",
            scheduleLabel = "রবিবার রাত ৯:৩০",
            meetingUrl = "https://meet.google.com/phd-lab",
            rules = "সপ্তাহে অন্তত ১টি ড্রাফট রিভিউ করতে হবে।",
            currentGroupChapter = 2
        )
        val updatedCircle = repo.state.value.circles.first { it.id == privCircle.id }
        assertEquals("SECRET-77", updatedCircle.inviteCode)
        assertEquals(2, updatedCircle.currentGroupChapter)
    }

    @Test
    fun `exercise template parser, bulk repository import, human evaluation flow, and v3_7 tab labels work end to end`() {
        assertEquals("পাঠ ও অনুশীলন", org.sparc.studycircle.ui.MainTab.ASSESSMENT.label("bn"))
        assertEquals("Read & Practice", org.sparc.studycircle.ui.MainTab.ASSESSMENT.label("en"))
        assertEquals("পাবলিশ", org.sparc.studycircle.ui.MainTab.RESEARCH.label("bn"))
        assertEquals("Publish", org.sparc.studycircle.ui.MainTab.RESEARCH.label("en"))

        val sampleBn = ExerciseTemplateParser.generateSampleTemplate("bn")
        val parsed = ExerciseTemplateParser.parse(sampleBn).getOrThrow()
        assertEquals(3, parsed.questions.size)
        assertEquals(2, parsed.flashNotes.size)
        assertTrue(parsed.questions[0].isAutoScored)
        assertFalse(parsed.questions[2].isAutoScored)

        val csvSample = """
            TYPE,SECTION,PROMPT,OPT1,OPT2,OPT3,CORRECT,EXPLANATION
            MCQ,C,গুণগত গবেষণার প্রধান শক্তি কোনটি?,গভীর প্রেক্ষাপট,শুধু সংখ্যা,কোনোটিই নয়,গভীর প্রেক্ষাপট,প্রেক্ষাপট বিশ্লেষণ মূল শক্তি
        """.trimIndent()
        val parsedCsv = ExerciseTemplateParser.parse(csvSample).getOrThrow()
        assertEquals(1, parsedCsv.questions.size)
        assertEquals("গভীর প্রেক্ষাপট", parsedCsv.questions.first().correctAnswer)

        val repo = org.sparc.studycircle.core.data.SparcRepository(
            supabaseClient = org.sparc.studycircle.core.data.SupabaseRestClient("", "")
        )
        val book = repo.state.value.books.first()
        val importRes = repo.importExerciseTemplate(
            bookId = book.id,
            overrideChapterNo = 4,
            rawTemplate = sampleBn,
            replaceExistingQuestions = true
        )
        assertTrue(importRes.isSuccess)
        val ch4 = repo.state.value.books.first { it.id == book.id }.chapters.find { it.chapterNo == 4 }
        assertNotNull(ch4)
        assertEquals(3, ch4!!.questions.size)

        val longDraft = (1..25).joinToString(" ") { "একাডেমিক" }
        repo.submitWritingForHumanEvaluation(ch4, longDraft)
        assertTrue(repo.state.value.statusBannerMessage?.contains("Evaluate") == true)
    }

    @Test
    fun `v3_8 two-tier role system separates Circle Lead from Mentor, awards Host +50 pts, pairs Peer Buddy, and enforces Admin rules`() {
        val repo = org.sparc.studycircle.core.data.SparcRepository(
            supabaseClient = org.sparc.studycircle.core.data.SupabaseRestClient("", "")
        )

        // 1. Default user is Researcher (MEMBER)
        assertEquals(UserRole.MEMBER, repo.state.value.currentUser.role)
        assertEquals("গবেষক", UserRole.MEMBER.shortLabel("bn"))
        assertEquals("Researcher", UserRole.MEMBER.shortLabel("en"))
        assertEquals("মেন্টর", UserRole.SUPERVISOR.shortLabel("bn"))
        assertEquals("সার্কেল লিড", UserRole.MODERATOR.shortLabel("bn"))

        // 2. Creating a study circle awards +50 Host points & Circle Initiator badge
        val beforePts = repo.state.value.currentUser.gamificationPoints
        val book = repo.state.value.books.first()
        repo.createNewStudyCircle("Urban Policy Cohort", book, minMembers = 6)
        assertEquals(beforePts + 50, repo.state.value.currentUser.gamificationPoints)
        assertTrue("Circle Initiator" in repo.state.value.currentUser.earnedBadges)
        val newCircle = repo.state.value.circles.last()
        assertEquals(repo.state.value.currentUser.id, newCircle.initiatorId)

        // 3. Voting elects Circle Lead WITHOUT overwriting SPARC-assigned Mentor (supervisorId)
        val seededCircle = repo.state.value.circles.first()
        assertEquals("u-sup-1", seededCircle.supervisorId)
        repo.voteForCircleLead(seededCircle.id, "u-3")
        val afterVote = repo.state.value.circles.first()
        assertEquals("u-sup-1", afterVote.supervisorId)
        assertEquals("u-3", afterVote.circleLeadVotes[repo.state.value.currentUser.id])

        // 4. Non-Admin cannot change minMembersRequired on an existing circle
        repo.updateStudyCircleCustomization(
            circleId = seededCircle.id,
            name = seededCircle.name,
            minMembers = 15,
            maxMembers = 25,
            isPrivate = false,
            inviteCode = null,
            scheduleLabel = seededCircle.sessionScheduleLabel,
            meetingUrl = seededCircle.defaultMeetingUrl,
            rules = seededCircle.circleRules,
            currentGroupChapter = 1
        )
        assertEquals(5, repo.state.value.circles.first().minMembersRequired)

        // 5. Switch to Mentor -> grade open question (0-3) & assign Peer Buddy
        repo.switchMyRolePreview(UserRole.SUPERVISOR)
        assertEquals(UserRole.SUPERVISOR, repo.state.value.currentUser.role)
        val ch1 = book.chapters.first()
        repo.mentorEvaluateAndSupport(
            circleId = seededCircle.id,
            targetUserId = "u-5",
            chapter = ch1,
            openScores = mapOf("q3" to 3),
            peerBuddyId = "u-2",
            manualUnlock = true
        )
        val u5Prog = repo.state.value.circleMemberProgress.first { it.userId == "u-5" && it.chapterNo == 1 }
        assertEquals("u-2", u5Prog.peerBuddyId)
        assertEquals(3, u5Prog.supervisorScore)

        // 6. Switch to Admin -> assign Mentor, minMembers & promote user role
        repo.switchMyRolePreview(UserRole.ADMIN)
        repo.adminAssignUserRole("u-2", UserRole.MODERATOR)
        assertEquals(UserRole.MODERATOR, repo.state.value.directoryMembers.first { it.id == "u-2" }.role)
        repo.adminAssignCircleMentorAndMinMembers(seededCircle.id, mentorId = "u-sup-1", minMembers = 12)
        assertEquals(12, repo.state.value.circles.first().minMembersRequired)

        // 7. Resolve dynamic ActivityRoleBadges accurately
        val sess = repo.state.value.sessions.first()
        val farhan = repo.state.value.directoryMembers.first { it.id == "u-2" }
        repo.assignSessionPresenter(sess.id, farhan)
        val farhanBadges = SparcDomainEngine.resolveUserActivityBadges(
            userId = "u-2",
            earnedBadges = farhan.earnedBadges,
            activeCircle = repo.state.value.circles.first(),
            sessions = repo.state.value.sessions,
            coAuthorUserIds = repo.state.value.coAuthorUserIds,
            peerBuddyUserIds = setOf("u-2")
        )
        assertTrue(ActivityRoleBadge.CIRCLE_LEAD in farhanBadges)
        assertTrue(ActivityRoleBadge.CHAPTER_PRESENTER in farhanBadges)
        assertTrue(ActivityRoleBadge.CO_AUTHOR in farhanBadges)
        assertTrue(ActivityRoleBadge.PEER_BUDDY in farhanBadges)
    }
}
