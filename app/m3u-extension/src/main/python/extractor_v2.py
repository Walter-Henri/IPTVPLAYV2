"""
YouTube HLS Stream Extractor - v5.0
Integrado com YouTubeWebViewTokenManager do Android

Recebe tokens extraídos diretamente do WebView do dispositivo:
- User-Agent real
- Cookies de sessão
- visitor_data (do ytcfg)
- PO Token (Proof of Origin Token)
- clientVersion, apiKey

Com esses tokens, o yt-dlp autentica como se fosse o browser real
do dispositivo → sem 403.
"""

import json
import sys
import time
import os
import tempfile
from urllib.parse import urlparse
import http.client
import ssl

print("=== EXTRACTOR V2 (WebView Tokens) ===", file=sys.stderr)

try:
    import yt_dlp
    print(f"✓ yt_dlp: {yt_dlp.version.__version__}", file=sys.stderr)
except ImportError:
    print("❌ yt_dlp não encontrado!", file=sys.stderr)
    sys.exit(1)


# ──────────────────────────────────────────────────────────────
# CORE: construir opções com tokens do WebView
# ──────────────────────────────────────────────────────────────

def build_opts_with_webview_tokens(tokens: dict, cookies_file: str = None) -> dict:
    ua = tokens.get("userAgent", "")
    visitor_data = tokens.get("visitorData", "")
    po_token = tokens.get("poToken", "")
    client_version = tokens.get("clientVersion", "")
    hl = tokens.get("hl", "pt")
    fmt = tokens.get("format", "best[protocol^=m3u8]/best")

    has_po_token = bool(po_token and po_token.strip())
    has_visitor_data = bool(visitor_data and visitor_data.strip())
    
    print(f"Formato: {fmt}", file=sys.stderr)

    # Com PO Token → usar web (mais estável, requer token)
    # Sem PO Token → tv_embedded ainda funciona em muitos casos
    if has_po_token:
        player_clients = ["web"]
        print("→ player_client: web (com PO Token)", file=sys.stderr)
    elif has_visitor_data:
        player_clients = ["tv_embedded", "ios"]
        print("→ player_client: tv_embedded/ios (com visitorData)", file=sys.stderr)
    else:
        player_clients = ["tv_embedded", "ios", "android_embedded"]
        print("→ player_client: tv_embedded/ios/android (sem tokens)", file=sys.stderr)

    opts = {
        "quiet": True,
        "no_warnings": True,
        "format": fmt,
        "socket_timeout": 30,
        "nocheckcertificate": True,
        "geo_bypass": True,
        "user_agent": ua,
        "http_headers": {
            "User-Agent": ua,
            "Accept": "*/*",
            "Accept-Language": f"{hl},{hl[:2]};q=0.9,en-US;q=0.7",
            "Referer": "https://www.youtube.com/",
            "Origin": "https://www.youtube.com",
        },
        "extractor_args": {
            "youtube": {
                "player_client": player_clients,
                "skip": ["dash"],
            }
        },
        "noplaylist": True,
    }

    # Adicionar PO Token
    if has_po_token:
        opts["extractor_args"]["youtube"]["po_token"] = [f"web+{po_token}"]

    # Adicionar visitor_data
    if has_visitor_data:
        opts["extractor_args"]["youtube"]["visitor_data"] = [visitor_data]

    # Cookies
    if cookies_file and os.path.exists(cookies_file):
        opts["cookiefile"] = cookies_file
        print(f"  cookies: ✓ (arquivo: {cookies_file})", file=sys.stderr)

    return opts


# ──────────────────────────────────────────────────────────────
# EXTRAÇÃO DE URL HLS
# ──────────────────────────────────────────────────────────────

def extract_best_hls(info: dict) -> tuple:
    """Retorna (url, tipo) do melhor stream HLS disponível."""

    # 1. Master playlist (melhor)
    manifest = info.get("hls_manifest_url") or info.get("hlsManifestUrl")
    if manifest:
        return manifest, "hls_manifest"

    # 2. Formatos m3u8
    formats = info.get("formats", [])
    hls = [
        f for f in formats
        if f.get("protocol") in ("m3u8", "m3u8_native")
        or ".m3u8" in f.get("url", "")
    ]

    if hls:
        hls.sort(key=lambda f: (f.get("height") or 0, f.get("tbr") or 0), reverse=True)
        combined = [f for f in hls if f.get("acodec") != "none" and f.get("vcodec") != "none"]
        best = (combined or hls)[0]
        return best["url"], "hls_format"

    # 3. URL direta
    return info.get("url", ""), "direct"


