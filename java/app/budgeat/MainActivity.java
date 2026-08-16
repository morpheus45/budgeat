package app.budgeat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Budgeat tourne entièrement hors ligne : la page est un asset de l'APK,
 * l'app ne demande aucune permission et n'ouvre aucune connexion.
 */
public class MainActivity extends Activity {

    private static final int REQ_GEO = 42;

    private WebView web;
    private Updater updater;

    // Demande de géolocalisation laissée en attente le temps qu'Android réponde.
    private String geoOrigine;
    private GeolocationPermissions.Callback geoCallback;

    /**
     * Pont exposé à la page. Elle est un asset local et toute navigation externe est
     * bloquée : aucun contenu tiers ne peut atteindre ces méthodes.
     */
    private class Pont {
        @JavascriptInterface
        public String version() {
            return updater.versionInstallee();
        }

        @JavascriptInterface
        public void chercherMiseAJour() {
            updater.check(false);
        }
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        updater = new Updater(this);
        web = new WebView(this);
        web.addJavascriptInterface(new Pont(), "BudgeatApp");
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // Les assets de l'APK restent lisibles ; le reste du système, non.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        // Sans ça, navigator.geolocation reste muet dans une WebView.
        s.setGeolocationEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        // Rien ne doit quitter l'app : toute URL externe est ignorée.
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return !url.startsWith("file:///android_asset/");
            }
        });

        // La page ne peut se géolocaliser que si Android l'y autorise, et
        // seulement pour trier les magasins par distance.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                boolean accorde = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                       == PackageManager.PERMISSION_GRANTED;
                if (accorde) {
                    callback.invoke(origin, true, false);
                    return;
                }
                // On garde la demande en attente : refuser sèchement puis demander
                // l'autorisation laisserait la page sans réponse même après ton accord.
                geoOrigine = origin;
                geoCallback = callback;
                requestPermissions(
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEO);
            }
        });
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (saved != null) {
            web.restoreState(saved);
        } else {
            web.loadUrl("file:///android_asset/budgeat.html");
            // Contrôle discret au lancement : on ne parle que s'il y a du neuf.
            web.postDelayed(new Runnable() {
                @Override public void run() { updater.check(true); }
            }, 2500);
        }
    }

    /** Ton accord arrive après coup : c'est ici qu'on débloque la page. */
    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] resultats) {
        if (code == REQ_GEO && geoCallback != null) {
            boolean ok = resultats.length > 0
                      && resultats[0] == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigine, ok, false);
            geoCallback = null;
            geoOrigine = null;
            if (ok && web != null) {
                // La première demande a échoué faute d'autorisation : on relance.
                web.evaluateJavascript("window.__budgeatRelancerPosition && window.__budgeatRelancerPosition()", null);
            }
            return;
        }
        super.onRequestPermissionsResult(code, perms, resultats);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    /**
     * Retour : on ferme d'abord une feuille ouverte, puis on remonte à l'Accueil,
     * et seulement ensuite on quitte l'app.
     */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){"
          + "  var sheet = document.querySelector('#sheet.on');"
          + "  if (sheet) { document.querySelector('#backdrop').click(); return 'sheet'; }"
          + "  var tab = document.querySelector('.tab[aria-selected=\"true\"]');"
          + "  if (tab && tab.getAttribute('data-screen') !== 'home') {"
          + "    document.querySelector('.tab[data-screen=\"home\"]').click(); return 'home';"
          + "  }"
          + "  return 'exit';"
          + "})()",
            value -> {
                if (value != null && value.contains("exit")) {
                    finish();
                }
            });
    }

    @Override
    protected void onDestroy() {
        if (updater != null) updater.detacher();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
