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

        // Instant 1-Tap Demo Entry Card
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
                        tr("ডেমো গবেষক হিসেবে সরাসরি প্রবেশ করুন", "Enter Instantly as Demo Researcher"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = p.text
                    )
                    Text(
                        tr("পাসওয়ার্ড ছাড়াই ১ ট্যাপে সব ফিচার ও ১২টি স্ক্রিন টেস্ট করুন", "Explore all features & 12 screens in 1 tap without a password"),
                        fontSize = 11.5.sp,
                        color = p.muted
                    )
                }
            }
        }
    }
}
