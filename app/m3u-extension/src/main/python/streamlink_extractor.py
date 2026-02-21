"""
Streamlink Extractor - API Integration for Android/Chaquopy
Este módulo utiliza a biblioteca Streamlink diretamente em vez de subprocess.
"""

import streamlink
import json
import sys
import logging

# Configuração básica de log para capturar mensagens do streamlink
logging.basicConfig(level=logging.INFO, stream=sys.stderr)

def extract_with_streamlink(url):
    """
    Extrai stream usando a API interna do Streamlink
    """
    try:
        # Criar sessão do streamlink
        session = streamlink.Streamlink()
        
        # Opcional: Configurar opções da sessão
        session.set_option("hls-live-edge", 3)
        session.set_option("http-timeout", 20)
        
        # Obter streams
        streams = session.streams(url)
        
        if not streams:
            print(f"Nenhum stream encontrado pelo Streamlink para: {url}", file=sys.stderr)
            return None
        
        # Priorizar qualidades
        priority = ['best', '1080p', '720p', '480p', 'worst']
        
        selected_quality = None
        for quality in priority:
            if quality in streams:
                selected_quality = quality
                break
        
        if not selected_quality:
            # Pegar o primeiro disponível
            selected_quality = list(streams.keys())[0]
            
        stream = streams[selected_quality]
        
        # O Streamlink retorna diferentes tipos de objetos stream
        # Queremos o URL final (geralmente HLSStream ou HTTPStream)
        stream_url = stream.to_url()
        
        if stream_url:
            print(f"✓ Streamlink sucesso ({selected_quality}): {stream_url[:60]}...", file=sys.stderr)
            
            # Alguns plugins do streamlink retornam headers específicos
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
                'Referer': 'https://www.youtube.com/',
                'Origin': 'https://www.youtube.com'
            }
            
            return {
                'url': stream_url,
                'quality': selected_quality,
                'headers': headers,
                'method': f'streamlink ({selected_quality})'
            }
        
        return None
        
    except Exception as e:
        print(f"Erro na API Streamlink: {e}", file=sys.stderr)
        return None

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Uso: python streamlink_extractor.py <url>", file=sys.stderr)
        sys.exit(1)
        
    url = sys.argv[1]
    result = extract_with_streamlink(url)
    
    if result:
        print(json.dumps(result))
    else:
        print(json.dumps({'error': 'Falha na extração'}))
        sys.exit(1)
