package org.sparc.studycircle.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.sparc.studycircle.core.model.SparcVisualTheme

data class SparcPalette(
    val theme: SparcVisualTheme,
    val isDark: Boolean,
    val useHeroHeader: Boolean,
    val bg: Color,
    val readerPaper: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val primaryEnd: Color,
    val primarySoft: Color,
    val primarySoftText: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val dockBg: Color,
    val dockBorder: Color,
    val dockInactive: Color
) {
    val primaryGradient: Brush
        get() = Brush.linearGradient(listOf(primary, primaryEnd))
}

val CobaltSaasPalette = SparcPalette(
    theme = SparcVisualTheme.COBALT_SAAS,
    isDark = false,
    useHeroHeader = false,
    bg = Color(0xFFF6F8FC),
    readerPaper = Color(0xFFFCFBF8),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF1F5F9),
    border = Color(0xFFE2E8F0),
    text = Color(0xFF0F172A),
    muted = Color(0xFF64748B),
    primary = Color(0xFF2563EB),
    primaryEnd = Color(0xFF1D4ED8),
    primarySoft = Color(0xFFDBEAFE),
    primarySoftText = Color(0xFF1E40AF),
    accent = Color(0xFFF59E0B),
    accentSoft = Color(0xFFFEF3C7),
    accentText = Color(0xFF92400E),
    dockBg = Color(0xF5FFFFFF),
    dockBorder = Color(0xFFE2E8F0),
    dockInactive = Color(0xFF64748B)
)

val MidnightGlassPalette = SparcPalette(
    theme = SparcVisualTheme.MIDNIGHT_GLASS,
    isDark = true,
    useHeroHeader = false,
    bg = Color(0xFF090D16),
    readerPaper = Color(0xFF0E1525),
    card = Color(0xFF111827),
    cardAlt = Color(0xFF1E293B),
    border = Color(0xFF1F2937),
    text = Color(0xFFF8FAFC),
    muted = Color(0xFF94A3B8),
    primary = Color(0xFF10B981),
    primaryEnd = Color(0xFF059669),
    primarySoft = Color(0xFF064E3B),
    primarySoftText = Color(0xFF34D399),
    accent = Color(0xFFF97316),
    accentSoft = Color(0xFF431407),
    accentText = Color(0xFFFB923C),
    dockBg = Color(0xEB111827),
    dockBorder = Color(0xFF334155),
    dockInactive = Color(0xFF94A3B8)
)

val OceanHeroPalette = SparcPalette(
    theme = SparcVisualTheme.OCEAN_HERO,
    isDark = false,
    useHeroHeader = true,
    bg = Color(0xFFF2F6F9),
    readerPaper = Color(0xFFFAF9F5),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFE6F4F1),
    border = Color(0xFFDCE6EE),
    text = Color(0xFF0F172A),
    muted = Color(0xFF5B6B7F),
    primary = Color(0xFF0D9488),
    primaryEnd = Color(0xFF0284C7),
    primarySoft = Color(0xFFCCFBF1),
    primarySoftText = Color(0xFF115E59),
    accent = Color(0xFFF43F5E),
    accentSoft = Color(0xFFFFE4E6),
    accentText = Color(0xFFBE123C),
    dockBg = Color(0xEB0F172A),
    dockBorder = Color(0xFF1E293B),
    dockInactive = Color(0xFF94A3B8)
)

fun paletteFor(
    theme: SparcVisualTheme,
    accentPreset: org.sparc.studycircle.core.model.SparcAccentPreset? = null
): SparcPalette {
    val base = when (theme) {
        SparcVisualTheme.COBALT_SAAS -> CobaltSaasPalette
        SparcVisualTheme.MIDNIGHT_GLASS -> MidnightGlassPalette
        SparcVisualTheme.OCEAN_HERO -> OceanHeroPalette
    }
    if (accentPreset == null) return base
    return base.copy(
        primary = Color(accentPreset.primaryHex),
        primaryEnd = Color(accentPreset.primaryEndHex),
        primarySoft = Color(if (base.isDark) accentPreset.darkSoftHex else accentPreset.lightSoftHex),
        primarySoftText = Color(if (base.isDark) accentPreset.darkSoftTextHex else accentPreset.lightSoftTextHex)
    )
}

