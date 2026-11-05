# Viso Studio

**Viso Studio** is a from-scratch Android creator app for editing video and audio, building beat-reactive visualizers, creating automatic multilingual captions, and exporting content for TikTok, YouTube Shorts, Instagram Reels, and other platforms.

## Product direction
- Professional, mobile-first editor with a real multi-track timeline.
- GPU-accelerated 2D/3D audio-reactive visualizer.
- Background artwork plus foreground PNG/image/video layers.
- Automatic subject cutout and background removal.
- Per-clip audio controls: volume, pitch, tempo, BPM, EQ, bass, voice and effects.
- Automatic speech/lyrics transcription, word timing, multilingual captions, Roman Urdu and Urdu script, plus voice typing.
- Green screen, effects, transitions, color tools, and platform-aware export.
- Rights-aware copyright guidance and supported social sharing/upload.

## Current build
This repository is a clean restart. The first Android Compose workspace UI is being established. UI controls and mock preview are not yet evidence of implemented media processing; each engine capability will be added and tested in its own milestone.

See docs/PRODUCT_SPEC.md and docs/ROADMAP.md.

## Build
GitHub Actions builds the debug APK on an Ubuntu Android SDK runner. Download the Viso-debug-apk artifact from a successful workflow run.
