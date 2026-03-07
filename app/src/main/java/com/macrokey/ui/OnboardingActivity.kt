package com.macrokey.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrokey.R
import com.macrokey.util.LocaleHelper
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // אם כבר עבר onboarding — קפוץ ישר ל-Main
        val prefs = getSharedPreferences("macrokey_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            startMainAndFinish()
            return
        }

        setContent {
            MaterialTheme {
                OnboardingScreen(
                    onFinish = {
                        prefs.edit().putBoolean("onboarding_done", true).apply()
                        startMainAndFinish()
                    },
                    onLanguageToggle = {
                        LocaleHelper.toggleLanguage(this)
                        recreate()
                    }
                )
            }
        }
    }

    private fun startMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

// ══════════════════════════════════════════════
// Onboarding Screen — 4 Pages
// ══════════════════════════════════════════════

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit, onLanguageToggle: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isHebrew = LocaleHelper.isHebrew(context)
    val primaryColor = Color(0xFFE64A19)
    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF3E0), Color.White)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar: Language toggle + Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language toggle button
                OutlinedButton(
                    onClick = onLanguageToggle,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isHebrew) "EN \uD83C\uDDEC\uD83C\uDDE7" else "עב \uD83C\uDDEE\uD83C\uDDF1",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Skip button
                if (pagerState.currentPage < 3) {
                    TextButton(onClick = onFinish) {
                        Text(
                            stringResource(R.string.onboarding_skip),
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingPage(
                        emoji = "⌨️",
                        title = stringResource(R.string.onboarding_welcome_title),
                        description = stringResource(R.string.onboarding_welcome_desc)
                    )
                    1 -> OnboardingPage(
                        emoji = "🎨",
                        title = stringResource(R.string.onboarding_create_title),
                        description = stringResource(R.string.onboarding_create_desc)
                    )
                    2 -> OnboardingPermissionsPage(context = context)
                    3 -> OnboardingPage(
                        emoji = "🚀",
                        title = stringResource(R.string.onboarding_ready_title),
                        description = stringResource(R.string.onboarding_ready_desc)
                    )
                }
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) primaryColor else Color(0xFFBDBDBD)
                            )
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.onboarding_back),
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    Spacer(Modifier.width(80.dp))
                }

                // Next / Start button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinish()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage < 3)
                            stringResource(R.string.onboarding_next)
                        else
                            stringResource(R.string.onboarding_start),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ══════════════════════════════════════════════
// Standard Page — emoji, title, description
// ══════════════════════════════════════════════

@Composable
fun OnboardingPage(
    emoji: String,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = emoji,
            fontSize = 72.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE64A19),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = description,
            fontSize = 16.sp,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

// ══════════════════════════════════════════════
// Permissions Page (Page 3) — with live status
// ══════════════════════════════════════════════

@Composable
fun OnboardingPermissionsPage(context: Context) {
    var refreshTrigger by remember { mutableStateOf(0) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isAccessibilityOn = remember(refreshTrigger) {
        isAccessibilityServiceEnabled(context)
    }
    val hasOverlayPermission = remember(refreshTrigger) {
        Settings.canDrawOverlays(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔐",
            fontSize = 72.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = stringResource(R.string.onboarding_enable_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE64A19),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = stringResource(R.string.onboarding_enable_desc),
            fontSize = 14.sp,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PermissionButton(
            label = stringResource(R.string.onboarding_enable_accessibility),
            isEnabled = isAccessibilityOn,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        Spacer(Modifier.height(12.dp))

        PermissionButton(
            label = stringResource(R.string.onboarding_enable_overlay),
            isEnabled = hasOverlayPermission,
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        if (isAccessibilityOn && hasOverlayPermission) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.all_set),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun PermissionButton(
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFE64A19)
        ),
        shape = RoundedCornerShape(12.dp),
        enabled = !isEnabled
    ) {
        Text(
            text = if (isEnabled)
                "$label — ${stringResource(R.string.onboarding_enabled)}"
            else
                label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