val LocalSparcPalette = staticCompositionLocalOf { CobaltSaasPalette }
val LocalAppLanguage = staticCompositionLocalOf { "bn" }

@Composable
fun tr(bn: String, en: String): String =
    if (LocalAppLanguage.current == "en") en else bn

private val SEED_EN_TRANSLATIONS = mapOf(
    "তানভীর আহমেদ" to "Tanvir Ahmed",
    "ড. নুসরাত জাহান" to "Dr. Nusrat Jahan",
    "ফারহান সাদিক" to "Farhan Sadiq",
    "মালিহা তাবাসসুম" to "Maliha Tabassum",
    "রাফিউজ্জামান খান" to "Rafiuzzaman Khan",
    "সাদিয়া ইসলাম" to "Sadia Islam",
    "তানভীর (Live)" to "Tanvir (Live)",
    "ড. নুসরাত জাহান ও SPARC প্যানেল" to "Dr. Nusrat Jahan & SPARC Panel",
    "CPAR কোহর্ট-০১ (সমাজবিজ্ঞান ও শিক্ষা গবেষণা)" to "CPAR Cohort-01 (Sociology & Education Research)",
    "অধ্যায় ১: CPAR-এর পরিচয় ও Practice Architectures" to "Chapter 1: Intro to CPAR & Practice Architectures",
    "অধ্যায় ২: Reconnaissance ও Communicative Space গঠন" to "Chapter 2: Reconnaissance & Communicative Space",
    "অধ্যায় ১: গবেষণা অ্যাপ্রোচ নির্বাচন ও দার্শনিক ভিত্তি" to "Chapter 1: Research Approaches & Philosophical Worldviews",
    "বাংলাদেশের বিশ্ববিদ্যালয়ে CPAR বাস্তবায়নে সবচেয়ে বড় বাধা কোনটি বলে মনে করেন?" to "What is the biggest barrier to implementing CPAR in universities?",
    "শিক্ষক-শিক্ষার্থী ক্ষমতার দূরত্ব (Relatings)" to "Teacher-student power distance (Relatings)",
    "সময় ও ফান্ডিং সীমাবদ্ধতা (Doings)" to "Time & funding constraints (Doings)",
    "গবেষণা ভীতি ও প্রচলিত মুখস্থ সংস্কৃতি (Sayings)" to "Research anxiety & rote culture (Sayings)",
    "চলুন একসাথে অধ্যায়টি শেষ করি।" to "Let's finish this chapter together!",
    "এগিয়ে চলুন!" to "Keep going!",
    "পড়া কেমন চলছে?" to "How is your reading going?",
    "আমরা আপনার অপেক্ষায়!" to "We are cheering for you!",
    "আপনি পারবেন!" to "You can do it!",
    "ধন্যবাদ!" to "Thank you!",
    "করছি!" to "Working on it!",
    "আমি দ্রুত পড়া শেষ করতে চাই। আপনিও জয়েন করুন এই বইয়ের গ্রুপে।" to "I want to finish reading consistently — join this book's study circle with me!",
    "আমাদের রিসার্চ ইন্টারেস্ট এক! চলুন একসাথে CPAR স্টাডি সার্কেলে যুক্ত হই।" to "We share the same research interests! Let's join the CPAR Study Circle together.",
    "এই বইয়ের ১ম অধ্যায় ফ্রি—চলুন আজই স্টাডি সার্কেল শুরু করি।" to "Chapter 1 of this book is free — let's start a study circle today!",
    "সময় নিন — আপনার দল আপনার সঙ্গে আছে" to "Take your time — your study circle is right here with you",
    "CPAR-এ গবেষক বাইরে থেকে দর্শক নন; অংশগ্রহণকারীরাই নিজেদের প্র্যাকটিস রূপান্তর করেন।" to "In CPAR, researchers are not external spectators; practitioners transform their own practices.",
    "Practice Architectures তিনটি মাত্রায় গঠিত: Sayings (ভাষা/চিন্তা), Doings (কাজ/উপকরণ), Relatings (ক্ষমতা/সম্পর্ক)।" to "Practice Architectures comprise three dimensions: Sayings (discourse), Doings (activities), and Relatings (power/solidarity).",
    "Communicative Space এমন একটি উন্মুক্ত পরিসর যেখানে সবাই নির্ভয়ে সমালোচনামূলক মতামত দিতে পারেন।" to "Communicative Space is an open public sphere where everyone can share critical perspectives without fear.",
    "Kemmis et al.-এর মতে প্র্যাকটিসকে ধরে রাখে এমন তিনটি মাত্রা কোনটি?" to "According to Kemmis et al., which three dimensions hold a practice together?",
    "Kemmis et al.-এর মতে প্র্যাকটিসকে ধরে রাখে এমন তিনটি আন্তঃসম্পর্কিত মাত্রা কী কী?" to "According to Kemmis et al., what are the three interrelated dimensions that compose a practice?",
    "সাংস্কৃতিক-ডিসকার্সিভ (Sayings), বস্তুগত-অর্থনৈতিক (Doings) এবং সামাজিক-রাজনৈতিক (Relatings) একত্রে প্র্যাকটিস আর্কিটেকচার গঠন করে।" to "Cultural-discursive (Sayings), material-economic (Doings), and social-political (Relatings) arrangements form Practice Architectures.",
    "প্র্যাকটিস আর্কিটেকচার তত্ত্বে সাংস্কৃতিক-ডিসকার্সিভ (Sayings), বস্তুগত-অর্থনৈতিক (Doings) এবং সামাজিক-রাজনৈতিক (Relatings) মাত্রা একত্রে প্র্যাকটিস গঠন করে।" to "Practice architectures hold practices in place through sayings, doings, and relatings.",
    "বইয়ের ১ম অধ্যায় অনুযায়ী, CPAR শুধুমাত্র বহিরাগত বিশেষজ্ঞদের দ্বারা পরিচালিত হয়।" to "According to Chapter 1, CPAR is conducted exclusively by external experts.",
    "বইয়ের ১ম অধ্যায়ের উদাহরণ অনুযায়ী, CPAR শুধুমাত্র বহিরাগত বিশেষজ্ঞদের দ্বারা পরিচালিত হয় এবং স্থানীয় অংশগ্রহণকারীদের কোনো ভূমিকা থাকে না।" to "According to Chapter 1, CPAR is conducted exclusively by external experts with no role for local participants.",
    "CPAR-এ স্থানীয় অংশগ্রহণকারীরাই (Insiders) যৌথভাবে গবেষণা পরিচালনা করেন।" to "In CPAR, local insider participants collaboratively conduct the inquiry.",
    "CPAR-এ খোদ অংশগ্রহণকারীরাই (Insiders) নিজেদের প্র্যাকটিস নিয়ে যৌথভাবে গবেষণা ও পরিবর্তন সাধন করেন।" to "In CPAR, insiders/participants themselves research and transform their shared practices.",
    "সত্য (True)" to "True",
    "মিথ্যা (False)" to "False",
    "একটি বিভাগে শিক্ষার্থীদের সেমিনারে অংশগ্রহণ কমছে। Sayings, Doings ও Relatings ব্যবহার করে কীভাবে সমস্যাটি বিশ্লেষণ করবেন?" to "Seminar attendance is dropping in a department. How would you analyze this using Sayings, Doings, and Relatings?",
    "ধরুন একটি বিশ্ববিদ্যালয়ের বিভাগে শিক্ষার্থীদের উপস্থিতি কমছে। CPAR পদ্ধতিতে Sayings, Doings ও Relatings ব্যবহার করে কীভাবে সমস্যাটির প্রাথমিক অনুসন্ধান করবেন?" to "Suppose attendance is dropping in a university department. How would you investigate this using Sayings, Doings, and Relatings in CPAR?",
    "সুপারভাইজার নিচের মডেল পয়েন্টগুলোর ভিত্তিতে ০–৩ নম্বর প্রদান করবেন।" to "Graded 0–3 by Supervisor against model points.",
    "১ নম্বর: সেমিনার নিয়ে প্রচলিত ধারণা বা ভাষা (Sayings) চিহ্নিত করা" to "1 pt: Identify prevailing discourse & perceptions around seminars (Sayings)",
    "১ নম্বর: সময়সূচি ও কক্ষের পরিবেশ (Doings) মূল্যায়ন করা" to "1 pt: Evaluate scheduling & room arrangements (Doings)",
    "১ নম্বর: শিক্ষক-শিক্ষার্থী সংলাপের সংস্কৃতি (Relatings) ব্যাখ্যা করা" to "1 pt: Explain faculty-student dialogue hierarchy (Relatings)",
    "১ পয়েন্ট: শ্রেণিকক্ষ ও বিভাগের প্রচলিত আলোচনা/ভাষা (Sayings) চিহ্নিত করা" to "1 pt: Identify classroom & department discourse (Sayings)",
    "১ পয়েন্ট: ক্লাসরুমের সময়সূচি, রিসোর্স ও পাঠদান কার্যক্রম (Doings) বিশ্লেষণ করা" to "1 pt: Analyze schedules, resources & teaching activities (Doings)",
    "১ পয়েন্ট: শিক্ষক-শিক্ষার্থী সম্পর্ক ও অংশগ্রহণমূলক Communicative Space (Relatings) উল্লেখ করা" to "1 pt: Address faculty-student hierarchy & Communicative Space (Relatings)",
    "অধ্যায় ১ (পর্ব ১/২): Practice Architectures অনলাইন সেশন" to "Chapter 1 (Part 1/2): Practice Architectures Live Session",
    "শুক্রবার, রাত ৮:৩০ • ৪০ মিনিট" to "Friday, 8:30 PM • 40 Min",
    "মাসিক লাইভ রিসার্চ ক্লিনিক (Live Q&A with Senior Researchers)" to "Monthly Live Research Clinic (Q&A with Senior Researchers)",
    "মাসের শেষ শনিবার, রাত ৯:০০ • ৬০ মিনিট" to "Last Saturday, 9:00 PM • 60 Min",
    "বিশ্ববিদ্যালয় শ্রেণিকক্ষে অংশগ্রহণমূলক মূল্যায়ন সংস্কার" to "Participatory Assessment Reform in University Classrooms"
)

