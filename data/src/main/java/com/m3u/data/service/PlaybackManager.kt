package com.m3u.data.service
 
import android.content.Context
import android.content.Intent
import timber.log.Timber
import com.m3u.core.Contracts
import com.m3u.core.architecture.preferences.AppPreferences
import com.m3u.core.util.network.NetworkSentinel
import com.m3u.data.repository.channel.ChannelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
 
/**
 * PlaybackManager - Gerencia a lógica de auto-resume e inicialização de reprodução
 * 
 * Responsável por:
 * - Verificar conectividade antes de iniciar playback
 * - Retomar o último canal reproduzido automaticamente
 * - Gerenciar delays e timeouts de rede de forma configurável
 * - Salvar e limpar estado de reprodução
 * - Lançar PlayerActivity com canais específicos
 */
@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStateManager: PlaybackStateManager,
    private val channelRepository: ChannelRepository,
    private val settings: AppPreferences  // Adicionado para timeout configurável
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "PlaybackManager"
        // Timeout configurável via settings.connectTimeout
        // Valor padrão em segundos, não mais hardcoded
    }
    
    /**
     * Tenta retomar o último canal reproduzido
     * 
     * @param onSuccess Callback executado quando a reprodução é iniciada com sucesso
     * @param onFailure Callback executado quando a reprodução falha
     * @param force Se true, ignora verificações de sessão (útil para retomada manual)
     */
    suspend fun attemptAutoResume(
        onSuccess: (channelId: Int) -> Unit = {},
        onFailure: (reason: String) -> Unit = {},
        force: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Verifica se o auto-resume já foi executado nesta sessão
                if (!force && playbackStateManager.hasSessionAutoResumeTriggered()) {
                    Timber.tag(TAG).d("Auto-resume já executado nesta sessão, ignorando nova tentativa")
                    ErrorReporter.log(context, TAG, "Auto-resume ignorado: já executado na sessão")
                    onFailure("Auto-resume já executado")
                    return@withContext
                }
                
                // Verifica se o auto-resume está habilitado nas configurações
                if (!playbackStateManager.isAutoResumeEnabled()) {
                    Timber.tag(TAG).d("Auto-resume não está habilitado")
                    ErrorReporter.log(context, TAG, "Auto-resume desabilitado pelo usuário")
                    onFailure("Auto-resume desabilitado")
                    return@withContext
                }
                
                // Marca que o auto-resume foi tentado nesta sessão
                if (!force) {
                    playbackStateManager.markSessionAutoResumeTriggered()
                }
                
                // Obtém o delay de inicialização configurado pelo usuário
                val startupDelay = playbackStateManager.getStartupDelay()
                Timber.tag(TAG).d("Aplicando delay de inicialização: %ss", startupDelay)
                NetworkSentinel.applyStartupDelay(startupDelay)
                
                // Usa timeout configurável nas preferências em vez de valor hardcoded
                val networkTimeoutMs = settings.connectTimeout.value.coerceAtLeast(5000)
                val networkTimeoutSeconds = (networkTimeoutMs / 1000).toInt().coerceAtLeast(5)
                
                Timber.tag(TAG).d("Verificando conectividade de rede (timeout: %ss)...", networkTimeoutSeconds)
                val hasNetwork = NetworkSentinel.waitForNetwork(context, networkTimeoutSeconds)
                
                if (!hasNetwork) {
                    Timber.tag(TAG).w("Rede não disponível após %ss", networkTimeoutSeconds)
                    ErrorReporter.log(context, TAG, "Falha no auto-resume: rede indisponível após ${networkTimeoutSeconds}s")
                    onFailure("Sem conexão de rede")
                    return@withContext
                }
                
                Timber.tag(TAG).d("Rede disponível, recuperando estado de reprodução...")
                
                // Recupera o estado de reprodução salvo
                val playbackState = playbackStateManager.getPlaybackState()
                if (playbackState.channelId <= 0) {
                    Timber.tag(TAG).d("Nenhum estado de reprodução válido encontrado")
                    ErrorReporter.log(context, TAG, "Falha no auto-resume: nenhum canal salvo válido")
                    onFailure("Nenhum canal salvo")
                    return@withContext
                }
                
                Timber.tag(TAG).d("Estado recuperado: channelId=%s, url=%s", 
                    playbackState.channelId, playbackState.streamUrl)
                
                // Valida se o canal ainda existe no banco de dados
                val channel = channelRepository.get(playbackState.channelId)
                
                if (channel == null) {
                    Timber.tag(TAG).w("Canal %s não encontrado no banco de dados", playbackState.channelId)
                    ErrorReporter.log(context, TAG, "Falha no auto-resume: canal ${playbackState.channelId} inexistente no banco")
                    onFailure("Canal não encontrado")
                    return@withContext
                }
                
                Timber.tag(TAG).d("Canal validado, iniciando reprodução...")
                
                // Inicia a reprodução
                withContext(Dispatchers.Main) {
                    onSuccess(playbackState.channelId)
                }
                
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Erro ao tentar auto-resume")
                ErrorReporter.log(context, TAG, "Exceção ao tentar auto-resume", e)
                onFailure("Erro: ${e.message}")
            }
        }
    }
    
    /**
     * Lança a PlayerActivity com o canal especificado
     * 
     * @param channelId ID do canal a ser reproduzido
     * @param fullScreen Se true, limpa a stack de atividades e abre em tela cheia
     */
    fun launchPlayerActivity(channelId: Int, fullScreen: Boolean = false) {
        try {
            val engine = settings.playerEngine.value
            Timber.tag(TAG).d("launchPlayerActivity: engine=$engine, channelId=$channelId")
            
            val targetClassName = when (engine) {
                0, 2 -> "com.m3u.universal.ui.player.WebPlayerActivity"
                else -> "com.m3u.universal.ui.player.PlayerActivity"
            }
            
            val playerActivityClass = runCatching {
                Class.forName(targetClassName)
            }.getOrElse {
                Timber.tag(TAG).w("Classe $targetClassName não encontrada, usando PlayerActivity padrão")
                Class.forName("com.m3u.universal.ui.player.PlayerActivity")
            }
            
            val intent = Intent(context, playerActivityClass).apply {
                putExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_ID, channelId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (fullScreen) {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            context.startActivity(intent)
            Timber.tag(TAG).d("${playerActivityClass.simpleName} lançada com channelId=%s", channelId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Erro ao lançar PlayerActivity")
        }
    }
    
    /**
     * Salva o estado de reprodução quando um canal é reproduzido com sucesso
     * 
     * @param channelId ID do canal sendo reproduzido
     * @param streamUrl URL do stream sendo reproduzido
     * @param categoryName Nome da categoria do canal (opcional)
     * @param playlistUrl URL da playlist (opcional)
     */
    fun onPlaybackStarted(
        channelId: Int,
        streamUrl: String,
        categoryName: String = "",
        playlistUrl: String = ""
    ) {
        scope.launch {
            try {
                playbackStateManager.savePlaybackState(
                    channelId = channelId,
                    streamUrl = streamUrl,
                    categoryName = categoryName,
                    playlistUrl = playlistUrl
                )
                Timber.tag(TAG).d("Estado de reprodução salvo: channelId=%s", channelId)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Erro ao salvar estado de reprodução")
            }
        }
    }
    
    /**
     * Limpa o estado de reprodução salvo
     * Útil quando o usuário quer resetar a posição de reprodução ou ao fazer logout
     */
    fun clearPlaybackState() {
        scope.launch {
            try {
                playbackStateManager.clearPlaybackState()
                Timber.tag(TAG).d("Estado de reprodução limpo")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Erro ao limpar estado de reprodução")
            }
        }
    }
    
    /**
     * Verifica se o dispositivo tem conexão de rede
     * Usa o timeout configurável pelo usuário
     * 
     * @return true se há conexão de rede disponível
     */
    suspend fun hasNetworkConnection(): Boolean {
        val networkTimeoutMs = settings.connectTimeout.value.coerceAtLeast(5000)
        val networkTimeoutSeconds = (networkTimeoutMs / 1000).toInt().coerceAtLeast(5)
        return NetworkSentinel.waitForNetwork(context, networkTimeoutSeconds)
    }
}
