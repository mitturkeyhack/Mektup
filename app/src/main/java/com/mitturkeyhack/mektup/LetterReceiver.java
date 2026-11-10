package com.mitturkeyhack.mektup;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LetterReceiver extends BroadcastReceiver {
    static final String CHANNEL = "letters";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            LetterAlarms.rescheduleAll(ctx);
            return;
        }
        String id = intent.getStringExtra("id");
        String title = intent.getStringExtra("title");
        if (id == null) return;
        LetterAlarms.remove(ctx, id);
        show(ctx, id, title == null || title.isEmpty() ? "Mektubun" : title);
    }

    static void show(Context ctx, String id, String title) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Açılan mektuplar", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Mektubunun açılma zamanı geldiğinde haber verir");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);

        Intent open = new Intent(ctx, MainActivity.class);
        open.putExtra("open", id);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, id.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFE0A84A)
                .setContentTitle("Geçmişten bir mektubun var 💌")
                .setContentText("“" + title + "” açılmaya hazır. Mührü kırma zamanı!")
                .setStyle(new Notification.BigTextStyle().bigText("“" + title + "” açılmaya hazır. Geçmişteki sen sana bir şeyler yazdı — mührü kırma zamanı!"))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        try { nm.notify(id.hashCode(), n); } catch (SecurityException ignored) { }
    }
}
