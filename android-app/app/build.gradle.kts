plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.sparc.studycircle"
    compileSdk = 36

    val ciNdkPath = System.getenv("ANDROID_NDK_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }
    if (ciNdkPath != null) {
        ndkPath = ciNdkPath
    }

    defaultConfig {
        applicationId = "org.sparc.studycircle"
        minSdk = 26
        targetSdk = 36
        versionCode = 393
        versionName = "3.9.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${project.findProperty("SUPABASE_URL") ?: ""}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${project.findProperty("SUPABASE_ANON_KEY") ?: ""}\""
        )
    }

    // Production signing: keystore is supplied ONLY via environment variables
    // (GitHub Secrets in CI). It is never committed to the repository.
    val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Unsigned if no keystore is provided (never falls back to the debug key).
            signingConfig = signingConfigs.findByName("release")
            // R8 enabled: shrinks APK/AAB and automatically embeds proguard.map in AAB for Play Console.
            isMinifyEnabled = true
            isShrinkResources = true
            if (ciNdkPath != null) {
                ndk {
                    debugSymbolLevel = "FULL"
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    testImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Media3 ExoPlayer for Chapter Audio Summaries
    implementation("androidx.media3:media3-exoplayer:1.4.1")

    // Networking & JSON Serialization for Server REST, Auth, Realtime & Edge Functions
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
