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

## D'où viennent les prix

Deux origines, jamais confondues à l'écran.

**Mesuré.** Neuf catégories de fruits et légumes ont assez de relevés dans
Open Prices (Open Food Facts) pour être chiffrées pour de vrai. L'app prend la
médiane des prix au kilo, en ne gardant que les relevés **français** — l'API
ignore silencieusement tout filtre pays, donc le tri se fait côté client sur
`osm_address_country_code`. Sans ça, un relevé norvégien pollue la médiane.

Quand l'enseigne choisie a au moins quatre relevés, c'est son prix à elle qui
s'applique, et le profil de l'enseigne n'est alors **pas** réappliqué : ce serait
compter deux fois.

**Estimé.** Viande, poisson, crèmerie, épicerie, pain et surgelés n'ont
essentiellement aucun relevé en France. Leurs prix sont des estimations de
détail, recalibrées le 17 août 2026. Elles restent des estimations et peuvent
être fausses ; chaque ligne concernée porte la mention « estimé ».

## Mise à jour depuis GitHub

Depuis la 1.3, l'app interroge elle-même
`api.github.com/repos/morpheus45/budgeat/releases/latest` au lancement, et propose
la nouvelle version si le tag est supérieur au `versionName` installé. Le
téléchargement passe par `DownloadManager`, qui fournit une URI `content://`
directement utilisable par l'installateur — d'où l'absence de `FileProvider`.

Publier une release suffit donc à diffuser la mise à jour : il n'y a plus de
fichier à transmettre.

Deux permissions en découlent : `INTERNET` et `REQUEST_INSTALL_PACKAGES`. Android
demandera en plus l'accord explicite « autoriser depuis cette source » au premier
téléchargement.

L'installation par-dessus l'existant n'est possible que si la nouvelle version
porte la même signature — c'est Android qui le vérifie. D'où l'importance du
keystore stable dans les secrets du dépôt.

## Publier une version

L'APK distribué est construit par le CI, pas sur un poste :

```
git tag v1.2 && git push origin v1.2
```

Le workflow `build-apk.yml` construit, signe avec la clé des secrets du dépôt,
vérifie la signature et la présence de l'asset d'interface, refuse tout APK
compilé contre un SDK de préversion, puis attache le fichier à la release.

Un `workflow_dispatch` sans tag produit un build de contrôle, téléchargeable en
artefact de run.

Secrets attendus : `KEYSTORE_BASE64` (le keystore encodé en base64),
`KEYSTORE_PASSWORD`, `KEY_ALIAS`. Sans eux le CI signe avec une clé jetable et
émet un avertissement.

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
