package com.naldam.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * 위젯의 ↻ 를 누르면 잠깐 열리는 투명한 화면.
 * 1) 위치 허용을 아직 안 했으면 물어보고
 * 2) 지금 위치를 새로 찾아서 날씨를 받은 뒤 위젯을 다시 그리고 바로 닫혀요.
 */
public class RefreshActivity extends Activity {
    private static final int REQ_LOC = 11;
    private static final int REQ_BG = 12;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Loc.hasPermission(this)) {
            requestPermissions(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
        } else {
            maybeAskBackgroundThenRefresh();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_LOC) {
            if (Loc.hasPermission(this)) {
                maybeAskBackgroundThenRefresh();
            } else {
                Toast.makeText(this, "위치를 허용해야 지금 있는 곳의 날씨를 보여줄 수 있어요", Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == REQ_BG) {
            refresh();
        }
    }

    /** "항상 허용"을 한 번만 물어봐요. 안 해도 위젯은 되고, ↻ 를 누를 때마다 위치가 새로 잡혀요. */
    private void maybeAskBackgroundThenRefresh() {
        SharedPreferences sp = getSharedPreferences(Weather.PREFS, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 29 && !Loc.hasBackground(this) && !sp.getBoolean("askedBg", false)) {
            sp.edit().putBoolean("askedBg", true).apply();
            new AlertDialog.Builder(this)
                .setTitle("위치 '항상 허용'")
                .setMessage("다른 동네로 가도 위젯이 알아서 그곳 날씨로 바뀌려면, 다음 화면에서 위치 권한을 '항상 허용'으로 골라 주세요.\n\n허용하지 않아도 위젯은 쓸 수 있어요. 이때는 ↻ 를 누를 때 위치가 새로 잡혀요.")
                .setPositiveButton("설정하기", (d, which) -> requestPermissions(
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BG))
                .setNegativeButton("괜찮아요", (d, which) -> refresh())
                .setOnCancelListener(d -> refresh())
                .show();
        } else {
            refresh();
        }
    }

    private void refresh() {
        Toast.makeText(this, "날씨를 새로 불러오는 중…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = NaldamWidget.refreshNow(getApplicationContext(), true);
            runOnUiThread(() -> {
                Toast.makeText(this, ok ? "날씨를 새로 불러왔어요" : "날씨를 불러오지 못했어요. 인터넷을 확인해 주세요", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
