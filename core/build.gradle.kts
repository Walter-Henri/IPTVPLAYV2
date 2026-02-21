plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.parcelize)
}

android {
    namespace = "com.m3u.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    ndkVersion = project.property("ndkVersion") as String

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Módulos internos
    api(project(":i18n"))

    // Android core
    implementation(libs.androidx.core.ktx)

    // DataStore
    api(libs.androidx.datastore.preferences)

    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    api(composeBom)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.foundation.layout)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.windowSizeClass)
    api(libs.androidx.compose.material3.adaptive)
    api(libs.androidx.tv.material)

    // Permissions
    implementation(libs.google.accompanist.permissions)

    // Serialization + Network
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.squareup.retrofit2)

    // Media
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.okhttp)

    // Hilt
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)

    // Outros
    api(libs.kotlinx.datetime)
    api(libs.androidx.paging.runtime.ktx)
    api(libs.androidx.paging.compose)
    api(libs.timber)
}