@Composable
fun trContent(text: String): String {
    if (LocalAppLanguage.current != "en") return text
    return SEED_EN_TRANSLATIONS[text] ?: text
}

// Shared status colors
val SparcCrimson = Color(0xFFEF4444)
val SparcCrimsonContainer = Color(0xFFFEE2E2)
val SparcHighlightYellow = Color(0xFFFDE68A)
val SparcOliveBadge = Color(0xFF059669)

private val SparcTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun SparcTheme(
    visualTheme: SparcVisualTheme = SparcVisualTheme.COBALT_SAAS,
    accentPreset: org.sparc.studycircle.core.model.SparcAccentPreset? = null,
    appLanguage: String = "bn",
    content: @Composable () -> Unit
) {
    val palette = paletteFor(visualTheme, accentPreset)
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primarySoft,
            onPrimaryContainer = palette.primarySoftText,
            secondary = palette.accent,
            secondaryContainer = palette.accentSoft,
            onSecondaryContainer = palette.accentText,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceVariant = palette.cardAlt,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = SparcCrimson
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primarySoft,
            onPrimaryContainer = palette.primarySoftText,
            secondary = palette.accent,
            secondaryContainer = palette.accentSoft,
            onSecondaryContainer = palette.accentText,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceVariant = palette.cardAlt,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = SparcCrimson
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = if (palette.useHeroHeader) palette.primary.toArgb() else palette.card.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !palette.isDark && !palette.useHeroHeader
        }
    }

    CompositionLocalProvider(
        LocalSparcPalette provides palette,
        LocalAppLanguage provides appLanguage
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SparcTypography,
            content = content
        )
    }
}
