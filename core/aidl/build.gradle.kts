plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "com.m3u.core.aidl"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    buildFeatures {
        aidl = true
    }
}
