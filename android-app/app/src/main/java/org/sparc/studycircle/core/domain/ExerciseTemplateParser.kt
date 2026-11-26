package org.sparc.studycircle.core.domain

import kotlinx.serialization.json.*
import org.sparc.studycircle.core.model.ChapterType

data class ParsedQuestionDraft(
    val isAutoScored: Boolean,
    val sectionCode: String,
    val promptBn: String,
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val explanationBn: String = "",
    val modelPoints: List<String> = emptyList()
)

data class ParsedExerciseTemplate(
    val chapterNo: Int = 1,
    val chapterTitle: String = "",
    val chapterType: ChapterType = ChapterType.STANDARD,
    val totalPages: Int = 24,
    val flashNotes: List<String> = emptyList(),
    val learningOutcomes: List<String> = emptyList(),
    val writingPrompt: String = "",
    val minWords: Int = 400,
    val maxWords: Int = 600,
    val questions: List<ParsedQuestionDraft> = emptyList()
)

object ExerciseTemplateParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun generateSampleTemplate(lang: String = "bn"): String = if (lang == "en") {
        """# SPARC Standard Exercise Template (.txt / .md)
# Upload any Open-Access / Permitted Author PDF and paste or upload this template!

CHAPTER_NO: 1
CHAPTER_TITLE: Chapter 1: Research Problem & Theoretical Framing
CHAPTER_TYPE: STANDARD
TOTAL_PAGES: 22
FLASH_NOTE: A strong research question must be focused, arguable, and grounded in context.
FLASH_NOTE: Literature reviews synthesize scholarly gaps rather than merely listing summaries.
OUTCOME: Formulate a context-specific research problem statement
OUTCOME: Distinguish theoretical frameworks from empirical methods
WRITING_PROMPT: Write a 400-600 word analytical reflection identifying a concrete research gap in your field based on this paper.
WORD_LIMIT: 400-600

[MCQ | C]
Q: What is the primary purpose of a scholarly literature review?
OPT: Listing every book ever written on a broad topic
OPT*: Synthesizing existing debates to identify a clear research gap
OPT: Summarizing author biographies
EXP: Identifying a research gap anchors the contribution of your study.

[MCQ | D]
Q: Qualitative inquiry prioritizes contextual depth over purely numerical generalization.
OPT*: True
OPT: False
EXP: Qualitative designs explore lived practices, meanings, and mechanisms in depth.

[OPEN | H]
Q: How would you design an ethical preliminary field observation in your department based on this reading?
POINT: 1 pt: Clearly define the site and insider participants
POINT: 1 pt: Specify observation focus and note-taking protocol
POINT: 1 pt: Address informed consent and participant privacy
EXP: Evaluated 0-3 by your human Study Circle Supervisor."""
    } else {
        """# SPARC নির্ধারিত অনুশীলনী টেমপ্লেট (.txt / .md)
# অন্য লেখক বা গবেষকের PDF প্রবন্ধের আলোকে নিচের ছকে ফ্ল্যাশ নোট ও অনুশীলনী সাজান:

CHAPTER_NO: 1
CHAPTER_TITLE: অধ্যায় ১: গবেষণা প্রবন্ধের মূল কাঠামো ও সমস্যা নির্ণয়
CHAPTER_TYPE: STANDARD
TOTAL_PAGES: 22
FLASH_NOTE: গবেষণা প্রশ্ন সবসময় সুনির্দিষ্ট, প্রাসঙ্গিক ও বাস্তবসম্মত হতে হবে।
FLASH_NOTE: লিটারেচার রিভিউ মানে শুধু সারসংক্ষেপ নয়; পূর্ববর্তী গবেষণার শূন্যস্থান (Research Gap) চিহ্নিত করা।
OUTCOME: গবেষণা সমস্যা ও উদ্দেশ্য সুস্পষ্টভাবে আলাদা করতে পারা
OUTCOME: প্রবন্ধের মূল তাত্ত্বিক কাঠামো নিজের বিষয়ে প্রয়োগ করতে পারা
WRITING_PROMPT: এই প্রবন্ধের আলোকে ৪০০–৬০০ শব্দে আপনার নির্বাচিত বিষয়ের Research Gap ও প্রেক্ষাপট লিখুন (মানব মেন্টর মূল্যায়ন করবেন)।
WORD_LIMIT: 400-600

[MCQ | C]
Q: একাডেমিক লিটারেচার রিভিউয়ের প্রধান উদ্দেশ্য কোনটি?
OPT: শুধু বই ও প্রবন্ধের লম্বা তালিকা তৈরি করা
OPT*: পূর্ববর্তী গবেষণার বিতর্ক বিশ্লেষণ করে Research Gap চিহ্নিত করা
OPT: লেখকদের ব্যক্তিগত জীবনী আলোচনা করা
EXP: Research Gap চিহ্নিত করাই নতুন গবেষণার যৌক্তিক ভিত্তি তৈরি করে।

[MCQ | D]
Q: গুণগত গবেষণায় (Qualitative Research) স্থানীয় প্রেক্ষাপট ও অংশগ্রহণকারীদের অভিজ্ঞতার গভীরতাকে গুরুত্ব দেওয়া হয়।
OPT*: সত্য (True)
OPT: মিথ্যা (False)
EXP: গুণগত গবেষণা মানুষের আচরণ ও সামাজিক প্রেক্ষাপটের গভীর অর্থ অনুসন্ধান করে।

[OPEN | H]
Q: এই প্রবন্ধের পদ্ধতির আলোকে আপনার বিভাগে একটি প্রাথমিক ফিল্ড পর্যবেক্ষণ কীভাবে পরিকল্পনা করবেন?
POINT: ১ পয়েন্ট: গবেষণা ক্ষেত্র (Site) ও অংশগ্রহণকারী চিহ্নিত করা
POINT: ১ পয়েন্ট: পর্যবেক্ষণ ও ফিল্ড নোট নেওয়ার নিয়ম উল্লেখ করা
POINT: ১ পয়েন্ট: গবেষণার নৈতিক সম্মতি (Informed Consent) নিশ্চিত করা
EXP: সুপারভাইজার / মানব মেন্টর উপরের ৩টি পয়েন্টের ভিত্তিতে ০–৩ নম্বর দেবেন।"""
    }

    fun generateAiPrompt(lang: String = "bn", chapterNo: Int = 1, bookTitle: String = ""): String {
        val titleHint = if (bookTitle.isNotBlank()) " ($bookTitle)" else ""
        return if (lang == "en") {
            """You are an Academic Curriculum Designer for SPARC Study Circle. Read the attached PDF chapter$titleHint carefully and output exercises STRICTLY in the exact format below (no extra commentary outside the format):

CHAPTER_NO: $chapterNo
CHAPTER_TITLE: Chapter $chapterNo: [Concise Chapter Title]
CHAPTER_TYPE: STANDARD
TOTAL_PAGES: [Page Count]
FLASH_NOTE: [Key takeaway 1]
FLASH_NOTE: [Key takeaway 2]
FLASH_NOTE: [Key takeaway 3]
OUTCOME: [Learning outcome 1]
OUTCOME: [Learning outcome 2]
WRITING_PROMPT: [Analytical 400-600 word writing task prompt for human mentor evaluation]
WORD_LIMIT: 400-600

# Generate 6-10 MCQs across sections A..E (mark the single correct option with OPT*)
[MCQ | C]
Q: [Question text]
OPT: [Wrong option]
OPT*: [Correct option]
OPT: [Wrong option]
OPT: [Wrong option]
EXP: [1-sentence explanation]

# Generate 2-4 Open-Ended analytical questions across sections F..J (0-3 rubric points)
[OPEN | H]
Q: [Scenario or analytical question]
POINT: 1 pt: [Criterion 1]
POINT: 1 pt: [Criterion 2]
POINT: 1 pt: [Criterion 3]
EXP: Evaluated 0-3 by human Study Circle Mentor."""
        } else {
            """আপনি SPARC Study Circle-এর একাডেমিক কারিকুলাম ডিজাইনার। সংযুক্ত PDF অধ্যায়টি$titleHint মনোযোগ দিয়ে পড়ে ঠিক নিচের ফরম্যাটে (অতিরিক্ত কোনো ভূমিকা ছাড়া হুবহু এই ট্যাগগুলোতে) বাংলায় ফ্ল্যাশ নোট, রাইটিং টাস্ক এবং অনুশীলনী তৈরি করে দিন:

CHAPTER_NO: $chapterNo
CHAPTER_TITLE: অধ্যায় $chapterNo: [অধ্যায়ের সংক্ষিপ্ত শিরোনাম]
CHAPTER_TYPE: STANDARD
TOTAL_PAGES: [পৃষ্ঠা সংখ্যা]
FLASH_NOTE: [মূল পয়েন্ট ১]
FLASH_NOTE: [মূল পয়েন্ট ২]
FLASH_NOTE: [মূল পয়েন্ট ৩]
OUTCOME: [শিখনফল ১]
OUTCOME: [শিখনফল ২]
WRITING_PROMPT: [এই অধ্যায়ের আলোকে ৪০০–৬০০ শব্দের বিশ্লেষণমূলক রাইটিং টাস্ক প্রম্পট, যা মানব মেন্টর মূল্যায়ন করবেন]
WORD_LIMIT: 400-600

# ৬–১০টি বহুনির্বাচনী প্রশ্ন (A..E সেকশন, সঠিক উত্তরের আগে OPT* দিন):
[MCQ | C]
Q: [প্রশ্ন]
OPT: [ভুল অপশন]
OPT*: [সঠিক অপশন]
OPT: [ভুল অপশন]
OPT: [ভুল অপশন]
EXP: [১ বাক্যে ব্যাখ্যা]

# ২–৪টি সৃজনশীল/বিশ্লেষণমূলক প্রশ্ন (F..J সেকশন, মেন্টরের জন্য ৩টি পয়েন্ট রুব্রিকসহ):
[OPEN | H]
Q: [দৃশ্যপট বা বিশ্লেষণমূলক প্রশ্ন]
POINT: ১ পয়েন্ট: [মানদণ্ড ১]
POINT: ১ পয়েন্ট: [মানদণ্ড ২]
POINT: ১ পয়েন্ট: [মানদণ্ড ৩]
EXP: মানব মেন্টর উপরের ৩টি পয়েন্টের ভিত্তিতে ০–৩ নম্বর দেবেন।"""
        }
    }

    fun parse(rawInput: String): Result<ParsedExerciseTemplate> = runCatching {
        val trimmed = rawInput.trim()
        require(trimmed.isNotBlank()) { "Template content is empty" }

        when {
            trimmed.startsWith("{") -> parseJsonTemplate(trimmed)
            isLikelyCsv(trimmed) -> parseCsvTemplate(trimmed)
            else -> parseStructuredTextTemplate(trimmed)
        }
    }

    private fun isLikelyCsv(text: String): Boolean {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") } ?: return false
        return firstLine.contains(",") &&
            (firstLine.uppercase().startsWith("TYPE,") || firstLine.uppercase().startsWith("MCQ,") || firstLine.uppercase().startsWith("OPEN,"))
    }

    private fun parseJsonTemplate(raw: String): ParsedExerciseTemplate {
        val root = json.parseToJsonElement(raw).jsonObject
        val chNo = root["chapter_no"]?.jsonPrimitive?.intOrNull ?: 1
        val title = root["chapter_title"]?.jsonPrimitive?.contentOrNull
            ?: root["title"]?.jsonPrimitive?.contentOrNull ?: ""
        val typeStr = root["chapter_type"]?.jsonPrimitive?.contentOrNull ?: "STANDARD"
        val pages = root["total_pages"]?.jsonPrimitive?.intOrNull ?: 24
        val flash = root["flash_notes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val outcomes = root["learning_outcomes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val wPrompt = root["writing_prompt"]?.jsonPrimitive?.contentOrNull ?: ""
        val minW = root["min_words"]?.jsonPrimitive?.intOrNull ?: 400
        val maxW = root["max_words"]?.jsonPrimitive?.intOrNull ?: 600

        val qArr = root["questions"]?.jsonArray ?: JsonArray(emptyList())
        val parsedQs = qArr.mapNotNull { el ->
            val o = el.jsonObject
            val prompt = o["prompt"]?.jsonPrimitive?.contentOrNull
                ?: o["prompt_bn"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val isAuto = o["is_auto_scored"]?.jsonPrimitive?.booleanOrNull
                ?: ((o["type"]?.jsonPrimitive?.contentOrNull ?: "MCQ").uppercase() != "OPEN")
            val sec = o["section"]?.jsonPrimitive?.contentOrNull ?: if (isAuto) "C" else "H"
            val opts = o["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val ans = o["correct_answer"]?.jsonPrimitive?.contentOrNull ?: opts.firstOrNull().orEmpty()
            val exp = o["explanation"]?.jsonPrimitive?.contentOrNull ?: ""
            val pts = o["model_points"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            ParsedQuestionDraft(
                isAutoScored = isAuto,
                sectionCode = sec,
                promptBn = prompt,
                options = opts,
                correctAnswer = ans,
                explanationBn = exp,
                modelPoints = pts
            )
        }

        return ParsedExerciseTemplate(
            chapterNo = chNo.coerceAtLeast(1),
            chapterTitle = title,
            chapterType = if (typeStr.equals("LONG", true)) ChapterType.LONG else ChapterType.STANDARD,
            totalPages = pages.coerceAtLeast(1),
            flashNotes = flash,
            learningOutcomes = outcomes,
            writingPrompt = wPrompt,
            minWords = minW,
            maxWords = maxW,
            questions = parsedQs
        )
    }

    private fun parseCsvTemplate(raw: String): ParsedExerciseTemplate {
        val questions = mutableListOf<ParsedQuestionDraft>()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEachIndexed { idx, line ->
                val cols = line.split(",").map { it.trim().removeSurrounding("\"") }
                if (idx == 0 && cols.firstOrNull().equals("TYPE", ignoreCase = true)) return@forEachIndexed
                if (cols.size >= 3) {
                    val qType = cols[0].uppercase()
                    val sec = cols[1].ifBlank { if (qType == "OPEN") "H" else "C" }
                    val prompt = cols[2]
                    if (qType == "OPEN") {
                        val pts = cols.drop(3).take(3).filter { it.isNotBlank() }
                        val exp = cols.getOrNull(6) ?: ""
                        questions += ParsedQuestionDraft(
                            isAutoScored = false,
                            sectionCode = sec,
                            promptBn = prompt,
                            explanationBn = exp,
                            modelPoints = pts
                        )
                    } else {
                        val opts = listOfNotNull(cols.getOrNull(3), cols.getOrNull(4), cols.getOrNull(5))
                            .filter { it.isNotBlank() }
                        val ans = cols.getOrNull(6)?.takeIf { it.isNotBlank() } ?: opts.firstOrNull().orEmpty()
                        val exp = cols.getOrNull(7) ?: ""
                        questions += ParsedQuestionDraft(
                            isAutoScored = true,
                            sectionCode = sec,
                            promptBn = prompt,
                            options = opts,
                            correctAnswer = ans,
                            explanationBn = exp
                        )
                    }
                }
            }
        require(questions.isNotEmpty()) { "No valid CSV question rows found" }
        return ParsedExerciseTemplate(questions = questions)
    }

    private fun parseStructuredTextTemplate(raw: String): ParsedExerciseTemplate {
        var chNo = 1
        var chTitle = ""
        var chType = ChapterType.STANDARD
        var totalPages = 24
        val flashNotes = mutableListOf<String>()
        val outcomes = mutableListOf<String>()
        var writingPrompt = ""
        var minWords = 400
        var maxWords = 600
        val questions = mutableListOf<ParsedQuestionDraft>()

        var currentBlockIsAuto: Boolean? = null
        var currentSection = "C"
        var currentPrompt = ""
        var currentOptions = mutableListOf<String>()
        var currentCorrect = ""
        var currentExp = ""
        var currentPoints = mutableListOf<String>()

        fun flushQuestion() {
            val auto = currentBlockIsAuto ?: return
            if (currentPrompt.isNotBlank()) {
                val resolvedAns = currentCorrect.ifBlank { currentOptions.firstOrNull().orEmpty() }
                questions += ParsedQuestionDraft(
                    isAutoScored = auto,
                    sectionCode = currentSection,
                    promptBn = currentPrompt.trim(),
                    options = if (auto) currentOptions.toList() else emptyList(),
                    correctAnswer = if (auto) resolvedAns else "",
                    explanationBn = currentExp.trim(),
                    modelPoints = if (!auto) currentPoints.toList() else emptyList()
                )
            }
            currentBlockIsAuto = null
            currentPrompt = ""
            currentOptions = mutableListOf()
            currentCorrect = ""
            currentExp = ""
            currentPoints = mutableListOf()
        }

        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                flushQuestion()
                val inner = line.removeSurrounding("[", "]")
                val parts = inner.split("|").map { it.trim() }
                val kind = parts.firstOrNull()?.uppercase() ?: "MCQ"
                currentBlockIsAuto = kind != "OPEN" && kind != "CREATIVE" && kind != "SCENARIO"
                currentSection = parts.getOrNull(1)?.uppercase()?.take(1)
                    ?: if (currentBlockIsAuto == true) "C" else "H"
                return@forEach
            }

            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) return@forEach
            val key = line.substring(0, colonIdx).trim().uppercase()
            val value = line.substring(colonIdx + 1).trim()
            if (value.isBlank()) return@forEach

            if (currentBlockIsAuto != null) {
                when (key) {
                    "Q", "QUESTION", "PROMPT" -> currentPrompt = value
                    "OPT*" -> {
                        currentOptions += value
                        currentCorrect = value
                    }
                    "OPT", "OPTION" -> currentOptions += value
                    "ANS", "CORRECT" -> currentCorrect = value
                    "EXP", "EXPLANATION" -> currentExp = value
                    "POINT", "MODEL_POINT" -> currentPoints += value
                }
            } else {
                when (key) {
                    "CHAPTER_NO" -> chNo = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    "CHAPTER_TITLE", "TITLE" -> chTitle = value
                    "CHAPTER_TYPE", "TYPE" -> chType = if (value.equals("LONG", true)) ChapterType.LONG else ChapterType.STANDARD
                    "TOTAL_PAGES", "PAGES" -> totalPages = value.toIntOrNull()?.coerceAtLeast(1) ?: 24
                    "FLASH_NOTE", "NOTE" -> flashNotes += value
                    "OUTCOME", "LEARNING_OUTCOME" -> outcomes += value
                    "WRITING_PROMPT" -> writingPrompt = value
                    "WORD_LIMIT" -> {
                        val nums = value.split("-", "–", " ").mapNotNull { it.trim().toIntOrNull() }
                        if (nums.size >= 2) {
                            minWords = nums[0]
                            maxWords = nums[1]
                        }
                    }
                }
            }
        }
        flushQuestion()

        require(
            chTitle.isNotBlank() || flashNotes.isNotEmpty() || questions.isNotEmpty() || writingPrompt.isNotBlank()
        ) {
            "টেমপ্লেটে কোনো অধ্যায় তথ্য বা প্রশ্ন পাওয়া যায়নি (No valid template fields or questions found)."
        }

        return ParsedExerciseTemplate(
            chapterNo = chNo,
            chapterTitle = chTitle,
            chapterType = chType,
            totalPages = totalPages,
            flashNotes = flashNotes,
            learningOutcomes = outcomes,
            writingPrompt = writingPrompt,
            minWords = minWords,
            maxWords = maxWords,
            questions = questions
        )
    }
}
