# Viso Studio Product Specification

## Product
A professional Android video and audio creator studio for TikTok, YouTube Shorts, Instagram Reels, and long-form video. Use a preview-first editing workspace, contextual tool panels, and a multi-track timeline. Keep an original visual identity while matching the usability of established mobile editors.

## Visualizer Studio — highest priority
- Real audio-driven spectrum, circular/radial, waveform, particle, and GPU/3D modes.
- Background image/video plus multiple foreground PNG/image/video layers.
- Automatic subject cutout and transparent PNG export with edge refinement.
- Per-layer beat pulse, scale, rotation, glow, keyframes, opacity, masks, and blend modes.
- Center artwork and synced lyrics.

## Video editor
- Multi-clip import, trim, split, reorder, crop, rotate, merge, reverse, speed ramp, freeze frame.
- Transitions, filters, color tools, overlays, picture-in-picture, masks, keyframes, and chroma key.
- Per-clip source audio and extracted audio.

## Audio lab
Each clip must have independent volume, gain, fade, mute/solo, pitch/formant, pitch-preserving tempo, BPM analysis, beat markers, EQ, bass, compressor, limiter, voice changer, reverb, echo, noise reduction, and recording controls.

## AI lyrics and captions
- Transcribe vocals/speech when recognition supports it; show editable text and timestamps.
- Language detection; English, Urdu script, Roman Urdu, Roman English, Hindi, and additional supported languages.
- Voice typing, translation, karaoke highlight, emoji captions, SRT/VTT, and manual correction.
- Ask microphone/network permissions only when the user starts the relevant feature. Accuracy depends on audio clarity, accent, and language support.

## Images and compositing
Import images, transparent PNGs, stickers, logos, and video overlays. Remove backgrounds, refine edges, place artwork behind or in front of visualizers, and animate each layer independently to the beat.

## Social and rights
Use local import by default. Social URL import and direct upload are limited to platform terms and official supported APIs/OAuth. Provide system share-sheet fallback. Provide source/license notes and risk guidance; do not promise zero copyright or bypass copyright detection. Pitch, speed, or green-screen edits do not make third-party music copyright-free.

## Export
Platform presets for 9:16, 1:1, 4:5, and 16:9; resolution, FPS, bitrate, MP4 and supported audio formats; export progress, cancel/retry, and recovery. 4K depends on source and device. No forced watermark.

## Quality rules
No fake controls presented as functional. Label each feature UI-only, prototype, implemented, or verified. Offline-first editing. Test heat, memory, frame drops, sync, and exports on real Android devices.
