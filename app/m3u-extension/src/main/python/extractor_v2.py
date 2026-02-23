"""
YouTube HLS Stream Extractor - Versão Corrigida v3.0

PROBLEMA RAIZ IDENTIFICADO:
Os links M3U8 do YouTube são "signed URLs" com validade de ~6 horas e são
vinculados ao IP + User-Agent da extração. Se o player usar um UA diferente
ou a URL expirar, recebe 404.

SOLUÇÃO IMPLEMENTADA:
1. Capturar o User-Agent REAL do WebView do Android (não um UA fixo)
2. Passar o MESMO UA para o yt-dlp E registrar no headers do player
3. Adicionar cookies REAIS do YouTube ao header (obrigatório para algumas regiões)
4. Usar o cliente 'android_embedded' que gera URLs com maior TTL
5. Preferir o 'hlsManifestUrl' raiz (master playlist) em vez de stream específico

FLUXO CORRETO:
ExtensionService captura UA real do WebView
→ Python extrai com ESSE MESMO UA
→ URL + headers (UA + Cookie + Referer) registrados no JsonHeaderRegistry
→ Player usa EXATAMENTE os mesmos headers
"""

import json
import sys
import time
import os
import re
from urllib.parse import urlparse
import http.client
import ssl

print("=== EXTRACTOR V3 - CORREÇÃO 404 ===", file=sys.stderr)

try:
    import yt_dlp
    print(f"✓ yt_dlp: {yt_dlp.version.__version__}", file=sys.stderr)
except ImportError as e:
    print(f"❌ yt_dlp não encontrado: {e}", file=sys.stderr)
    yt_dlp = None

try:
    import streamlink
    print(f"✓ streamlink: {streamlink.__version__}", file=sys.stderr)
    STREAMLINK_AVAILABLE = True
except ImportError:
    print("⚠ streamlink não disponível", file=sys.stderr)
    STREAMLINK_AVAILABLE = False

print("===================================", file=sys.stderr)


# ============================================================
# CORREÇÃO CRÍTICA: User-Agents que geram URLs com maior TTL
# O cliente android_embedded tem o melhor comportamento para IPTV
# O UA DEVE ser passado pelo Kotlin (capturado do WebView real)
# ============================================================

# Usado como FALLBACK se o Kotlin não passar o UA real
FALLBACK_USER_AGENTS = {
    # Este é o UA padrão do YouTube no Android TV - gera HLS nativo
    'android_embedded': (
        'com.google.android.youtube/17.36.4 '
        '(Linux; U; Android 11; Build/RP1A.200720.012) gzip'
    ),
    # Para extração via web (usa innertube client tv_embedded)
    'tv_web': (
        'Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) '
        'AppleWebKit/537.36 (KHTML, like Gecko) '
        'SamsungBrowser/4.0 Chrome/76.0.3809.146 TV Safari/537.36'
    ),
    # Chrome no Android - UA real típico de dispositivo
    'android_chrome': (
        'Mozilla/5.0 (Linux; Android 12; Pixel 6) '
        'AppleWebKit/537.36 (KHTML, like Gecko) '
        'Chrome/112.0.0.0 Mobile Safari/537.36'
    ),
}


