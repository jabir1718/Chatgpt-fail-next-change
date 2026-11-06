# Creator Studio — Continuation Handoff (Read First)

If a new chat or context window starts, DO NOT restart, recreate the app, rename the repository, or invent a new roadmap.

## Exact first instruction
Open repository `jabir1718/Chatgpt-fail-next-change` on branch `main`. Read these files first:
1. `docs/STATE.md`
2. `docs/ROADMAP.md`
3. `docs/PRODUCT_SPEC.md`
4. The exact source files for the next task.

Then inspect the latest GitHub commit and latest Actions run. Continue from the existing HEAD. Do not assume old run status or reuse an old APK if a newer source commit exists.

## User's goal
Build a serious, original Android creator studio for TikTok, YouTube, Instagram and other social video workflows. The most important feature is a professional audio-reactive visualizer with a video/artwork canvas, background image/video, foreground transparent PNG layers that independently beat/pulse, and synchronized automatic lyrics/captions. Also needs a real multi-track video/audio timeline, per-clip sound controls, background removal, green screen, effects, voice typing, multilingual captions (Urdu script, Roman Urdu, Roman English and other supported languages), export presets and supported social sharing/upload.

## User communication requirements
- User is sleep-deprived and asked not to be kept waiting with repeated questions, confirmations or screen-on monitoring.
- Do not ask “start?”, “OK?”, or ask user to test every tiny change.
- Do not claim a feature works just because a button or screen exists.
- User prefers full-file replacement when sensible. Fetch the latest full file, inspect dependencies/callers, replace the complete file, then run CI. Preserve working features.
- Keep one coherent app and deliver a single useful APK after substantial implementation, not a chain of tiny test APKs.
- Device install/physical testing is done by user when the app is ready.

## Current evidence snapshot
- Run #231, ID `42189714215`, commit `63360976433bdc85fb8a9f63f32b0b6d032f79f6`: PCM bulk-read optimization and stereo/EOF test; CI build, debug APK and artifact upload passed.
- Run #230: imported audio/video transcription first integration and decoder import correction; CI tests/build/artifact passed.
- Run #228: sherpa-onnx Whisper model store and on-demand download source; CI build/artifact passed.
- Run #227: stale Lyrics disclaimer fix; CI passed.
- Run #220: manual timed captions CI-verified. Run #223: mic voice typing CI-verified, device behavior open.
- Latest source verification: Run #223, ID `42187782306`, commit `70ef8bf5751f41acac80b6237d5a6ee342e86eef8`; duplicate voice-typing imports fixed; Android debug APK build and artifact upload succeeded. This is CI evidence only, not device speech-service verification.\n- Latest source verification is Actions Run #218, ID `42136858074`, commit `41e2380a2712b83407785f35328cada5f36a6a4b`; configured tests, Android debug APK build and artifact upload all passed.
- Artifact: `Creator-Studio-debug-apk`, 19,788,638 bytes, SHA-256 `a62be5d05d0d1da60cae44550a5bbe3d4bdcf7c4d81969d67dfc10a1ca73c0b6`.
- Run #216 verified incremental active-session analyser reconciliation (19,785,986-byte artifact, SHA-256 `c401a1d3bcf7891f496e274c64b9563390bb811ff136ab4a1548773d20aba5ba`); Run #214 verified multi-session FFT/waveform capture (19,783,823-byte artifact, SHA-256 `fdf2d1565862ab2c61a17e988424de2a20c21365f18e15f605f012bc64423537`).
- Run #210 adds independent per-video/image clip beat-response settings (persisted in ProjectStore schema v7), carries them into deterministic render plans, and scales each offline image/video layer from its own settings and analyzed beat pulse.
- Run #208 includes export settings regression tests, overlapping video composition/source-time tests, and clip canvas mode propagation tests. Run #195 independently verified the first synchronized multi-video preview controller integration.
- Run #184 verified export retry UI/source compiles. Retry restarts from the beginning and asks for a new save location; it is not partial-export resume.
- CI unit tests, debug APK build and artifact upload succeeded.
- Device installation, launch, real media decode/encode and exported-file playback are NOT yet user-verified.
- Current source includes Compose editor shell, media pickers, Media3 preview, project persistence, multi-track editing, live visualizer, offline PCM/AAC audio export, offline audio-reactive still-image/source-video visualizer MP4 pipeline, source frame decoding, Canvas compositing, H.264 encoding, AAC/H.264 muxing and decodable embedded source-video audio mixing. Latest integration is CI-verified in Run #173; physical-device acceptance remains open.
- Not complete: device acceptance, accurate post-mixer audio analysis and preview/export parity, true 3D/GPU visualizer, background removal, token-timed caption segmentation, Urdu/Roman Urdu script conversion, karaoke sync, chroma key, advanced video effects, resumable export recovery and official social integrations. Imported-media offline Whisper transcription has initial source + CI implementation in Runs #228–#231; actual model download, recognition accuracy and device performance remain unverified. Manual timed captions are source + CI verified in Run #220; microphone voice typing is source + CI verified in Run #223, but device speech provider behavior is unverified. Live timeline image/video overlays and independent per-layer beat pulses are now source-implemented and CI-verified through Run #218.
- Do not tell user to install this as the final requested app. It is a foundation build only.

