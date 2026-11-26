package org.sparc.studycircle.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.model.AppCustomizationConfig
import org.sparc.studycircle.core.model.SparcVisualTheme
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.ui.components.SparcPrimaryButton
import org.sparc.studycircle.ui.components.SparcScholarlyCard
import org.sparc.studycircle.ui.components.SparcSegmentedPills

@Composable
fun SparcAnimatedSplashScreen(
    appCustomization: AppCustomizationConfig = AppCustomizationConfig(),
    onFinished: () -> Unit
) {
    val p = LocalSparcPalette.current
    var startAnim by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.72f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "splashScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 550),
        label = "splashAlpha"
    )

    LaunchedEffect(Unit) {
        startAnim = true
        delay(1250L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.primaryGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = "SPARC Emblem",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
            Text(
                text = appCustomization.appTitle,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = tr(appCustomization.appTaglineBn, appCustomization.appTaglineEn),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = tr(appCustomization.splashQuoteBn, appCustomization.splashQuoteEn),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SparcAuthScreen(
    repository: SparcRepository
) {
    val state by repository.state.collectAsState()
    val p = LocalSparcPalette.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Sign Up
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var fullName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("Sociology") }
    var selectedSkills by remember { mutableStateOf(setOf("Academic Writing", "Data Analysis")) }
    var referralCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val skillOptions = listOf("Academic Writing", "Data Analysis", "Literature Review", "Field Survey")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Brand Banner + Quick Language & Theme Toggle Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.primaryGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color.White)
                }
                Column {
                    Text(state.appCustomization.appTitle, style = MaterialTheme.typography.titleLarge, color = p.text)
                    Text(
                        tr(state.appCustomization.appTaglineBn, state.appCustomization.appTaglineEn),
                        fontSize = 12.sp,
                        color = p.muted
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.clickable {
                        repository.setAppLanguage(if (state.appLanguage == "en") "bn" else "en")
                    }
                ) {
                    Text(
                        text = if (state.appLanguage == "en") "বাংলা" else "EN",
                        color = p.text,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // Cycle Theme right on Auth Screen!
                Surface(
                    shape = RoundedCornerShape(50),
                    color = p.primarySoft,
                    modifier = Modifier.clickable {
                        val themes = SparcVisualTheme.entries
                        val next = themes[(themes.indexOf(state.visualTheme) + 1) % themes.size]
                        repository.setVisualTheme(next)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = p.primarySoftText, modifier = Modifier.size(15.dp))
                        Text(tr("থিম", "Theme"), color = p.primarySoftText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SparcSegmentedPills(
            options = listOf(
                tr("লগইন (Sign In)", "Sign In"),
                tr("নতুন অ্যাকাউন্ট (Sign Up)", "Create Account")
            ),
            selectedIndex = mode,
            onSelect = {
                mode = it
                errorMessage = null
            }
        )

        SparcScholarlyCard {
            if (mode == 1) {
                Text(
                    tr("নতুন গবেষক প্রোফাইল তৈরি করুন", "Create Your Researcher Profile"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(tr("আপনার পূর্ণ নাম", "Full Name")) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text(tr("বিভাগ (Department)", "Department")) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    tr("আপনার অ্যাকাউন্টে লগইন করুন", "Sign In to Your Account"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(tr("ইমেইল (Academic / Personal Email)", "Academic or Personal Email")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(tr("পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)", "Password (min 6 characters)")) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            var showForgotDialog by remember { mutableStateOf(false) }
            var resetSuccessMsg by remember { mutableStateOf<String?>(null) }

            if (mode == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = tr("🔑 পাসওয়ার্ড ভুলে গেছেন?", "🔑 Forgot Password?"),
                        color = p.primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            errorMessage = null
                            resetSuccessMsg = null
                            showForgotDialog = true
                        }
                    )
                }
            }

            if (showForgotDialog) {
                var resetEmail by remember(email) { mutableStateOf(email) }
                var resetContact by remember { mutableStateOf("") }
                var resetBusy by remember { mutableStateOf(false) }
                var resetErr by remember { mutableStateOf<String?>(null) }

                AlertDialog(
                    onDismissRequest = { if (!resetBusy) showForgotDialog = false },
                    title = {
                        Text(
                            tr("🔑 পাসওয়ার্ড রিসেট অনুরোধ", "🔑 Request Password Reset"),
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                tr(
                                    "আপনার নিবন্ধিত ইমেইল ও যোগাযোগের নম্বর (হোয়াটসঅ্যাপ/মোবাইল) দিন। SPARC অ্যাডমিন যাচাই করে আপনার পাসওয়ার্ড রিসেট করে দেবেন।",
                                    "Enter your registered email and WhatsApp/phone number. A SPARC Admin will verify and reset your password."
                                ),
                                fontSize = 12.sp,
                                color = p.muted
                            )
                            OutlinedTextField(
                                value = resetEmail,
                                onValueChange = { resetEmail = it },
                                label = { Text(tr("নিবন্ধিত ইমেইল", "Registered Email")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = resetContact,
                                onValueChange = { resetContact = it },
                                label = { Text(tr("হোয়াটসঅ্যাপ / মোবাইল নম্বর", "WhatsApp / Phone Number")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            resetErr?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = !resetBusy,
                            onClick = {
                                scope.launch {
                                    resetBusy = true
                                    resetErr = null
                                    val res = repository.requestPasswordReset(resetEmail, resetContact)
                                    resetBusy = false
                                    res.onSuccess {
                                        showForgotDialog = false
                                        resetSuccessMsg = if (state.appLanguage == "en") {
                                            "✓ Password reset request sent! Admin will contact you shortly."
                                        } else {
                                            "✓ পাসওয়ার্ড রিসেট অনুরোধ জমা হয়েছে! অ্যাডমিন দ্রুত নতুন পাসওয়ার্ড জানিয়ে দেবেন।"
                                        }
                                    }.onFailure {
                                        resetErr = it.message
                                    }
                                }
                            }
                        ) {
                            Text(if (resetBusy) tr("পাঠানো হচ্ছে...", "Sending...") else tr("অনুরোধ পাঠান", "Submit Request"))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showForgotDialog = false }) {
                            Text(tr("বাতিল", "Cancel"))
                        }
                    }
                )
            }

            resetSuccessMsg?.let { okMsg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = p.primarySoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        okMsg,
                        color = p.primarySoftText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (mode == 1) {
                Text(
                    tr("আপনার মূল দক্ষতা (কো-অথর ম্যাচমেকিং):", "Core Skills (Co-Author Matchmaking):"),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.text
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    skillOptions.forEach { sk ->
                        val active = sk in selectedSkills
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) p.primarySoft else p.cardAlt,
                            border = BorderStroke(1.dp, if (active) p.primary else p.border),
                            modifier = Modifier.clickable {
                                selectedSkills = if (active) selectedSkills - sk else selectedSkills + sk
                            }
                        ) {
                            Text(
                                text = if (active) "✓ $sk" else sk,
                                color = if (active) p.primarySoftText else p.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = referralCode,
                    onValueChange = { referralCode = it },
                    label = { Text(tr("রেফারেল কোড (ঐচ্ছিক — +৳৫০ বোনাস)", "Referral Code (Optional — +৳50 Bonus)")) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            errorMessage?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }

            SparcPrimaryButton(
                text = when {
                    isLoading -> tr("অপেক্ষা করুন...", "Please wait...")
                    mode == 0 -> tr("লগইন করুন", "Sign In")
                    else -> tr("অ্যাকাউন্ট খুলুন ও শুরু করুন", "Create Account & Get Started")
                },
                enabled = !isLoading,
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val res = if (mode == 0) {
                            repository.signInWithEmail(email, password)
                        } else {
                            repository.registerResearcher(
                                name = fullName,
                                email = email,
                                password = password,
                                department = department,
                                skills = selectedSkills.toList(),
                                referralCode = referralCode
                            )
                        }
                        isLoading = false
                        res.onFailure { errorMessage = it.message }
                    }
                }
            )
        }

        // Guest Preview Card + Privacy Policy Link
        var showPrivacyModal by remember { mutableStateOf(false) }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = p.card,
            border = BorderStroke(1.dp, p.border),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { repository.loginInstantDemo() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(p.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = p.accentText)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tr("গেস্ট প্রিভিউ মোডে ঘুরে দেখুন", "Explore in Guest Preview Mode"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = p.text
                    )
                    Text(
                        tr("অ্যাকাউন্ট খোলার আগে অ্যাপের ইন্টারফেস দেখে নিন", "Preview the interface before creating an account"),
                        fontSize = 11.5.sp,
                        color = p.muted
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = tr("🔒 গোপনীয়তা নীতি ও ডেটা সুরক্ষা (Privacy Policy)", "🔒 Privacy Policy & Data Safety"),
                fontSize = 12.sp,
                color = p.muted,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { showPrivacyModal = true }
            )
        }

        if (showPrivacyModal) {
            AlertDialog(
                onDismissRequest = { showPrivacyModal = false },
                title = { Text(tr("🔒 SPARC গোপনীয়তা নীতি", "🔒 SPARC Privacy Policy"), fontWeight = FontWeight.ExtraBold) },
                text = {
                    Text(
                        tr(
                            "SPARC Study Circle আপনার নাম, ইমেইল, বিভাগ, পাঠ অগ্রগতি এবং রাইটিং পাণ্ডুলিপি শুধুমাত্র একাডেমিক পাঠচক্র ও মানব মেন্টর মূল্যায়নের জন্য সুরক্ষিত সার্ভারে সংরক্ষণ করে। আপনার তথ্য কখনোই কোনো তৃতীয় পক্ষের কাছে বিক্রি বা বিজ্ঞাপনের কাজে শেয়ার করা হয় না। লগইন করার পর Settings -> App থেকে যেকোনো সময় ১ ক্লিকে আপনার অ্যাকাউন্ট ও সমস্ত ডেটা স্থায়ীভাবে মুছে ফেলতে পারবেন।",
                            "SPARC Study Circle stores your name, email, department, reading progress, and manuscripts securely solely for academic study circles and human mentor evaluation. We never sell or share user data for advertising. You can permanently delete your account and all associated data anytime from Settings -> App."
                        ),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showPrivacyModal = false }) {
                        Text(tr("বুঝেছি", "Got It"))
                    }
                }
            )
        }
    }
}
