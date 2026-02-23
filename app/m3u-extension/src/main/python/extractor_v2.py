"""
YouTube HLS Stream Extractor - v6.0 (Refined)

Multi-strategy extractor that tries clients in priority order:
  1. WebView tokens (web client + PO Token when available)
  2. tv_embedded (no token required, most stable for live streams)
  3. ios (works without PO Token for most live content)
  4. android_embedded (last resort)

Design principles:
  - Each strategy is isolated; failure in one doesn't abort others.
  - Prefer HLS manifest URLs (m3u8) over DASH for broadest player compat.
  - Validation is skipped for googlevideo.com URLs (they don't allow HEAD/GET without full auth).
  - Cookies are forwarded to yt-dlp via file (Netscape format) when available.
"""

import json
import sys
import time
import os
from urllib.parse import urlparse
import http.client
import ssl

print("=== EXTRACTOR V6 (Multi-Strategy) ===", file=sys.stderr)

try:
    import yt_dlp
    print(f"✓ yt_dlp: {yt_dlp.version.__version__}", file=sys.stderr)
except ImportError:
    print("❌ yt_dlp nao encontrado!", file=sys.stderr)
    sys.exit(1)


# ──────────────────────────────────────────────────────────────
# FORMAT SELECTOR
# ──────────────────────────────────────────────────────────────

# Best HLS stream that includes both video+audio in one manifest.
# Live streams typically only offer combined HLS formats.
LIVE_FORMAT  = "best[protocol^=m3u8]/best"
VOD_FORMAT   = "bestvideo[protocol^=m3u8]+bestaudio[protocol^=m3u8]/best[protocol^=m3u8]/best"


# ──────────────────────────────────────────────────────────────
# BASE OPTIONS BUILDER
# ──────────────────────────────────────────────────────────────

def base_opts(ua: str, hl: str = "pt", fmt: str = None) -> dict:
    """Shared yt-dlp options applied to all strategies."""
    return {
        "quiet":              True,
        "no_warnings":        True,
        "format":             fmt or LIVE_FORMAT,
        "socket_timeout":     35,
        "nocheckcertificate": True,
        "geo_bypass":         True,
        "noplaylist":         True,
        "user_agent":         ua,
        "http_headers": {
            "User-Agent":      ua,
            "Accept":          "*/*",
            "Accept-Language": f"{hl},{hl[:2]};q=0.9,en-US;q=0.7",
            "Referer":         "https://www.youtube.com/",
            "Origin":          "https://www.youtube.com",
        },
    }


def opts_web(tokens: dict, cookies_file: str = None) -> dict:
    """Strategy 1: web client with PO Token (strongest auth)."""
    ua  = tokens.get("userAgent", "")
    hl  = tokens.get("hl", "pt")
    fmt = tokens.get("format", LIVE_FORMAT)
    po  = tokens.get("poToken", "")
    vd  = tokens.get("visitorData", "")

    opts = base_opts(ua, hl, fmt)
    opts["extractor_args"] = {
        "youtube": {
            "player_client": ["web"],
            "skip": ["dash"],
        }
    }
    if po:
        opts["extractor_args"]["youtube"]["po_token"]     = [f"web+{po}"]
    if vd:
        opts["extractor_args"]["youtube"]["visitor_data"] = [vd]
    if cookies_file and os.path.exists(cookies_file):
        opts["cookiefile"] = cookies_file
    return opts


def opts_tv_embedded(tokens: dict, cookies_file: str = None) -> dict:
    """Strategy 2: tv_embedded — bypasses most bot checks, no PO Token needed."""
    ua  = tokens.get("userAgent", "")
    hl  = tokens.get("hl", "pt")
    fmt = tokens.get("format", LIVE_FORMAT)
    vd  = tokens.get("visitorData", "")

    opts = base_opts(ua, hl, fmt)
    opts["extractor_args"] = {
        "youtube": {
            "player_client": ["tv_embedded"],
            "skip": ["dash"],
        }
    }
    if vd:
        opts["extractor_args"]["youtube"]["visitor_data"] = [vd]
    if cookies_file and os.path.exists(cookies_file):
        opts["cookiefile"] = cookies_file
    return opts


