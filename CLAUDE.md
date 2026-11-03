# VideoConverter — instructions du projet
(Règles générales : skill developpement-pro. En cas de conflit, ce fichier et la sécurité priment.)
Suivi : docs/SUIVI.md · Décisions : docs/DECISIONS.md · Autres : README.md, ROADMAP.md, TECHNICAL.md, TODO.md, docs/CODECS.md, docs/TESTS.md

## 1. Présentation
Objectif : application Android native de conversion vidéo par lots, 100 % locale : (A) optimisation MP4 H.265/H.264, (B) AVI pour AviMéca / AviStep (RGB, YUY2, MS Video 1, Cinepak, Xvid).
Plateforme : Android 10+ (minSdk 29, targetSdk 36), Pixel 6a prioritaire (arm64-v8a).
Livrable : `apk\VideoConverter-<version>-debug.apk` et `-release.apk` (copiés automatiquement par `tools\Build.ps1`). Package `fr.videoconverter`.

## 2. Environnement (vérifié le 03/11/2026)
- Machine : PC de l'utilisateur, Windows 10 Pro, i5-6600K, 24 Go. Sources : `C:\Users\Doanh\Desktop\Programme IA\VideoConverter`.
- Compilation sur le PC avec JDK 17 Temurin + SDK Android de `%USERPROFILE%\Documents\Codex\AndroidDev` (activés par `tools\Activate-Android.ps1`). Android Studio (JBR 25) est aussi installé mais Gradle 8.13 exige JDK 17.
- Réseau : Maven Central et dl.google.com accessibles.
- Émulateur : inutilisable (pas d'hyperviseur : `emulator -accel-check` code 6). Aucun appareil branché (`adb devices` vide).
- ADB : `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.

## 3. Outils
Gradle 8.13 (wrapper), AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.10.00, Navigation Compose 2.9.5, kotlinx-serialization 1.9.0.
Moteur : FFmpegKit « full-gpl » 6.0.4 (FFmpeg 6.1.6) fork `ffmpegkit-maintained`, AAR local `app/libs/` (arm64-v8a + x86_64). Le dépôt officiel FFmpegKit a été retiré de Maven (404).

## 4. Appareils
Pixel 6a visé. Jamais branché dans les sessions : aucune validation sur appareil réel à ce jour.

## 5. Réseau et autorisations
Aucun service externe. Permissions : READ_MEDIA_VIDEO, FOREGROUND_SERVICE(_MEDIA_PROCESSING), POST_NOTIFICATIONS, WAKE_LOCK.
**Clé de signature : `keys/videoconverter-release.jks` + `keys/keystore.properties` (mots de passe). À sauvegarder : sans elle, impossible de mettre à jour l'app sans la désinstaller.** Exclue de Git. Même clé en debug et release.

## 6. Architecture
`app/src/main/kotlin/fr/videoconverter/` : `model/` (profils, file), `engine/` (CommandBuilder pur et testé, Ffmpeg, Converter = préchecks+encodage+vérification, CodecCheck = test réel des encodeurs), `data/` (Store JSON, OutputFiles MediaStore, MediaRepo), `service/` (QueueManager, ConversionService premier plan), `ui/` (Compose).
FFmpeg écrit dans un fichier temporaire (cache), vérifié (dont PSNR vs original), puis copié dans MediaStore (`Films/VideoConverter/{Optimized,AVI}`, IS_PENDING) ; jamais publié si la vérification échoue. Le test des codecs (`CodecCheck` v2) inclut le contrôle PSNR ; un encodeur matériel qui échoue est désactivé.

## 7. Contraintes
Originaux jamais modifiés ni supprimés automatiquement. Un encodage à la fois. AVI : cadence d'origine par défaut, jamais d'interpolation. Pause = après le fichier en cours (FFmpeg ne gèle pas).
Licence : binaire GPL (x264/x265/Xvid) → app à usage personnel sous GPL.

## 8. Commandes
`.\tools\Build.ps1` (tests + debug + release + copie dans `apk\`) · `-SansRelease`, `-SansTests`.
Installer : `adb install -r apk\VideoConverter-1.0.0-debug.apk` ou copie de l'APK sur le téléphone.

## 9. Particularités
- PowerShell 5.1 : Gradle écrit sur stderr → `$ErrorActionPreference='Continue'` autour de l'appel (déjà fait dans Build.ps1).
- Heredoc Bash avec apostrophes françaises : utiliser l'outil Write pour créer les fichiers.
