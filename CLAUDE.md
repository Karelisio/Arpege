# Arpège — notes pour Claude

App Android native (Kotlin, Jetpack Compose, Material 3 Expressive) : outils, théorie,
entraînement et journal de pratique musicale. Locale, sans compte, hors ligne. UI en français.

## Commandes
- `./gradlew assembleRelease lint testDebugUnitTest :theory:test` — vérification complète (= CI)
- `./gradlew :theory:test` — tests du moteur (JVM pur, rapide)
- `./gradlew :app:testDebugUnitTest -Pscreenshots` — captures PNG des écrans dans `build/screenshots`
- Échantillons Guitar Pro locaux : `./gradlew :theory:test -Dgp.samples=/chemin`

## Modules
- `:theory` — Kotlin/JVM pur, **aucune dépendance Android** :
  `Note/Pitch/Interval/Chord/Scale/Key/Clef/Tuning`, `VoicingGenerator`, `dsp/` (YIN, synthèse,
  métronome), `training/` (oreille, rythme, solfège), `song/` (ChordPro, ASCII, Guitar Pro),
  `practice/` (stats du journal).
- `:app` — UI et intégration Android :
  - `data/` `AppContainer` (DI manuelle, `context.container`), `SettingsRepository` (DataStore),
    `db/` Room, `PracticeRepository` + `TrackPractice`, `StatsRepository`
  - `audio/` `TunerEngine` (AudioRecord), `SoundPlayer`, `MetronomeEngine/Controller/Service`
  - `ui/<feature>/` un package par écran ; `ui/draw/` manche, clavier, diagrammes, portée Bravura ;
    `ui/components/` ; `ui/nav/` (routes, sections, NavHost, shared elements) ; `ui/theme/`
  - `update/` mise à jour in-app ; `widget/` Glance

## Standards
- Logique musicale, DSP ou calcul → `:theory` avec test unitaire, jamais dans `:app`.
- Audio : jamais de timer ; positions calculées à l'échantillon, synchro UI via la position de lecture.
- AudioTrack : toujours vérifier `STATE_INITIALIZED`, ne jamais planter si l'audio est indisponible.
- Compose : `collectAsStateWithLifecycle`, état hissé, `ToolScaffold` pour les écrans d'outil.
- Animations : respecter `LocalReduceMotion` ; haptique via `rememberHaptics()`.
- Notation via `Note.name(notation)` ; glyphes SMuFL dans `Smufl` (échappements `\uE…`).
- Dépendances uniquement dans `gradle/libs.versions.toml`.
- Room : schéma exporté dans `app/schemas/` ; toute modification = nouvelle version + migration.
- Lint bloquant (`abortOnError`).

## Release
- `VERSION` (X.Y.Z) = versionName ; versionCode = `GITHUB_RUN_NUMBER`.
- Publier : `VERSION` + section `## X.Y.Z` dans `CHANGELOG.md`, push sur `main` → tag + Release.
- Secrets : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (même keystore toujours).
