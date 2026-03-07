// =============================================================================
// MacroKey – Project-Level build.gradle.kts
// =============================================================================
// This is the root build file. Plugin versions are declared here and applied
// in the app-level module.
// =============================================================================

plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
}
