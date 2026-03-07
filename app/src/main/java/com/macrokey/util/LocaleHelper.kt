package com.macrokey.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * מנהל שפת האפליקציה — פנימי, לא תלוי בשפת המכשיר.
 * ברירת מחדל: אנגלית.
 * המשתמש יכול להחליף לעברית מתוך האפליקציה.
 */
object LocaleHelper {

    private const val PREFS_NAME = "macrokey_prefs"
    private const val KEY_LANGUAGE = "app_language"
    private const val DEFAULT_LANGUAGE = "en"

    /** קבלת השפה הנוכחית */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /** שמירת שפה חדשה */
    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    /** האם השפה הנוכחית היא עברית? */
    fun isHebrew(context: Context): Boolean = getLanguage(context) == "iw"

    /** החלפת שפה (toggle) */
    fun toggleLanguage(context: Context): String {
        val newLang = if (isHebrew(context)) "en" else "iw"
        setLanguage(context, newLang)
        return newLang
    }

    /** יצירת Context עם השפה הנבחרת — קוראים מ-attachBaseContext */
    fun applyLocale(context: Context): Context {
        val language = getLanguage(context)
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}
