package com.macrokey.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrokey.R
import com.macrokey.util.LocaleHelper

class GuideActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GuideScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE64A19),
                    titleContentColor = Color.White
                ),
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←", color = Color.White, fontSize = 20.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GuideStep(
                emoji = "➕",
                title = stringResource(R.string.guide_step1_title),
                description = stringResource(R.string.guide_step1_desc)
            )
            GuideStep(
                emoji = "🟠",
                title = stringResource(R.string.guide_step2_title),
                description = stringResource(R.string.guide_step2_desc)
            )
            GuideStep(
                emoji = "⚡",
                title = stringResource(R.string.guide_step3_title),
                description = stringResource(R.string.guide_step3_desc)
            )
            GuideStep(
                emoji = "📤",
                title = stringResource(R.string.guide_step4_title),
                description = stringResource(R.string.guide_step4_desc)
            )
            GuideStep(
                emoji = "✏️",
                title = stringResource(R.string.guide_step5_title),
                description = stringResource(R.string.guide_step5_desc)
            )
            GuideStep(
                emoji = "💡",
                title = stringResource(R.string.guide_tips_title),
                description = stringResource(R.string.guide_tips_desc)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun GuideStep(emoji: String, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F0))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = emoji,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp)
            )
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFFE64A19),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color(0xFF555555),
                    lineHeight = 20.sp
                )
            }
        }
    }
}
