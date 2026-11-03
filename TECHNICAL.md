# Technique

## Flux d'une conversion
1. `QueueManager.runLoop` (service premier plan) prend le premier élément `PENDING`.
2. `Converter.convert` : accès source → `Ffmpeg.probe` → vérif codec (`CodecCheck`) → espace libre (estimation majorante + 200 Mo) → FFmpeg écrit dans `cacheDir/work/<id>` (progression depuis les statistiques FFmpeg) → `verify` (conteneur, pistes, codec, dimensions, durée, cadence, décodage, **PSNR vs original** ; échec avec encodeur matériel → refait en logiciel) → création du fichier MediaStore `IS_PENDING=1` → copie + comparaison de taille → publication (`IS_PENDING=0`) ; fichier temporaire toujours supprimé. Espace requis : 2× la taille estimée + 200 Mo.
4. `PlayerScreen` : lecture du résultat (VideoView ; AVI → aperçu H.264 temporaire de 60 s décodé par FFmpeg) puis suppression de l'original via `MediaStore.createDeleteRequest` (confirmation système).
3. Mode MP4 « Matériel » : si l'encodeur MediaCodec échoue, nouvel essai automatique en logiciel (journalisé).
4. L'état est écrit dans `files/queue.json` à chaque changement ; au démarrage, `RUNNING` redevient `PENDING` (reprise).

## Vérification (`Converter.verify`)
Fichier non vide, conteneur (avi/mp4), pistes vidéo/audio attendues, codec attendu, dimensions attendues, durée ±max(1 s, 2 %), cadence AVI (± 0,01), comparaison du nombre d'images (avertissement), décodage `-v error` du début (6 s) et de la fin (6 s).

## Cadence AVI
`-r <avg_frame_rate source> -fps_mode cfr`. Pour une source à cadence variable, FFmpeg peut dupliquer/supprimer quelques images ; l'écart du nombre d'images est signalé dans l'historique. Une cadence personnalisée affiche un avertissement.

## Fichiers
`files/queue.json` (file + historique), `profiles.json`, `settings.json`, `codecs.json` (dernier diagnostic), `diagnostic.log` (+ `.old.log`, 2 Mo max).

## Points d'extension
Nouveau codec AVI : `AviCodec` → `CommandBuilder.avi` → `Converter.verify` (codec attendu) → `CodecCheck.allIds` → `ConfigScreen`.