## Next task sequence
1. Check latest Actions run and source tree before editing.
2. Audit Gradle catalog, app Gradle config, manifest, MainActivity, VisoApp and theme.
3. Build a persistent editor domain model: Project, MediaAsset, Track, Clip, Transform, AudioSettings, CaptionSegment. Include stable IDs, timeline start/duration, source in/out, track type and per-clip settings.
4. Add repository/storage layer with project serialization and safe media URI permissions/copy strategy.
5. Replace the current simple layer list with a functional multi-track timeline: selection, trim, split, move/reorder, delete, snapping, playhead, undo/redo, save/reopen.
6. Keep Media3 preview stable and synchronize playhead/clip selection.
7. Add proper DSP pipeline for independent clip volume, pitch, time stretch, BPM/beat detection, EQ/bass and voice effects.
8. Implement visualizer as the product's signature: audio FFT/RMS/onset pipeline, waveform/spectrum/radial/particles, GPU/3D renderer, background artwork, multiple foreground PNG layers, independent beat response and synced lyric overlay. Test preview/render parity.
9. Continue imported audio/video transcription: the on-device Whisper model download + initial decode/recognize/caption insertion path is source + CI verified in Runs #228–#231. Next consume token timestamps for short phrase captions, then implement Urdu/Roman Urdu/Roman English script conversion, editable word timing, karaoke and emoji. Manual captions and mic voice typing exist in source + CI (Runs #220/#223). Device/model validation remains open.
10. Test source-video embedded-audio A/V sync on Android hardware; the source integration is already CI-verified in Run #173.
11. Export canvas/FPS/bitrate controls and retry action are source + CI verified (Runs #183–#185); implement resumable partial-export recovery separately.
12. Synchronized multi-video preview, deterministic overlap ordering/source-time alignment, clip FIT/FILL/CROP modes and preview-only safe-area guides are source + CI verified (Run #208). Per-layer offline beat response is verified in Run #210. Live image/video timeline overlays, multi-session beat analysis and independent live beat pulses are source + CI verified in Runs #214, #216 and #218. Next validate preview/export parity and device performance; note that per-session snapshot averaging is not a sample-accurate output-mix capture.
13. Implement image segmentation/cutout, transparent PNG refinement, chroma key, compositing, transitions and effects.
12. Add official supported social import/upload only where platform terms and API/OAuth permit; system share-sheet fallback.
13. Run CI and inspect APK artifact; complete automated tests and static analysis. Keep release status honest.
14. Update `docs/STATE.md` and `docs/ROADMAP.md` with every material change and exact commit/run/artifact evidence.

## Safety/legal/technical boundaries
- Never promise “zero copyright”. Pitch/speed/green screen do not grant rights or defeat copyright detection.
- Do not scrape or bypass social platforms; use supported official APIs/OAuth or Android share sheet.
- Ask permission only when user starts a feature that needs it.
- Offline-first editor; clearly label network/model/account/cost requirements.
- No force-push; no fake feature claims; no release tag until acceptance gates pass.

## Reserved future upgrades
Keep future expansion at the END of the roadmap: cloud sync, templates/presets marketplace, AI beat cuts/auto-edit, advanced tracking/reframe, batch export, brand kits, collaboration, plugin API, desktop companion, advanced grading and pro audio routing. Do not let these distract from completing the core editor first.


## Latest continuation — caption font family
- Added per-caption Sans, Serif, Monospace and Cursive font-family choices, persisted with a backwards-compatible Sans default and mapped in both live preview and offline MP4 rendering. Regression coverage added; await the new CI run before marking it verified. Check font fallback/visual parity on a physical Android device.
