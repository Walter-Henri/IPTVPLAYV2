# Task: Configure yt-dlp Fallback Engine

## Status
- **Agent**: `mobile-developer`
- **Goal**: Implement yt-dlp as a fallback extraction engine for Smart TV/TV Box compatibility.
- **Priority**: High

## Plan

### Phase 1: Dependency Configuration
1. Update `libs.versions.toml`:
    - Add Chaquopy plugin and version.
    - Add FFmpeg-kit-full dependency.
2. Update root `build.gradle.kts`:
    - Add Chaquopy to `plugins` block.
3. Update `app/m3u-extension/build.gradle.kts`:
    - Apply Chaquopy plugin.
    - Configure `abiFilters` for Smart TV architectures (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
    - Configure `python` block:
        - Version 3.11.
        - Pip packages: `yt-dlp`, `certifi`, `urllib3`.
    - Add `ffmpeg-kit-full` dependency.

### Phase 2: Python Script Implementation
1. Create `app/m3u-extension/src/main/python/extractor_fallback.py`:
    - Implement yt-dlp extraction logic.
    - Support User-Agents, cookies, and geo-bypass.
    - Implement socket timeout.
    - Return JSON result.

### Phase 3: Kotlin Bridge Implementation
1. Modify `NewPipeResolver.kt` or create a new `YtDlpResolver.kt`:
    - Integration with Chaquopy.
    - Logic to call `extractor_fallback.py`.
    - Handle FFmpeg location dynamically.
    - Integration as fallback when NewPipe fails.

### Phase 4: Validation
1. Verify build completion.
2. Ensure ABI filters are correctly applied.
3. Test extraction with a sample URL.

## Technical Details
- **ABIs**: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- **Python**: 3.11+.
- **FFmpeg**: `com.github.tanersener:ffmpeg-kit-full:5.1`.
- **yt-dlp options**: `--geo-bypass`, `--socket-timeout`, `--ffmpeg-location`.
