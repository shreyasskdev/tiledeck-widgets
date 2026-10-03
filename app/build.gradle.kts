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
        versionCode = 1
        versionName = "2.0.0"
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
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.14.0-alpha03")
    implementation("androidx.compose.ui:ui:1.13.0-alpha03")
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.13.0-alpha03")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Home screen widget (Jetpack Compose for widgets)
    implementation("androidx.glance:glance-appwidget:1.3.0-alpha02")
    implementation("androidx.glance:glance-material3:1.3.0-alpha02")

    // Background refresh
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Encrypted local storage for Etlab credentials
    implementation("androidx.security:security-crypto:1.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.12.1")
    implementation("androidx.graphics:graphics-shapes:1.1.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // OTA updates
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")

    // NEW — Glance preview support
    implementation("androidx.glance:glance-appwidget-preview:1.3.0-alpha02")
    implementation("androidx.glance:glance-preview:1.3.0-alpha02")
}
