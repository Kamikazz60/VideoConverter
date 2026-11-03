# VideoConverter

Conversion vidéo par lots, 100 % locale (sans pub, sans abonnement, sans cloud) :
- **Optimiser mes vidéos** : MP4 H.265 / H.264, 1080p/720p/480p/original/personnalisé, qualité Élevée/Équilibrée/Compacte/Personnalisée, audio conservé ou supprimé.
- **Convertir en AVI** (AviMéca / AviStep) : RGB non compressé, YUY2, MS Video 1, Cinepak, Xvid. Cadence d'origine conservée par défaut, aucune interpolation.

Sorties : `Films/VideoConverter/Optimized` et `Films/VideoConverter/AVI`. Les originaux ne sont jamais modifiés ni supprimés automatiquement.

## Installation sur le Pixel 6a
1. Copiez `apk\VideoConverter-1.0.0-release.apk` (ou `-debug.apk`) sur le téléphone (câble USB, Drive…).
2. Ouvrez le fichier sur le téléphone ; autorisez « Installer des applications inconnues » pour l'application utilisée si Android le demande.
3. Au premier lancement : acceptez les notifications, puis l'accès aux vidéos au moment de choisir des fichiers. Un test des codecs se lance seul (quelques secondes).
4. Ou, avec le câble et le débogage USB : `adb install -r apk\VideoConverter-1.0.0-release.apk`.

Mise à jour : installez la nouvelle APK par-dessus (même clé de signature, `keys\`, à sauvegarder).

## Utilisation
Accueil → un des deux modes → cocher les vidéos (ou « Tout le dossier ») → réglages → **Tester un extrait** (recommandé) → **Convertir**. Le suivi se fait dans l'écran Conversion et la notification (Pause = après le fichier en cours ; Annuler). Historique : ouvrir un résultat, proposer la suppression des originaux (confirmation Android obligatoire).

## Limites actuelles
Voir docs/SUIVI.md. **Version non encore validée sur appareil** : les résultats du test des codecs et des conversions réelles restent à confirmer (docs/TESTS.md).

## Développement
`.\tools\Build.ps1` → tests unitaires + APK debug/release dans `apk\`. Détails : CLAUDE.md, TECHNICAL.md.
