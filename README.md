# Arpège

Appli Android native tout-en-un pour pratiquer **guitare, ukulélé, basse et piano** :
outils (accordeur, métronome, tablatures), théorie (accords, gammes & modes, cercle des quintes),
entraînement (oreille, rythme, solfège) et journal de pratique.
**Locale, sans compte, hors ligne** (seule la vérification des mises à jour utilise le réseau).

Kotlin · Jetpack Compose · Material 3 Expressive · Room · DataStore · Glance · minSdk 29.

## Fonctionnalités

| Section | Contenu |
|---|---|
| Outils | Accordeur YIN (source UNPROCESSED si dispo), 7 accordages + chromatique, La4 432–446 Hz, note de référence · Métronome 20–300 BPM à l'échantillon près, tap tempo, roue, subdivisions, accents par temps, mesures simples/composées/irrégulières, service de premier plan + notification, vibration · Tablatures ASCII, ChordPro et Guitar Pro (GP3–GP5), lecture audio des pistes Guitar Pro avec curseur synchronisé et vitesse 50–150 % |
| Théorie | Accords générés par algorithme (20 types, positions classées par facilité, capo, diagrammes, doigtés, écoute plaquée/arpégée, favoris) · Gammes & modes (14 gammes, manche ou clavier, degrés colorés, accords diatoniques) · Cercle des quintes interactif · Grilles d'accords (14 progressions en degrés, 12 tonalités majeures/mineures, chiffrage romain, mini-diagrammes, boucle plaquée/rythmée/arpégée, cordes ou piano, clic, accord en cours surligné) · Accord inverse (cases sur le manche ou touches du clavier → noms d'accords classés, renversements, accords incomplets) |
| Entraînement | Oreille (intervalles, accords, gammes, cadences ; niveaux, sélection d'éléments, répétition adaptative) · Rythme (lecture Bravura, tap calé sur l'horloge audio, écart en ms) · Solfège (clés de sol, fa, ut 3, ut 4 ; boutons ou clavier ; chrono) |
| Journal | Séances auto (> 1 min) et manuelles, objectif hebdo, série, heatmap annuelle, stats par instrument et activité · Widget Glance |

Réglages : notation Do Ré Mi / C D E, instrument principal, La4, objectif, vibration, autorisations, mises à jour.

## Deep links

| Lien | Effet |
|---|---|
| `arpege://tuner` | Ouvre l'accordeur |
| `arpege://metronome` | Ouvre le métronome |
| `arpege://metronome?bpm=120` | Règle le tempo et lance le métronome |
| `arpege://chord?name=Am7` | Ouvre l'accord (noms anglais ou latins : `Ré7`, `Bbmaj7`, `D/F%23`) |

⚠️ Encoder `#` en `%23` dans l'URL (`C%23m7`), sinon il est lu comme un fragment.
Exemple depuis une autre appli : `startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("arpege://metronome?bpm=90")))`.

## Build

```bash
./gradlew assembleDebug                                        # APK debug
./gradlew assembleRelease lint testDebugUnitTest :theory:test  # vérification complète (= CI)
./gradlew :app:testDebugUnitTest -Pscreenshots                 # captures des écrans (Robolectric) dans build/screenshots
```

Sans keystore (build local), l'APK release est signé avec la clé debug.

## Publier une version

1. Modifier `VERSION` (ex. `0.2.0`) et ajouter une section `## 0.2.0` dans `CHANGELOG.md`.
2. Pousser sur `main`.

Le workflow `.github/workflows/build.yml` construit l'APK signé ; si le tag `vX.Y.Z` n'existe pas,
il crée le tag et une GitHub Release (APK + changelog). Sinon c'est un simple build de vérification
(les autres branches font aussi un build de vérification). `versionCode` = numéro d'exécution GitHub.
L'appli interroge `releases/latest` au plus toutes les 6 h et propose la mise à jour.

## Étapes manuelles