# ──────────────────────────────────────────────────────────────
# VALIDAÇÃO
# ──────────────────────────────────────────────────────────────

def validate_url(url: str, headers: dict, timeout: int = 10) -> tuple:
    """Retorna (ok: bool, status_code: int)."""
    if not url:
        return False, 0
    try:
        parsed = urlparse(url)
        ctx = ssl._create_unverified_context()
        conn = http.client.HTTPSConnection(parsed.netloc, timeout=timeout, context=ctx)
        path = parsed.path + ("?" + parsed.query if parsed.query else "")
        conn.request("HEAD", path, headers=headers)
        resp = conn.getresponse()
        conn.close()
        ok = resp.status in (200, 206, 301, 302, 307, 308)
        print(f"  Validação HTTP {resp.status} → {'✓' if ok else '✗'}", file=sys.stderr)
        return ok, resp.status
    except Exception as e:
        print(f"  Erro na validação: {e}", file=sys.stderr)
        return False, 0


# ──────────────────────────────────────────────────────────────
# EXTRAÇÃO DE CANAL
# ──────────────────────────────────────────────────────────────

def extract_channel(channel: dict, tokens: dict, cookies_file: str = None) -> dict:
    url = channel.get("url", "")
    name = channel.get("name", url)

    print(f"\n{'─'*50}", file=sys.stderr)
    print(f"Canal: {name}", file=sys.stderr)
    print(f"URL: {url}", file=sys.stderr)

    if not url:
        return _fail(channel, name, "URL vazia")

    ua = tokens.get("userAgent", "Mozilla/5.0")
    attempts = []

    # ── Tentativa 1: com tokens do WebView ──────────────────────
    print(f"\n[1/2] Usando tokens do WebView Android...", file=sys.stderr)
    try:
        t0 = time.time()
        opts = build_opts_with_webview_tokens(tokens, cookies_file)

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)

        m3u8_url, url_type = extract_best_hls(info)
        ms = int((time.time() - t0) * 1000)

        if m3u8_url:
            headers = {
                "User-Agent": ua,
                "Referer": "https://www.youtube.com/",
                "Origin": "https://www.youtube.com",
            }
            # Incluir cookies se disponíveis
            cookies_raw = tokens.get("cookies", "")
            if cookies_raw:
                headers["Cookie"] = cookies_raw

            ok, status = validate_url(m3u8_url, headers)
            attempts.append({"method": "webview_tokens", "ok": ok, "status": status, "ms": ms})

            if ok:
                print(f"✅ SUCESSO com tokens WebView ({url_type}, {ms}ms)", file=sys.stderr)
                return _ok(channel, name, m3u8_url, headers, "webview_tokens", url_type, attempts)
            else:
                print(f"✗ HTTP {status} — tokens podem ter expirado", file=sys.stderr)

    except Exception as e:
        ms = int((time.time() - t0) * 1000)
        print(f"✗ Falha: {str(e)[:120]}", file=sys.stderr)
        attempts.append({"method": "webview_tokens", "ok": False, "error": str(e)[:120], "ms": ms})

    # ── Tentativa 2: fallback tv_embedded sem tokens ──────────────
    print(f"\n[2/2] Fallback: tv_embedded sem tokens...", file=sys.stderr)
    try:
        t0 = time.time()
        fallback_ua = (
            "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "SamsungBrowser/4.0 Chrome/76.0.3809.146 TV Safari/537.36"
        )
        opts = {
            "quiet": True,
            "no_warnings": True,
            "format": "best[protocol^=m3u8]/best",
            "socket_timeout": 30,
            "nocheckcertificate": True,
            "http_headers": {
                "User-Agent": fallback_ua,
                "Referer": "https://www.youtube.com/",
            },
            "extractor_args": {
                "youtube": {
                    "player_client": ["tv_embedded"],
                    "skip": ["dash"],
                }
            },
            "noplaylist": True,
        }

        if cookies_file and os.path.exists(cookies_file):
            opts["cookiefile"] = cookies_file

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)

        m3u8_url, url_type = extract_best_hls(info)
        ms = int((time.time() - t0) * 1000)

        if m3u8_url:
            headers = {
                "User-Agent": fallback_ua,
                "Referer": "https://www.youtube.com/",
            }
            ok, status = validate_url(m3u8_url, headers)
            attempts.append({"method": "tv_embedded_fallback", "ok": ok, "status": status, "ms": ms})

            if ok:
                print(f"✅ SUCESSO com tv_embedded fallback ({ms}ms)", file=sys.stderr)
                return _ok(channel, name, m3u8_url, headers, "tv_embedded_fallback", url_type, attempts)

    except Exception as e:
        print(f"✗ Fallback também falhou: {str(e)[:80]}", file=sys.stderr)
        attempts.append({"method": "tv_embedded_fallback", "ok": False, "error": str(e)[:80]})

    print(f"\n❌ FALHA TOTAL: {name}", file=sys.stderr)
    return _fail(channel, name, "Todos os métodos falharam", attempts)


