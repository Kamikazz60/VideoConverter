# Décisions techniques

| Date | Décision | Raison | Alternatives écartées |
|---|---|---|---|
| 03/11/2026 | Moteur = FFmpegKit « full-gpl » 6.0.4 du fork `ffmpegkit-maintained`, AAR local | FFmpegKit officiel retiré de Maven (404). Ce build contient libx264, libx265, libxvid, encodeurs natifs msvideo1/cinepak/rawvideo, MediaCodec | Compiler FFmpeg soi-même (lourd sous Windows) ; MediaCodec/Media3 seuls (pas d'AVI) |
| 03/11/2026 | Licence GPL pour l'app (usage personnel) | Binaire GPL (x264/x265/Xvid) | Build LGPL sans x265/x264 : pas de HEVC logiciel |
| 03/11/2026 | Persistance en JSON (pas Room) | Moins de dépendances/plugins (KSP), volumétrie faible | Room |
| 03/11/2026 | Service de premier plan `mediaProcessing` plutôt que WorkManager | Un seul encodage long, notification riche, contrôle direct de l'annulation | WorkManager |
| 03/11/2026 | ~~Écriture directe dans MediaStore~~ **remplacée** : FFmpeg écrit dans un fichier temporaire (cache), vérifié, puis copié dans MediaStore (IS_PENDING) | L'écriture `saf:` + relecture du fichier en attente a échoué sur le Pixel 6a (conversions réelles en erreur) | Écriture directe `saf:` (abandonnée). Coût : 2× l'espace le temps de la copie |
| 03/11/2026 | Contrôle du contenu par PSNR (sortie vs original, 3 premières s) à chaque conversion et dans le test des codecs ; repli logiciel si l'encodeur matériel produit une image invalide | L'encodeur matériel du Pixel 6a produisait une image rayée/verte avec un décodage « sans erreur » ; un AVI pouvait être noir | Contrôle visuel manuel seulement |
| 03/11/2026 | Aperçu AVI = MP4 H.264 temporaire décodé depuis l'AVI par FFmpeg | MediaPlayer Android ne lit pas rawvideo (image noire + son) | Ouvrir l'AVI dans une autre appli |
| 03/11/2026 | AVI sans son par défaut | Demande de l'utilisateur (inutile pour AviMéca / AviStep) | Son par défaut |
| 03/11/2026 | Codec « disponible » = test réel (encode, relecture, décodage) | Exigence : ne jamais afficher un codec non fonctionnel | Liste statique |
| 03/11/2026 | AVI : bandes noires (pad) plutôt que rééchantillonnage pour multiples de 2/4 | Ne pas altérer les pixels ni l'origine des coordonnées pour la mesure | Redimensionnement |
| 03/11/2026 | AVI : `-r` = cadence d'origine + `-fps_mode cfr`, écarts signalés à la vérification | AVI est à cadence constante ; pas d'interpolation | VFR (non supporté en AVI) |
| 03/11/2026 | Moteur MP4 par défaut = matériel avec repli logiciel | Vitesse/chauffe sur 50-100 vidéos | Logiciel seul (lent) |
| 03/11/2026 | minSdk 29 | MediaStore RELATIVE_PATH, miniatures, large compatibilité | minSdk 26 |
