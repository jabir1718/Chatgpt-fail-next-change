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
- [x] Synchronized multi-video timeline preview with independent Media3 players, timeline source-time alignment, drift correction, track-order compositing and clip transforms (Run #195 source + CI verified; device sync/memory acceptance open).
- [x] Per-video/image-clip FIT/FILL/CROP modes persisted in ProjectStore schema v6 and applied in live source preview, synchronized video preview and offline MP4 frame composition; preview-only safe-area guides (Run #208 CI verified; device preview/export parity open).
**Gate:** Multi-asset edit, save/reopen, synchronized playback, no crash.

## M2 — Audio engine & sound design
- [x] Per-clip volume/mute, fades, track gain/solo and timeline scheduling.
- [x] Waveform decoding/cache/timeline display (Run #137 CI-verified).
- [x] Native live-preview five-band EQ, low-band bass boost and compressor (Run #140 CI-verified; phone audibility/support open).
- [ ] Pitch-preserving time stretch, formant and independent pitch/tempo.
- [ ] BPM/beat markers, limiter/normalization, voice effects, voiceover recording, extraction and mix bus.
- [x] Streaming/disk-backed source PCM decode adapter for offline export (Run #147 CI-verified).
- [x] Block-based offline PCM mixing core: timeline placement, linear interpolation, clip rate/gain/fades, track gain/mute/solo, EQ, compressor and peak limiting (source + Run #145 unit tests/build/artifact verified).
- [x] AAC-LC audio-only encoding/muxing and project audio-mix export pipeline (Run #148 CI build/tests/artifact verified).
- [x] Export tab audio-mix action with Android SAF save destination (Run #150 CI build/tests/artifact verified).
- [x] Export progress and cancellation propagation (Run #152 CI build/tests/artifact verified).
- [x] Conservative disk-space preflight for PCM cache + AAC output + safety reserve (Run #155 CI build/tests/artifact verified).
- [x] Failed audio/video export retry action (reruns from the beginning and requests a new destination; Run #184 CI-verified).
- [ ] Resumable partial-export recovery, preview/export mix parity and physical-device audio acceptance.
**Current note:** OfflineAudioMixEngine consumes disk-backed PCM sources decoded by MediaExtractor/MediaCodec and is connected to AAC-LC M4A export. The visualizer MP4 pipeline includes decodable audio from both audio clips and source-video clips in its timeline soundtrack for AAC output and offline FFT analysis. Video-only files with no audio stream are skipped; an audio-kind clip with missing/unreadable audio produces a clear failure. Run #173 verifies source compilation, configured tests, debug APK build and artifact upload. Physical-device A/V sync and codec behavior remain open.
**Gate:** Two or more clips with independent settings render to a valid audio file, with no clipping and correct timing.

## M3 — Visualizer Studio (signature priority)
- [x] Live FFT/spectrum, waveform, onset/beat pulse and rough BPM analysis.
- [x] Spectrum, radial, waveform, rings and particle previews (rings/particles are 2D Canvas).
- [ ] Complete true 3D/GPU renderer, quality settings and fallback. Live OpenGL ES 2.0 Orb preview and deterministic software-rendered offline Orb are implemented (Runs #257–#259); pixel parity, quality controls, unsupported-device fallback wiring and device performance remain open.
- [x] Offline audio analysis produces per-frame RMS/FFT/waveform/beat snapshots from timeline PCM (Run #157 CI build/tests/artifact verified).
- [x] Android Bitmap/Canvas renderer for Spectrum, Radial, Waveform, Rings and Particles with supplied image/video-frame layers and captions (Run #159 CI build/artifact verified).
- [x] Bounded H.264 video-only encoder using flexible YUV input and deterministic frame timestamps (Run #161 CI build/tests/artifact verified).
- [x] Still-image URI decoding with resolution/pixel budget and H.264/AAC MP4 track muxer (Run #164 CI build/tests/artifact verified).
- [x] Still-image visualizer MP4 pipeline connects audio decode/mix/AAC, offline FFT analysis, PNG/image layers, Canvas frames, H.264 and AAC/H.264 muxing; Export Studio SAF action, progress and cancellation are wired (Run #168 CI build/artifact; Run #171 reverified latest integration).
- [x] Add source video frame decoding and connect video clips to the offline visualizer MP4 compositor (Run #171 CI build/tests/artifact verified; device decoding/rotation/performance acceptance open).
- [x] Include decodable embedded audio from source-video clips in the visualizer MP4 soundtrack; safely skip video-only sources with no audio stream and fail clearly for missing audio on audio-kind clips (Run #173 CI build/tests/artifact verified; device A/V sync acceptance open).
- [ ] Full live background image/video and multiple independently composited foreground PNG/video layers with preview/export parity.
- [x] Per-video/image timeline clips have independent persisted beat-response enable, pulse-scale and sensitivity settings; RenderCompositionPlan carries each layer's settings and offline MP4 renderer applies beat-pulse scaling independently (Run #210 CI verified).
- [x] Live timeline preview composites active image and video clips in shared z-order and applies each layer's saved beat-response pulse independently. Multi-session Android Visualizer analysis supplies the beat counter; shared scale math and enabled/disabled/bounds behavior have regression tests (Runs #214, #216 and #218 CI verified). Per-session snapshot averaging is not sample-accurate post-mixer analysis; device behavior and preview/export parity remain open.
- [x] Shared per-clip canvas content mode flows through render plans; offline image/video layers support FIT, FILL and CROP while live video preview uses the same mode; safe guides stay out of exports (Run #208 CI verified).
- [ ] Lyrics overlay, masks, blend modes, keyframes, presets and transitions.
- [x] Deterministic frame composition planning contract (Run #141 CI build/tests/artifact verified).
- [x] Android still-image and source-video frame decoding, Canvas pixel compositing and H.264 video frame encoding are connected in the offline visualizer export pipeline (Run #171 CI build/tests/artifact verified; physical-device validation open).
- [x] Offline MP4 3D Orb mode renders a shaded sphere, FFT-driven outer bands and beat pulse from per-frame analyzer snapshots (Run #259 CI verified; software Canvas renderer is intentionally not pixel-identical to the live GLSL shader).
- [ ] True 3D/GPU composition, accelerated multi-layer rendering and preview/export parity.
**Gate:** Imported song drives animation; foreground reacts independently; export matches preview.

## M4 — Lyrics/captions/voice input
- [x] Imported audio/video offline Whisper transcription path with on-demand model download, media PCM decode, chunked recognition and caption insertion (Runs #228–#231 CI verified; device/model download/recognition acceptance open).
- [x] Whisper automatic language detection is configured for multilingual recognition; actual language accuracy/device validation open.
- [x] Caption script choice: keep detected Urdu/English output or transliterate Urdu-script words to editable best-effort Roman Urdu; English text and punctuation are preserved (Runs #236–#239 CI verified; device language accuracy open).
- [x] Manual editable timed captions and live timeline preview (Run #220 CI-verified; device timing/parity open).
- [x] Microphone voice typing with explicit Start/Stop and runtime RECORD_AUDIO permission; partial/final SpeechRecognizer results insert into caption draft (Run #223 CI build/artifact verified; device speech provider/accuracy unverified; offline preference is not a guarantee).
- [x] Whisper token-timed phrase captions with safe window fallback, editable word-level timing, synchronized active-word highlight in live preview and offline MP4 rendering (Runs #239–#242 CI verified; token timing is estimated from Whisper token timestamps, not phoneme-accurate).
- [x] SRT and WebVTT phrase-cue import/export with timestamp parsing, cue ordering and clip-duration validation (Runs #243–#244 CI verified). Word-level karaoke timings remain project metadata and are not encoded in SRT/VTT.
- [x] Quick emoji insertion into the timed-caption draft and Unicode/emoji SRT/WebVTT round-trip test (Run #246 CI verified).
- [x] Per-caption text color, background visibility and font-size controls shared by preview/offline render (Run #248 CI build/tests/artifact verified; device preview/export parity open).
- [x] Per-caption left/center/right alignment shared by live preview and offline MP4 renderer (Runs #249–#250 CI verified).
- [x] Per-caption Sans/Serif/Monospace/Cursive font family, persisted with legacy Sans fallback and mapped in live preview/offline MP4 (Run #254 CI verified).
- [x] Per-caption bold/italic typography toggles persisted and matched in live preview/offline MP4 (Run #255 CI verified; device font rendering remains open).
- [ ] Device acceptance of transcription, script output, timing and export.
**Gate:** Transcribe, correct, select script, edit word timing, exchange SRT/VTT and retain sync through preview/export. CI verifies source/build only; real model and language acceptance requires Android device testing.

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
- [x] H.264 + AAC MP4 visualizer export for audio + still images/PNG + source video picture layers and decodable embedded source-video audio (Run #173 CI build/tests/artifact verified; device codec compatibility and A/V sync acceptance open).
- [x] Visualizer export canvas presets (9:16, 1:1, 4:5, 16:9), 24/30/60 FPS and 4/8/12/20 Mbps H.264 bitrate; project-persisted settings (Runs #183 and #185 CI-verified, including regression tests). Codec remains H.264 + AAC and is not user-selectable.
- [x] Export progress/cancel and conservative storage preflight for audio/video visualizer export (Run #173 latest source build; retry/recovery remains open).
- [x] Failed visualizer/audio export retry action (Run #184 CI-verified; restart only, no partial resume).
- [x] Android share-sheet fallback for last saved MP4/M4A exports using ACTION_SEND, ClipData and temporary read permission (Run #256 CI verified; recipient-app behavior remains device-unverified); direct social upload only through permitted official APIs/OAuth.
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


## Caption typography
- [x] Per-caption font family selector (Sans, Serif, Monospace, Cursive) with project persistence and live/offline renderer mapping; Run #254 CI verified.
- [x] Per-caption bold and italic toggles with project persistence and live/offline renderer parity; Run #255 CI verified.


## Offline Orb angular lookup regression follow-up (2026-11-06)
- Run #260 compiled and assembled the Debug APK, but its configured unit-test task failed: 114/115 tests passed; OfflineOrbFrameMathTest.angularBandLookupMatchesShaderWrapAndClampsInvalidSamples exposed a Float precision boundary at the zero-angle band index. APK artifact upload was skipped because tests failed.
- Corrected the wrapped-angle calculation to use Double precision and floor-based indexing; expanded regression coverage for the positive-pi wrap and multiple full rotations. This is a targeted test correction, not a new feature-completion claim. Repair CI result pending.


## Offline Orb wrap-boundary follow-up (2026-11-06)
- Run #261 completed with failure: 114/115 unit tests passed. The remaining assertion was the exact negative-PI seam, where Float PI representation left the wrapped fraction a few ulps below 1 and selected the final FFT band instead of band zero.
- Added a narrow seam snap before floor-based band selection. Repair CI #262 must verify the full unit-test suite, Debug APK build, artifact upload and cleanup before continuing.


## 3D Orb device capability fallback (2026-11-06)
- Added a deterministic visualizer-mode resolver: when a device does not report OpenGL ES 2.0 support, a requested live 3D Orb preview safely displays the existing Spectrum renderer instead of attempting to create the GPU surface.
- The preview shows a clear fallback notice; the saved requested mode is not overwritten, so the 3D Orb selection can work again on a capable device.
- Added JVM regression tests for supported and unsupported capability paths. CI verification is pending; shader/runtime stability and actual device GPU performance still require physical-device acceptance.
