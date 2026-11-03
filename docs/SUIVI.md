# Suivi — VideoConverter

## Objectif
Application Android native de conversion vidéo par lots : MP4 (H.265/H.264) et AVI (RGB, YUY2, MS Video 1, Cinepak, Xvid) pour AviMéca / AviStep. Cahier des charges complet : voir la demande initiale résumée dans README.md.

## État actuel (03/11/2026) — niveaux : 1 implémenté · 2 compilé · 3 testé auto · 4 émulateur · 5 appareil réel

| Fonction | Niveau | Remarque |
|---|---|---|
| Construction des commandes FFmpeg (MP4 + 5 profils AVI, dimensions, cadence, avertissements) | 3 | 13 tests unitaires réussis |
| Sonde ffprobe (durée, fps, codecs, rotation) | 2 | jamais exécutée |
| Conversion MP4 HEVC/AVC (matériel + repli logiciel) | 2 | jamais exécutée sur appareil |
| Conversion AVI (5 profils) | 2 | jamais exécutée ; MS Video 1 / Cinepak / `-q:v` non confirmés |
| Test réel des codecs (CodecCheck) + masquage des codecs en échec | 2 | s'exécute au 1er lancement sur le téléphone |
| Vérification après conversion (conteneur, pistes, durée, dimensions, cadence, décodage) | 2 | |
| File persistante, service premier plan, notification, pause, annulation, reprise, thermique | 2 | |
| Sélection (MediaStore + sélecteur système), miniatures, infos par vidéo | 2 | |
| Profils enregistrables (créer, renommer, dupliquer, supprimer) | 2 | |
| Test sur extrait avec comparaison d'images | 2 | |
| Historique, suppression manuelle des originaux (demande système) | 2 | |
| Journal de diagnostic exportable | 2 | |
| APK debug + release signée, icône originale | 2 | `apk\` |

**Aucune fonction n'a été validée sur appareil ni sur émulateur** (pas d'hyperviseur, Pixel non branché).

## Problèmes connus / risques
- Écriture MP4 directe via `saf:` avec `+faststart` (relit le fichier) : à confirmer ; si échec, repli à prévoir (écrire en cache puis copier).
- `hevc_mediacodec` / `h264_mediacodec` via FFmpeg : comportement sur Tensor G1 inconnu ; repli logiciel automatique prévu.
- Sources HDR 10 bits : pas de tone-mapping (HEVC reste en 10 bits logiciel ; AVC → 8 bits avec couleurs ternes).
- Conversion YUV→RGB : matrice par défaut de swscale (à vérifier visuellement sur sources HD).
- Pause = fin du fichier en cours (limite FFmpeg).
- Validation AviMéca / AviStep : uniquement sur ordinateur.

## Dernière session (03/11/2026)
Création complète du projet, moteur, UI, documentation, 2 APK. Environnement vérifié. Essai de rejouer les commandes avec un FFmpeg PC : exécution refusée par le système (binaire tiers non approuvé) → non fait.

Mise à jour 03/11/2026 : sur le Pixel 6a, le test réel des 9 encodeurs ne signale aucun échec (niveau 5 pour l'encodage de courts fichiers de test, rapporté par l'utilisateur).

## Retours de test sur Pixel 6a (03/11/2026, rapportés par l'utilisateur)
- MP4 logiciel (x265) : fonctionne (2 min / 188 Mo → 15 Mo en 7 min). Niveau 5.
- MP4 matériel (mediacodec) : image rayée + bande verte (son OK) → corrigé **par contrôle, pas par réparation** : PSNR dans CodecCheck v2 et dans chaque conversion (repli logiciel automatique). Niveau 2 (à confirmer sur appareil).
- AVI AviMéca : image noire avec son → cause non établie (lecteur du téléphone incapable de lire rawvideo, ou image réellement noire). Ajouts : contrôle PSNR bloquant, aperçu décodé par FFmpeg dans l'appli. Niveau 2. À vérifier : ouvrir l'AVI dans l'aperçu intégré puis sur PC.
- Avertissement « encodage matériel » affiché à tort en logiciel : non reproduit dans le code (le texte n'est ajouté que si l'encodeur est *_mediacodec) ; l'encodeur utilisé est maintenant affiché dans la file et l'historique pour le vérifier.
- Ajouts : lecteur intégré + suppression de l'original après validation, progression % du test d'extrait, AVI sans son par défaut (interrupteur « Inclure le son »). Niveau 2 (tests unitaires 14/14).

## Mises à jour AVI (03/11/2026, après essais de l'utilisateur dans AviMéca v2.7)
- Xvid / MPEG-4 : s'ouvre dans AviMéca (niveau 5, rapporté). Le seul codec AVI conservé (demande de l'utilisateur) ; RGB, YUY2, MS Video 1 et Cinepak retirés de l'interface et du test des codecs (le code reste dans CommandBuilder, testé).
- AviMéca étire tout clip dans sa zone d'affichage large (~16:9). Un AVI portrait 1080×1920 sans cadre apparaissait écrasé ; le cadre « Paysage 16:9 » (bandes noires centrées, image intacte) donne une balle ronde (niveau 5, rapporté). Cadre 16:9 = défaut des profils AviMéca / AviStep. Le cadre carré (1:1) a aggravé l'étirement.
- Reste à vérifier : étalonnage (coordonnées en pixels du fichier, bandes comprises) ; AviStep jamais essayé.

## Prochaine tâche exacte
Exécuter docs/TESTS.md à partir du point 2 (conversion MP4 réelle d'une vraie vidéo, puis AVI, lot, reprise) sur le Pixel 6a ; corriger selon les constats.

## Mise à jour (liste des conversions)
- File : les conversions terminées sont triées de la plus récente à la plus ancienne (en haut des terminées). Niveau 2 (compilé, tests 16/16).
- Champ `originalDeleted` : après suppression de l'original (lecteur ou historique, confirmée par Android), la ligne est grisée avec la pastille « Original supprimé » (file + historique). Niveau 2, à vérifier sur le Pixel.
