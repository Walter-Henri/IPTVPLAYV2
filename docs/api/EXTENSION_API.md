# M3U Extension API Documentation

This document describes the communication protocol between the M3U Universal app and the M3U Extension (Native Extraction Engine).

## Overview

The M3U Extension acts as a media extraction service. It receives URLs (e.g., YouTube, Twitch, etc.) via Android Intents or AIDL service, extracts the underlying stream URL (m3u8, mp4, etc.) using a **Native Intelligent Engine** (NewPipe-based with multi-redirect sniffing), and returns the result.

## Communication Protocol

### 1. Sending URLs to Extension

The Universal app (or any other app) can send a URL to the extension using the standard Android `ACTION_SEND` Intent or specialized AIDL calls.

**Intent Action:** `android.intent.action.SEND`
**MIME Type:** `text/plain`
**Extra Data:** `Intent.EXTRA_TEXT` (The URL to process)

**Example (Kotlin):**
```kotlin
val shareIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
}
startActivity(Intent.createChooser(shareIntent, "Open with M3U Extension"))
```

### 2. Receiving Extracted Streams

When the extension successfully extracts a stream, it shares the result back to the system using `ACTION_SEND`. The Universal app listens for this Intent to automatically add the stream to its "Virtual Playlist".

**Intent Action:** `android.intent.action.SEND`
**MIME Type:** `text/plain`
**Extra Data:** `Intent.EXTRA_TEXT` (The extracted stream URL)

### 3. Service Communication (AIDL) - RECOMMENDED

The extension now supports silent background extraction via **AIDL (Android Interface Definition Language)**. This allows the Universal app to resolve links without switching screens.

*   **Interface:** `IExtension.aidl`
*   **Package:** `com.m3u.core.extension`

## Architecture

### M3U Extension
*   **Engine:** Native Extraction Engine (NewPipe Extractor + Sniffer).
*   **Logic Layer:** `YouTubeInteractor` (Replaced YtDlpInteractor).
*   **Service:** `ExtensionService.kt` (AIDL Binding support).
*   **UI:** Jetpack Compose Dashboard for status monitoring.

### M3U Universal App
*   **Repository:** `ExtensionRepository` for AIDL binding.
*   **Playlist:** Virtual Playlist (SQLite/Room) for temporary streams.

## Future Improvements

*   **Metadata:** Pass JSON with Title, Thumbnail, and Duration from Extension to App.
*   **Multi-Engine:** Support for more services (Twitch, Kick, Rumble) via specialized native extractors.
