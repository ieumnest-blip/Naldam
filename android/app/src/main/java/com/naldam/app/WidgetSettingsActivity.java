package com.naldam.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 위젯 꾸미기: 배경 진하기(투명도)와 글자색.
 * 위젯을 길게 누르고 '설정'을 누르면 열려요. 모든 날담 위젯에 같이 적용돼요.
 */
public class WidgetSettingsActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int alphaPct;
    private boolean white;
    private FrameLayout preview;
    private GradientDrawable previewBg;
    private TextView previewText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle ex = getIntent().getExtras();
        if (ex != null) widgetId = ex.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        // 그냥 닫아도 위젯은 그대로 쓰이게
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));

        SharedPreferences sp = getSharedPreferences(Weather.PREFS, MODE_PRIVATE);
        alphaPct = sp.getInt("bgAlpha", 100);
        white = sp.getBoolean("textWhite", false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        box.setPadding(pad, dp(28), pad, pad);

        TextView title = text("날담 위젯 꾸미기", 22, true, 0xFF3A3352);
        box.addView(title);
        box.addView(space(18));

        // 미리보기
        preview = new FrameLayout(this);
        preview.setBackgroundColor(0xFF7B8DB0); // 바탕화면 대신
        previewBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFE3F1FF, 0xFFFFF1D6});
        previewBg.setCornerRadius(dp(22));
        TextView card = new TextView(this);
        card.setBackground(previewBg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        previewText = card;
        card.setText("● 대구광역시 달서구\n16.6℃  흐림\n💨 지금 남실바람; 얼굴에 바람이 살랑살랑 느껴져");
        card.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        card.setLineSpacing(0, 1.3f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(14), dp(14), dp(14), dp(14));
        preview.addView(card, lp);
        box.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        box.addView(space(22));

        // 배경 진하기
        final TextView alphaLabel = text("", 16, true, 0xFF3A3352);
        box.addView(alphaLabel);
        SeekBar bar = new SeekBar(this);
        bar.setMax(20);                      // 5% 단위
        bar.setProgress(alphaPct / 5);
        box.addView(bar, new LinearLayout.LayoutParams(-1, dp(44)));
        box.addView(text("0% = 완전 투명 (바탕화면이 그대로 보여요)  ·  100% = 원래 색", 13, false, 0xFF8A84A3));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { alphaPct = p * 5; refreshPreview(alphaLabel); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(space(22));

        // 글자색
        box.addView(text("글자색", 16, true, 0xFF3A3352));
        RadioGroup rg = new RadioGroup(this);
        RadioButton dark = new RadioButton(this);
        dark.setText("진한 글자 (밝은 배경에 잘 보여요)");
        dark.setId(1);
        RadioButton light = new RadioButton(this);
        light.setText("흰 글자 (투명하게 할 때 잘 보여요)");
        light.setId(2);
        rg.addView(dark);
        rg.addView(light);
        rg.check(white ? 2 : 1);
        rg.setOnCheckedChangeListener((g, id) -> { white = id == 2; refreshPreview(alphaLabel); });
        box.addView(rg);
        box.addView(space(26));

        Button save = new Button(this);
        save.setText("저장");
        save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        save.setOnClickListener(v -> {
            getSharedPreferences(Weather.PREFS, MODE_PRIVATE).edit()
                .putInt("bgAlpha", alphaPct).putBoolean("textWhite", white).apply();
            NaldamWidget.renderAll(getApplicationContext());
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
            finish();
        });
        box.addView(save, new LinearLayout.LayoutParams(-1, dp(52)));

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        setContentView(sv);
        refreshPreview(alphaLabel);
    }

    private void refreshPreview(TextView alphaLabel) {
        alphaLabel.setText("배경 진하기: " + alphaPct + "%");
        previewBg.setAlpha(Math.round(alphaPct * 2.55f));
        previewText.setTextColor(white ? Color.WHITE : 0xFF3A3352);
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.START);
        return t;
    }

    private android.view.View space(int h) {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
