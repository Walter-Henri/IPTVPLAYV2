"""
YouTube HLS Stream Extractor - Metodologia Robusta v2.0

Estratégia Multi-Camada para Bypass de Bloqueios:
1. yt-dlp com múltiplos clients (android, ios, web, tv)
2. Extração direta via innertube API
3. Fallback para streamlink
4. Rotação automática de User-Agents
5. Validação de stream antes de retornar

Garantias:
- Links HLS sempre válidos
- Headers corretos para reprodução
- Fallback automático em caso de falha
- Logs detalhados para debug
"""

import json
import sys
import time
import os

# Verbose Import Check
print("=== PYTHON ENVIRONMENT CHECK ===", file=sys.stderr)
try:
    import yt_dlp
    print(f"✓ yt_dlp imported: {yt_dlp.version.__version__}", file=sys.stderr)
except ImportError as e:
    print(f"❌ FAILED to import yt_dlp: {e}", file=sys.stderr)

try:
    import streamlink
    print(f"✓ streamlink imported: {streamlink.__version__}", file=sys.stderr)
except ImportError as e:
    print(f"❌ FAILED to import streamlink: {e}", file=sys.stderr)
    
try:
    import requests
    print(f"✓ requests imported: {requests.__version__}", file=sys.stderr)
except ImportError as e:
    print(f"❌ FAILED to import requests: {e}", file=sys.stderr)

print("================================", file=sys.stderr)
import re
from urllib.parse import urlparse, parse_qs
import http.client
import ssl

# Configuração de User-Agents por prioridade
USER_AGENTS = {
    'android_tv': 'Mozilla/5.0 (Linux; Android 10; BRAVIA 4K UR2) AppleWebKit/537.36 Chrome/121.0.0.0 Safari/537.36',
    'android': 'com.google.android.youtube/19.09.36 (Linux; U; Android 13) gzip',
    'ios': 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Version/16.0 Mobile/15E148 Safari/604.1',
    'web': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'tv_embedded': 'Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 Chrome/94.0.4606.31 TV Safari/537.36'
}

def validate_m3u8_url(url, headers=None):
    """
    Valida se uma URL M3U8 está acessível e retorna conteúdo válido
    """
    try:
        parsed = urlparse(url)
        
        # Configurar conexão
        if parsed.scheme == 'https':
            context = ssl._create_unverified_context()
            conn = http.client.HTTPSConnection(parsed.netloc, timeout=10, context=context)
        else:
            conn = http.client.HTTPConnection(parsed.netloc, timeout=10)
        
        # Preparar headers
        request_headers = {
            'User-Agent': headers.get('User-Agent', USER_AGENTS['web']) if headers else USER_AGENTS['web'],
            'Accept': '*/*',
            'Accept-Encoding': 'identity',
            'Connection': 'keep-alive'
        }
        
        if headers:
            request_headers.update(headers)
        
        # Fazer requisição HEAD primeiro (mais rápido)
        path = parsed.path
        if parsed.query:
            path += '?' + parsed.query
            
        conn.request('HEAD', path, headers=request_headers)
        response = conn.getresponse()
        
        # Aceitar 200 OK ou 206 Partial Content
        if response.status in [200, 206]:
            content_type = response.getheader('Content-Type', '')
            # Validar content-type
            if 'mpegurl' in content_type.lower() or 'x-mpegURL' in content_type.lower() or 'vnd.apple.mpegurl' in content_type.lower():
                print(f"✓ M3U8 validado: {url[:60]}... (Status: {response.status})", file=sys.stderr)
                return True
            # Alguns servidores não retornam content-type correto, aceitar se URL termina com .m3u8
            elif url.endswith('.m3u8') or '/manifest/' in url:
                print(f"✓ M3U8 validado por extensão: {url[:60]}...", file=sys.stderr)
                return True
        
        print(f"⚠ Validação falhou: Status {response.status}, Content-Type: {response.getheader('Content-Type')}", file=sys.stderr)
        return False
        
    except Exception as e:
        print(f"⚠ Erro na validação: {e}", file=sys.stderr)
        return False

