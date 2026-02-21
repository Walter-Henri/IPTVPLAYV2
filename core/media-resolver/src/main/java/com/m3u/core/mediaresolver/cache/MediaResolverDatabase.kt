package com.m3u.core.mediaresolver.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Database Room para o MediaResolver
 * Armazenado em /data/user/0/<package>/databases/
 * Sobrevive à limpeza de cache do Android
 */
@Database(
    entities = [ResolvedUrlEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ResolvedUrlConverters::class)
abstract class MediaResolverDatabase : RoomDatabase() {
    
    abstract fun resolvedUrlDao(): ResolvedUrlDao
    
    companion object {
        const val DATABASE_NAME = "media_resolver.db"
    }
}
