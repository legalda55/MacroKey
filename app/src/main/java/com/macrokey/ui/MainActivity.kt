package com.macrokey.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrokey.R
import com.macrokey.data.MacroBlock
import com.macrokey.data.MacroKeyDatabase
import com.macrokey.service.MacroKeyAccessibilityService
import com.macrokey.util.ColorPalette
import com.macrokey.util.ImageHelper
import com.macrokey.util.LocaleHelper
import com.macrokey.billing.TrialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Trial check — redirect to Paywall if expired
        TrialManager.recordInstallIfNeeded(this)
        if (!TrialManager.canUseApp(this)) {
            startActivity(Intent(this, PaywallActivity::class.java))
            finish()
            return
        }

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
    override fun onResume() {
        super.onResume()
        if (!TrialManager.canUseApp(this)) {
            startActivity(Intent(this, PaywallActivity::class.java))
            finish()
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_website)) },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://legaldan55.github.io/MacroKey/")))
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
            // ── Trial Banner ──
            val trialActive = TrialManager.isTrialActive(context) && !TrialManager.hasPurchasedPro(context)
            if (trialActive) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF22D67A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.trial_banner, TrialManager.daysRemaining(context)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            color = Color(0xFF0A0A0F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

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
                            withContext(Dispatchers.IO) {
                                // Delete associated image file
                                ImageHelper.deleteImage(block.imagePath)
                                dao.deleteBlock(block)
                            }
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
            onSave = { title, content, color, imagePath ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (editingBlock != null) {
                            // If image changed, delete old one
                            val oldPath = editingBlock!!.imagePath
                            if (oldPath != imagePath && oldPath != null) {
                                ImageHelper.deleteImage(oldPath)
                            }
                            dao.updateBlock(editingBlock!!.copy(
                                title = title, content = content, colorHex = color,
                                imagePath = imagePath
                            ))
                        } else {
                            dao.insertBlock(MacroBlock(
                                title = title, content = content, colorHex = color,
                                sortOrder = blocks.size, imagePath = imagePath
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
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

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
                    showAccessibilityDisclosure = true
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

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAllow = {
                showAccessibilityDisclosure = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = {
                showAccessibilityDisclosure = false
            }
        )
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
// Block Card — enlarged with image support
// ══════════════════════════════════════════════

@Composable
fun BlockCard(block: MacroBlock, onEdit: () -> Unit, onDelete: () -> Unit) {
    val blockColor = parseComposeColor(block.colorHex)

    // Load image bitmap if exists
    val imageBitmap = remember(block.imagePath) {
        block.imagePath?.let { path ->
            try {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (_: Exception) { null }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // ── Color strip on left ──
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .heightIn(min = 90.dp)
                    .fillMaxHeight()
                    .background(blockColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                // ── Top row: thumbnail + title ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    Text(
                        text = block.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            stringResource(R.string.delete),
                            tint = Color(0xFFE53935)
                        )
                    }
                }

                // ── Bottom: content preview ──
                if (block.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = block.content.take(120) + if (block.content.length > 120) "..." else "",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (block.imagePath != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "\uD83D\uDDBC Image",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════
// Add/Edit Dialog — with image picker
// ══════════════════════════════════════════════

@Composable
fun AddEditBlockDialog(
    existing: MacroBlock?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, colorHex: String, imagePath: String?) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    var selectedColor by remember { mutableStateOf(existing?.colorHex ?: ColorPalette.BLOCK_COLORS[0]) }
    var imagePath by remember { mutableStateOf(existing?.imagePath) }

    // Image bitmap for preview
    val imageBitmap = remember(imagePath) {
        imagePath?.let { path ->
            try {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (_: Exception) { null }
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Delete old image if replacing
            if (imagePath != null && imagePath != existing?.imagePath) {
                ImageHelper.deleteImage(imagePath)
            }
            val newPath = ImageHelper.saveImageFromUri(context, uri)
            if (newPath != null) {
                imagePath = newPath
            } else {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val colors = ColorPalette.BLOCK_COLORS

    AlertDialog(
        onDismissRequest = {
            // Clean up newly picked image if user cancels
            if (imagePath != null && imagePath != existing?.imagePath) {
                ImageHelper.deleteImage(imagePath)
            }
            onDismiss()
        },
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

                Spacer(Modifier.height(10.dp))

                // ── Image section — prominent, above text ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable { imagePickerLauncher.launch("image/*") }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.image_attached),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    stringResource(R.string.tap_to_change),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                if (imagePath != existing?.imagePath) {
                                    ImageHelper.deleteImage(imagePath)
                                }
                                imagePath = null
                            }) {
                                Text("✕", color = Color(0xFFE53935), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83D\uDDBC", fontSize = 28.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.add_image),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE64A19)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.block_content)) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        val clipContext = LocalContext.current
                        IconButton(onClick = {
                            val clipboard = clipContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                                content = if (content.isEmpty()) pastedText else content + pastedText
                            }
                        }) {
                            Text("\uD83D\uDCCB", fontSize = 18.sp)
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

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
                    if (title.isNotBlank() && (content.isNotBlank() || imagePath != null)) {
                        onSave(title.trim(), content.trim(), selectedColor, imagePath)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE64A19))
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                // Clean up newly picked image if user cancels
                if (imagePath != null && imagePath != existing?.imagePath) {
                    ImageHelper.deleteImage(imagePath)
                }
                onDismiss()
            }) { Text(stringResource(R.string.cancel)) }
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
    // Split by ':' to match exact service components, preventing substring false positives
    return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
}
