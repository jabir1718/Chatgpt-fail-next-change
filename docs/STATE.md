# Creator Studio — Current State

Last updated: 2026-11-06

## Repository identity
- Repository: `jabir1718/Chatgpt-fail-next-change`
- Branch: `main`
- Product: Creator Studio / Viso Studio Android video and audio editor.
- Canonical plan: `docs/ROADMAP.md`
- Full feature requirements: `docs/PRODUCT_SPEC.md`
- Handoff instructions: `docs/CONTINUATION.md`

## Verified CI evidence
- Latest source verification: Run #223, ID `42187782306`, commit `70ef8bf5751f41acac80b6237d5a6ee342e86eef8` — voice-typing duplicate-import fix; configured build completed, Android debug APK build and artifact upload all successful. This verifies compilation/build, not device speech recognition.
- Previous major visual-preview verification: Run #218, ID `42136858074`, commit `41e2380a2712b83407785f35328cada5f36a6a4b` — configured tests, Android debug APK build and artifact upload all successful.
- Run #218 artifact `Creator-Studio-debug-apk`, 19,788,638 bytes, SHA-256 `a62be5d05d0d1da60cae44550a5bbe3d4bdcf7c4d81969d67dfc10a1ca73c0b6`.
- Run #216, ID `42136266130`, commit `3723f6814cb48b8ab651e2dd0a3c31548cbf908f`, verified incremental multi-session live analyser reconciliation, regression tests, debug APK build and artifact upload. Artifact 19,785,986 bytes, SHA-256 `c401a1d3bcf7891f496e274c64b9563390bb811ff136ab4a1548773d20aba5ba`.
- Run #214, ID `42135838905`, commit `1ba5a3ddfe9af4435824478cc837a198f8a37632`, verified multi-session FFT/waveform analyser integration, debug APK build and artifact upload. Artifact 19,783,823 bytes, SHA-256 `fdf2d1565862ab2c61a17e988424de2a20c21365f18e15f605f012bc64423537`.
- Run #208 previously verified multi-video preview/canvas content mode integration.
- Run #195, ID `42131807951`, commit `6eb95db8e65bc69fd30c5a9ab6c76a592b9e6f5d`, verified synchronized multi-video preview controller source, overlap-order/source-position tests, debug APK build and artifact upload. Run #208 reverified the full later integration.
- Run #184 verified export retry UI/source compiles and APK artifact upload; retry reruns the selected pipeline with a new save location, not partial resume.
- Run #183 verified export canvas/FPS/bitrate controls and pipeline wiring; Run #185 adds regression tests for defaults, accepted/rejected FPS and bitrate, and settings preservation.
- Run #173 completed successfully: source-video embedded audio selection/mixing, configured unit tests, Android debug APK build and artifact upload passed.
- Run #173 artifact: `Creator-Studio-debug-apk`, 19,737,358 bytes, SHA-256 `008058bcf48bdbbcfa0f6a0a9bdc0a9ecb1139095b85534d0a95108f9625b9b0`.
- Run #140 completed successfully: Android SDK setup, `assembleDebug`, configured tests, and APK artifact upload all succeeded.
- Run #140 artifact: `Creator-Studio-debug-apk`, 19,643,832 bytes, SHA-256 `77f565128599e024f7d6c1e94d06d82021da940351ec7f6e54c0244848569d1a`.
- Run #141 completed successfully: frame composition planning source, tests, debug APK build and artifact upload passed. Artifact size 19,648,872 bytes, SHA-256 `999f0c80914b62b32b246e53cc933c0a3fca6e2cc3838e6a84694454b9593390`.
- Run #145 completed successfully after the offline mixer fade-boundary test correction: all configured unit tests, debug APK build and artifact upload passed. Artifact size 19,656,590 bytes, SHA-256 `71df30519b5fa1cbd55bd61e4a52eed3ad4c1417c4cc3ad98536d98296b92d13`.
- Run #147 completed successfully: disk-backed PCM source, MediaCodec decoder adapter, mixer refactor and regression tests compiled; debug APK build and artifact upload passed. Artifact size 19,666,555 bytes, SHA-256 `59502cb0571261c7c70db5b35e7364c0d42d367103df3f91a50d7a2a11726639`.
- Run #148 completed successfully: AAC-LC encoder, project audio-mix export pipeline, decoder cleanup and PCM16 conversion tests compiled; debug APK build and artifact upload passed. Artifact `Creator-Studio-debug-apk`, 19,677,482 bytes, SHA-256 `62c3e9270a2db25aca1837ba98f0e83983e67e800bdad5231eee6b59df405427`.
- Run #140 verifies native DynamicsProcessing source integration compiles and configured tests pass; it does not prove effect audibility/device support.
- CI success verifies configured tests and artifact upload, not device behavior or product completion.
- User has not confirmed device installation, launch, real-time DSP audibility, editing/export on device.

