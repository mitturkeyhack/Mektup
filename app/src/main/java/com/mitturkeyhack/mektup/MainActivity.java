package com.mitturkeyhack.mektup;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;

public class MainActivity extends Activity {
    private WebView web;
    private String pendingOpen;
    private boolean pageReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) c.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1026);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B1026);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(this), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                flushPendingOpen();
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }

        handleIntent(getIntent());
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        flushPendingOpen();
    }

    private void handleIntent(Intent i) {
        if (i != null && i.hasExtra("open")) {
            pendingOpen = i.getStringExtra("open");
            i.removeExtra("open");
        }
    }

    private void flushPendingOpen() {
        if (pageReady && pendingOpen != null) {
            String id = pendingOpen.replaceAll("[^A-Za-z0-9_]", "");
            pendingOpen = null;
            web.evaluateJavascript("window.openFromNotif&&window.openFromNotif('" + id + "')", null);
        }
    }

    private void handleBack() {
        web.evaluateJavascript("(window.handleBack&&window.handleBack())?'1':'0'", r -> {
            if (r == null || !r.contains("1")) finish();
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pageReady) web.evaluateJavascript("window.render&&render()", null);
    }

    public class Bridge {
        private final Context ctx;
        Bridge(Context c) { ctx = c.getApplicationContext(); }

        @JavascriptInterface
        public void schedule(String id, double when, String title) {
            LetterAlarms.schedule(ctx, id, (long) when, title);
        }

        @JavascriptInterface
        public void cancel(String id) {
            LetterAlarms.cancel(ctx, id);
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) return;
                int amp = ms <= 10 ? 60 : ms <= 40 ? 140 : 255;
                v.vibrate(VibrationEffect.createOneShot(Math.max(1, ms), amp));
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void askNotif() {
            if (Build.VERSION.SDK_INT >= 33) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
                    }
                });
            }
        }

        @JavascriptInterface
        public String version() {
            try {
                PackageInfo p = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                return p.versionName;
            } catch (Exception e) { return "1.0"; }
        }
    }
}
