import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.parcelize)
}

// Redireciona o diretório de build para uma pasta padrão acessível
// layout.buildDirectory.set(rootProject.file("app/APK BUILD/Universal"))

android {
    namespace = "com.m3u.universal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.m3u.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-universal"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations.addAll(listOf("en", "pt-rBR"))

        manifestPlaceholders += mapOf(
            "description" to "M3U Universal App with Extension Support",
            "version" to "1",
            "mainClass" to ".MainActivity"
        )

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    ndkVersion = project.property("ndkVersion") as String

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { 
                    localProperties.load(it) 
                }
            }

            // Prioritiza Environment Variables (CI) > local.properties > Default
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")
                ?: localProperties["RELEASE_STORE_FILE"] as? String
                ?: "meu-app.keystore"

            val storeFileObj = if (File(storeFilePath).isAbsolute) {
                File(storeFilePath)
            } else {
                rootProject.file(storeFilePath)
            }

            if (storeFileObj.exists()) {
                storeFile = storeFileObj
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: localProperties["RELEASE_STORE_PASSWORD"] as? String
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: localProperties["RELEASE_KEY_ALIAS"] as? String
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: localProperties["RELEASE_KEY_PASSWORD"] as? String
            } else {
                val userDebug = file(System.getProperty("user.home") + "/.android/debug.keystore")
                if (userDebug.exists()) {
                    storeFile = userDebug
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                listOf(
                    "-Xcontext-receivers",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
                    "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                    "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
                    "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                    "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
                    "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api"
                )
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "META-INF/**"
    }

    lint {
        abortOnError = false
        disable += setOf(
            "PermissionLaunchedDuringComposition"
        )
    }
}

dependencies {
    implementation(project(":i18n"))
    implementation(project(":core"))
    implementation(project(":core:media-resolver"))
    implementation(project(":core:foundation"))
    implementation(project(":core:extension"))
    implementation(project(":core:aidl"))
    implementation(project(":data"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.m3u.extension.api)
    implementation(libs.m3u.extension.annotation)
    ksp(libs.m3u.extension.processor)
    // business
    implementation(project(":business:foryou"))
    implementation(project(":business:favorite"))
    implementation(project(":business:setting"))
    implementation(project(":business:playlist"))
    implementation(project(":business:channel"))
    implementation(project(":business:playlist-configuration"))
    implementation(project(":business:extension"))
    // baselineprofile
    implementation(libs.androidx.profileinstaller)
    // "baselineProfile"(project(":baselineprofile:smartphone")) // TODO: Create module :baselineprofile:smartphone
    // base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.google.material)
    // lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    // work
    implementation(libs.androidx.work.runtime.ktx)
    // dagger
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    // compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.navigation.compose)
    // compose-material3
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    // glance
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // accompanist
    implementation(libs.google.accompanist.permissions)
    // performance
    debugImplementation(libs.squareup.leakcanary)
    // other
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.io.coil.kt)
    implementation(libs.io.coil.kt.compose)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.datasource.rtmp)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.airbnb.lottie.compose)
    implementation(libs.minabox)
    implementation(libs.net.mm2d.mmupnp)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.acra.notification)
    implementation(libs.acra.mail)
    // gson
    implementation(libs.gson)
}
