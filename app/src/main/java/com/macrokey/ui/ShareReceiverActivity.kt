package com.macrokey.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.macrokey.R
import com.macrokey.data.MacroBlock
import com.macrokey.data.MacroKeyDatabase
import com.macrokey.service.MacroKeyAccessibilityService
import com.macrokey.util.ColorPalette
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * מקבלת טקסט משיתוף מכל אפליקציה ושומרת כבלוק חדש.
 *
 * Flow: המשתמש מסמן טקסט → שיתוף → MacroKey → נפתח מסך קטן לשמירה
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.macrokey.util.LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            else -> ""
        }

        if (sharedText.isBlank()) {
            Toast.makeText(this, getString(R.string.no_text_received), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                SaveSharedTextScreen(
                    sharedText = sharedText,
                    onSave = { title, color ->
                        saveBlock(title, sharedText, color)
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun saveBlock(title: String, content: String, colorHex: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = MacroKeyDatabase.getInstance(applicationContext).blockDao()
                val count = dao.getBlockCount()
                dao.insertBlock(
                    MacroBlock(
                        title = title.take(20),
                        content = content,
                        colorHex = colorHex,
                        sortOrder = count
                    )
                )
            }
            MacroKeyAccessibilityService.instance?.refreshBlocks()
            Toast.makeText(this@ShareReceiverActivity, getString(R.string.saved_as_block), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

@Composable
fun SaveSharedTextScreen(
    sharedText: String,
    onSave: (title: String, colorHex: String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(ColorPalette.BLOCK_COLORS[0]) }

    val colors = ColorPalette.BLOCK_COLORS

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.save_to_macrokey_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFE64A19)
                )

                Spacer(Modifier.height(12.dp))

                // תצוגה מקדימה של הטקסט
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = sharedText.take(200) + if (sharedText.length > 200) "..." else "",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        maxLines = 5
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(20) },
                    label = { Text(stringResource(R.string.block_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.color_label), fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseComposeColor(hex))
                                .then(
                                    if (hex == selectedColor)
                                        Modifier.padding(2.dp) else Modifier
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) onSave(title.trim(), selectedColor)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE64A19)
                        )
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
