# Viso Studio — Current State

Last updated: 2026-11-05

## Repository identity
- Repository: `jabir1718/Chatgpt-fail-next-change`
- Branch: `main`
- Product: Viso Studio — Android video/audio creator app.
- Canonical plan: `docs/ROADMAP.md`
- Full feature requirements: `docs/PRODUCT_SPEC.md`
- Handoff instructions: `docs/CONTINUATION.md`

## Verified build evidence
- GitHub Actions workflow: `.github/workflows/android.yml`
- Successful run: [Run #6](https://github.com/jabir1718/Chatgpt-fail-next-change/actions/runs/42079253528)
- Built commit: `0b8831f35fcee2677bd46e3fc64519c29aec7683`
- Job: Assemble debug APK — success.
- Build step: success.
- Artifact upload: success.
- Artifact name: `Viso-debug-apk`
- Artifact size: 19,381,660 bytes (about 18.5 MiB).
- Artifact expires: 2026-11-19.
- User has NOT yet confirmed device installation or launch.

## Implemented / verified in source
- Fresh Kotlin + Jetpack Compose Android project.
- Five navigation areas: Studio, Audio, Visuals, Lyrics, Export.
- Android document picker actions for video, audio and image.
- Imported items listed as selectable timeline layers.
- Media3 ExoPlayer playback path for selected video/audio.
- Video preview surface for selected video.
- Basic active-preview volume, pitch and speed controls.
- Remove imported layer action.
- GitHub Actions debug APK pipeline using JDK 17, Gradle 8.11.1 and Android SDK Platform 36.

## Important limitations — do not misrepresent
- Current timeline is a list of imported layers, NOT a real editable multi-track timeline.
- Pitch/speed/volume controls affect active preview only; no persistent per-clip DSP or rendered export.
- Visualizer is a placeholder graphic, NOT audio-reactive FFT/3D rendering.
- PNG picker imports a layer reference, but image compositing/background removal are not implemented.
- Lyrics/transcription, language detection, Roman Urdu/Urdu/English output and voice typing are not connected.
- Chroma key, effects engine, transitions and background cutout are not implemented.
- Video/audio export and social direct upload are not implemented.
- Direct social upload must use official supported APIs/OAuth only. Never promise copyright-free/zero claims or bypass copyright detection.

## Current milestone status
- M0: CI foundation and APK artifact complete. Device install/launch acceptance pending user.
- M1: In progress; basic import/playback foundation only.
- M2–M8: Planned. See roadmap for detailed acceptance criteria.
- No release tag; no claim of complete app.

## Next engineering priorities
1. Inspect current Gradle/source/manifest and build logs at latest HEAD.
2. Implement persistent project model and managed media import.
3. Implement proper multi-track timeline model and editor actions: select, trim, split, reorder, delete, undo/redo, save/reopen.
4. Add playback transport/playhead synchronization.
5. Then implement DSP/audio engine, prioritizing separate per-clip controls.
6. Visualizer remains highest product priority after editor foundation: real FFT/beat response, background artwork, foreground transparent PNG layers, per-layer beat animation, 3D/GPU renderer.
7. Add caption/transcription provider adapter with explicit permissions and language/script choices.
8. Compositing, cutout, chroma key, export and social integrations follow the roadmap.

## Working rules
- User is exhausted and explicitly asked not to be asked for repeated OK/start confirmations. Do not ask them to monitor builds or keep the screen on.
- User wants a single install-ready app only after substantial requested functionality is implemented; do not ask for frequent interim installs.
- User prefers full-file replacement, but replacement must be based on the latest complete file and preserve validated dependencies/callers. Do not blindly overwrite files.
- User handles Android device installation/testing; assistant can work on GitHub and inspect CI.
- Be truthful about the difference between UI, implemented code, CI build, and device-verified behavior.
- Do not say work continues in the background after the conversation ends. Resume from repository state when user returns.
