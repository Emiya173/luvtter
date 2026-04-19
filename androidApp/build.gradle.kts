plugins {
    // AGP 9: application 插件已内置 Kotlin 支持,无需单独 apply kotlin-android
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.letter.app.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.letter.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":composeApp"))

    implementation(libs.androidx.activity.compose)
}
