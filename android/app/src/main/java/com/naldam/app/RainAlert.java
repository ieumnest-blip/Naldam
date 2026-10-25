package com.naldam.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * "곧 비가 와요" 알림.
 * 위젯이 날씨를 새로 받을 때(30분쯤마다) 1시간 반 안에 비 소식이 있으면 알려줘요.
 * 같은 비로 여러 번 울리지 않게, 한 번 알리면 3시간 동안은 다시 알리지 않아요.
 */
final class RainAlert {
    private RainAlert() { }

    private static final String CHANNEL = "rain";
    private static final long QUIET_MS = 3 * 3600_000L;

    static void maybeNotify(Context ctx, Weather w) {
        if (w == null || w.rainingNow || w.rainEta <= 0 || w.rainEta > 90) return;
        if (Build.VERSION.SDK_INT >= 33
            && ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;

        SharedPreferences sp = ctx.getSharedPreferences(Weather.PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now < sp.getLong("rainQuietUntil", 0)) return;
        sp.edit().putLong("rainQuietUntil", now + QUIET_MS).apply();

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "곧 비 소식", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("1시간 반 안에 비가 올 것 같으면 알려줘요");
            nm.createNotificationChannel(ch);
        }

        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (open == null) open = new Intent();
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String where = (w.placeShort != null && !w.placeShort.isEmpty()) ? w.placeShort + "에 " : "";
        Notification n = new Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_rain_small)
            .setContentTitle("☔ 곧 비가 와요")
            .setContentText(Weather.fmtMin(w.rainEta) + " 뒤 " + where + "비 소식이 있어요. 우산 챙겨요!")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();
        nm.notify(7, n);
    }
}
