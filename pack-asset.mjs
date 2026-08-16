/**
 * Fabrique assets/budgeat.html à partir de la page publiée en artifact.
 *
 * L'artifact est servi par claude.ai qui l'enveloppe lui-même dans un document
 * complet. En APK il n'y a pas d'hôte : il faut fournir <head>, le charset et
 * surtout le viewport, sans quoi la WebView rend la page sur 980 px de large.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const SRC = process.argv[2] || resolve(here, "budgeat.artifact.html");
const OUT = resolve(here, "assets", "budgeat.html");

let body = readFileSync(SRC, "utf8");

// Le <title> remonte dans le <head> ; le reste devient le corps du document.
const titleMatch = body.match(/<title>([\s\S]*?)<\/title>/i);
const title = titleMatch ? titleMatch[1].trim() : "Budgeat";
body = body.replace(/<title>[\s\S]*?<\/title>\s*/i, "");

const doc = `<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="color-scheme" content="light dark">
<title>${title}</title>
<style>
  /* Sur un vrai téléphone, le cadre de simulation et la fausse barre d'état
     feraient doublon avec l'appareil. On les retire à toutes les largeurs,
     y compris sur tablette où la maquette réapparaîtrait sinon. */
  html, body { margin:0; padding:0; height:100%; overscroll-behavior:none; }
  .stage { padding:0 !important; gap:0 !important; min-height:100dvh !important; }
  .pitch { display:none !important; }
  .statusbar { display:none !important; }
  .device {
    width:100% !important; height:100dvh !important;
    border-radius:0 !important; box-shadow:none !important;
  }
  .appbar { padding-top:16px; }
  /* Pas de sélection ni de surlignage tactile : ça doit se comporter en app. */
  * { -webkit-tap-highlight-color: transparent; }
  body { -webkit-user-select:none; user-select:none; }
  input[type=text] { -webkit-user-select:text; user-select:text; }
</style>
</head>
<body>
${body}
</body>
</html>
`;

writeFileSync(OUT, doc, "utf8");
console.log("assets/budgeat.html écrit —", (doc.length / 1024).toFixed(0), "KB");
