# TODO

1. Installer l'APK sur le Pixel 6a et lancer le diagnostic des codecs (Paramètres) — noter les échecs.
2. Exécuter docs/TESTS.md (cases 1 à 13) et reporter les résultats dans docs/SUIVI.md et docs/CODECS.md.
3. Copier un AVI RGB et un YUY2 sur PC → ouvrir dans AviMéca et AviStep ; noter le fourcc/codec accepté.
4. Si MS Video 1 / Cinepak échouent : tester sans `-q:v` ou avec `-pix_fmt pal8` / dimensions ; sinon documenter « indisponible ».
5. Sauvegarder `keys\` (clé de signature) hors du PC.
6. Retirer `DumpCommandsTest` si inutile (outil de diagnostic, inactif sans variable VC_DUMP).
