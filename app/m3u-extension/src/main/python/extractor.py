import json
import yt_dlp
import sys
import os
import time

def extract(input_path, output_path, cookies_path=None, video_format='best'):
    """
    YT Extractor Module - Converts channels.json to channels2.json with signed m3u8 URLs.
    Supports cookies for authentication and maintains IP affinity.
    """
    if not os.path.exists(input_path):
        print(f"Error: Input file {input_path} not found.", file=sys.stderr)
        return

    try:
        with open(input_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
            channels = data.get('channels', [])
    except Exception as e:
        print(f"Error reading input JSON: {e}", file=sys.stderr)
        return

    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'format': video_format,
        'force_generic_extractor': False,
        'socket_timeout': 15, # Requirement 1: Socket timeout to prevent hanging
        'nocheckcertificate': True,
        'geo_bypass': True,
        # Requirement 2: Explicit User-Agent to match ExoPlayer later
        'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios'],
                'skip': ['dash'] # Only HLS/MP4 needed for iptv
            }
        }
    }

    if cookies_path and os.path.exists(cookies_path):
        ydl_opts['cookiefile'] = cookies_path

    processed_channels = []

    for channel in channels:
        name = channel.get('name', 'Unknown')
        url = channel.get('url')
        
        if not url:
            continue

        # Efficiency: Skip extraction if it doesn't look like a supported platform
        # yt-dlp is heavy, so we only use it for known complex extractors
        is_youtube = "youtube.com" in url or "youtu.be" in url
        if not is_youtube:
            # Skip non-youtube URLs to save time and battery
            continue

        print(f"Processing: {name} ({url})", file=sys.stderr)
        
        USER_AGENTS = [
            'Mozilla/5.0 (Linux; Android 10; BRAVIA 4K UR2 Build/PTT1.190515.001.S52) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
            'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15'
        ]

        success = False
        for i, ua in enumerate(USER_AGENTS):
            if success: break
            
            current_opts = ydl_opts.copy()
            current_opts['user_agent'] = ua
            
            try:
                print(f"Extraction attempt {i+1} for {name} with UA: {ua[:20]}...", file=sys.stderr)
                with yt_dlp.YoutubeDL(current_opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                    m3u8_url = info.get('url')
                    headers = info.get('http_headers', {})
                    
                    result_headers = {
                        "User-Agent": ua,
                        "Referer": headers.get("Referer", "https://www.youtube.com/")
                    }
                    
                    print(f"Success: {name}", file=sys.stderr)

                    processed_channels.append({
                        "name": name,
                        "m3u8": m3u8_url,
                        "logo": channel.get('logo', ''),
                        "group": channel.get('group', 'General'),
                        "headers": result_headers
                    })
                    success = True
            except Exception as e:
                print(f"Attempt {i+1} failed: {e}", file=sys.stderr)
                if i == len(USER_AGENTS) - 1:
                    print(f"Fatal: All attempts failed for {name}", file=sys.stderr)
                    continue

    output_data = {"channels": processed_channels}
    
    try:
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(output_data, f, indent=4, ensure_ascii=False)
        print(f"Successfully generated {output_path}", file=sys.stderr)
    except Exception as e:
        print(f"Error writing output JSON: {e}", file=sys.stderr)

if __name__ == "__main__":
    # Args: input_path output_path [cookies_path]
    if len(sys.argv) < 3:
        print("Usage: python extractor.py <input.json> <output.json> [cookies.txt]", file=sys.stderr)
    else:
        in_p = sys.argv[1]
        out_p = sys.argv[2]
        cook_p = sys.argv[3] if len(sys.argv) > 3 else None
        extract(in_p, out_p, cook_p)
