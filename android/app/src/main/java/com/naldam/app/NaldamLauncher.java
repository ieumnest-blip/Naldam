package com.naldam.app;

import android.net.Uri;

import com.google.androidbrowserhelper.trusted.LauncherActivity;

/**
 * 앱에서 열었다는 표시(from=app)를 붙이고,
 * 앱을 열 때 폰이 알고 있는 위치(위젯이 쓰는 위치)를 날담 화면에 같이 넘겨줘요.
 * 그래서 크롬이 위치를 못 잡아도 날담 화면에 지금 동네 날씨가 바로 나와요.
 */
public class NaldamLauncher extends LauncherActivity {
    @Override
    protected Uri getLaunchingUrl() {
        Uri url = super.getLaunchingUrl();
        // 날담 화면은 앱에서 열 때만 보여요 (웹사이트로 직접 들어오면 '앱에서 이용해 주세요' 안내)
        if (url != null && url.getQueryParameter("from") == null) {
            url = url.buildUpon().appendQueryParameter("from", "app").build();
        }
        try {
            double[] ll = Loc.get(this, false);   // 금방 끝나요 (폰이 최근에 알던 위치)
            if (ll != null && url != null && url.getQueryParameter("lat") == null) {
                url = url.buildUpon()
                    .appendQueryParameter("lat", String.format(java.util.Locale.US, "%.4f", ll[0]))
                    .appendQueryParameter("lon", String.format(java.util.Locale.US, "%.4f", ll[1]))
                    .build();
            }
        } catch (Exception ignored) {
        }
        return url;
    }
}
