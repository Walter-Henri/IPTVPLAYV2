package com.m3u.core.mediaresolver.di

import android.content.Context
import androidx.room.Room
import com.m3u.core.mediaresolver.*
import com.m3u.core.mediaresolver.cache.MediaResolverDatabase
import com.m3u.core.mediaresolver.cache.ResolvedUrlDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para injeção de dependências do MediaResolver
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaResolverModule {
    
    /**
     * Provê configuração do MediaResolver
     */
    @Provides
    @Singleton
    fun provideMediaResolverConfig(): MediaResolverConfig {
        return MediaResolverConfig(
            userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            connectTimeout = 15000L,
            readTimeout = 15000L,
            followRedirects = true,
            maxRedirects = 10,
            cacheValidityHours = 5,
            preferredQuality = "best",
            preferredFormat = StreamFormat.HLS,
            liveBufferMs = 0,
            enableDecryption = true
        )
    }
    
    /**
     * Provê database Room
     */
    @Provides
    @Singleton
    fun provideMediaResolverDatabase(
        @ApplicationContext context: Context
    ): MediaResolverDatabase {
        return Room.databaseBuilder(
            context,
            MediaResolverDatabase::class.java,
            MediaResolverDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    /**
     * Provê DAO
     */
    @Provides
    @Singleton
    fun provideResolvedUrlDao(
        database: MediaResolverDatabase
    ): ResolvedUrlDao {
        return database.resolvedUrlDao()
    }
    
    /**
     * Provê RedirectResolver
     */
    @Provides
    @Singleton
    fun provideRedirectResolver(
        config: MediaResolverConfig
    ): RedirectResolver {
        return RedirectResolver(config)
    }
    
    
    /**
     * Provê UrlCache
     */
    @Provides
    @Singleton
    fun provideUrlCache(
        database: MediaResolverDatabase,
        config: MediaResolverConfig
    ): UrlCache {
        return UrlCache(database, config)
    }

    @Provides
    @Singleton
    fun provideMediaResolver(
        @ApplicationContext context: Context,
        redirectResolver: RedirectResolver,
        urlCache: UrlCache,
        config: MediaResolverConfig
    ): MediaResolver {
        return MediaResolverImpl(context, redirectResolver, urlCache, config)
    }

    /**
     * Provê UrlUpdateManager
     */
    @Provides
    @Singleton
    fun provideUrlUpdateManager(
        mediaResolver: MediaResolver,
        urlCache: UrlCache,
        config: MediaResolverConfig
    ): UrlUpdateManager {
        return UrlUpdateManager(mediaResolver, urlCache, config)
    }
}