package com.macrokey.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrokey.R
import com.macrokey.billing.BillingManager
import com.macrokey.billing.TrialManager
import com.macrokey.util.LocaleHelper

class PaywallActivity : ComponentActivity() {

    private lateinit var billingManager: BillingManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        billingManager = BillingManager(this) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, getString(R.string.purchase_success), Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    Toast.makeText(this, getString(R.string.purchase_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
        billingManager.initialize()

        // Block back button — user must purchase or stay on paywall
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing — paywall cannot be dismissed
            }
        })

        setContent {
            MaterialTheme {
                PaywallScreen(
                    onPurchase = { billingManager.launchPurchase(this) },
                    onRestore = {
                        // initialize() already handles re-use of existing connections
                        billingManager.initialize()
                        Toast.makeText(this, getString(R.string.restoring_purchase), Toast.LENGTH_SHORT).show()
                    },
                    onExit = { finishAffinity() }
                )
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        billingManager.destroy()
        super.onDestroy()
    }
}

@Composable
fun PaywallScreen(onPurchase: () -> Unit, onRestore: () -> Unit, onExit: () -> Unit) {
    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF3E0), Color.White)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "⌨️", fontSize = 64.sp)

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.paywall_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE64A19),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.paywall_subtitle),
            fontSize = 15.sp,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.pro_features),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.feature_unlimited), fontSize = 14.sp, color = Color(0xFF555555))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.feature_categories), fontSize = 14.sp, color = Color(0xFF555555))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.feature_updates), fontSize = 14.sp, color = Color(0xFF555555))
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onPurchase,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D67A)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = stringResource(R.string.upgrade_pro),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onRestore) {
            Text(
                text = stringResource(R.string.restore_purchase),
                fontSize = 13.sp,
                color = Color(0xFF757575)
            )
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onExit) {
            Text(
                text = stringResource(R.string.exit_app),
                fontSize = 13.sp,
                color = Color(0xFFBDBDBD)
            )
        }
    }
}
