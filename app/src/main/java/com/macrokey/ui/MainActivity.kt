package com.macrokey.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrokey.R
import com.macrokey.data.MacroBlock
import com.macrokey.data.MacroKeyDatabase
import com.macrokey.service.MacroKeyAccessibilityService
import com.macrokey.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MacroKeySettingsScreen(
                    onLanguageToggle = {
                        LocaleHelper.toggleLanguage(this)
                        recreate()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroKeySettingsScreen(onLanguageToggle: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { MacroKeyDatabase.getInstance(context).blockDao() }

    val isHebrew = LocaleHelper.isHebrew(context)

    var blocks by remember { mutableStateOf<List<MacroBlock>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<MacroBlock?>(null) }

    // טעינת בלוקים
    LaunchedEffect(Unit) {
        blocks = withContext(Dispatchers.IO) { dao.getAllBlocks() }
    }

    // פונקציית רענון
    fun refresh() {
        scope.launch {
            blocks = withContext(Dispatchers.IO) { dao.getAllBlocks() }
            MacroKeyAccessibilityService.instance?.refreshBlocks()
        }
    }

    // Re-check permissions on resume
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

    val isAccessibilityOn = remember(refreshTrigger) { isAccessibilityServiceEnabled(context) }
    val hasOverlayPermission = remember(refreshTrigger) { Settings.canDrawOverlays(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MacroKey", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE64A19),
                    titleContentColor = Color.White
                ),
                actions = {
                    // Language toggle button
                    TextButton(
                        onClick = onLanguageToggle,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (isHebrew) "EN" else "עב",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Menu button (⋮)
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("⋮", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_guide)) },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(Intent(context, GuideActivity::class.java))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_privacy)) },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(Intent(context, PrivacyActivity::class.java))
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFFE64A19)
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.add_block), tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── הרשאות ──
            item {
                PermissionsCard(
                    isAccessibilityOn = isAccessibilityOn,
                    hasOverlayPermission = hasOverlayPermission,
                    context = context
                )
            }

            // ── כותרת בלוקים ──
            item {
                Text(
                    stringResource(R.string.your_blocks, blocks.size),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (blocks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Text(
                            stringResource(R.string.no_blocks_yet),
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }
            }

            // ── רשימת בלוקים ──
            items(blocks, key = { it.id }) { block ->
                BlockCard(
                    block = block,
                    onEdit = { editingBlock = block; showAddDialog = true },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.deleteBlock(block) }
                            refresh()
                        }
                    }
                )
            }
        }
    }

    // ── דיאלוג הוספה/עריכה ──
    if (showAddDialog) {
        AddEditBlockDialog(
            existing = editingBlock,
            onDismiss = { showAddDialog = false; editingBlock = null },
            onSave = { title, content, color ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (editingBlock != null) {
                            dao.updateBlock(editingBlock!!.copy(
                                title = title, content = content, colorHex = color
                            ))
                        } else {
                            dao.insertBlock(MacroBlock(
                                title = title, content = content, colorHex = color,
                                sortOrder = blocks.size
                            ))
                        }
                    }
                    showAddDialog = false
                    editingBlock = null
                    refresh()
                }
            }
        )
    }
}

// ══════════════════════════════════════════════
// Permissions Card
// ══════════════════════════════════════════════

@Composable
fun PermissionsCard(
    isAccessibilityOn: Boolean,
    hasOverlayPermission: Boolean,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAccessibilityOn && hasOverlayPermission)
                Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.required_settings),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))

            PermissionRow(
                label = stringResource(R.string.accessibility_service),
                isEnabled = isAccessibilityOn,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            Spacer(Modifier.height(8.dp))

            PermissionRow(
                label = stringResource(R.string.display_over_apps),
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
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.all_set), color = Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
fun PermissionRow(label: String, isEnabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Text(
            if (isEnabled) stringResource(R.string.onboarding_enabled)
            else stringResource(R.string.onboarding_tap_to_enable),
            color = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFE64A19),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

// ══════════════════════════════════════════════
// Block Card
// ══════════════════════════════════════════════

@Composable
fun BlockCard(block: MacroBlock, onEdit: () -> Unit, onDelete: () -> Unit) {
    val blockColor = parseComposeColor(block.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(blockColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    block.title.take(2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    block.content.take(60) + if (block.content.length > 60) "..." else "",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(R.string.delete),
                    tint = Color(0xFFE53935)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════
// Add/Edit Dialog
// ══════════════════════════════════════════════

@Composable
fun AddEditBlockDialog(
    existing: MacroBlock?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, colorHex: String) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    var selectedColor by remember { mutableStateOf(existing?.colorHex ?: "#4CAF50") }

    val colors = listOf(
        "#4CAF50", "#2196F3", "#FF9800", "#9C27B0",
        "#F44336", "#00BCD4", "#795548", "#607D8B"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing != null) stringResource(R.string.edit_block)
                else stringResource(R.string.new_block),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(20) },
                    label = { Text(stringResource(R.string.block_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.block_content)) },
                    minLines = 3,
                    maxLines = 8,
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
                                        Modifier.border(3.dp, Color.Black, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(title.trim(), content.trim(), selectedColor)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE64A19))
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ══════════════════════════════════════════════
// Utilities
// ══════════════════════════════════════════════

fun parseComposeColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFF4CAF50)
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val service = "${context.packageName}/.service.MacroKeyAccessibilityService"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(service)
}
