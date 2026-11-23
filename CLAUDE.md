# Arpège — notes pour Claude

App Android native (Kotlin, Jetpack Compose, Material 3 Expressive) : outils, théorie,
entraînement et journal de pratique musicale. Locale, sans compte, hors ligne.

## Commandes
- `./gradlew assembleDebug` — APK debug
- `./gradlew assembleRelease lint testDebugUnitTest :theory:test` — vérification complète (= CI)
- `./gradlew :theory:test` — tests du moteur de théorie / DSP (JVM pur, rapide)

## Modules
- `:theory` — Kotlin/JVM pur, **aucune dépendance Android**. Notes, intervalles, accords,
  gammes, tonalités, générateur de positions, YIN, synthèse (Karplus-Strong, additive),
  ordonnanceur du métronome, parseurs ChordPro/ASCII, exercices. Tout y est testé.
- `:app` — UI et intégration Android.
  - `data/` DataStore (`SettingsRepository`), Room (`db/`), `AppContainer` (DI manuelle)
  - `audio/` AudioRecord (accordeur), AudioTrack (lecteur, métronome), `MetronomeService`
  - `ui/<feature>/` un package par écran ; `ui/components/` composants partagés ;
    `ui/nav/` routes, sections, NavHost ; `ui/theme/`
  - `update/` mise à jour in-app (GitHub releases/latest + PackageInstaller)
  - `widget/` widget Glance

## Standards
- Logique musicale ou DSP → `:theory` avec un test unitaire, jamais dans `:app`.
- Pas de timers pour l'audio : tout est calculé à l'échantillon près.
- Compose : état hissé, `collectAsStateWithLifecycle`, pas de ViewModel lourd ; accès aux
  dépendances via `LocalContext.current.container`.
- Animations : respecter `LocalReduceMotion`. Haptique via `rememberHaptics()`.
- Textes UI en français. Notation via `Notation` (Do Ré Mi / C D E).
- Dépendances uniquement dans `gradle/libs.versions.toml`.
- Room : incrémenter la version + migration à chaque changement de schéma (`app/schemas/`).

## Release
- `VERSION` (X.Y.Z) = source unique de versionName ; versionCode = `GITHUB_RUN_NUMBER`.
- Publier : modifier `VERSION` + section `## X.Y.Z` dans `CHANGELOG.md`, pousser sur `main`.
- Signature via secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
