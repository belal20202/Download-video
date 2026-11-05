plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.videodl.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.videodl.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // مفتاح التوقيع يأتي من متغيرات البيئة (GitHub Secrets) ولا يُكتب في الكود أبداً
    val ksPath: String? = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (!ksPath.isNullOrEmpty() && File(ksPath).exists()) {
            create("release") {
                storeFile = File(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures { compose = true }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("io.github.junkfood02.youtubedl-android:library:0.17.2")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
