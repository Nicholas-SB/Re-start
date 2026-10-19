import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)  // code generator Room uses to write the database code for us
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "io.github.nicholas_sb.restart"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.nicholas_sb.restart"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The downloader ships Python + ffmpeg as native code for each CPU type. Only build
        // for 64-bit phones (arm64) and the emulator (x86_64) to keep the app smaller.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // The key that signs the release APK. Its passwords live in keystore.properties, which
    // is never committed (see .gitignore); without that file the release APK is unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val keystore = Properties().apply { keystoreFile.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            // R8: shrinks and optimizes all the code. (Optimizing only some packages left
            // Kotlin's own classes inaccessible to the rest, and the app crashed at start.)
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // The downloader runs yt-dlp/ffmpeg as programs, which only works if their native
        // libraries are unpacked onto the device at install time.
        jniLibs.useLegacyPackaging = true
    }
}

room {
    // Room saves a JSON description of each database version here. Commit these files:
    // they're needed to write and test migrations when the database changes later.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Remove this line (and the Download tab in MusicPlayerApp) to build the player without it.
    implementation(project(":downloader"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.inspector)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}