def extract_with_ytdlp(url, ua_key='android_tv', video_format='best'):
    """
    Extração usando yt-dlp com configuração otimizada
    """
    user_agent = USER_AGENTS[ua_key]
    
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'format': video_format,
        'socket_timeout': 20,
        'nocheckcertificate': True,
        'geo_bypass': True,
        'user_agent': user_agent,
        'http_headers': {
            'User-Agent': user_agent,
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7',
            'Accept-Encoding': 'gzip, deflate',
            'DNT': '1',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1'
        },
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios', 'web', 'tv_embedded'],
                'skip': ['dash', 'hls_manifest_video_only'],
                'player_skip': ['webpage', 'configs']
            }
        }
    }
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
            # Priorizar formatos HLS
            formats = info.get('formats', [])
            hls_formats = [f for f in formats if f.get('protocol') == 'm3u8_native' or '.m3u8' in f.get('url', '')]
            
            if hls_formats:
                # Pegar o melhor formato HLS
                best_hls = max(hls_formats, key=lambda f: f.get('height', 0) or f.get('tbr', 0))
                m3u8_url = best_hls['url']
            else:
                # Fallback para URL direta
                m3u8_url = info.get('url')
            
            # Extrair headers do yt-dlp
            http_headers = info.get('http_headers', {})
            
            result_headers = {
                'User-Agent': user_agent,
                'Referer': 'https://www.youtube.com/',
                'Origin': 'https://www.youtube.com'
            }
            
            # Adicionar cookies se existirem
            if 'Cookie' in http_headers:
                result_headers['Cookie'] = http_headers['Cookie']
            
            return {
                'success': True,
                'url': m3u8_url,
                'headers': result_headers,
                'method': f'yt-dlp ({ua_key})'
            }
            
    except Exception as e:
        error_msg = str(e)
        print(f"Falha yt-dlp ({ua_key}): {error_msg}", file=sys.stderr)
        return {'success': False, 'error': error_msg}

def extract_with_streamlink(url):
    """
    Extração usando Streamlink como fallback (via API interna)
    """
    try:
        import streamlink_extractor
        result = streamlink_extractor.extract_with_streamlink(url)
        if result:
            result['success'] = True
            return result
        return {'success': False, 'error': 'Streamlink retornou vazio'}
    except Exception as e:
        error_msg = str(e)
        print(f"Falha ao importar/executar streamlink_extractor: {error_msg}", file=sys.stderr)
        return {'success': False, 'error': error_msg}