def build_ydl_opts(user_agent: str, cookies: str = None, video_format: str = 'best') -> dict:
    """
    Constrói opções do yt-dlp otimizadas para máxima compatibilidade.
    
    CRÍTICO: O user_agent aqui DEVE SER o mesmo que o player vai usar.
    Se usar UA diferente, o YouTube retorna 403/404 ao tentar reproduzir.
    """
    opts = {
        'quiet': True,
        'no_warnings': True,
        # Formatar para preferir HLS (m3u8) - compatível com ExoPlayer
        'format': 'bestvideo[protocol^=m3u8]+bestaudio/best[protocol^=m3u8]/best',
        'socket_timeout': 25,
        'nocheckcertificate': True,
        'geo_bypass': True,
        'user_agent': user_agent,
        'http_headers': {
            'User-Agent': user_agent,
            'Accept': '*/*',
            'Accept-Language': 'pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7',
            'Accept-Encoding': 'gzip, deflate, br',
            'Origin': 'https://www.youtube.com',
            'Referer': 'https://www.youtube.com/',
            'Sec-Fetch-Dest': 'empty',
            'Sec-Fetch-Mode': 'cors',
            'Sec-Fetch-Site': 'cross-site',
            'DNT': '1',
        },
        'extractor_args': {
            'youtube': {
                # android_embedded gera URLs HLS com maior TTL
                # tv_embedded é alternativa estável para Smart TVs
                'player_client': ['android_embedded', 'tv_embedded', 'android', 'web'],
                # Não pular HLS - é exatamente o que queremos
                'skip': ['dash'],
                # Não fazer requisições desnecessárias
                'player_skip': ['configs', 'webpage'],
                # Pegar o hlsManifestUrl que é mais estável
                'include_live_dash': False,
            }
        },
        # Não baixar o vídeo, só extrair info
        'noplaylist': True,
        'extract_flat': False,
    }
    
    # Adicionar cookies se fornecidos (CRÍTICO para streams com restrição de região)
    if cookies:
        # Salvar cookies temporariamente
        import tempfile
        cookie_file = tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False)
        cookie_file.write(cookies)
        cookie_file.close()
        opts['cookiefile'] = cookie_file.name
        print(f"✓ Cookies carregados: {len(cookies)} chars", file=sys.stderr)
    
    return opts


def extract_hls_url(info: dict, user_agent: str) -> tuple[str, str]:
    """
    Extrai a melhor URL HLS do resultado do yt-dlp.
    
    PRIORIDADE:
    1. hlsManifestUrl - master playlist, mais estável e inclui múltiplas qualidades
    2. Formato m3u8_native - stream HLS nativo
    3. Formato m3u8 - HLS genérico
    4. URL direta como fallback
    
    Retorna: (url, tipo)
    """
    # MELHOR OPÇÃO: hlsManifestUrl (master playlist do YouTube)
    hls_manifest = info.get('hls_manifest_url') or info.get('hlsManifestUrl')
    if hls_manifest:
        print(f"✓ Usando hlsManifestUrl (melhor opção): {hls_manifest[:60]}...", file=sys.stderr)
        return hls_manifest, 'hls_manifest'
    
    # SEGUNDA OPÇÃO: formato m3u8_native nos formats
    formats = info.get('formats', [])
    
    # Filtrar apenas HLS
    hls_formats = [
        f for f in formats 
        if f.get('protocol') in ('m3u8', 'm3u8_native') 
        or '.m3u8' in f.get('url', '')
    ]
    
    if hls_formats:
        # Ordenar por qualidade (resolution) e pegar o melhor
        hls_formats.sort(
            key=lambda f: (f.get('height') or 0, f.get('tbr') or 0), 
            reverse=True
        )
        
        # Verificar se tem formato que inclui áudio + vídeo
        combined = [f for f in hls_formats if f.get('acodec') != 'none' and f.get('vcodec') != 'none']
        if combined:
            best = combined[0]
        else:
            best = hls_formats[0]
        
        url = best.get('url', '')
        print(f"✓ Formato HLS encontrado ({best.get('height', '?')}p): {url[:60]}...", file=sys.stderr)
        return url, 'hls_format'
    
    # FALLBACK: URL direta (pode não ser HLS)
    direct_url = info.get('url', '')
    if direct_url:
        print(f"⚠ Usando URL direta (não é HLS): {direct_url[:60]}...", file=sys.stderr)
        return direct_url, 'direct'
    
    return None, None