def opts_ios(tokens: dict) -> dict:
    """Strategy 3: iOS client — no PO Token, works for most live channels."""
    ua  = tokens.get("userAgent", "com.google.ios.youtube/19.45.4")
    hl  = tokens.get("hl", "pt")
    fmt = tokens.get("format", LIVE_FORMAT)

    opts = base_opts(ua, hl, fmt)
    opts["extractor_args"] = {
        "youtube": {
            "player_client": ["ios"],
            "skip": ["dash"],
        }
    }
    return opts


def opts_android_embedded(tokens: dict) -> dict:
    """Strategy 4: android_embedded — last resort."""
    ua  = tokens.get("userAgent", "")
    hl  = tokens.get("hl", "pt")
    fmt = tokens.get("format", LIVE_FORMAT)

    opts = base_opts(ua, hl, fmt)
    opts["extractor_args"] = {
        "youtube": {
            "player_client": ["android_embedded"],
            "skip": ["dash"],
        }
    }
    return opts


# ──────────────────────────────────────────────────────────────
# HLS EXTRACTION HELPERS
# ──────────────────────────────────────────────────────────────

def pick_best_hls(info: dict) -> tuple:
    """Return (url, type_label) for the best HLS stream in the info dict."""

    # 1. Master HLS manifest (most compatible with ExoPlayer adaptive streaming)
    manifest = info.get("hls_manifest_url") or info.get("hlsManifestUrl")
    if manifest:
        return manifest, "hls_manifest"

    # 2. Best combined HLS format (video+audio in one stream)
    formats = info.get("formats", [])
    hls = [
        f for f in formats
        if f.get("protocol") in ("m3u8", "m3u8_native")
        or ".m3u8" in f.get("url", "")
    ]

    if hls:
        hls.sort(key=lambda f: (f.get("height") or 0, f.get("tbr") or 0), reverse=True)
        combined = [
            f for f in hls
            if f.get("acodec") != "none" and f.get("vcodec") != "none"
        ]
        best = (combined or hls)[0]
        return best["url"], "hls_format"

    # 3. Direct URL (may not be m3u8 but better than nothing)
    direct = info.get("url", "")
    return direct, "direct"


def build_headers_from_info(info: dict, tokens: dict) -> dict:
    """Build HTTP headers needed to play the extracted URL."""
    ua = tokens.get("userAgent", "")
    headers = {
        "User-Agent": ua,
        "Referer":    "https://www.youtube.com/",
        "Origin":     "https://www.youtube.com",
    }
    # Pull headers from yt-dlp info if present
    for key, val in info.get("http_headers", {}).items():
        headers[key] = val
    # Add cookies string (separate from cookies file)
    raw_cookies = tokens.get("cookies", "")
    if raw_cookies:
        headers["Cookie"] = raw_cookies
    return headers


# ──────────────────────────────────────────────────────────────
# STREAM VALIDATION
# ──────────────────────────────────────────────────────────────

def is_googlevideo_url(url: str) -> bool:
    return "googlevideo.com" in url


