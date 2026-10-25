package com.naldam.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 폰 위치 찾기. 찾은 위치는 저장해 두고, 못 찾으면 마지막으로 찾은 위치를 써요. */
final class Loc {
    private Loc() { }

    static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasBackground(Context ctx) {
        if (Build.VERSION.SDK_INT < 29) return hasPermission(ctx);
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 위치 하나 얻기 (백그라운드 스레드에서 불러요).
     * fresh=true 이면 새로 측정해 보고(최대 8초), 아니면 폰이 최근에 알고 있던 위치를 써요.
     * @return {위도, 경도} 또는 null
     */
    static double[] get(Context ctx, boolean fresh) {
        Location best = null;
        if (hasPermission(ctx)) {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                if (fresh && Build.VERSION.SDK_INT >= 30) best = current(lm);
                if (best == null) best = lastKnown(lm);
            }
        }
        SharedPreferences sp = ctx.getSharedPreferences(Weather.PREFS, Context.MODE_PRIVATE);
        if (best != null) {
            sp.edit().putFloat("lat", (float) best.getLatitude()).putFloat("lon", (float) best.getLongitude()).apply();
            return new double[]{best.getLatitude(), best.getLongitude()};
        }
        if (sp.contains("lat")) return new double[]{sp.getFloat("lat", 0), sp.getFloat("lon", 0)};
        return null;
    }

    @SuppressWarnings("MissingPermission")
    private static Location lastKnown(LocationManager lm) {
        Location best = null;
        for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) { }
        }
        return best;
    }

    @SuppressWarnings("MissingPermission")
    private static Location current(LocationManager lm) {
        if (Build.VERSION.SDK_INT < 30) return null;
        final Location[] out = new Location[1];
        final CountDownLatch latch = new CountDownLatch(1);
        String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
            : (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null);
        if (provider == null) return null;
        CancellationSignal cancel = new CancellationSignal();
        try {
            lm.getCurrentLocation(provider, cancel, Executors.newSingleThreadExecutor(), loc -> {
                out[0] = loc;
                latch.countDown();
            });
            latch.await(8, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        } finally {
            cancel.cancel();
        }
        return out[0];
    }
}
