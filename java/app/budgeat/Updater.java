package app.budgeat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Mise à jour depuis les releases GitHub du dépôt.
 *
 * Android refuse d'installer par-dessus une app signée avec une autre clé : c'est
 * cette vérification native qui protège l'installation, pas un contrôle maison.
 */
class Updater {

    private static final String API =
        "https://api.github.com/repos/morpheus45/budgeat/releases/latest";
    private static final String ASSET = "budgeat.apk";

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;

    Updater(Activity act) {
        this.act = act;
    }

    /** @param silencieux true au démarrage : on ne dit rien s'il n'y a rien de neuf. */
    void check(final boolean silencieux) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject release = fetchLatest();
                    final String tag = release.optString("tag_name", "").replaceFirst("^v", "");
                    final String notes = release.optString("body", "");
                    final String url = assetUrl(release);
                    final String actuelle = versionInstallee();

                    if (tag.isEmpty() || url == null) {
                        if (!silencieux) toast("Aucun APK dans la dernière release.");
                        return;
                    }
                    if (compare(tag, actuelle) <= 0) {
                        if (!silencieux) toast("Budgeat " + actuelle + " est à jour.");
                        return;
                    }
                    ui.post(new Runnable() {
                        @Override public void run() { proposer(tag, notes, url); }
                    });
                } catch (Exception e) {
                    if (!silencieux) toast("Vérification impossible : " + e.getClass().getSimpleName());
                }
            }
        }).start();
    }

    private JSONObject fetchLatest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setRequestProperty("Accept", "application/vnd.github+json");
        // GitHub rejette les requêtes sans User-Agent.
        c.setRequestProperty("User-Agent", "Budgeat-Android");
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return new JSONObject(sb.toString());
        } finally {
            c.disconnect();
        }
    }

    private String assetUrl(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && ASSET.equals(a.optString("name"))) {
                return a.optString("browser_download_url", null);
            }
        }
        return null;
    }

    String versionInstallee() {
        try {
            return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** Compare 1.10 et 1.9 numériquement, pas alphabétiquement. */
    static int compare(String a, String b) {
        String[] xa = a.split("\\.");
        String[] xb = b.split("\\.");
        for (int i = 0; i < Math.max(xa.length, xb.length); i++) {
            int va = i < xa.length ? nombre(xa[i]) : 0;
            int vb = i < xb.length ? nombre(xb[i]) : 0;
            if (va != vb) return va < vb ? -1 : 1;
        }
        return 0;
    }

    private static int nombre(String s) {
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) d.append(s.charAt(i)); else break;
        }
        return d.length() == 0 ? 0 : Integer.parseInt(d.toString());
    }

    private void proposer(final String version, String notes, final String url) {
        String resume = notes == null ? "" : notes.trim();
        if (resume.length() > 400) resume = resume.substring(0, 400) + "…";

        new AlertDialog.Builder(act)
            .setTitle("Budgeat " + version + " est disponible")
            .setMessage(resume.isEmpty()
                ? "Tu as la version " + versionInstallee() + "."
                : resume + "\n\nTu as la version " + versionInstallee() + ".")
            .setPositiveButton("Télécharger", (d, w) -> autoriserPuisTelecharger(url, version))
            .setNegativeButton("Plus tard", null)
            .show();
    }

    /** Depuis Android 8, installer un APK exige une autorisation explicite par app. */
    private void autoriserPuisTelecharger(String url, String version) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !act.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(act)
                .setTitle("Autorisation requise")
                .setMessage("Android demande ton accord pour que Budgeat puisse installer "
                          + "ses propres mises à jour. Active « Autoriser depuis cette source », "
                          + "puis relance le téléchargement.")
                .setPositiveButton("Ouvrir les réglages", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                          Uri.parse("package:" + act.getPackageName()));
                    act.startActivity(i);
                })
                .setNegativeButton("Annuler", null)
                .show();
            return;
        }
        telecharger(url, version);
    }

    private void telecharger(String url, String version) {
        try {
            DownloadManager dm = (DownloadManager) act.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("Budgeat " + version);
            req.setDescription("Téléchargement de la mise à jour");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            // Dossier privé de l'app : aucune permission de stockage nécessaire.
            req.setDestinationInExternalFilesDir(
                act, Environment.DIRECTORY_DOWNLOADS, "budgeat-" + version + ".apk");

            final long id = dm.enqueue(req);
            toast("Téléchargement de Budgeat " + version + "…");

            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (id != i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)) return;
                    detacher();
                    installer(dm, id);
                }
            };
            IntentFilter filtre = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                act.registerReceiver(receiver, filtre, Context.RECEIVER_EXPORTED);
            } else {
                act.registerReceiver(receiver, filtre);
            }
        } catch (Exception e) {
            toast("Téléchargement impossible : " + e.getClass().getSimpleName());
        }
    }

    private void installer(DownloadManager dm, long id) {
        // DownloadManager fournit lui-même une URI content:// partageable avec
        // l'installateur : pas besoin d'un FileProvider.
        Uri uri = dm.getUriForDownloadedFile(id);
        if (uri == null) {
            toast("Fichier téléchargé introuvable.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        act.startActivity(i);
    }

    void detacher() {
        if (receiver != null) {
            try { act.unregisterReceiver(receiver); } catch (Exception ignored) {}
            receiver = null;
        }
    }

    private void toast(final String m) {
        ui.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(act, m, Toast.LENGTH_LONG).show();
            }
        });
    }
}
