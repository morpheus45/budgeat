# Budgeat — APK

App Android qui embarque l'interface Budgeat dans une WebView. Tout est local :
la page est un asset de l'APK, l'app ne déclare **aucune permission** et n'ouvre
aucune connexion réseau.

## Construire

```bash
bash build.sh
```

Produit `budgeat.apk` à la racine du dossier.

Pas de Gradle : le script enchaîne directement les outils du SDK
(`aapt2 compile` → `aapt2 link` → `javac` → `d8` → `zipalign` → `apksigner`).
C'est volontaire — le projet n'a qu'une seule classe et aucune dépendance, un
build Gradle n'apporterait que le téléchargement d'une distribution.

Prérequis : JDK 17 dans le PATH, et un SDK Android. Les chemins se surchargent
par variables d'environnement :

| Variable | Défaut |
|---|---|
| `ANDROID_HOME` | `/c/Android` |
| `BT_VER` | `35.0.0` |
| `PLATFORM` | `android-34` |
| `VERSION_CODE` | `1` |
| `VERSION_NAME` | `1.0` |

## Mettre à jour l'interface

`assets/budgeat.html` est **généré**, ne pas l'éditer à la main. La source est
`budgeat.artifact.html` (la page publiée en artifact). `pack-asset.mjs` l'enveloppe
dans un document complet : charset, `viewport` (sans quoi la WebView rendrait la
page sur 980 px de large), et le retrait du cadre de téléphone et de la fausse
barre d'état, qui feraient doublon avec l'appareil réel.

`build.sh` relance cette génération à chaque build.

## Signature

Le keystore `budgeat.keystore` est créé au premier build (mot de passe `budgeat`,
`CN=Budgeat`). C'est une signature auto-générée pour installation perso — elle
n'a rien à voir avec les certificats de release de PIPSILY ou gsystem, et cet APK
n'a pas vocation à être publié sur un store.

**Le keystore n'est pas versionné** (voir `.gitignore`) : c'est du matériel de
signature. Un clone du dépôt en régénère un au premier build, et produira donc un
APK que ton téléphone verra comme une app différente.

Garder le même keystore pour toutes les mises à jour : Android refuse d'installer
par-dessus une app signée par une autre clé.

## Choix techniques

- **minSdk 26** (Android 8) : permet l'icône adaptative en XML seule, sans avoir
  à fournir de PNG à toutes les densités.
- **targetSdk 34** et non 35 : évite l'edge-to-edge forcé d'Android 15, qui ferait
  passer la WebView sous la barre d'état. Sans intérêt ici, l'app n'allant pas
  sur un store.
- **Thème sombre** : la WebView ne propage pas toujours `prefers-color-scheme`.
  Le bouton soleil dans l'app fait le basculement de façon fiable.
- **Retour** : ferme d'abord une feuille ouverte, puis remonte à l'Accueil, et
  seulement ensuite quitte l'app.
