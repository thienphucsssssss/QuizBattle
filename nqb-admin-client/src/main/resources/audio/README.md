# Local SFX integration (16 October 2026)

The 17 user-supplied WAV files and manifest are packaged under `/ui/audio/`, not this legacy placeholder directory. Source pack: `E:/_HUTECH/Network_Quiz_Battle_SFX_V2_17_WAV`. Originals were not changed; each copied WAV matches its manifest SHA-256. The combined listening preview is not bundled.

PCM WAV: mono, 44.1 kHz, 16-bit. `SfxSlots` maps all 17 cues and uses standard JDK Java Sound on a dedicated bounded background queue. No JavaFX media dependency, network audio, or background music. The projector shares its owner's SFX instance and never plays cues separately.

Student defaults to mute; teacher defaults to 35% volume. User preferences, mute, test button, event deduplication, click debounce and bounded overlap are implemented. QA is always silent. Clips are lazily opened off the FX thread and cached, then closed at app exit. An unavailable audio device does not stop the quiz. `SfxAssetsTest` checks all hashes/PCM formats/durations without playing sound. Actual loudness/quality and speaker selection still require a listening check.

The source pack README describes algorithmically synthesized originals without imported samples. We preserve the supplied files; no new sound generation or independent legal license verification was performed.
