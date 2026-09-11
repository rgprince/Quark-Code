plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rg.quarkcode"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rg.quarkcode"
        minSdk = 28
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"
    }

    signingConfigs {
        // Stable dummy key checked into the repo so every CI build shares
        // one signature: `adb install -r` updates instead of forcing
        // uninstall/reinstall. Replace with a real keystore before Play.
        create("quarkDemo") {
            storeFile = rootProject.file("keystore/dummy.jks")
            storePassword = "quarkdemo"
            keyAlias = "quarkdemo"
            keyPassword = "quarkdemo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("quarkDemo")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.androidx.work.ktx)
    implementation(libs.coil.kt.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
