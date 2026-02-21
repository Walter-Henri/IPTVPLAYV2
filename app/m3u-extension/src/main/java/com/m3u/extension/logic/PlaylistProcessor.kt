package com.m3u.extension.logic

import android.util.Log

/**
 * PlaylistProcessor - Especializado em Normalização e Padronização de Listas IPTV
 */
object PlaylistProcessor {
    private const val TAG = "PlaylistProcessor"

    /**
     * Normaliza o nome do canal (Versão menos agressiva para manter organização)
     */
    fun normalizeName(name: String): String {
        // Remove apenas espaços extras e trim, mantendo a formatação original
        return name.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Mapeia um canal para uma categoria, respeitando o grupo original
     */
    fun inferCategory(name: String, currentGroup: String?): String {
        // Se já existe um grupo válido, respeitá-lo (Correção para "lista desorganizada")
        if (!currentGroup.isNullOrBlank() && !currentGroup.equals("General", ignoreCase = true) && !currentGroup.equals("Ungrouped", ignoreCase = true)) {
            return currentGroup
        }

        val cleanName = name.lowercase()

        return when {
            cleanName.contains("espn") || cleanName.contains("sport") || cleanName.contains("fox") || cleanName.contains("premiere") -> "ESPORTES"
            cleanName.contains("cartoon") || cleanName.contains("nick") || cleanName.contains("disney") || cleanName.contains("kids") -> "INFANTIL"
            cleanName.contains("globo") || cleanName.contains("record") || cleanName.contains("sbt") || cleanName.contains("band") -> "CANAIS ABERTOS"
            cleanName.contains("series") || cleanName.contains("netflix") || cleanName.contains("prime") -> "SÉRIES"
            cleanName.contains("news") || cleanName.contains("cnn") || cleanName.contains("jovem pan") -> "NOTÍCIAS"
            !currentGroup.isNullOrBlank() && currentGroup != "General" -> currentGroup.uppercase()
            else -> "VARIEDADES"
        }
    }

    /**
     * Garante que o logo seja uma URL válida ou fornece um fallback
     */
    fun validateLogo(logo: String?, channelName: String): String {
        if (logo.isNullOrBlank() || !logo.startsWith("http")) {
            val name = channelName.lowercase()
            return when {
                name.contains("globo") -> "https://raw.githubusercontent.com/Walter-Henri/Projeto-Play/main/assets/logos/globo.png"
                name.contains("sbt") -> "https://raw.githubusercontent.com/Walter-Henri/Projeto-Play/main/assets/logos/sbt.png"
                name.contains("record") -> "https://raw.githubusercontent.com/Walter-Henri/Projeto-Play/main/assets/logos/record.png"
                name.contains("band") -> "https://raw.githubusercontent.com/Walter-Henri/Projeto-Play/main/assets/logos/band.png"
                name.contains("espn") -> "https://raw.githubusercontent.com/Walter-Henri/Projeto-Play/main/assets/logos/espn.png"
                name.contains("fox") -> "https://raw.githubusercontent.com/Walter-Henri/Projeto-Play/main/assets/logos/fox.png"
                name.contains("hbo") -> "https://raw.githubusercontent.com/Walter-Henri/Projeto-Play/main/assets/logos/hbo.png"
                name.contains("telecine") -> "https://raw.githubusercontent.com/Walter-Henri/Projeto-Play/main/assets/logos/telecine.png"
                else -> "" // Retorna vazio para exibir ícone padrão em vez de link quebrado
            }
        }
        return logo
    }

    /**
     * Gera uma entrada M3U formatada profissionalmente com suporte a Headers
     */
    fun generateM3UEntry(
        name: String,
        url: String,
        logo: String,
        group: String,
        headers: Map<String, String>? = null,
        id: String = ""
    ): String {
        val cleanName = normalizeName(name)
        val category = inferCategory(name, group)
        
        // Injetar headers na URL no formato Kodi para compatibilidade universal
        val finalUrl = if (headers != null && headers.isNotEmpty()) {
            val options = headers.map { "${it.key}=${it.value}" }.joinToString("&")
            if (url.contains("|")) "$url&$options" else "$url|$options"
        } else url
        
        // Padrão Profissional M3U
        return """
            #EXTINF:-1 tvg-id="$id" tvg-name="$cleanName" tvg-logo="$logo" group-title="$category",$cleanName
            $finalUrl
        """.trimIndent()
    }
}
