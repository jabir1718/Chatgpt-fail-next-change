# Creator Studio — Master Roadmap & Acceptance Gates

This is the canonical product plan. Read this file and `docs/STATE.md` before every new development session. Do not restart the roadmap from memory or silently drop requirements.

## Product promise
A polished, original Android creator studio for TikTok, YouTube Shorts, Instagram Reels and longer videos. It should feel as capable and organized as leading mobile editors (VN, InShot, CapCut) without copying their branding. Preview-first workspace, contextual tool drawers, separate tool groups, a real multi-track timeline, responsive 3D visualizer, and no forced watermark.

## M0 — Clean foundation & build
- [x] Fresh Android Kotlin/Jetpack Compose project in this repository.
- [x] GitHub Actions Android SDK build workflow.
- [x] Debug APK artifact pipeline verified; the current artifact name is `Creator-Studio-debug-apk`.
- [ ] User installs the APK and confirms it launches on device.
- [ ] Add crash reporting/log capture for debug builds (opt-in, privacy-conscious).
**Gate:** CI build + artifact + user launch smoke test.

## M1 — Real editor workspace
- [ ] Project create/open/rename/duplicate/delete; autosave and recovery.
- [ ] Android document picker import for video, audio, still image, transparent PNG.
- [ ] Persist read permissions and copy/import assets into managed project storage safely.
- [ ] Real video preview and audio preview; synchronized playhead and transport.
- [ ] Multi-track timeline: video, audio, image/overlay, text/caption, effect tracks.
- [ ] Clip selection, trim handles, split at playhead, move/reorder, ripple delete, snapping, markers.
- [ ] Undo/redo history, autosave, project serialization/versioning.
- [ ] Per-clip properties and independent source-audio controls.
- [ ] Portrait/landscape canvas, fit/fill/crop, safe areas and aspect presets.
**Current implementation note:** Project create/open/rename/duplicate/delete and autosave, typed tracks, clip selection, trim/split/delete, ±1 second repositioning, undo/redo, playhead seeking, typed-track creation and compatible cross-track movement are present in source. Media import attempts persistable URI permission and falls back to app-managed copies. Latest typed-track/cross-track changes passed CI Run #102. Ripple delete and track mute/lock controls are now implemented in source with unit coverage; direct drag gestures, snapping, markers, and complete synchronized multi-clip transport remain open. Device verification is still required.
**Gate:** Import multiple video/audio/image assets, edit timeline, save/reopen project, playback stays synchronized, no crash.

## M2 — Audio engine & sound design
- [ ] Independent clip volume/gain, mute/solo, pan, fades and keyframe automation.
- [ ] Waveform generation/cache and timeline waveform display.
- [ ] Pitch shift/formant and pitch-preserving time stretch; independent pitch and tempo.
- [ ] BPM and beat/onset detection with editable beat markers.
- [ ] Parametric EQ, bass boost/cut, compressor, limiter, normalization.
- [ ] Voice changer, reverb, echo, delay, chorus, de-esser and noise reduction.
- [ ] Voiceover recording, microphone monitor, input gain and take management.
- [ ] Audio extraction from video; mix bus and master output meter.
- [ ] Optional vocal/instrumental separation only if a suitable model/runtime/license is validated.
**Current implementation note:** Per-clip volume/mute, pitch-semitone and playback-rate values are stored and applied to the selected preview clip. Full independent DSP, waveform cache, mix bus and offline mix/render are not implemented.
**Gate:** Two clips can have different settings; preview and exported mix match; no clipping or broken sync.

## M3 — Visualizer Studio (highest product priority)
- [ ] Real audio analysis (FFT/spectrum, RMS, transient/onset and beat tracking).
- [ ] Visualizer types: spectrum bars, circular/radial, waveform, rings, particles, equalizer shapes, 3D objects.
- [ ] 3D/GPU renderer with quality/performance settings and device fallback.
- [ ] MP3/audio track drives animation in real time and in offline render.
- [ ] Background image/video and independent foreground image/PNG/video layers.
- [ ] Multiple overlays, center artwork/logo/subject, positioning, scale, rotation, crop, opacity.
- [ ] Per-layer beat response: pulse, scale, rotation, glow, shake, particles and sensitivity.
- [ ] Keyframes, easing, masks, blend modes, shadows, outlines and effects.
- [ ] Lyrics/captions over visualizer with safe placement and word highlighting.
- [ ] Save reusable visualizer presets/templates.
**Current implementation note:** A live Android playback-session analyser produces FFT spectrum bands, waveform samples, beat pulses and a rough BPM estimate. Spectrum, radial and waveform preview modes plus image layer references and foreground beat pulse controls are present. This is not offline analysis/rendering; full compositing, multiple independent layers, particle/ring/3D GPU modes and export remain open.
**Gate:** Imported song drives visualizer; background art and foreground transparent PNG are composited; foreground reacts independently to beat; exported video matches preview.