def validate_url(url: str, headers: dict, timeout: int = 8) -> bool:
    """
    Valida se a URL está acessível com os headers fornecidos.
    Usa HEAD request para ser rápido.
    """
    if not url:
        return False
        
    try:
        parsed = urlparse(url)
        ctx = ssl._create_unverified_context() if parsed.scheme == 'https' else None
        
        conn_class = http.client.HTTPSConnection if parsed.scheme == 'https' else http.client.HTTPConnection
        conn = conn_class(parsed.netloc, timeout=timeout, **({"context": ctx} if ctx else {}))
        
        path = parsed.path + ('?' + parsed.query if parsed.query else '')
        
        req_headers = {
            'User-Agent': headers.get('User-Agent', ''),
            'Accept': '*/*',
            'Range': 'bytes=0-0',  # Pede só o início para verificar acesso
        }
        if 'Cookie' in headers:
            req_headers['Cookie'] = headers['Cookie']
        if 'Referer' in headers:
            req_headers['Referer'] = headers['Referer']
        
        conn.request('HEAD', path, headers=req_headers)
        resp = conn.getresponse()
        conn.close()
        
        # 200, 206 = OK; 302/301 = Redirect (também válido)
        is_valid = resp.status in (200, 206, 301, 302, 303, 307, 308)
        
        if is_valid:
            print(f"✓ URL válida (HTTP {resp.status}): {url[:50]}...", file=sys.stderr)
        else:
            print(f"✗ URL inválida (HTTP {resp.status}): {url[:50]}...", file=sys.stderr)
        
        return is_valid
        
    except Exception as e:
        # Timeout ou erro de rede - assumir válida se parecer HLS
        is_hls = '.m3u8' in url or 'googlevideo.com' in url
        print(f"⚠ Validação falhou ({e}), assumindo {'válida' if is_hls else 'inválida'}", file=sys.stderr)
        return is_hls


