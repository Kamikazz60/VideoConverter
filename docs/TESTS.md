# Tests

## Exécutés (03/11/2026)
| Test | Résultat |
|---|---|
| Tests unitaires `CommandBuilderTest` (13) : 1080p→720p, portrait, pas d'agrandissement, HEVC/AVC, audio supprimé, débit compatible avec 200-350 Mo pour 16 min en 720p, AVI RGB/YUY2/MS Video/Xvid, bandes noires multiples de 4, avertissement cadence | **13/13 réussis** (niveau 3) |
| Compilation debug et release, signature release (`apksigner verify`) | réussis |
| Émulateur | impossible (pas d'hyperviseur) |
| Rejeu des commandes avec FFmpeg de bureau | non exécuté (binaire tiers refusé par le système) |

Aucun test sur appareil n'a été exécuté. **Aucun des tests suivants ne doit être considéré comme réussi.**

## À exécuter sur le Pixel 6a (cases à cocher puis noter résultat/date)
1. [x] (03/11/2026, rapporté par l'utilisateur : aucun échec) Au 1er lancement, l'écran Paramètres › Diagnostic liste les 9 encodeurs : noter OK/échec de chacun (HEVC/AVC logiciel et matériel, RGB, YUY2, MS Video 1, Cinepak, Xvid).
2. [ ] MP4 H.265 : 1 vidéo ; vérifier lecture dans Google Photos, taille, durée.
3. [ ] MP4 H.264.
4. [ ] 1080p → 720p (dimensions 1280×720 dans « Tester un extrait » puis historique).
5. [ ] AVI RGB sur une vidéo courte ; copier sur PC, ouvrir dans AviMéca et AviStep.
6. [ ] AVI YUY2 (idem).
7. [ ] MS Video 1 et Cinepak : disponibilité (voir 1), puis ouverture sur PC.
8. [ ] Lot de 5 à 10 vidéos consécutives.
9. [ ] Interruption : tuer l'app pendant une conversion → rouvrir → « Reprendre ».
10. [ ] Annulation d'un fichier et de tout le lot (aucun fichier partiel ne doit rester dans Films/VideoConverter).
11. [ ] Stockage insuffisant : AVI RGB d'une longue vidéo HD → message « Espace de stockage insuffisant » avant l'encodage.
12. [ ] Originaux intacts : tailles/dates inchangées après le lot.
13. [ ] Durées et cadence : comparer ffprobe PC (source vs sortie) ; cadence identique, nombre d'images identique pour une source à cadence constante.