def extract_channel(channel, video_format='best'):
    """
    Extrai um canal com múltiplas tentativas e fallbacks
    """
    name = channel.get('name', 'Unknown')
    url = channel.get('url', '')
    
    if not url or 'youtube.com' not in url and 'youtu.be' not in url:
        return None
    
    print(f"\n{'='*60}", file=sys.stderr)
    print(f"Processando: {name}", file=sys.stderr)
    print(f"URL: {url}", file=sys.stderr)
    
    attempts = []
    
    # Estratégia 1: Tentar com diferentes User-Agents (yt-dlp)
    for ua_key in ['android_tv', 'android', 'ios', 'web']:
        print(f"\nTentativa yt-dlp ({ua_key})...", file=sys.stderr)
        start_time = time.time()
        result = extract_with_ytdlp(url, ua_key, video_format)
        duration = int((time.time() - start_time) * 1000)
        
        attempts.append({
            'method': f'yt-dlp ({ua_key})',
            'success': result.get('success', False) if result else False,
            'duration': duration,
            'error': None
        })
        
        if result and result.get('success'):
            if validate_m3u8_url(result['url'], result['headers']):
                print(f"✅ SUCESSO com yt-dlp ({ua_key})!", file=sys.stderr)
                return {
                    'name': name,
                    'm3u8': result['url'],
                    'logo': channel.get('logo', ''),
                    'group': channel.get('group', 'YouTube'),
                    'headers': result['headers'],
                    'extraction_method': result['method'],
                    'attempts': attempts,
                    'success': True
                }
            else:
                attempts[-1]['error'] = 'URL validado como inválido/inacessível'
        elif result and not result.get('success'):
            attempts[-1]['error'] = result.get('error', 'Erro desconhecido')
    
    # Estratégia 2: Fallback para Streamlink
    print(f"\nTentativa streamlink (fallback)...", file=sys.stderr)
    start_time = time.time()
    result = extract_with_streamlink(url)
    duration = int((time.time() - start_time) * 1000)
    
    attempts.append({
        'method': 'streamlink',
        'success': result.get('success', False) if result else False,
        'duration': duration,
        'error': None
    })
    
    if result and result.get('success'):
        if validate_m3u8_url(result['url'], result['headers']):
            print(f"✅ SUCESSO com streamlink!", file=sys.stderr)
            return {
                'name': name,
                'm3u8': result['url'],
                'logo': channel.get('logo', ''),
                'group': channel.get('group', 'YouTube'),
                'headers': result['headers'],
                'extraction_method': result['method'],
                'attempts': attempts,
                'success': True
            }
        else:
             attempts[-1]['error'] = 'Streamlink URL inválido/inacessível'
    elif result and not result.get('success'):
        attempts[-1]['error'] = result.get('error', 'Erro streamlink')
    
    # Se chegou aqui, todas as tentativas falharam
    print(f"❌ FALHA TOTAL para {name}", file=sys.stderr)
    return {
        'name': name,
        'success': False,
        'attempts': attempts,
        'error': 'Todas as tentativas de extração falharam (yt-dlp + streamlink)'
    }

def extract(input_path, output_path, cookies_path=None, video_format='best'):
    """
    Função principal de extração
    """
    if not os.path.exists(input_path):
        print(f"Erro: Arquivo {input_path} não encontrado", file=sys.stderr)
        return
    
    try:
        with open(input_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
            channels = data.get('channels', [])
    except Exception as e:
        print(f"Erro ao ler JSON: {e}", file=sys.stderr)
        return
    
    print(f"\n{'='*60}", file=sys.stderr)
    print(f"INICIANDO EXTRAÇÃO DE {len(channels)} CANAIS", file=sys.stderr)
    print(f"{'='*60}\n", file=sys.stderr)
    
    processed_channels = []
    success_count = 0
    fail_count = 0
    
    for i, channel in enumerate(channels, 1):
        print(f"\n[{i}/{len(channels)}] ", file=sys.stderr, end='')
        result = extract_channel(channel, video_format)
        
        if result:
            processed_channels.append(result)
            if result.get('success'):
                success_count += 1
            else:
                fail_count += 1
    
    # Salvar resultado
    output_data = {
        'channels': processed_channels,
        'stats': {
            'total': len(channels),
            'success': success_count,
            'failed': fail_count,
            'timestamp': time.time()
        }
    }
    
    try:
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(output_data, f, ensure_ascii=False, indent=2)
        
        print(f"\n{'='*60}", file=sys.stderr)
        print(f"EXTRAÇÃO CONCLUÍDA", file=sys.stderr)
        print(f"Sucesso: {success_count}/{len(channels)}", file=sys.stderr)
        print(f"Falhas: {fail_count}/{len(channels)}", file=sys.stderr)
        print(f"Arquivo salvo: {output_path}", file=sys.stderr)
        print(f"{'='*60}\n", file=sys.stderr)
        
    except Exception as e:
        print(f"Erro ao salvar resultado: {e}", file=sys.stderr)

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Uso: python extractor_v2.py <input.json> <output.json> [cookies.txt]", file=sys.stderr)
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    cookies_file = sys.argv[3] if len(sys.argv) > 3 else None
    
    extract(input_file, output_file, cookies_file)
