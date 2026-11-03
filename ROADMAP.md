# Feuille de route

## P0 — Indispensable
- [x] Moteur FFmpeg intégré, commandes MP4 + 5 profils AVI (testées en unitaire)
- [x] File persistante, service, vérifications, UI complète, profils, historique, journal
- [x] APK debug + release signée
- [ ] **Validation sur le Pixel 6a** (codecs, MP4, AVI, lot, reprise) → docs/TESTS.md
- [ ] Validation des AVI dans AviMéca et AviStep sur PC

## P1 — Important
- [ ] Corriger selon les résultats du Pixel (repli d'écriture MP4 si `saf:` + faststart pose problème)
- [ ] Tone-mapping HDR → SDR pour sorties AVC
- [ ] Dossier de destination personnalisé via SAF (hors Films/)
- [ ] Tests instrumentés (reprise, annulation, stockage)

## P2 — Bonus
- [ ] Estimation de taille sur la sélection avant lancement
- [ ] Choix de la matrice couleur (BT.601/709) pour les AVI RGB
- [ ] Mode sombre/clair forcé, langues