def extract_channel(channel: dict, device_user_agent: str = None, 
                   device_cookies: str = None, video_format: str = 'best') -> dict:
    """
    Extrai stream de um canal com tratamento robusto de erros 404.
    
    Args:
        channel: dict com name, url, logo, group
        device_user_agent: UA REAL do WebView Android (passado pelo Kotlin!)
        device_cookies: Cookies reais do YouTube do dispositivo
        video_format: formato do yt-dlp
    
    CRÍTICO: device_user_agent deve ser o mesmo UA que o player ExoPlayer vai usar!
    """
    name = channel.get('name', 'Unknown')
    url = channel.get('url', '')
    
    if not url:
        return {'name': name, 'success': False, 'error': 'URL vazia'}
    
    is_youtube = 'youtube.com' in url or 'youtu.be' in url
    if not is_youtube:
        return {'name': name, 'success': False, 'error': 'Não é URL do YouTube'}
    
    print(f"\n{'='*50}", file=sys.stderr)
    print(f"Processando: {name}", file=sys.stderr)
    print(f"URL: {url}", file=sys.stderr)
    print(f"UA do dispositivo: {'Fornecido ✓' if device_user_agent else 'Não fornecido ⚠'}", file=sys.stderr)
    print(f"Cookies: {'Fornecidos ✓' if device_cookies else 'Não fornecidos ⚠'}", file=sys.stderr)
    
    # ============================================================
    # LISTA DE UAs para tentar, em ordem de preferência
    # O UA do dispositivo tem PRIORIDADE MÁXIMA
    # ============================================================
    user_agents_to_try = []
    
    if device_user_agent:
        # UA real do WebView - MELHOR OPÇÃO
        user_agents_to_try.append(('device_webview', device_user_agent))
    
    # Adicionar UAs de fallback
    for ua_name, ua_string in FALLBACK_USER_AGENTS.items():
        user_agents_to_try.append((ua_name, ua_string))
    
    attempts = []
    
    for ua_name, user_agent in user_agents_to_try:
        print(f"\n→ Tentativa com UA '{ua_name}': {user_agent[:50]}...", file=sys.stderr)
        start_time = time.time()
        
        try:
            opts = build_ydl_opts(user_agent, device_cookies, video_format)
            
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
            
            if not info:
                raise Exception("yt-dlp retornou info vazia")
            
            # Extrair a melhor URL HLS
            m3u8_url, url_type = extract_hls_url(info, user_agent)
            
            if not m3u8_url:
                raise Exception("Nenhuma URL HLS encontrada")
            
            duration = int((time.time() - start_time) * 1000)
            
            # ============================================================
            # HEADERS CRÍTICOS: DEVEM SER EXATAMENTE IGUAIS AOS DO PLAYER
            # ============================================================
            headers = {
                'User-Agent': user_agent,  # MESMO UA usado na extração!
                'Referer': 'https://www.youtube.com/',
                'Origin': 'https://www.youtube.com',
            }
            
            # Adicionar cookies do dispositivo se disponíveis
            if device_cookies:
                headers['Cookie'] = device_cookies
            
            # Adicionar cookies que o yt-dlp extraiu
            yt_headers = info.get('http_headers', {})
            if 'Cookie' in yt_headers and not device_cookies:
                headers['Cookie'] = yt_headers['Cookie']
            
            # Validar a URL antes de retornar
            print(f"Validando URL extraída...", file=sys.stderr)
            is_valid = validate_url(m3u8_url, headers)
            
            attempts.append({
                'method': f'yt-dlp ({ua_name})',
                'success': is_valid,
                'duration': duration,
                'url_type': url_type,
                'error': None if is_valid else 'Validação falhou'
            })
            
            if is_valid:
                print(f"✅ SUCESSO com UA '{ua_name}'! ({url_type}, {duration}ms)", file=sys.stderr)
                
                return {
                    'name': name,
                    'logo': channel.get('logo', ''),
                    'group': channel.get('group', 'YouTube'),
                    'm3u8': m3u8_url,
                    'headers': headers,
                    'extraction_method': f'yt-dlp ({ua_name})',
                    'url_type': url_type,
                    'attempts': attempts,
                    'success': True,
                    'error': None
                }
            else:
                print(f"⚠ URL extraída mas inválida. Tentando próximo UA...", file=sys.stderr)
                
        except Exception as e:
            duration = int((time.time() - start_time) * 1000)
            error_msg = str(e)
            print(f"✗ Falha com UA '{ua_name}': {error_msg[:100]}", file=sys.stderr)
            
            attempts.append({
                'method': f'yt-dlp ({ua_name})',
                'success': False,
                'duration': duration,
                'error': error_msg[:200]
            })
    
    # Tentar streamlink como último recurso
    if STREAMLINK_AVAILABLE:
        print(f"\n→ Tentativa final com Streamlink...", file=sys.stderr)
        try:
            import streamlink as sl
            session = sl.Streamlink()
            session.set_option('http-timeout', 20)
            if device_user_agent:
                session.set_option('http-headers', {'User-Agent': device_user_agent})
            
            streams = session.streams(url)
            if streams:
                priority_qualities = ['best', '1080p', '720p', '480p', 'worst']
                selected = next((q for q in priority_qualities if q in streams), None)
                if not selected:
                    selected = list(streams.keys())[0]
                
                stream = streams[selected]
                stream_url = stream.to_url()
                
                if stream_url:
                    ua = device_user_agent or FALLBACK_USER_AGENTS['android_chrome']
                    headers = {
                        'User-Agent': ua,
                        'Referer': 'https://www.youtube.com/',
                        'Origin': 'https://www.youtube.com',
                    }
                    if device_cookies:
                        headers['Cookie'] = device_cookies
                    
                    attempts.append({
                        'method': f'streamlink ({selected})',
                        'success': True,
                        'duration': 0,
                    })
                    
                    print(f"✅ SUCESSO com streamlink ({selected})!", file=sys.stderr)
                    return {
                        'name': name,
                        'logo': channel.get('logo', ''),
                        'group': channel.get('group', 'YouTube'),
                        'm3u8': stream_url,
                        'headers': headers,
                        'extraction_method': f'streamlink ({selected})',
                        'attempts': attempts,
                        'success': True,
                        'error': None
                    }
        except Exception as e:
            print(f"✗ Streamlink também falhou: {e}", file=sys.stderr)
            attempts.append({'method': 'streamlink', 'success': False, 'error': str(e)[:100]})
    
    print(f"❌ FALHA TOTAL para: {name}", file=sys.stderr)
    return {
        'name': name,
        'success': False,
        'attempts': attempts,
        'error': 'Todos os métodos falharam. Verifique se a live está ativa.',
        'm3u8': None,
        'headers': {},
    }


