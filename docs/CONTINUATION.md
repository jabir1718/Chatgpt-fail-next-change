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
- Latest completed successful build is Actions run #171, ID `42127760975`, source SHA `c8fc505e3c6c59a68b5d4ba7087bd45aded00825`.
- Artifact: `Creator-Studio-debug-apk`, 19,735,833 bytes, SHA-256 `59c7643d9dbcf5aa935abba36b43520ce85689741c1adcf2f1a508ee29d87740`.
- CI unit tests, debug APK build and artifact upload succeeded.
- Device installation, launch, real media decode/encode and exported-file playback are NOT yet user-verified.
- Current source includes Compose editor shell, media pickers, Media3 preview, project persistence, multi-track editing, live visualizer, offline PCM/AAC audio export, offline audio-reactive still-image/source-video visualizer MP4 pipeline, source frame decoding, Canvas compositing, H.264 encoding and AAC/H.264 muxing. Latest integration is CI-verified in Run #171; physical-device acceptance remains open.
- Not complete: device acceptance, video embedded-audio mix in visualizer export, full synchronized preview compositing, 3D/GPU visualizer, background removal, transcription, voice typing, chroma key, advanced video effects, export presets/recovery and official social integrations.
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
9. Implement lyrics/captions: speech/lyrics provider adapter, Start/Stop permissions, language detection, editable word timing, Urdu/Roman Urdu/Roman English, voice typing, karaoke and emoji.
10. Include source-video embedded audio in the visualizer MP4 soundtrack and test A/V sync.
11. Add export presets and resolution/FPS/bitrate controls, then implement retry/recovery.
12. Implement image segmentation/cutout, transparent PNG refinement, chroma key, compositing, transitions and effects.
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
