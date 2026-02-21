import json
import yt_dlp
import os
import sys

def extract_url(url, ffmpeg_path=None, video_format='best'):
    """
    Extrator universal via yt-dlp para fallback em Smart TV & TV Box.
    Configurações otimizadas para redes instáveis e bypass geográfico.
    """
    
    # Configurações de redirecionamento de logs para o logcat via python.stderr
    class Logger:
        def debug(self, msg): 
            if msg.startswith('[debug] '):
                print(msg, file=sys.stderr)
        def warning(self, msg): 
            print(f"WARNING: {msg}", file=sys.stderr)
        def error(self, msg): 
            print(f"ERROR: {msg}", file=sys.stderr)

    # Lista de identidades para bypass de 403
    USER_AGENTS = [
        # 1. Padrão Smart TV (Bravia)
        'Mozilla/5.0 (Linux; Android 10; BRAVIA 4K UR2 Build/PTT1.190515.001.S52) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
        # 2. Chrome Desktop (Windows)
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        # 3. Safari (macOS)
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15',
        # 4. Firefox (Linux)
        'Mozilla/5.0 (X11; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0'
    ]

    for i, ua in enumerate(USER_AGENTS):
        ydl_opts = {
            'logger': Logger(),
            'quiet': False,
            'no_warnings': False,
            'socket_timeout': 20,  
            'geo_bypass': True,    
            'nocheckcertificate': True, 
            'format': video_format,
            'noplaylist': True,
            'user_agent': ua,
            'extractor_args': {
                'youtube': {
                    'player_client': ['android', 'ios'],
                    'skip': ['dash', 'hls']
                }
            }
        }

        if ffmpeg_path:
            ydl_opts['ffmpeg_location'] = ffmpeg_path

        try:
            print(f"Tentativa {i+1} com UA: {ua[:30]}...", file=sys.stderr)
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                headers = info.get('http_headers', {})
                # Garantir que o UA bem-sucedido seja reportado
                headers['User-Agent'] = ua 
                
                result = {
                    "url": info.get('url'),
                    "headers": headers,
                    "title": info.get('title'),
                    "thumbnail": info.get('thumbnail'),
                    "ua_index": i
                }
                return json.dumps(result)
                
        except Exception as e:
            err_msg = str(e)
            print(f"Erro na tentativa {i+1}: {err_msg}", file=sys.stderr)
            # Se não for erro de permissão/proibido, talvez não adiante girar o UA
            # Mas vamos girar de qualquer forma para os primeiros 401/403
            if "403" in err_msg or "401" in err_msg or "HTTP Error" in err_msg:
                continue
            else:
                # Se for erro de rede/timeout, para por aqui
                break

    return json.dumps({"error": "Falha em todas as tentativas de extração com UA rotativo"})

if __name__ == "__main__":
    # Teste rápido se executado diretamente
    if len(sys.argv) > 1:
        print(extract_url(sys.argv[1]))
