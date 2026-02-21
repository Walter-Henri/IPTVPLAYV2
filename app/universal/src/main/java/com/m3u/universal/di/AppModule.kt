package com.m3u.universal.di

import android.os.Build
import androidx.work.Configuration
import com.m3u.core.architecture.Abi
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.universal.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePublisher(): Publisher {
        return object : Publisher {
            override val applicationId: String = BuildConfig.APPLICATION_ID
            override val versionName: String = BuildConfig.VERSION_NAME
            override val versionCode: Int = BuildConfig.VERSION_CODE
            override val debug: Boolean = BuildConfig.DEBUG
            override val model: String = Build.MODEL
            override val abi: Abi = Abi.of(Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        }
    }

    @Provides
    @Singleton
    fun provideWorkConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()
    }

    @Provides
    @Singleton
    fun provideLogger(): Logger = object : Logger {
        override fun log(message: String, level: Int, tag: String) {
            android.util.Log.println(level, tag, message)
        }
    }
}
