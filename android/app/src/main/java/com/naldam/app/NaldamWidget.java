package com.naldam.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 날담 홈 화면 위젯.
 * - 30분마다(폰이 정한 때에) 폰 위치의 날씨를 새로 받아요.
 * - 위젯 크기를 줄이면 아래 줄부터(바람 → 한마디 → 최고·최저 → 날씨 이름) 알아서 빠져요.
 * - 위젯을 누르면 날담 앱이 열리고, ↻ 를 누르면 지금 위치로 새로 받아요.
 */
public class NaldamWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        renderAll(ctx);                       // 저장된 값으로 먼저 그리고
        final PendingResult pr = goAsync();   // 인터넷에서 새로 받아서 다시 그려요
        new Thread(() -> {
            try {
                refreshNow(ctx.getApplicationContext(), false);
            } finally {
                pr.finish();
            }
        }).start();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle newOptions) {
        render(ctx, mgr, id, Weather.load(ctx)); // 크기가 바뀌면 저장된 값으로 다시 그리기
    }

    /** 위치 찾기 → 날씨 받기 → 저장 → 모든 위젯 다시 그리기 (백그라운드 스레드에서 불러요) */
    static boolean refreshNow(Context ctx, boolean fresh) {
        boolean ok = false;
        try {
            double[] ll = Loc.get(ctx, fresh);
            if (ll != null) {
                Weather w = Weather.fetch(ctx, ll[0], ll[1]);
                w.save(ctx);
                ok = true;
                RainAlert.maybeNotify(ctx, w);
            }
        } catch (Exception ignored) {
        }
        renderAll(ctx);
        return ok;
    }

    static void renderAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, NaldamWidget.class));
        Weather w = Weather.load(ctx);
        for (int id : ids) render(ctx, mgr, id, w);
    }

    private static int dp(Bundle o, String key, int def) {
        int v = o != null ? o.getInt(key, 0) : 0;
        return v > 0 ? v : def;
    }

    static void render(Context ctx, AppWidgetManager mgr, int id, Weather w) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_naldam);
        Bundle o = mgr.getAppWidgetOptions(id);
        // 세로로 들고 있을 때의 크기(dp): 너비는 MIN_WIDTH, 높이는 MAX_HEIGHT
        int wdp = dp(o, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int hdp = dp(o, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110);

        // 누르면: 위치를 모르면 위치 허용 화면, 아니면 날담 앱
        PendingIntent openApp = PendingIntent.getActivity(ctx, 0, launchIntent(ctx),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent refresh = PendingIntent.getActivity(ctx, 1,
            new Intent(ctx, RefreshActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.refresh, refresh);

        if (w == null) {
            v.setInt(R.id.root, "setBackgroundResource", R.drawable.bg_none);
            v.setTextViewText(R.id.place, "날담");
            v.setViewVisibility(R.id.time, View.GONE);
            v.setTextViewText(R.id.temp, "--");
            v.setTextViewText(R.id.cond, "");
            v.setViewVisibility(R.id.rowHiLo, View.GONE);
            v.setViewVisibility(R.id.wind, View.GONE);
            v.setViewVisibility(R.id.rain, View.GONE);
            v.setViewVisibility(R.id.chat, View.VISIBLE);
            v.setTextViewText(R.id.chat, Loc.hasPermission(ctx)
                ? "날씨를 불러오는 중이에요… ↻ 를 눌러 보세요"
                : "위치를 허용하려면 여기를 눌러 주세요");
            v.setOnClickPendingIntent(R.id.root, Loc.hasPermission(ctx) ? openApp : refresh);
            mgr.updateAppWidget(id, v);
            return;
        }
        v.setOnClickPendingIntent(R.id.root, openApp);

        String kind = Weather.kindOf(w.code);
        v.setInt(R.id.root, "setBackgroundResource", bgRes(kind));
        int icon = iconRes(kind);
        v.setImageViewResource(R.id.iconBig, icon);
        v.setImageViewResource(R.id.iconSmall, icon);

        // ---- 크기에 따라 보일 것 정하기 ----
        boolean tiny = wdp < 150 || hdp < 70;
        boolean showTime = wdp >= 210 && hdp >= 70;
        boolean showCond = !tiny && wdp >= 190;
        boolean showHiLo = !tiny && hdp >= 100 && wdp >= 170;
        boolean showSrc = showHiLo && wdp >= 300;
        boolean showChat = hdp >= 150 && wdp >= 200;
        boolean showWind = hdp >= 175 && wdp >= 200;
        boolean bigIcon = hdp >= 100 && wdp >= 170;

        float tempSp = hdp < 80 ? 26 : (hdp < 120 ? 34 : (hdp < 170 ? 40 : 46));
        if (wdp < 190) tempSp = Math.min(tempSp, 30);
        if (wdp < 130) tempSp = Math.min(tempSp, 24);
        v.setTextViewTextSize(R.id.temp, TypedValue.COMPLEX_UNIT_SP, tempSp);

        // 비 소식: 줄이 들어갈 만큼 크면 따로 한 줄, 작으면 지역 이름 앞에 ☔ 만
        boolean recent = System.currentTimeMillis() - w.time < 3 * 3600_000L;
        String rain = recent ? w.rainMsg : null;
        boolean showRain = rain != null && hdp >= 100 && wdp >= 170;
        v.setViewVisibility(R.id.rain, showRain ? View.VISIBLE : View.GONE);
        if (rain != null) v.setTextViewText(R.id.rain, rain);
        String placeText = (wdp >= 260 ? w.place : w.placeShort);
        if (rain != null && !showRain) placeText = "☔ " + placeText;
        v.setTextViewText(R.id.place, placeText);
        v.setViewVisibility(R.id.time, showTime ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.time, new SimpleDateFormat(wdp >= 300 ? "yyyy.MM.dd HH:mm" : "HH:mm", Locale.KOREA).format(new Date(w.time)));

        v.setViewVisibility(R.id.iconBig, bigIcon ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.iconSmall, bigIcon ? View.GONE : View.VISIBLE);

        String t = Double.isNaN(w.temp) ? "--" : (Math.round(w.temp * 10) / 10.0 + "℃").replace(".0℃", "℃");
        v.setTextViewText(R.id.temp, t);
        v.setViewVisibility(R.id.cond, showCond ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.cond, w.cond);

        boolean hasHiLo = !Double.isNaN(w.tmax) && !Double.isNaN(w.tmin);
        v.setViewVisibility(R.id.rowHiLo, showHiLo && hasHiLo ? View.VISIBLE : View.GONE);
        if (hasHiLo) {
            v.setTextViewText(R.id.hi, "최고 " + Math.round(w.tmax) + "°");
            v.setTextViewText(R.id.lo, "최저 " + Math.round(w.tmin) + "°");
        }
        v.setViewVisibility(R.id.src, showSrc ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.src, w.kma ? "기상청 기준" : "예보 기준");

        if (showRain && hdp < 190) showChat = false;
        v.setViewVisibility(R.id.chat, showChat ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.chat, w.chatLine());

        String[] wf = Weather.windFeel(w.wind);
        v.setViewVisibility(R.id.wind, showWind && wf != null ? View.VISIBLE : View.GONE);
        if (wf != null) v.setTextViewText(R.id.wind, "💨 지금 " + wf[0] + "; " + wf[1]);

        mgr.updateAppWidget(id, v);
    }

    private static Intent launchIntent(Context ctx) {
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (i == null) i = new Intent();
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private static int bgRes(String k) {
        switch (k) {
            case "sun": return R.drawable.bg_sun;
            case "part": return R.drawable.bg_part;
            case "cloud": return R.drawable.bg_cloud;
            case "rain": return R.drawable.bg_rain;
            case "snow": return R.drawable.bg_snow;
            case "storm": return R.drawable.bg_storm;
            case "fog": return R.drawable.bg_fog;
            default: return R.drawable.bg_none;
        }
    }

    private static int iconRes(String k) {
        switch (k) {
            case "sun": return R.drawable.wx_sun;
            case "cloud": return R.drawable.wx_cloud;
            case "rain": return R.drawable.wx_rain;
            case "snow": return R.drawable.wx_snow;
            case "storm": return R.drawable.wx_storm;
            case "fog": return R.drawable.wx_fog;
            default: return R.drawable.wx_part;
        }
    }
}