1. **Secrets GitHub** (déjà en place) : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
   Toujours garder le même keystore (sinon les mises à jour in-app échouent : signature différente).
   Pour en créer un : `keytool -genkeypair -v -keystore arpege.jks -alias arpege -keyalg RSA -keysize 4096 -validity 36500`
   puis `base64 -w0 arpege.jks` → `KEYSTORE_BASE64`.
2. **Première installation** : télécharger l'APK de la Release, autoriser l'installation depuis le navigateur.
3. **Honor / MagicOS** : dans l'onboarding (ou Réglages > Autorisations), accepter « Batterie sans restriction »,
   puis « Lancement auto » → Arpège → gestion manuelle, tout activer.
4. Le dépôt est public : les Releases servent directement aux mises à jour (pas de dépôt dédié).

## Choix et simplifications

- **DI manuelle** (`AppContainer`) plutôt que Hilt ; pas de ViewModel lourd.
- **YIN** plutôt que MPM pour la hauteur (±1 cent sur sinus testé de 82 à 1047 Hz).
- **BPM entier** : l'ordonnancement du métronome est alors exact en arithmétique entière (aucune dérive).
- Mesures composées (6/8, 9/8, 12/8) et irrégulières (5/8, 7/8…) : le BPM porte sur l'unité (croche en x/8) ;
  accents moyens sur chaque groupe (ex. 7/8 = 2+2+3), modifiables temps par temps.
- Changement de tempo en lecture : pris en compte au temps suivant, sans perdre la position dans la mesure.
- **Journal automatique** : temps passé au premier plan sur un outil/exercice (≥ 1 min) ; le métronome compte
  son temps de lecture réel (même écran éteint). Ajout manuel : aujourd'hui, hier ou avant-hier.
- **Accords en tablature** : affichés tels qu'écrits (formes relatives au capo indiqué dans le fichier).
- **Transposition** des tablatures : accords uniquement (les chiffres de tab ne sont pas transposés).
- **Guitar Pro** : GP3, GP4 et GP5 lus (pistes, mesures, notes, accords, marqueurs), rendus en tablature texte ;
  voix 1 seulement, effets (bends, slides…) ignorés à l'affichage. GPX/GP (6+) non pris en charge.
- **Lecture Guitar Pro** : piste choisie synthétisée en corde pincée (Karplus-Strong), hauteur = accordage + capo + case,
  durées depuis valeur/point/n-olet, liaisons prolongées, notes étouffées brèves. Rendu **en flux** (chaque note
  synthétisée quand la lecture l'atteint, mémoire bornée), curseur calé sur la position audio, défilement auto.
  Lecture **linéaire** comme l'affichage (reprises non dépliées) ; tempo du fichier, changements de tempo ignorés.
  Les systèmes de tablature sont coupés à la largeur de l'écran.
- **Grilles d'accords** : un passage de la grille est rendu dans **un seul tampon** (temps et accords à l'échantillon
  près, traîne repliée au début) puis rejoué en boucle sans couture par un flux AudioTrack (`StreamPlayer`) ;
  modifier un réglage relance le rendu. Piano : conduite des voix (renversement le plus proche). Tonalités
  orthographiées avec le moins d'altérations (Ré♭ majeur, Do♯ mineur).
- **Accord inverse** : notes hors accord admises seulement à la basse (« D/E ») ; pénalités pour notes absentes
  (quinte juste peu pénalisée), renversements et types rares. x02010 = Am7 (Am = x02210).
- **Basse** dans Accords : fondamentale + intervalles sur le manche (pas de « positions »).
- **Arpège au lancement** : trois notes jouées doucement en ouvrant l'appli, synchronisées avec le logo animé.
- Les tablatures importées sont copiées dans la base locale (le fichier d'origine n'est plus nécessaire).

## Licences

- Police musicale **Bravura** © Steinberg Media Technologies, SIL Open Font License 1.1 (`BRAVURA-LICENSE.txt`).
