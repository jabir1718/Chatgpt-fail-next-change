# Creator Studio — Master Roadmap & Acceptance Gates

This is the canonical product plan. Read this file and `docs/STATE.md` before every development session.

## Product promise
A polished, original Android creator studio for TikTok, YouTube Shorts, Instagram Reels and longer videos, with preview-first editing, contextual tool drawers, a true multi-track timeline, responsive audio-reactive visualizer, and no forced watermark.

## M0 — Foundation & build
- [x] Android Kotlin/Jetpack Compose project and GitHub Actions debug APK pipeline.
- [ ] User device install/launch acceptance and opt-in crash diagnostics.
**Gate:** CI build + artifact + device launch smoke test.

## M1 — Editor workspace
- [x] Project create/open/rename/duplicate/delete, autosave and recovery (source + CI; device acceptance open).
- [x] Video/audio/image/transparent PNG picker and media URI persistence/copy fallback (source; device acceptance open).
- [x] Media3 selected-source preview and multi-track timeline editing foundation.
- [x] Clip select/move/trim/split/delete/ripple-delete, snapping, undo/redo, track lock/mute/gain/solo.
- [ ] Synchronized multi-video compositing, canvas fit/fill/crop, safe areas and aspect presets.
**Gate:** Multi-asset edit, save/reopen, synchronized playback, no crash.

## M2 — Audio engine & sound design
- [x] Per-clip volume/mute, fades, track gain/solo and timeline scheduling.
- [x] Waveform decoding/cache/timeline display (Run #137 CI-verified).
- [x] Native live-preview five-band EQ, low-band bass boost and compressor (Run #140 CI-verified; phone audibility/support open).
- [ ] Pitch-preserving time stretch, formant and independent pitch/tempo.
- [ ] BPM/beat markers, limiter/normalization, voice effects, voiceover recording, extraction and mix bus.
- [ ] Streaming/disk-backed source PCM decode adapter for offline export (source implemented; Run #146 caught a constructor-property syntax typo, corrected in the next commit; CI re-run pending).
- [x] Block-based offline PCM mixing core: timeline placement, linear interpolation, clip rate/gain/fades, track gain/mute/solo, EQ, compressor and peak limiting (source + Run #145 unit tests/build/artifact verified).
- [x] AAC-LC audio-only encoding/muxing and project audio-mix export pipeline (Run #148 CI build/tests/artifact verified).
- [x] Export tab audio-mix action with Android SAF save destination (Run #150 CI build/tests/artifact verified).
- [x] Export progress and cancellation propagation (Run #152 CI build/tests/artifact verified).
- [x] Conservative disk-space preflight for PCM cache + AAC output + safety reserve (Run #155 CI build/tests/artifact verified).
- [ ] Preview/export mix parity, retry/recovery, and physical-device audio acceptance.
**Current note:** OfflineAudioMixEngine consumes decoded PCM buffers and returns contiguous output blocks. It is not yet connected to MediaExtractor/MediaCodec streaming decode or an AAC encoder/muxer. Do not call audio export complete.
**Gate:** Two or more clips with independent settings render to a valid audio file, with no clipping and correct timing.

## M3 — Visualizer Studio (signature priority)
- [x] Live FFT/spectrum, waveform, onset/beat pulse and rough BPM analysis.
- [x] Spectrum, radial, waveform, rings and particle previews (rings/particles are 2D Canvas).
- [ ] True 3D/GPU renderer, quality settings and fallback.
- [ ] Offline audio analysis drives visualizer frame-by-frame.
- [ ] Background image/video, multiple independent foreground PNG/video layers and beat response.
- [ ] Lyrics overlay, masks, blend modes, keyframes, presets and transitions.
- [x] Deterministic frame composition planning contract (Run #141 CI build/tests/artifact verified).
- [ ] Actual video/image decoding, pixel compositing and video frame encoding.
**Gate:** Imported song drives animation; foreground reacts independently; export matches preview.

## M4 — Lyrics/captions/voice input
- [ ] Disclosed transcription provider with explicit Start/Stop and permissions.
- [ ] Language identification; Urdu, Roman Urdu, Roman English and other supported scripts.
- [ ] Editable word timing, karaoke highlight, voice typing, emoji captions, SRT/VTT.
**Gate:** Transcribe, correct, select script, retain sync through edits.

## M5 — Cutout/chroma/compositing
- [ ] Validated on-device/online subject segmentation and transparent PNG output.
- [ ] Edge refinement, erase/restore, chroma key, masks, blend modes, background replacement.
- [ ] Filters, LUT, blur, glow, shadows, transitions and keyframes.
**Gate:** Multi-layer output with clean alpha edges and preview/render parity.

## M6 — Video editing/effects
- [ ] Reverse/freeze, speed ramps/time remapping, transitions, filters, masks, keyframes, stabilization if supported.
- [ ] Audio extraction/replacement and proxy editing.
**Gate:** Multi-clip render with stable timing and audio sync.

## M7 — Export/sharing/social
- [ ] MP4/audio encoding, presets (9:16, 1:1, 4:5, 16:9), resolution/FPS/bitrate/codec.
- [ ] Export progress/cancel/retry/recovery and storage estimates.
- [ ] Android share-sheet fallback; direct social upload only through permitted official APIs/OAuth.
**Gate:** Valid portrait/landscape files, share locally, official integrations only.

## M8 — Reliability/release
- [ ] Device matrix, thermal/memory/long-render/audio-sync/recovery testing.
- [ ] Accessibility, privacy policy, licensing, app icon/splash/onboarding/help.
- [ ] Secure release signing and final APK/AAB device acceptance.
**Gate:** CI + device test checklist + release candidate.

## Reserved future upgrades
Cloud sync, templates marketplace, AI beat cuts/auto-edit, advanced tracking/reframe, batch export, brand kits, collaboration, plugin API, desktop companion and advanced color grading/pro audio routing.

## Non-negotiable rules
1. Never mark a UI-only control as working.
2. Track planned/source/CI/device states separately.
3. Offline-first; disclose network/model/account/cost requirements.
4. No copyright-evasion claims; editing does not grant rights.
5. Use official social APIs/OAuth or system share sheet; no scraping/access-control bypass.
6. Ask permissions only when the user starts the feature that needs them.
7. Do not skip acceptance gates.
8. Preserve working behavior and inspect full files/callers before replacement.
9. Read state, roadmap and exact sources before each session.
10. Update roadmap/state with every material change.
