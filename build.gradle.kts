// Quark root build file — modern plugins block only.
// No Kotlin plugin here (AGP handles Kotlin natively; module applies it).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
