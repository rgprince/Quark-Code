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
        versionCode = 11
        versionName = "0.11.0"
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

    packaging {
        // proot MUST live on disk in nativeLibraryDir to be exec-able (W^X
        // blocks exec from anywhere else). Legacy packaging forces extraction.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            // Parent of the per-ABI dirs (AGP expects <srcDir>/<abi>/lib*.so).
            jniLibs.srcDir(
                layout.buildDirectory.dir("generated/proot-jnilibs")
                    .get().asFile.absolutePath
            )
        }
    }
}

// Build-time fetch of the proot launcher suite from Termux packages
// (proot + guest loaders + talloc/shmem). Small, pinned, reviewable — the
// big pieces (Debian rootfs, opencode) still download at runtime on opt-in.
val fetchProotAssets by tasks.registering(Exec::class) {
    val outDir = layout.buildDirectory.dir("generated/proot-jnilibs/arm64-v8a")
    commandLine(
        "python3",
        rootProject.file("scripts/fetch_proot_assets.py").absolutePath,
        "--out", outDir.get().asFile.absolutePath
    )
    outputs.dir(outDir)
}

afterEvaluate {
    tasks.named("preBuild").configure { dependsOn(fetchProotAssets) }
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
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.androidx.work.ktx)
    implementation(libs.coil.kt.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
