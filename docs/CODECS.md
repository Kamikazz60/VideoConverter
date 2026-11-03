# Codecs intégrés et licences

Moteur : **FFmpegKit full-gpl 6.0.4** (FFmpeg n6.1.6), build de https://github.com/ffmpegkit-maintained/ffmpeg (release `v6.0.4-lts-android`), AAR arm64-v8a + x86_64.
SHA-256 de l'AAR : `7453e9c43749cd44689d6da49dbb24b5061f74d1c8fe0858e3866a7c5daa4d2c`.

## Présence des noms d'encodeurs dans libavcodec.so (analyse du binaire, 03/11/2026)
Les chaînes `msvideo1`, `cinepak`, `libx265`, `libx264`, `libxvid`, `mpeg4`, `rawvideo`, `h264_mediacodec`, `hevc_mediacodec`, `pcm_s16le` sont présentes. **Cela ne prouve pas** que l'encodeur fonctionne (le nom peut aussi désigner un décodeur).

## Disponibilité réelle
Déterminée **par l'application elle-même** (Paramètres › Diagnostic) : encode d'une vidéo de test avec la commande réellement utilisée, relecture ffprobe, décodage complet. Un profil échoué est grisé avec la raison.
Résultats sur le Pixel 6a (rapportés par l'utilisateur, 03/11/2026) : **aucun des 9 encodeurs n'a échoué** au test réel (HEVC/AVC logiciel et matériel, RGB, YUY2, MS Video 1, Cinepak, Xvid). Les détails par codec n'ont pas été relevés. Cela valide l'encodage de courts fichiers de test, pas l'ouverture dans AviMéca / AviStep.

| Profil | Encodeur FFmpeg | Format de pixel | Contraintes |
|---|---|---|---|
| MP4 HEVC | libx265 (CRF, preset veryfast) ou hevc_mediacodec (débit) | yuv420p / 10 bits si source 10 bits (logiciel) / nv12 (matériel) | tag hvc1 |
| MP4 AVC | libx264 ou h264_mediacodec | yuv420p / nv12 | |
| AVI RGB | rawvideo | bgr24 (ou bgr0) | dimensions libres |
| AVI YUY2 | rawvideo, tag `YUY2` | yuyv422 | dimensions paires |
| AVI MS Video 1 | msvideo1 | rgb555le | multiples de 4 (bandes noires) |
| AVI Cinepak | cinepak | yuv420p | multiples de 4 (bandes noires) |
| AVI Xvid | libxvid, tag `XVID` | yuv420p | dimensions paires |
Audio : MP4 → AAC recopié ou ré-encodé 128 kb/s ; AVI → PCM 16 bits.

## Licences
- FFmpeg : LGPL 2.1+ ; avec x264, x265, Xvid activés → **GPL v2+/v3**. L'application, qui lie ce binaire, doit donc être distribuée sous GPL (usage personnel : aucune obligation de publication tant qu'il n'y a pas de redistribution ; en cas de partage de l'APK, fournir le code source).
- x264 / x265 : GPL v2. Xvid : GPL v2. MediaCodec : API Android.
- Le détail des licences des bibliothèques embarquées est dans l'AAR (`res/raw/license_*.txt`).
- Brevets : HEVC/H.264/MPEG-4 sont soumis à des brevets ; usage personnel non commercial uniquement.