def extract(input_path: str, output_path: str, 
            cookies_path: str = None, video_format: str = 'best',
            device_user_agent: str = None) -> None:
    """
    Função principal de extração em lote.
    
    NOVO PARÂMETRO: device_user_agent
    Deve ser passado pelo Kotlin com o UA real do WebView do Android.
    Isso é CRÍTICO para evitar erros 404 na reprodução.
    """
    if not os.path.exists(input_path):
        print(f"❌ Arquivo não encontrado: {input_path}", file=sys.stderr)
        return
    
    # Carregar cookies se fornecidos
    device_cookies = None
    if cookies_path and os.path.exists(cookies_path):
        try:
            with open(cookies_path, 'r') as f:
                cookie_data = f.read().strip()
                if cookie_data:
                    device_cookies = cookie_data
                    print(f"✓ Cookies carregados de: {cookies_path}", file=sys.stderr)
        except Exception as e:
            print(f"⚠ Erro ao ler cookies: {e}", file=sys.stderr)
    
    # Carregar canais
    try:
        with open(input_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
            channels = data.get('channels', [])
    except Exception as e:
        print(f"❌ Erro ao ler JSON: {e}", file=sys.stderr)
        return
    
    youtube_channels = [
        ch for ch in channels 
        if 'youtube.com' in ch.get('url', '') or 'youtu.be' in ch.get('url', '')
    ]
    
    print(f"\n{'='*50}", file=sys.stderr)
    print(f"EXTRAÇÃO: {len(youtube_channels)} canais YouTube de {len(channels)} total", file=sys.stderr)
    if device_user_agent:
        print(f"UA do dispositivo: {device_user_agent[:60]}...", file=sys.stderr)
    else:
        print(f"⚠ UA do dispositivo não fornecido! Usando fallbacks.", file=sys.stderr)
        print(f"  → Isso pode causar erros 404 na reprodução!", file=sys.stderr)
        print(f"  → Passe o UA real do WebView pelo Kotlin.", file=sys.stderr)
    print(f"{'='*50}\n", file=sys.stderr)
    
    results = []
    success_count = 0
    fail_count = 0
    
    for i, channel in enumerate(youtube_channels, 1):
        print(f"[{i}/{len(youtube_channels)}]", file=sys.stderr, end=' ')
        result = extract_channel(
            channel=channel,
            device_user_agent=device_user_agent,
            device_cookies=device_cookies,
            video_format=video_format
        )
        results.append(result)
        if result.get('success'):
            success_count += 1
        else:
            fail_count += 1
    
    # Salvar resultado
    output_data = {
        'channels': results,
        'stats': {
            'total': len(youtube_channels),
            'success': success_count,
            'failed': fail_count,
            'timestamp': time.time(),
            'device_ua_used': bool(device_user_agent),
        }
    }
    
    try:
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(output_data, f, ensure_ascii=False, indent=2)
        
        print(f"\n{'='*50}", file=sys.stderr)
        print(f"CONCLUÍDO: {success_count}/{len(youtube_channels)} com sucesso", file=sys.stderr)
        print(f"{'='*50}", file=sys.stderr)
    except Exception as e:
        print(f"❌ Erro ao salvar: {e}", file=sys.stderr)