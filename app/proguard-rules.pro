# MacroKey ProGuard Rules

# Google Play Billing
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# Room Database
-keep class com.macrokey.data.** { *; }

# Keep Compose
-dontwarn androidx.compose.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep MacroKey billing (trial/purchase validation)
-keep class com.macrokey.billing.** { *; }

# Accessibility service
-keep class com.macrokey.service.MacroKeyAccessibilityService { *; }