def _ok(ch, name, url, headers, method, url_type, attempts):
    return {
        "name": name, "logo": ch.get("logo", ""), "group": ch.get("group", "YouTube"),
        "success": True, "m3u8": url, "headers": headers,
        "extraction_method": method, "url_type": url_type,
        "attempts": attempts, "error": None,
    }

def _fail(ch, name, error, attempts=None):
    return {
        "name": name, "logo": ch.get("logo", "") if ch else "",
        "group": ch.get("group", "YouTube") if ch else "YouTube",
        "success": False, "m3u8": None, "headers": {},
        "attempts": attempts or [], "error": error,
    }


# ──────────────────────────────────────────────────────────────
# EXTRAÇÃO EM LOTE (chamada pelo Kotlin via Chaquopy)
# ──────────────────────────────────────────────────────────────

def extract(input_path: str, output_path: str,
            cookies_file: str = None,
            device_user_agent: str = None) -> None:
    """
    Ponto de entrada chamado pelo YouTubeExtractorV3.kt.

    O input_path agora contém um objeto "tokens" além dos "channels":
    {
        "channels": [...],
        "tokens": {
            "userAgent": "...",
            "visitorData": "...",
            "poToken": "...",
            ...
        }
    }
    """
    if not os.path.exists(input_path):
        print(f"❌ Arquivo não encontrado: {input_path}", file=sys.stderr)
        return

    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    channels = data.get("channels", [])
    tokens = data.get("tokens", {})

    # Garantir que o UA do device_user_agent seja usado se tokens estiver vazio
    if device_user_agent and not tokens.get("userAgent"):
        tokens["userAgent"] = device_user_agent

    yt_channels = [
        ch for ch in channels
        if "youtube.com" in ch.get("url", "") or "youtu.be" in ch.get("url", "")
    ]

    print(f"\n{'='*50}", file=sys.stderr)
    print(f"EXTRAÇÃO V5: {len(yt_channels)} canais", file=sys.stderr)
    print(f"poToken: {'✓' if tokens.get('poToken') else '✗'}", file=sys.stderr)
    print(f"visitorData: {'✓' if tokens.get('visitorData') else '✗'}", file=sys.stderr)
    print(f"cookies: {'✓' if tokens.get('cookies') else '✗'}", file=sys.stderr)
    print(f"{'='*50}\n", file=sys.stderr)

    results = []
    success_count = 0

    for i, channel in enumerate(yt_channels, 1):
        print(f"[{i}/{len(yt_channels)}]", file=sys.stderr, end=" ")
        result = extract_channel(channel, tokens, cookies_file)
        results.append(result)
        if result.get("success"):
            success_count += 1

    output_data = {
        "channels": results,
        "stats": {
            "total": len(yt_channels),
            "success": success_count,
            "failed": len(yt_channels) - success_count,
            "timestamp": time.time(),
            "had_po_token": bool(tokens.get("poToken")),
            "had_visitor_data": bool(tokens.get("visitorData")),
        }
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)

    print(f"\n{'='*50}", file=sys.stderr)
    print(f"CONCLUÍDO: {success_count}/{len(yt_channels)} ✅", file=sys.stderr)
    print(f"{'='*50}", file=sys.stderr)
