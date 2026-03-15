package com.macrokey.billing

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * TrialManager — manages the 7-day free trial period.
 *
 * Security layers:
 * 1. HMAC-style checksum using Android ID to prevent SharedPreferences tampering.
 * 2. Elapsed realtime tracking to detect clock manipulation.
 * 3. Clear-data detection via Android ID anchor.
 */
object TrialManager {

    private const val PREFS_NAME = "macrokey_trial"
    private const val KEY_FIRST_INSTALL = "first_install_timestamp"
    private const val KEY_ELAPSED_BASE = "elapsed_base"
    private const val KEY_PRO_PURCHASED = "pro_purchased"
    private const val KEY_PURCHASE_TOKEN = "purchase_token"
    private const val KEY_PURCHASE_CHECK = "purchase_check"
    private const val KEY_CHECKSUM = "install_check"
    private const val TRIAL_DAYS = 7L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Generates a checksum that ties the install timestamp to the device,
     * making it harder to tamper with via SharedPreferences editors.
     */
    private fun computeChecksum(context: Context, installTime: Long): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "fallback_id"
        val input = "$installTime:$androidId:macrokey_salt_2024"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Call once on first launch to record the install timestamp.
     */
    fun recordInstallIfNeeded(context: Context) {
        val sp = prefs(context)
        if (sp.getLong(KEY_FIRST_INSTALL, 0L) == 0L) {
            val now = System.currentTimeMillis()
            val elapsedBase = SystemClock.elapsedRealtime()
            sp.edit()
                .putLong(KEY_FIRST_INSTALL, now)
                .putLong(KEY_ELAPSED_BASE, elapsedBase)
                .putString(KEY_CHECKSUM, computeChecksum(context, now))
                .apply()
        }
    }

    /**
     * Returns the app's firstInstallTime from PackageManager.
     * Used as fallback when SharedPreferences are cleared (clear data bypass).
     */
    private fun getPackageInstallTime(context: Context): Long {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
    }

    /**
     * Returns true if the 7-day trial is still active.
     * Detects clock manipulation via elapsed realtime cross-check.
     * Detects clear-data bypass via PackageManager.firstInstallTime fallback.
     */
    fun isTrialActive(context: Context): Boolean {
        val sp = prefs(context)
        var installTime = sp.getLong(KEY_FIRST_INSTALL, 0L)
        if (installTime == 0L) {
            // SharedPreferences cleared — check PackageManager as fallback
            val pkgInstallTime = getPackageInstallTime(context)
            if (pkgInstallTime > 0L) {
                val elapsed = System.currentTimeMillis() - pkgInstallTime
                if (elapsed > TimeUnit.DAYS.toMillis(TRIAL_DAYS)) {
                    // App was installed more than 7 days ago — trial expired
                    return false
                }
                // App was installed recently — re-record and continue
                installTime = pkgInstallTime
                val elapsedBase = SystemClock.elapsedRealtime() - elapsed
                sp.edit()
                    .putLong(KEY_FIRST_INSTALL, installTime)
                    .putLong(KEY_ELAPSED_BASE, elapsedBase.coerceAtLeast(0L))
                    .putString(KEY_CHECKSUM, computeChecksum(context, installTime))
                    .apply()
            } else {
                return true // genuinely first launch
            }
        }

        // Verify checksum to detect SharedPreferences tampering
        val storedCheck = sp.getString(KEY_CHECKSUM, null)
        if (storedCheck != computeChecksum(context, installTime)) return false

        val now = System.currentTimeMillis()

        // Clock set backwards? Trial invalid.
        if (now < installTime) return false

        val wallElapsed = now - installTime
        val trialMillis = TimeUnit.DAYS.toMillis(TRIAL_DAYS)

        // Cross-check with elapsed realtime to detect forward clock manipulation
        val elapsedBase = sp.getLong(KEY_ELAPSED_BASE, 0L)
        if (elapsedBase > 0L) {
            val realtimeElapsed = SystemClock.elapsedRealtime() - elapsedBase
            // If wall clock says much less time passed than realtime, clock was set back
            // Use the larger of the two elapsed times
            val effectiveElapsed = maxOf(wallElapsed, realtimeElapsed)
            return effectiveElapsed < trialMillis
        }

        return wallElapsed < trialMillis
    }

    /**
     * Returns how many full days remain in the trial (0..7).
     */
    fun daysRemaining(context: Context): Int {
        val sp = prefs(context)
        var installTime = sp.getLong(KEY_FIRST_INSTALL, 0L)
        if (installTime == 0L) {
            // Fallback to PackageManager if prefs were cleared
            val pkgInstallTime = getPackageInstallTime(context)
            if (pkgInstallTime > 0L) {
                installTime = pkgInstallTime
            } else {
                return TRIAL_DAYS.toInt()
            }
        }

        val now = System.currentTimeMillis()
        if (now < installTime) return 0

        val wallElapsed = now - installTime

        // Cross-check with elapsed realtime
        val elapsedBase = sp.getLong(KEY_ELAPSED_BASE, 0L)
        val effectiveElapsed = if (elapsedBase > 0L) {
            val realtimeElapsed = SystemClock.elapsedRealtime() - elapsedBase
            maxOf(wallElapsed, realtimeElapsed)
        } else {
            wallElapsed
        }

        val remaining = TRIAL_DAYS - TimeUnit.MILLISECONDS.toDays(effectiveElapsed)
        return remaining.coerceIn(0, TRIAL_DAYS).toInt()
    }

    /**
     * Computes a device-specific purchase verification hash.
     * Prevents simply setting pro_purchased=true in SharedPreferences.
     */
    private fun computePurchaseCheck(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "fallback_id"
        val input = "purchased:$androidId:macrokey_pro_2024"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns true if the user completed the in-app purchase.
     * Validates purchase token AND device-specific checksum (not just a boolean flag).
     */
    fun hasPurchasedPro(context: Context): Boolean {
        val sp = prefs(context)
        if (!sp.getBoolean(KEY_PRO_PURCHASED, false)) return false
        if (sp.getString(KEY_PURCHASE_TOKEN, null) == null) return false
        // Verify device-specific purchase checksum
        val storedCheck = sp.getString(KEY_PURCHASE_CHECK, null)
        return storedCheck == computePurchaseCheck(context)
    }

    /**
     * Mark Pro as purchased with the purchase token for validation.
     * Called from BillingManager after successful purchase + acknowledgement.
     */
    fun setPurchased(context: Context, purchaseToken: String = "verified") {
        prefs(context).edit()
            .putBoolean(KEY_PRO_PURCHASED, true)
            .putString(KEY_PURCHASE_TOKEN, purchaseToken)
            .putString(KEY_PURCHASE_CHECK, computePurchaseCheck(context))
            .apply()
    }

    /**
     * Returns true if user can use the app (trial active OR pro purchased).
     */
    fun canUseApp(context: Context): Boolean =
        hasPurchasedPro(context) || isTrialActive(context)
}
