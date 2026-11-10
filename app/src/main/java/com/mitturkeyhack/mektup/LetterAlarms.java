package com.mitturkeyhack.mektup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Map;

/** Mektup açılma zamanlarını saklar ve AlarmManager ile kurar. */
public final class LetterAlarms {
    static final String PREFS = "letter_alarms";

    private LetterAlarms() { }

    static void schedule(Context ctx, String id, long when, String title) {
        if (id == null) return;
        if (title == null) title = "Mektubun";
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(id, when + "|" + title.replace("\n", " ")).apply();
        set(ctx, id, when, title);
    }

    static void cancel(Context ctx, String id) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(id).apply();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(ctx, id, ""));
    }

    static void remove(Context ctx, String id) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(id).apply();
    }

    static void rescheduleAll(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            Object v = e.getValue();
            if (!(v instanceof String)) continue;
            String s = (String) v;
            int bar = s.indexOf('|');
            if (bar < 0) continue;
            try {
                long when = Long.parseLong(s.substring(0, bar));
                String title = s.substring(bar + 1);
                set(ctx, e.getKey(), Math.max(when, now + 5000), title);
            } catch (NumberFormatException ignored) { }
        }
    }

    private static PendingIntent pending(Context ctx, String id, String title) {
        Intent i = new Intent(ctx, LetterReceiver.class);
        i.setAction("com.mitturkeyhack.mektup.LETTER_READY");
        i.putExtra("id", id);
        i.putExtra("title", title);
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void set(Context ctx, String id, long when, String title) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(ctx, id, title);
        try {
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } catch (SecurityException se) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }
}
