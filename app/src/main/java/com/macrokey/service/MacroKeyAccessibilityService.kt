package com.macrokey.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.macrokey.data.MacroBlock
import com.macrokey.data.MacroKeyDatabase
import kotlinx.coroutines.*

/**
 * MacroKey Floating Text Assistant
 *
 * כפתור צף קטן שיושב ליד המקלדת. לחיצה פותחת גריד של בלוקים צבעוניים.
 * לחיצה על בלוק = הזרקת הטקסט לשדה הפעיל.
 * כפתור "+" מאפשר יצירת בלוק חדש ישירות מהצף.
 *
 * Flow:
 * [כפתור M קטן] → לחיצה → [גריד בלוקים + כפתור הוספה] → לחיצה על בלוק → טקסט נכנס → סגירה
 */
class MacroKeyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MacroKeyService"

        @Volatile
        var instance: MacroKeyAccessibilityService? = null
            private set
    }

    // ══════════════════════════════════════════════
    // Properties
    // ══════════════════════════════════════════════

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Views
    private var fabView: View? = null
    private var panelView: View? = null
    private var feedbackView: View? = null

    // State
    private var isExpanded = false
    private var blocks: List<MacroBlock> = emptyList()

    // FAB drag tracking
    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false

    // Predefined color palette for new blocks
    private val colorPalette = listOf(
        "#4CAF50", // ירוק
        "#2196F3", // כחול
        "#FF9800", // כתום
        "#9C27B0", // סגול
        "#F44336", // אדום
        "#00BCD4", // טורקיז
        "#795548", // חום
        "#607D8B"  // אפור-כחול
    )

    // ══════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        configureAccessibility()
        loadBlocksAndShowFab()

        Log.d(TAG, "MacroKey service connected")
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        removeFab()
        removePanel()
        super.onDestroy()
        Log.d(TAG, "MacroKey service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* לא נדרש */ }
    override fun onInterrupt() { Log.w(TAG, "Service interrupted") }

    private fun configureAccessibility() {
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
    }

    // ══════════════════════════════════════════════
    // Data Loading
    // ══════════════════════════════════════════════

    private fun loadBlocksAndShowFab() {
        serviceScope.launch {
            blocks = withContext(Dispatchers.IO) {
                MacroKeyDatabase.getInstance(applicationContext).blockDao().getAllBlocks()
            }
            createFab()
            Log.d(TAG, "Loaded ${blocks.size} blocks")
        }
    }

    /** נקרא מבחוץ (מה-Settings app) כשמוסיפים/עורכים בלוקים */
    fun refreshBlocks() {
        serviceScope.launch {
            blocks = withContext(Dispatchers.IO) {
                MacroKeyDatabase.getInstance(applicationContext).blockDao().getAllBlocks()
            }
            // אם הפאנל פתוח — בנה מחדש
            if (isExpanded) {
                removePanel()
                showPanel()
            }
        }
    }

    // ══════════════════════════════════════════════
    // FAB — כפתור צף קטן
    // ══════════════════════════════════════════════

    private fun createFab() {
        if (fabView != null) return

        val sizePx = dp(44)

        val fab = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E64A19"))
            }
            alpha = 0.9f
            elevation = dp(4).toFloat()

            addView(TextView(context).apply {
                text = "M"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        val params = overlayParams(sizePx, sizePx).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(12)
            y = dp(200) // ממוקם מעל המקלדת
        }

        fab.setOnTouchListener(fabTouchListener(params))

        safeAddView(fab, params)
        fabView = fab
    }

    private fun fabTouchListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        return View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) isDragging = true
                    if (isDragging) {
                        params.x = (dragStartX - dx.toInt()).coerceAtLeast(0)
                        params.y = dragStartY - dy.toInt()
                        safeUpdateView(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) togglePanel()
                    true
                }
                else -> false
            }
        }
    }

    // ══════════════════════════════════════════════
    // Panel — גריד בלוקים + כפתור הוספה
    // ══════════════════════════════════════════════

    private fun togglePanel() {
        if (isExpanded) removePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView != null) return

        val panelWidth = dp(260)

        // ── Container ──
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#FAFAFA"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#E0E0E0"))
            }
            elevation = dp(8).toFloat()
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // ── כותרת ──
        container.addView(TextView(this).apply {
            text = "MacroKey"
            setTextColor(Color.parseColor("#E64A19"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        })

        // ── גריד בלוקים בתוך ScrollView ──
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { bottomMargin = dp(4) }
        }

        val grid = GridLayout(this).apply {
            columnCount = 2
        }

        if (blocks.isEmpty()) {
            grid.addView(TextView(this).apply {
                text = "אין בלוקים עדיין\nלחץ על + כדי להוסיף"
                setTextColor(Color.GRAY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
                layoutParams = GridLayout.LayoutParams().apply {
                    columnSpec = GridLayout.spec(0, 2)
                    width = panelWidth - dp(16)
                }
            })
        } else {
            val blockWidth = (panelWidth - dp(24)) / 2

            blocks.forEach { block ->
                grid.addView(createBlockButton(block, blockWidth))
            }
        }

        scrollView.addView(grid)
        container.addView(scrollView)

        // ── שורה תחתונה: כפתור הוספה + סגירה ──
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        }

        // כפתור + הוספת בלוק חדש
        bottomRow.addView(createActionButton("＋ בלוק חדש", "#4CAF50") {
            removePanel()
            showAddBlockDialog()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                marginEnd = dp(4)
            }
        })

        // כפתור סגירה
        bottomRow.addView(createActionButton("✕", "#9E9E9E") {
            removePanel()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(36))
        })

        container.addView(bottomRow)

        // ── מיקום הפאנל ──
        val maxHeight = dp(320)
        val params = overlayParams(panelWidth, maxHeight).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(8)
            y = dp(200)
        }

        safeAddView(container, params)
        panelView = container
        isExpanded = true
    }

    private fun removePanel() {
        panelView?.let { safeRemoveView(it) }
        panelView = null
        isExpanded = false
    }

    // ══════════════════════════════════════════════
    // Block Button — כפתור בלוק בודד בגריד
    // ══════════════════════════════════════════════

    private fun createBlockButton(block: MacroBlock, widthPx: Int): FrameLayout {
        val blockColor = parseColorSafe(block.colorHex)
        val textColor = contrastColor(blockColor)

        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(blockColor)
                cornerRadius = dp(10).toFloat()
            }

            addView(TextView(context).apply {
                text = block.title
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // לחיצה = הזרקת טקסט
            setOnClickListener {
                injectText(block.content)
                removePanel()
                incrementUsage(block.id)
            }

            // לחיצה ארוכה = תצוגה מקדימה
            setOnLongClickListener {
                val preview = block.content.take(120) +
                        if (block.content.length > 120) "..." else ""
                Toast.makeText(context, preview, Toast.LENGTH_LONG).show()
                true
            }

            layoutParams = GridLayout.LayoutParams().apply {
                width = widthPx
                height = dp(52)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
    }

    private fun createActionButton(
        label: String,
        colorHex: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(parseColorSafe(colorHex))
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    // ══════════════════════════════════════════════
    // Add Block Dialog — יצירת בלוק חדש מהצף
    // ══════════════════════════════════════════════

    private fun showAddBlockDialog() {
        // Reference to dialog view for removal
        var dialogView: View? = null

        // ── Outer wrapper: semi-transparent background + tap-to-dismiss ──
        val wrapper = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(100, 0, 0, 0))
            setOnClickListener {
                dialogView?.let { safeRemoveView(it) }
            }
        }

        // ── ScrollView to ensure buttons are always reachable ──
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            // Prevent clicks from dismissing through the dialog
            setOnClickListener { /* absorb */ }
        }

        // ── Dialog container ──
        val dialogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#BDBDBD"))
            }
            elevation = dp(12).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(16))
            setOnClickListener { /* absorb clicks */ }
        }

        // ── Top row: title + X close button ──
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        titleRow.addView(TextView(this).apply {
            text = "בלוק חדש"
            setTextColor(Color.parseColor("#E64A19"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        titleRow.addView(TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#999999"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener {
                dialogView?.let { safeRemoveView(it) }
            }
        })

        dialogContainer.addView(titleRow)

        // שדה שם
        val nameInput = EditText(this).apply {
            hint = "שם הבלוק (למשל: פרטי בנק)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            background = createInputBackground()
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        dialogContainer.addView(nameInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // שדה תוכן
        val contentInput = EditText(this).apply {
            hint = "הטקסט שיישלח בלחיצה..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
            background = createInputBackground()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.TOP or Gravity.END
        }
        dialogContainer.addView(contentInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // בחירת צבע
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }

        var selectedColor = colorPalette[0]
        val colorViews = mutableListOf<View>()

        colorPalette.forEach { hex ->
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                    if (hex == selectedColor) setStroke(dp(3), Color.BLACK)
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                }
                setOnClickListener { v ->
                    selectedColor = hex
                    colorViews.forEach { cv ->
                        (cv.background as GradientDrawable).setStroke(0, Color.TRANSPARENT)
                    }
                    (v.background as GradientDrawable).setStroke(dp(3), Color.BLACK)
                }
            }
            colorViews.add(dot)
            colorRow.addView(dot)
        }

        dialogContainer.addView(colorRow)

        // כפתורי שמירה וביטול
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        buttonsRow.addView(createActionButton("שמור", "#4CAF50") {
            val name = nameInput.text.toString().trim()
            val content = contentInput.text.toString().trim()

            if (name.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "יש למלא שם ותוכן", Toast.LENGTH_SHORT).show()
                return@createActionButton
            }

            // שמירה ל-DB
            serviceScope.launch {
                withContext(Dispatchers.IO) {
                    MacroKeyDatabase.getInstance(applicationContext).blockDao()
                        .insertBlock(
                            MacroBlock(
                                title = name.take(20),
                                content = content,
                                colorHex = selectedColor,
                                sortOrder = blocks.size
                            )
                        )
                }
                // רענון
                refreshBlocks()
                dialogView?.let { safeRemoveView(it) }
                Toast.makeText(this@MacroKeyAccessibilityService, "✓ הבלוק נשמר!", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(4)
            }
        })

        buttonsRow.addView(createActionButton("ביטול", "#9E9E9E") {
            dialogView?.let { safeRemoveView(it) }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(4)
            }
        })

        dialogContainer.addView(buttonsRow)

        // ── Assemble: dialog inside scroll, scroll inside wrapper ──
        scrollView.addView(dialogContainer, FrameLayout.LayoutParams(
            dp(280), ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        wrapper.addView(scrollView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // ── Overlay params: allow focus for typing, DON'T cover nav bar ──
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
            // FLAG_NOT_TOUCH_MODAL: touches outside dialog pass through (nav bar works)
            // NO FLAG_LAYOUT_IN_SCREEN: dialog stays above nav bar, not covering it
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        safeAddView(wrapper, params)
        dialogView = wrapper
    }

    private fun createInputBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#F5F5F5"))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor("#E0E0E0"))
        }
    }

    // ══════════════════════════════════════════════
    // TEXT INJECTION — הזרקת טקסט
    // ══════════════════════════════════════════════

    /**
     * 3 שכבות הגנה:
     * 1. ACTION_SET_TEXT — הדרך הנקייה
     * 2. Clipboard + ACTION_PASTE — fallback
     * 3. Clipboard + הודעה למשתמש — last resort
     */
    private fun injectText(text: String) {
        val node = findFocusedInput()

        if (node != null) {
            val success = trySetText(node, text) || tryPaste(node, text)
            node.recycle()

            if (success) {
                showCheckmark()
            } else {
                clipboardFallback(text)
            }
        } else {
            clipboardFallback(text)
        }
    }

    private fun findFocusedInput(): AccessibilityNodeInfo? {
        // ניסיון 1: חיפוש ישיר
        rootInActiveWindow?.let { root ->
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { node ->
                if (node.isEditable) return node
                node.recycle()
            }
        }

        // ניסיון 2: סריקת כל החלונות
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (window in windows) {
                window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { node ->
                    if (node.isEditable) return node
                    node.recycle()
                }
            }
        }

        // ניסיון 3: סריקה רקורסיבית
        rootInActiveWindow?.let { return findEditableRecursive(it) }

        return null
    }

    private fun findEditableRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableRecursive(child)?.let {
                child.recycle()
                return it
            }
            child.recycle()
        }
        return null
    }

    /** ניסיון 1: ACTION_SET_TEXT — שותל את הטקסט ישירות */
    private fun trySetText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            Log.e(TAG, "SET_TEXT failed", e)
            false
        }
    }

    /** ניסיון 2: העתקה + הדבקה */
    private fun tryPaste(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            copyToClipboard(text)
            Thread.sleep(50)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            Log.e(TAG, "PASTE failed", e)
            false
        }
    }

    /** ניסיון 3: העתקה + הודעה למשתמש */
    private fun clipboardFallback(text: String) {
        copyToClipboard(text)
        Toast.makeText(this, "הטקסט הועתק ✓ הדביקי עם לחיצה ארוכה", Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MacroKey", text))
    }

    // ══════════════════════════════════════════════
    // Visual Feedback — ✓ ירוק
    // ══════════════════════════════════════════════

    private fun showCheckmark() {
        val check = TextView(this).apply {
            text = "✓"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50"))
            }
        }

        val params = overlayParams(dp(48), dp(48)).apply { gravity = Gravity.CENTER }
        safeAddView(check, params)

        check.postDelayed({ safeRemoveView(check) }, 600)
    }

    // ══════════════════════════════════════════════
    // Usage Count
    // ══════════════════════════════════════════════

    private fun incrementUsage(blockId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                MacroKeyDatabase.getInstance(applicationContext).blockDao()
                    .incrementUsageCount(blockId)
            } catch (_: Exception) {}
        }
    }

    // ══════════════════════════════════════════════
    // Window Manager Utilities
    // ══════════════════════════════════════════════

    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun safeAddView(view: View, params: WindowManager.LayoutParams) {
        try { windowManager.addView(view, params) }
        catch (e: Exception) { Log.e(TAG, "addView failed", e) }
    }

    private fun safeUpdateView(view: View, params: WindowManager.LayoutParams) {
        try { windowManager.updateViewLayout(view, params) }
        catch (e: Exception) { Log.e(TAG, "updateView failed", e) }
    }

    private fun safeRemoveView(view: View) {
        try { windowManager.removeView(view) }
        catch (_: Exception) {}
    }

    private fun removeFab() {
        fabView?.let { safeRemoveView(it) }; fabView = null
    }

    // ══════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun parseColorSafe(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.parseColor("#4CAF50")
    }

    private fun contrastColor(bg: Int): Int {
        val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255
        return if (lum > 0.5) Color.BLACK else Color.WHITE
    }
}
