plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.shreyasskdev.tiledeck"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.shreyasskdev.tiledeck"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "2.5.0"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
                "x86"
            )
            isUniversalApk = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.14.0-alpha03")
    implementation("androidx.compose.ui:ui:1.13.0-beta01")
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Home screen widget (Jetpack Compose for widgets)
    implementation("androidx.glance:glance-appwidget:1.3.0-alpha02")
    implementation("androidx.glance:glance-material3:1.3.0-alpha02")

    // Background refresh
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Encrypted local storage for Etlab credentials
    implementation("androidx.security:security-crypto:1.1.0")

    implementation("androidx.graphics:graphics-shapes:1.1.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // OTA updates
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")
}
