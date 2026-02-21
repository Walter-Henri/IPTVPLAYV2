package com.m3u.data.database
 
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
 
internal object DatabaseMigrations {
    
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lives ADD COLUMN banned INTEGER NOT NULL DEFAULT 0")
        }
    }
 
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS posts")
        }
    }
 
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // AutoMigration3To4 lida com as mudanças
        }
    }
    
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Nenhuma alteração necessária
        }
    }
    
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Nenhuma alteração necessária
        }
    }
    
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Nenhuma alteração necessária
        }
    }
 
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN pinned_groups TEXT NOT NULL DEFAULT '[]'")
        }
    }
 
    @RenameColumn(
        tableName = "streams",
        fromColumnName = "banned",
        toColumnName = "hidden"
    )
    class AutoMigration8To9 : AutoMigrationSpec
 
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN hidden_groups TEXT NOT NULL DEFAULT '[]'")
        }
    }
 
    @DeleteColumn(
        tableName = "playlists",
        columnName = "epg_url"
    )
    class AutoMigrate14To16 : AutoMigrationSpec
 
    @RenameColumn.Entries(
        RenameColumn(
            tableName = "streams",
            fromColumnName = "playlistUrl",
            toColumnName = "playlist_url"
        ),
        RenameColumn(
            tableName = "streams",
            fromColumnName = "channel_id",
            toColumnName = "relation_id"
        ),
        RenameColumn(
            tableName = "playlists",
            fromColumnName = "pinned_groups",
            toColumnName = "pinned_categories"
        ),
        RenameColumn(
            tableName = "playlists",
            fromColumnName = "hidden_groups",
            toColumnName = "hidden_categories"
        ),
        RenameColumn(
            tableName = "programmes",
            fromColumnName = "channel_id",
            toColumnName = "relation_id"
        )
    )
    @DeleteColumn.Entries(
        DeleteColumn(tableName = "programmes", columnName = "new"),
        DeleteColumn(tableName = "programmes", columnName = "live"),
        DeleteColumn(tableName = "programmes", columnName = "previous_start")
    )
    class AutoMigrate18To19: AutoMigrationSpec
 
 
    val ALL_MIGRATIONS = listOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_10_11
    )
}
