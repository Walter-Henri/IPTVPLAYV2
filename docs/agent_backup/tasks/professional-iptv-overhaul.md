# Task: Professional IPTV Overhaul (Android TV & Smartphone)

Building a state-of-the-art IPTV experience for both TV (D-Pad navigation, Focus states) and Mobile (Gestures, Adaptive UI).

## Status Tracker
- [ ] Phase 1: Professional UI & TV Navigation (D-Pad, Focus, OSD)
- [ ] Phase 2: Playback Resilience (Adaptive Buffer, Fallback, ABR)
- [ ] Phase 3: Advanced Features (EPG, Stats for Nerds, Parental Lock)
- [ ] Phase 4: Stability & Optimization (Resource cleanup, Perf tuning)
- [/] Last Watched Channel (Auto-Resume) -> **Verify and Polish**

## Phase 1: Professional UI & TV Navigation
### 1.1 Focus Indicators (TV)
- [ ] Create a custom Focus `Modifier` for visual highlighting on TV.
- [ ] Apply to all clickable elements in main lists and menus.
- [ ] Ensure consistent focus order (FocusFinder).

### 1.2 Modern OSD (Refactor)
- [ ] Premium design for `PlayerControls.kt`.
- [ ] Adaptive layout for TV (bottom bar) vs Mobile (overlay).
- [ ] Integration of media keys (Play/Pause/Stop/Seek).

### 1.3 Navigational Gestures (Mobile)
- [ ] Swipe for volume (left side).
- [ ] Swipe for brightness (right side).
- [ ] Double-tap for seek (Left/Right).

---

## Technical Details

### Focus Modifiers
- Use `onFocusChanged` and `border`/`scale` animations.
- Implement `Modifier.focusRestorer()` where needed.

### Media Keys Mapping
- Support `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_STOP`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`.

### Fallback Implementation
- `MediaSource` retry logic in `PlayerManagerImpl.kt`.