## Implemented in source (not all device-verified)
- Kotlin + Jetpack Compose Android app with Studio, Audio, Visuals, Lyrics and Export areas.
- Android document pickers for video, audio and images; project media URI permission/copy strategy.
- Project create/open/rename/duplicate/delete and autosave/recovery via ProjectStore.
- Multi-track timeline editing: clip selection, move, trim, split, delete/ripple-delete, undo/redo, snapping, track lock/mute/gain/solo and per-clip audio settings.
- Synchronized multi-video timeline preview uses independent muted Media3 players, a monotonic timeline clock, source-time seeks, drift correction and track-order compositing. Per-clip transforms and source aspect ratio feed preview layers; Run #208 verifies source compilation and configured tests, but actual device sync/decoder load remains unverified.
- Per-video/image clip canvas modes FIT, FILL and CROP are editable in the selected-clip controls, saved in ProjectStore schema v7 with FIT fallback for older projects, carried by RenderCompositionPlan and applied by offline MP4 image/video rendering. Live selected-source and multi-video preview apply the selected clip mode. Safe-area guides are preview-only.
- `BeatResponseSettings` is persisted per video/image timeline clip (ProjectStore schema v7; older projects default disabled). Each selected layer has independent ON/OFF, pulse-scale (0–50%) and sensitivity (0.25×–2×) controls. `RenderCompositionPlan` carries settings for each layer and the offline renderer applies analyzed beat pulses independently. Live timeline preview now also carries each clip's settings through `TimelineVideoPreviewController` and applies an animated scale pulse to each image/video layer independently. A shared `scaleForPulse` function is covered by regression tests and used by both live and offline rendering. Run #218 verifies the source, configured tests, APK build and artifact upload; physical-device preview/export parity remains unverified.
- Concurrent Media3 timeline audio preview uses independently scheduled players, timeline clock alignment, clip gain/fades, pitch and playback rate. This uses Android's output mixer, not sample-accurate studio mixing.
- Waveform decode/cache and timeline waveform display (MediaCodec PCM decode; Run #137 CI-verified).
- Live FFT/spectrum, waveform, onset/beat pulse and rough BPM analysis; Spectrum, Radial, Waveform, Rings and Particles visualizer previews (Rings/Particles are 2D Canvas, not 3D/GPU). Timeline mode can attach/detach Android Visualizer effects incrementally across active Media3 audio sessions and combines their FFT/waveform snapshots for live analysis (Runs #214 and #216). The aggregation is per-session snapshot averaging, not sample-accurate capture of the final Android output mix.
- Project-linked visualizer background/foreground image references and foreground beat pulse/scale settings.
- Master audio processing settings (five EQ bands, low-frequency EQ boost and compressor) persist in ProjectStore schema v4; Android DynamicsProcessing is attached to selected-source and timeline Media3 sessions on supported API 28+ devices. Run #140 CI-verified; device audibility remains unverified.
- `RenderCompositionPlan` describes deterministic frame timestamps, visual z-order, video source positions, active captions, audio schedules and master settings (Run #141 CI-verified). It now feeds the actual offline frame compositor.
- `OfflineAudioMixEngine` provides deterministic block-based PCM mixing from decoded in-memory PCM buffers: timeline placement, linear source interpolation, clip playback rate, volume, fade-in/out, track gain, track mute, audio-capable solo rules, five-band peaking EQ, low-band bass boost, compressor and output peak limiting. Run #145 verified the offline mixer and regression tests after correcting the final-sample fade-out tolerance. `PcmAudioSource` now supports both in-memory and disk-backed float PCM; a disk-backed cache implementation and MediaCodec decoder adapter are source-implemented. Run #147 CI-verified their compilation and the configured tests; physical-device decoding and end-to-end export remain unverified.
- The mixer consumes disk-backed `PcmFileAudioSource` data decoded by `MediaCodecPcmDecoder`. AAC-LC encoding and audio-only M4A export are CI-verified (Runs #148, #152); storage preflight tests are CI-verified (Run #155). Export Studio now offers Android SAF save actions for M4A and visualizer MP4, with progress and cancellation (Run #168). The visualizer MP4 pipeline combines the timeline audio mix/AAC, offline FFT analysis, still-image/PNG layers, on-demand source-video frame decoding, Canvas composition, H.264 encoding and AAC/H.264 muxing. Run #173 verified the latest source integration, including decodable embedded source-video audio, configured unit tests, debug APK build and artifact upload. Actual codec behavior, saved files, A/V sync, video rotation and performance remain device-unverified.

## Important limitations — do not misrepresent
- All timeline/project/media features need real-device acceptance tests including app restart and permission recovery.
- Native DynamicsProcessing is live preview only on supported Android versions/devices. Offline mixer DSP is a separate pure Kotlin implementation and its configured tests/build are CI-verified.
- Offline mixer consumes disk-backed PCM decoded through MediaExtractor/MediaCodec and is connected to AAC-LC M4A export; physical-device decoding/export acceptance remains open.
- Concurrent audio preview uses Android's output mixer, not sample-accurate DSP/offline mix. Synchronized multi-video preview is now source-implemented and CI-verified (Run #208), but device playback sync, simultaneous decoder limits, thermal/memory behavior and preview/export parity remain unverified.
- Live visualizer analysis is real-time for active Android playback. Timeline audio-session capture averages the individual active ExoPlayer session FFT/waveform snapshots; it is not a sample-accurate post-mixer bus capture. Live timeline image/video layer beat pulses are source-implemented and CI-verified in Run #218; actual device responsiveness and preview/export parity remain unverified. The pure Kotlin offline analyzer derives per-frame RMS, FFT bands, waveform and beat pulses from the timeline mix (Run #157). `OfflineVisualizerFrameRenderer` draws Spectrum, Radial, Waveform, Rings and Particles with still/video bitmaps, captions, background and foreground beat scaling (Run #159). The bounded `OfflineH264VideoEncoder` encodes one rendered bitmap at a time; `ImageAssetBitmapLoader` handles still-image downsampling; `VideoAssetFrameDecoder` uses MediaMetadataRetriever for active source-video frames with bounded scaling; `OfflineAudioVideoMuxer` combines H.264/AAC. These are connected end-to-end to the Export Studio MP4 action and CI-verified through Run #173. Hardware codec support, rotation correctness, A/V sync, thermal behavior and performance remain device-unverified. Decodable embedded audio inside source-video clips is mixed by this visualizer export path (Run #173); device A/V sync remains unverified. True 3D/GPU and preview/render parity remain open.
- Automatic background removal, edge refinement, masks, chroma key, imported-audio transcription, language/script conversion and karaoke sync are not implemented. Microphone voice typing is source-implemented and CI-verified in Run #223; Android speech-service availability, permission flow, recognition accuracy and language behavior remain device-unverified.
- Video transitions/effects/filters/reverse/speed ramps/keyframes/motion tracking and full multi-clip rendering are not implemented.
- Audio M4A and audio-reactive visualizer MP4 pipelines are source-implemented and CI-verified. Visualizer export aspect/FPS/bitrate selectors are source-implemented and CI-verified: 9:16, 1:1, 4:5, 16:9; 24/30/60 FPS; 4/8/12/20 Mbps H.264. ProjectStore schema v7 persists bitrate, per-clip canvas content mode and per-clip beat-response settings with backward-compatible defaults (missing/unknown content mode falls back to FIT). Run #185 regression tests cover defaults, valid/invalid FPS and bitrate, and copy preservation. Run #210 adds per-layer beat-response model/render-plan regression tests. Export retry is source-implemented and CI-verified (Run #184); it restarts an export and asks for a new save location, not partial resume. Device acceptance and official social integrations remain open.
- Social integrations must use official supported APIs/OAuth or Android share-sheet fallback. Never claim copyright bypass or “zero copyright”.
- No release build, signed release APK or complete-app claim.

## Lyrics & voice input update (2026-11-06)\n- Manual timed captions editor and live scheduled caption preview: source-implemented; Run #220 successful.\n- Microphone voice typing: Android SpeechRecognizer with explicit Start/Stop, runtime RECORD_AUDIO permission requested only on Start, partial/final result handling, and draft insertion; Run #223 successful after fixing duplicate imports. `EXTRA_PREFER_OFFLINE` is only a preference and does not guarantee offline recognition.\n- This feature transcribes live microphone speech only. It does not transcribe imported MP3, song or video audio. Device speech provider, permission UX, partial results and recognition accuracy are not yet device-tested.\n\n## Current milestone status
- M0: CI foundation/debug artifact pipeline complete; device install/launch acceptance open.
- M1: Timeline/project foundation and synchronized multi-video preview/canvas mode controls are source-implemented and CI-verified through Run #208; device acceptance open.
- M2: Partial. Waveform, timeline preview scheduling, native live EQ/bass/compressor, disk-backed PCM decode, offline mix and AAC-LC M4A export are source-implemented and CI-verified; device verification and preview/export parity remain open.
- M3: Partial. Live/offline audio-reactive visualizers, live timeline image/video overlays, multi-session beat analysis, independent live/offline per-layer beat response, still-image/source-video frame decoding, Canvas compositing and H.264/AAC MP4 export are source-implemented and CI-verified through Run #218. Device acceptance, accurate post-mixer analysis, preview/render parity and true 3D/GPU remain open.
- M4–M8: Not complete. Manual captions and microphone voice typing have source + CI evidence; imported-media transcription and the remaining M4 features are open. See roadmap.

## Next engineering priorities
1. Validate synchronized multi-video preview, FIT/FILL/CROP and preview/export parity on Android hardware, including rotation, decoder limits, A/V sync and thermal/memory behavior.
2. Validate live image/video overlay playback, independent beat pulses, and preview/export parity on Android hardware; improve audio analysis beyond averaged per-session snapshots if device/API support permits.
3. Build true 3D/GPU visualizer renderer with capability detection and a tested 2D fallback.
4. Implement imported audio/video transcription with explicit Start/Stop, permission disclosure, language detection and Urdu/Roman Urdu/Roman English script support; microphone voice typing is already source + CI verified but is not file transcription.
5. Implement subject cutout/background removal, edge refinement, chroma key and transparent PNG output.
6. Add advanced video effects, transitions, reverse/speed ramps and resumable partial-export recovery.
7. Continue regression tests and update state/roadmap with exact CI/device evidence.

## Working rules
- User does not want repeated start confirmations or to be asked to monitor builds/keep screen on.
- User wants one final install-ready APK after substantial implementation; no interim APK/repository links.
- User handles Android device installation/testing.
- Keep source, CI, APK artifact, device verification and product completion as separate states.
- Do not claim background work continues after chat ends.


Latest source verification: Run #218 successfully built and uploaded the APK for live independent beat pulses on timeline image/video layers. Run #216 verified incremental multi-session audio analysis. Physical-device playback, analysis accuracy, image/video decoder performance and preview/export parity remain open.
