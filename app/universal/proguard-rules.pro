# M3U Universal - ProGuard Rules
# Otimizado para Android Mobile, TV e Smart TV

# ===== REGRAS GERAIS =====

# Manter informações de linha para stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manter anotações
-keepattributes *Annotation*

# Manter assinaturas genéricas
-keepattributes Signature

# Manter classes internas
-keepattributes InnerClasses,EnclosingMethod

# ===== KOTLIN =====

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== JETPACK COMPOSE =====

# Compose Runtime
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**

# Compose UI
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.ui.**

# Material3
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**

# ===== HILT / DAGGER =====

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Dagger
-dontwarn com.google.errorprone.annotations.**

# ===== ANDROIDX =====

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Navigation
-keep class androidx.navigation.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# DataStore
-keep class androidx.datastore.** { *; }

# Media3 (ExoPlayer)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# TV
-keep class androidx.tv.** { *; }
-dontwarn androidx.tv.**

# ===== RETROFIT & NETWORKING =====

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ===== M3U EXTENSION =====

# M3U Extension API
-keep class com.m3u.extension.** { *; }
-keep interface com.m3u.extension.** { *; }
-keepclassmembers class * {
    @com.m3u.extension.annotation.* <methods>;
}

# ===== COIL (IMAGE LOADING) =====

-keep class coil.** { *; }
-dontwarn coil.**

# ===== ACRA (CRASH REPORTING) =====

-keep class org.acra.** { *; }
-dontwarn org.acra.**

# ===== TIMBER (LOGGING) =====

-keep class timber.log.** { *; }
-dontwarn timber.log.**

# ===== LOTTIE =====

-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ===== UPNP =====

-keep class net.mm2d.** { *; }
-dontwarn net.mm2d.**

# ===== KTOR =====

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ===== MODELOS DE DADOS =====

# Manter todas as data classes
-keep class * extends java.io.Serializable { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Manter classes de modelo (ajustar conforme necessário)
-keep class com.m3u.data.model.** { *; }
-keep class com.m3u.core.model.** { *; }

# ===== OTIMIZAÇÕES =====

# Otimizações agressivas
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Remover logs em produção
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ===== WARNINGS =====

# Suprimir warnings conhecidos
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn io.netty.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn jdk.jfr.**
-dontwarn reactor.blockhound.integration.**
-dontwarn javax.lang.model.element.**
