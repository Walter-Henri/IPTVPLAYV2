package com.m3u.core.mediaresolver.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.m3u.core.mediaresolver.StreamFormat

/**
 * Entidade Room para armazenar URLs resolvidas
 * Armazenada no storage interno privado (/data/user/0/...)
 * Sobrevive à limpeza de cache, removida apenas com "Limpar Dados"
 */
@Entity(tableName = "resolved_urls")
@TypeConverters(ResolvedUrlConverters::class)
data class ResolvedUrlEntity(
    @PrimaryKey
    val originalUrl: String,
    
    val resolvedUrl: String,
    
    val timestamp: Long,
    
    val expiresAt: Long,
    
    val quality: String?,
    
    val format: StreamFormat,
    
    val headers: Map<String, String>
) {
    /**
     * Verifica se a URL ainda é válida baseado no timestamp
     */
    fun isValid(): Boolean {
        return System.currentTimeMillis() < expiresAt
    }
    
    /**
     * Calcula tempo restante até expiração em milissegundos
     */
    fun timeUntilExpiration(): Long {
        return expiresAt - System.currentTimeMillis()
    }
    
    /**
     * Verifica se precisa atualização (baseado na regra de 5 horas)
     */
    fun needsUpdate(updateThresholdHours: Int = 5): Boolean {
        val currentTime = System.currentTimeMillis()
        val thresholdMillis = updateThresholdHours * 60 * 60 * 1000L
        return (currentTime - timestamp) >= thresholdMillis
    }
}

/**
 * Converters para tipos complexos do Room
 */
class ResolvedUrlConverters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromStreamFormat(format: StreamFormat): String {
        return format.name
    }
    
    @TypeConverter
    fun toStreamFormat(value: String): StreamFormat {
        return try {
            StreamFormat.valueOf(value)
        } catch (e: Exception) {
            StreamFormat.UNKNOWN
        }
    }
    
    @TypeConverter
    fun fromHeaders(headers: Map<String, String>): String {
        return gson.toJson(headers)
    }
    
    @TypeConverter
    fun toHeaders(value: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(value, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