## M4 — Automatic lyrics, captions & voice input
- [ ] Speech/lyrics transcription adapter with a clearly disclosed on-device or online provider.
- [ ] Explicit Start/Stop controls and microphone/network permission prompts only when needed.
- [ ] Automatic language identification and editable word/phrase timestamps.
- [ ] Urdu script, Roman Urdu, Roman English, English, Hindi and further provider-supported languages.
- [ ] Translation/transliteration modes selectable by user; do not silently change script.
- [ ] Voice typing for text/caption fields.
- [ ] Karaoke word highlight, line-by-line styles, emoji/sticker captions, fonts and animation.
- [ ] Caption timing editor, correction workflow, SRT/VTT import/export.
- [ ] Music-vocal transcription caveat and manual correction for uncertain words.
**Gate:** User can transcribe supported audio, edit words/timing, choose script/language, and retain sync after timeline edits.

## M5 — Image cutout, chroma key & compositing
- [ ] Automatic subject/background removal using a validated on-device or online segmentation model.
- [ ] Transparent PNG output, edge refinement, feather, spill cleanup and manual erase/restore brush.
- [ ] Green screen/chroma key with color picker, tolerance, edge softness and spill suppression.
- [ ] Layer masks, blend modes, opacity, crop, transform, picture-in-picture.
- [ ] Background replacement and foreground overlays.
- [ ] Filters, LUT/color adjustment, blur, sharpen, glow, shadow, outlines and transitions.
- [ ] Keyframe animation and motion tracking if device budget permits.
**Gate:** Multi-layer composition exports correctly with transparent edges and no preview/render mismatch.

## M6 — Video editing & effects
- [ ] Trim, split, merge, reorder, duplicate, reverse and freeze frame.
- [ ] Speed controls, ramps, slow motion and time remapping.
- [ ] Transitions, filters, overlays, text/stickers, masks and keyframes.
- [ ] Per-clip crop, rotate, transform, stabilization only if supported.
- [ ] Source audio extraction/replacement and independent audio tracks.
- [ ] Proxy editing and low-memory preview for large media.
**Gate:** Representative multi-clip edit renders with stable timing, transitions and audio sync.

## M7 — Export, sharing & social workflows
- [ ] Export presets: TikTok/Reels/Shorts 9:16, square 1:1, 4:5 and landscape 16:9.
- [ ] Resolution, FPS, bitrate, codec and audio quality controls; device-aware 4K availability.
- [ ] MP4 and supported audio formats; export queue, progress, cancel, retry and recovery.
- [ ] Storage-space estimate and safe handling of interrupted exports.
- [ ] Android system share sheet as universal fallback.
- [ ] Social URL import only where platform terms and supported official interfaces permit it.
- [ ] Direct upload only through official OAuth/API flows with platform permission; disclose account/API restrictions.
- [ ] Draft metadata, caption/hashtags helper and export naming.
**Gate:** Render portrait and landscape projects, validate output, share locally, and use direct upload only where officially supported.

## M8 — Reliability, accessibility & release
- [ ] Real-device performance testing across low/mid/high-end Android devices.
- [ ] Thermal, memory, battery, dropped-frame and long-render testing.
- [ ] Audio/video sync, project recovery, corrupt-media and permission-denied tests.
- [ ] Accessibility labels, touch targets, readable scaling and localization.
- [ ] Privacy policy, clear network disclosure, licenses/attributions and open-source notices.
- [ ] App icon, splash screen, onboarding, settings, help and crash diagnostics.
- [ ] Release signing configuration kept secure; never commit signing keys/secrets.
- [ ] Final APK/AAB build, artifact verification and user install acceptance.
**Gate:** Release candidate passes CI and device test checklist.

## Future upgrades — intentionally reserved after first complete release
- Cloud project sync/backup and cross-device continuation.
- Template marketplace and user-created preset packs.
- Advanced motion tracking, auto-reframe and smart subject tracking.
- AI-assisted beat cuts and music-aware auto-edit templates.
- Batch export, queue management and reusable brand kits.
- Collaborative projects and team review/comments.
- Plugin/extension API for third-party effects.
- Optional desktop companion/project interchange.
- Advanced color grading, scopes and professional audio routing.

## Non-negotiable rules
1. Never call a screen/control a working feature until its underlying behavior is implemented and tested.
2. Every task has states: planned, UI-only, implemented, CI-verified, device-verified.
3. Keep editing offline-first. Network features clearly disclose provider, privacy, cost and account requirements.
4. Never promise zero copyright or implement copyright-detection evasion. Editing a track does not grant rights.
5. Do not bypass social platform access controls. Use official documented APIs/OAuth or system sharing.
6. Request permissions only at feature start and explain why.
7. No milestone is skipped until its acceptance gate passes.
8. Preserve existing working behavior. Replace a whole file only after fetching/reviewing the current complete file and validating imports, callers and dependencies.
9. Before every session, read `docs/STATE.md`, this roadmap, and the exact source files being changed.
10. Keep this roadmap and state document updated in the same change whenever scope or completion status changes.