def validate_url(url: str, headers: dict, timeout: int = 10) -> tuple:
    """Returns (ok: bool, status_code: int). Skips validation for googlevideo URLs."""
    if not url:
        return False, 0

    # GoogleVideo URLs reject HEAD/small-GET without the full signed params — always accept them.
    if is_googlevideo_url(url):
        print("  Validacao: googlevideo.com -> aceito sem verificacao", file=sys.stderr)
        return True, 200

    try:
        parsed = urlparse(url)
        ctx    = ssl._create_unverified_context()
        # Use HTTPS port 443 for most, or HTTP 80
        port   = parsed.port or (443 if parsed.scheme == "https" else 80)
        if parsed.scheme == "https":
            conn = http.client.HTTPSConnection(parsed.netloc, port=port, timeout=timeout, context=ctx)
        else:
            conn = http.client.HTTPConnection(parsed.netloc, port=port, timeout=timeout)

        path = parsed.path + ("?" + parsed.query if parsed.query else "")
        conn.request("HEAD", path, headers={k: v for k, v in headers.items() if k != "Cookie"})
        resp = conn.getresponse()
        conn.close()
        ok = resp.status in (200, 206, 301, 302, 307, 308)
        print(f"  Validacao HTTP {resp.status} -> {'OK' if ok else 'FAIL'}", file=sys.stderr)
        return ok, resp.status
    except Exception as e:
        print(f"  Validacao excecao: {e}", file=sys.stderr)
        # .m3u8 URLs are likely valid even if we can't verify
        return ".m3u8" in url, 0


# ──────────────────────────────────────────────────────────────
# STRATEGY RUNNER
# ──────────────────────────────────────────────────────────────

def try_strategy(name: str, url: str, opts: dict, tokens: dict) -> dict | None:
    """Run a single yt-dlp extraction strategy. Returns result dict or None on failure."""
    print(f"\n  [{name}] Tentando...", file=sys.stderr)
    t0 = time.time()
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        ms = int((time.time() - t0) * 1000)

        m3u8_url, url_type = pick_best_hls(info)
        if not m3u8_url:
            print(f"  [{name}] Sem URL HLS ({ms}ms)", file=sys.stderr)
            return None

        headers   = build_headers_from_info(info, tokens)
        ok, status = validate_url(m3u8_url, headers)

        print(f"  [{name}] {'OK' if ok else 'FAIL'} status={status} type={url_type} {ms}ms", file=sys.stderr)

        return {
            "strategy": name,
            "ok":       ok,
            "url":      m3u8_url,
            "headers":  headers,
            "url_type": url_type,
            "ms":       ms,
            "status":   status,
        }
    except Exception as e:
        ms = int((time.time() - t0) * 1000)
        print(f"  [{name}] Excecao: {str(e)[:120]} ({ms}ms)", file=sys.stderr)
        return None


# ──────────────────────────────────────────────────────────────
# CHANNEL EXTRACTOR (orchestrates strategies)
# ──────────────────────────────────────────────────────────────

def extract_channel(channel: dict, tokens: dict, cookies_file: str = None) -> dict:
    url  = channel.get("url", "")
    name = channel.get("name", url)

    print(f"\n{'─'*50}", file=sys.stderr)
    print(f"Canal : {name}", file=sys.stderr)
    print(f"URL   : {url}", file=sys.stderr)
    print(f"Tokens: po={bool(tokens.get('poToken'))} | vd={bool(tokens.get('visitorData'))} | "
          f"ck={bool(tokens.get('cookies'))}", file=sys.stderr)

    if not url:
        return _fail(channel, name, "URL vazia")

    has_po = bool(tokens.get("poToken"))
    has_vd = bool(tokens.get("visitorData"))

    # Build ordered strategy list based on available tokens
    strategies = []

    if has_po:
        # Web client is most authoritative when we have a PO Token
        strategies.append(("web+poToken", opts_web(tokens, cookies_file)))

    # tv_embedded works for most live channels with or without visitor_data
    strategies.append(("tv_embedded",      opts_tv_embedded(tokens, cookies_file)))

    # iOS client: no PO Token needed, widely tested
    strategies.append(("ios",              opts_ios(tokens)))

    if has_po:
        pass  # web already tried first
    else:
        # Try web without PO Token as well (might work for older/public streams)
        strategies.append(("web_no_token",     opts_web(tokens, cookies_file)))

    # Last resort
    strategies.append(("android_embedded", opts_android_embedded(tokens)))

    attempts = []

    for strategy_name, strategy_opts in strategies:
        result = try_strategy(strategy_name, url, strategy_opts, tokens)
        if result:
            attempts.append(result)
            if result["ok"]:
                print(f"\n✅ SUCESSO [{strategy_name}]: {result['url'][:60]}...", file=sys.stderr)
                return _ok(channel, name, result["url"], result["headers"],
                           strategy_name, result["url_type"], attempts)
        else:
            attempts.append({"strategy": strategy_name, "ok": False})

    print(f"\n❌ FALHA TOTAL: {name}", file=sys.stderr)
    return _fail(channel, name, "Todas as estrategias falharam", attempts)


