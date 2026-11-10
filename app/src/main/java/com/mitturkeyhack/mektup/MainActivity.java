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
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.ClipData;
import android.content.ClipboardManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {
    private WebView web;
    private String pendingOpen;
    private boolean pageReady = false;

    static final String CRASH_FILE = "son_hata.txt";

    private void installCrashCatcher() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            saveCrash("Uygulama çöktü (" + t.getName() + ")", e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    private void saveCrash(String title, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            if (e != null) e.printStackTrace(new PrintWriter(sw));
            String txt = title + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
                    + Build.MANUFACTURER + " " + Build.MODEL + "\n\n" + sw;
            FileOutputStream fo = openFileOutput(CRASH_FILE, MODE_PRIVATE);
            fo.write(txt.getBytes("UTF-8"));
            fo.close();
        } catch (Throwable ignored) { }
    }

    private String readCrash() {
        File f = new File(getFilesDir(), CRASH_FILE);
        if (!f.exists()) return null;
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) Math.min(f.length(), 20000)];
            int n = in.read(b);
            in.close();
            f.delete();
            return n > 0 ? new String(b, 0, n, "UTF-8") : null;
        } catch (Throwable t) { f.delete(); return null; }
    }

    private void showError(final String text) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFF0B1026);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p * 2, p, p);
        TextView h = new TextView(this);
        h.setText("Bir hata oluştu 😕\nAşağıdaki yazının ekran görüntüsünü alıp Claude'a gönder.");
        h.setTextColor(0xFFF2C46D);
        h.setTextSize(17);
        box.addView(h);
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFEEF0FF);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        tv.setPadding(0, p, 0, p);
        sv.addView(tv);
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button again = new Button(this);
        again.setText("Kopyala ve tekrar dene");
        again.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("hata", text));
            } catch (Throwable ignored) { }
            recreate();
        });
        box.addView(again);
        setContentView(box);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashCatcher();
        String last = readCrash();
        if (last != null) { showError(last); return; }
        try {
            setupUi();
        } catch (Throwable e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            showError("Açılış hatası\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n\n" + sw);
        }
    }

    private void setupUi() {
        Window w = getWindow();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1026);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B1026);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        try {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(false);
            } else {
                w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        } catch (Throwable ignored) { }

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
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

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                saveCrash("WebView sayfası çöktü (didCrash=" + (detail != null && detail.didCrash()) + ")", null);
                runOnUiThread(() -> recreate());
                return true;
            }
        });

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
        if (pageReady && pendingOpen != null && web != null) {
            String id = pendingOpen.replaceAll("[^A-Za-z0-9_]", "");
            pendingOpen = null;
            web.evaluateJavascript("window.openFromNotif&&window.openFromNotif('" + id + "')", null);
        }
    }

    private void handleBack() {
        if (web == null) { finish(); return; }
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
        if (pageReady && web != null) web.evaluateJavascript("window.render&&render()", null);
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
