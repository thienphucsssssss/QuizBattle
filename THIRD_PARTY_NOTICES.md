# Third-party assets

## Be Vietnam Pro

Copyright 2021 The Be Vietnam Pro Project Authors.

Files: `nqb-client-core/src/main/resources/ui/fonts/BeVietnamPro-Regular.ttf`, `BeVietnamPro-SemiBold.ttf`, `BeVietnamPro-ExtraBold.ttf`.

Source: [Google Fonts repository](https://github.com/google/fonts/tree/main/ofl/bevietnampro), [upstream project](https://github.com/bettergui/BeVietnamPro).

License: SIL Open Font License 1.1; full license distributed alongside the fonts as `OFL.txt`. Fonts are bundled for offline use; the application does not fetch them at runtime. Font files were not modified.

Java dependencies and their versions are declared in Maven POMs. The UI uses original JavaFX mascot/vector artwork rather than copied Kahoot artwork.

## Project-provided audio

The project owner supplied 17 WAV interface/game effects and two WAV music tracks, bundled under `nqb-admin-client/src/main/resources/ui/audio/`. The original music filenames are `Network_Quiz_Battle_BGM_Seamless_Loop (1).wav` and `High_Score_Glory.wav`. File provenance and SHA256 values are recorded in `manifest.json` and `music-manifest.json`; the application uses the supplied audio without downloading media at runtime. The font and Java dependency licenses do not grant rights to these audio files; consult the project owner about their separate terms before redistributing them outside the project.

## Apache POI

Apache POI 5.5.1 and its runtime dependencies are bundled in the desktop Client for local XLSX/DOCX import and blank template creation. POI is licensed under Apache License 2.0; upstream LICENSE/NOTICE files remain in the distributed dependency JARs. Sources and release information: [Apache POI](https://poi.apache.org/). No Microsoft Office installation or Internet connection is needed by the product. The Server does not include POI.

## Java Native Access (JNA)

Java Native Access (`net.java.dev.jna:jna`, version 5.16.0) is bundled in the desktop Client (`nqb-client-core`) to call the Windows Native Wifi API (`wlanapi.dll`) for LAN-only Wi-Fi RSSI proximity estimation and the Windows Runtime permission API (`combase.dll`) for user consent. JNA is dual-licensed under the Apache License 2.0 and LGPL 2.1+; this project uses JNA under the Apache License 2.0. Upstream notices and source information: [Java Native Access](https://github.com/java-native-access/jna).