# ──────────────────────────────────────────────────────────────
# RESULT HELPERS
# ──────────────────────────────────────────────────────────────

def _ok(ch, name, url, headers, method, url_type, attempts):
    return {
        "name":               name,
        "logo":               ch.get("logo", ""),
        "group":              ch.get("group", "YouTube"),
        "success":            True,
        "m3u8":               url,
        "headers":            headers,
        "extraction_method":  method,
        "url_type":           url_type,
        "attempts":           attempts,
        "error":              None,
    }


def _fail(ch, name, error, attempts=None):
    return {
        "name":    name,
        "logo":    ch.get("logo", "") if ch else "",
        "group":   ch.get("group", "YouTube") if ch else "YouTube",
        "success": False,
        "m3u8":    None,
        "headers": {},
        "attempts": attempts or [],
        "error":   error,
    }


# ──────────────────────────────────────────────────────────────
# BATCH ENTRY POINT (called from Kotlin via Chaquopy)
# ──────────────────────────────────────────────────────────────

def extract(input_path: str, output_path: str,
            cookies_file: str = None,
            device_user_agent: str = None) -> None:
    """
    Entry point called by YouTubeExtractorV2.kt.

    Input JSON schema:
    {
        "channels": [{"name": "...", "url": "...", "logo": "...", "group": "..."}],
        "tokens": {
            "userAgent": "...", "visitorData": "...", "poToken": "...",
            "clientVersion": "...", "apiKey": "...", "hl": "...", "gl": "...",
            "cookies": "...", "format": "..."
        }
    }
    """
    if not os.path.exists(input_path):
        print(f"❌ Arquivo nao encontrado: {input_path}", file=sys.stderr)
        return

    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    channels = data.get("channels", [])
    tokens   = data.get("tokens", {})

    if device_user_agent and not tokens.get("userAgent"):
        tokens["userAgent"] = device_user_agent

    yt_channels = [
        ch for ch in channels
        if "youtube.com" in ch.get("url", "") or "youtu.be" in ch.get("url", "")
    ]

    print(f"\n{'='*50}", file=sys.stderr)
    print(f"EXTRACAO V6 — {len(yt_channels)} canais YouTube", file=sys.stderr)
    print(f"  poToken     : {'✓' if tokens.get('poToken')     else '✗'}", file=sys.stderr)
    print(f"  visitorData : {'✓' if tokens.get('visitorData') else '✗'}", file=sys.stderr)
    print(f"  cookies     : {'✓' if tokens.get('cookies')     else '✗'}", file=sys.stderr)
    print(f"  format      : {tokens.get('format', LIVE_FORMAT)}", file=sys.stderr)
    print(f"{'='*50}\n", file=sys.stderr)

    results       = []
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
            "total":            len(yt_channels),
            "success":          success_count,
            "failed":           len(yt_channels) - success_count,
            "timestamp":        time.time(),
            "had_po_token":     bool(tokens.get("poToken")),
            "had_visitor_data": bool(tokens.get("visitorData")),
        }
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)

    print(f"\n{'='*50}", file=sys.stderr)
    print(f"CONCLUIDO: {success_count}/{len(yt_channels)} ✅", file=sys.stderr)
    print(f"{'='*50}", file=sys.stderr)
