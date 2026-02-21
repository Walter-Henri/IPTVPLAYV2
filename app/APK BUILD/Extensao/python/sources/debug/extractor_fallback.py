import json
import yt_dlp
import os
import sys

def extract_url(url, ffmpeg_path=None):
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

    ydl_opts = {
        'logger': Logger(),
        'quiet': False,
        'no_warnings': False,
        # Otimização para Smart TV
        'socket_timeout': 15,  # Timeout para evitar travamentos em redes Wi-Fi de TVs
        'geo_bypass': True,    # Evitar bloqueios regionais
        'nocheckcertificate': True, # Ignorar erros de SSL em TVs antigas
        'format': 'best',      # Priorizar melhor qualidade disponível
        'noplaylist': True,
        # User-Agents comuns para simular navegadores de TV e Mobile
        'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
    }

    if ffmpeg_path:
        ydl_opts['ffmpeg_location'] = ffmpeg_path

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
            # Extração de headers de cookies e referer
            headers = info.get('http_headers', {})
            
            result = {
                "url": info.get('url'),
                "headers": headers,
                "title": info.get('title'),
                "thumbnail": info.get('thumbnail'),
                "duration": info.get('duration'),
                "ext": info.get('ext')
            }
            return json.dumps(result)
            
    except Exception as e:
        error_result = {
            "error": str(e)
        }
        return json.dumps(error_result)

if __name__ == "__main__":
    # Teste rápido se executado diretamente
    if len(sys.argv) > 1:
        print(extract_url(sys.argv[1]))
