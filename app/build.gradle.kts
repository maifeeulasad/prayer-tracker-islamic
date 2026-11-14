plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    signingConfigs {
        create("release") {
            storeFile = file("../secret/key-store.jks")
            storePassword = project.findProperty("PRAYER_TRACKER_STORE_PASSWORD") as String
            keyAlias = project.findProperty("PRAYER_TRACKER_KEY_ALIAS") as String
            keyPassword = project.findProperty("PRAYER_TRACKER_KEY_PASSWORD") as String
        }
    }

    namespace = "com.mua.prayertracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mua.prayertracker"
        minSdk = 24
        targetSdk = 37
        versionCode = 14
        versionName = "1.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // No native code in this app; dependencies ship arm/x86 variants only.
        // Restricting ABIs keeps the universal APK small (Play bundles still
        // split per-ABI automatically, so this is a no-op for the bundle).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module",
                "/META-INF/versions/9/previous-compilation-data.bin",
                "/kotlin/**",
                "/META-INF/*.version",
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // Keep the generated dependency metadata out of the APK/AAB for a small
    // size win; Play Console still receives it via the upload path.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.play.services.location)
    implementation(libs.coroutines.play.services)
    implementation(libs.accompanist.permissions)
    implementation(libs.minidb)
    implementation(libs.velvet)
}