package com.brl.quietmode;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0B1118;
    private static final int CARD = 0xFF141E28;
    private static final int CARD_HIGH = 0xFF1B2935;
    private static final int TEXT = 0xFFF2F7FB;
    private static final int MUTED = 0xFF8FA3B3;
    private static final int ACCENT = 0xFFA9CAE8;
    private static final int HAIRLINE = 0x1AFFFFFF;

    private Switch toggle;
    private TextView statusLabel;
    private TextView statusDetail;
    private TextView dbValue;
    private SeekBar dbSeek;
    private boolean refreshing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.getDecorView().setSystemUiVisibility(0);
        clearLegacyNotification();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (QuietController.isOn(this)) {
            QuietController.ensureEffect(this);
        }
        refresh();
    }

    private void clearLegacyNotification() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.cancel(41);
                nm.deleteNotificationChannel("quiet_mode");
            }
        } catch (Throwable ignored) {
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(30));
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_quiet);
        mark.setColorFilter(ACCENT);
        mark.setPadding(dp(11), dp(11), dp(11), dp(11));
        mark.setBackground(background(CARD_HIGH, 16, HAIRLINE));
        header.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("qwiet");
        title.setTextColor(TEXT);
        title.setTextSize(27);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titles.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("quiet, on demand");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(12);
        subtitle.setLetterSpacing(0.04f);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(subtitle, matchWrap());

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(13);
        header.addView(titles, titleParams);
        root.addView(header, matchWrap());

        addSpace(root, 25);

        LinearLayout hero = card();
        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);

        TextView heroTitle = new TextView(this);
        heroTitle.setText("Quiet mode");
        heroTitle.setTextColor(TEXT);
        heroTitle.setTextSize(20);
        heroTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heroTop.addView(heroTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        toggle = new Switch(this);
        toggle.setShowText(false);
        toggle.setContentDescription("Quiet mode");
        toggle.setThumbTintList(switchColors(ACCENT, 0xFF8497A6));
        toggle.setTrackTintList(switchColors(0xFF45647A, 0xFF263844));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (refreshing) {
                    return;
                }
                boolean ok = QuietController.setQuietMode(MainActivity.this, isChecked);
                ControlReceiver.sendState(MainActivity.this);
                if (isChecked && !ok) {
                    Toast.makeText(MainActivity.this,
                            "Global gain is unavailable on this build", Toast.LENGTH_LONG).show();
                }
                refresh();
            }
        });
        heroTop.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        hero.addView(heroTop, matchWrap());

        statusLabel = new TextView(this);
        statusLabel.setText("Off");
        statusLabel.setTextSize(13);
        statusLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusLabel.setLetterSpacing(0.04f);
        statusLabel.setTextColor(MUTED);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(14);
        hero.addView(statusLabel, statusParams);

        statusDetail = new TextView(this);
        statusDetail.setTextSize(12);
        statusDetail.setTextColor(MUTED);
        statusDetail.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(4);
        hero.addView(statusDetail, detailParams);

        root.addView(hero, matchWrap());
        addSpace(root, 14);

        LinearLayout reduction = card();
        LinearLayout reductionTop = new LinearLayout(this);
        reductionTop.setOrientation(LinearLayout.HORIZONTAL);
        reductionTop.setGravity(Gravity.CENTER_VERTICAL);

        TextView reductionTitle = new TextView(this);
        reductionTitle.setText("Reduction");
        reductionTitle.setTextColor(TEXT);
        reductionTitle.setTextSize(15);
        reductionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        reductionTop.addView(reductionTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        dbValue = new TextView(this);
        dbValue.setText("25 dB");
        dbValue.setTextColor(ACCENT);
        dbValue.setTextSize(21);
        dbValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        reductionTop.addView(dbValue, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        reduction.addView(reductionTop, matchWrap());

        dbSeek = new SeekBar(this);
        dbSeek.setMax(35);
        dbSeek.setProgress(20);
        dbSeek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        dbSeek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        dbSeek.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF293947));
        dbSeek.setPadding(0, dp(7), 0, dp(7));
        dbSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int db = progress + 5;
                dbValue.setText(db + " dB");
                if (fromUser) {
                    QuietController.setDb(MainActivity.this, db);
                    refreshStatusText();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        LinearLayout.LayoutParams seekParams = matchWrap();
        seekParams.topMargin = dp(8);
        reduction.addView(dbSeek, seekParams);

        TextView reductionHint = new TextView(this);
        reductionHint.setText("Global gain. Media volume stays exactly where you left it.");
        reductionHint.setTextColor(MUTED);
        reductionHint.setTextSize(11);
        reductionHint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(3);
        reduction.addView(reductionHint, hintParams);

        root.addView(reduction, matchWrap());
        addSpace(root, 14);

        LinearLayout panelCard = card();
        panelCard.setOrientation(LinearLayout.HORIZONTAL);
        panelCard.setGravity(Gravity.CENTER_VERTICAL);

        ImageView panelIcon = new ImageView(this);
        panelIcon.setImageResource(R.drawable.ic_quiet);
        panelIcon.setColorFilter(ACCENT);
        panelIcon.setPadding(dp(7), dp(7), dp(7), dp(7));
        panelIcon.setBackground(background(CARD_HIGH, 13, 0));
        panelCard.addView(panelIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout panelText = new LinearLayout(this);
        panelText.setOrientation(LinearLayout.VERTICAL);

        TextView panelTitle = new TextView(this);
        panelTitle.setText("Volume panel");
        panelTitle.setTextColor(TEXT);
        panelTitle.setTextSize(14);
        panelTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panelText.addView(panelTitle, matchWrap());

        TextView panelHint = new TextView(this);
        panelHint.setText("Tap the moon under the media slider.");
        panelHint.setTextColor(MUTED);
        panelHint.setTextSize(11);
        panelHint.setPadding(0, dp(3), 0, 0);
        panelText.addView(panelHint, matchWrap());

        LinearLayout.LayoutParams panelTextParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        panelTextParams.leftMargin = dp(13);
        panelCard.addView(panelText, panelTextParams);
        root.addView(panelCard, matchWrap());
        addSpace(root, 14);

        Button quick = new Button(this);
        quick.setText("Open Quick Settings");
        quick.setTextColor(ACCENT);
        quick.setTextSize(14);
        quick.setAllCaps(false);
        quick.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        quick.setGravity(Gravity.CENTER);
        quick.setPadding(dp(18), dp(14), dp(18), dp(14));
        quick.setBackground(background(CARD_HIGH, 18, HAIRLINE));
        quick.setElevation(0f);
        quick.setStateListAnimator(null);
        quick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openQuickSettings();
            }
        });
        root.addView(quick, matchWrap());
        addSpace(root, 24);

        TextView footer = new TextView(this);
        footer.setText("No notification. The moon in the volume panel is the main control.");
        footer.setTextColor(0xFF718491);
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(dp(2), 1f);
        root.addView(footer, matchWrap());

        scroll.addView(root);
        setContentView(scroll);
    }

    private void openQuickSettings() {
        try {
            startActivity(new Intent("android.settings.QUICK_SETTINGS_SETTINGS"));
        } catch (Throwable first) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Throwable second) {
                Toast.makeText(this, "Open Quick Settings from the system", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refresh() {
        boolean on = QuietController.isOn(this);
        refreshing = true;
        toggle.setChecked(on);
        refreshing = false;
        dbValue.setText(QuietController.getDb(this) + " dB");
        dbSeek.setProgress(QuietController.getDb(this) - 5);
        refreshStatusText();
    }

    private void refreshStatusText() {
        boolean on = QuietController.isOn(this);
        int db = QuietController.getDb(this);
        statusLabel.setText(on ? "On" : "Off");
        statusLabel.setTextColor(on ? ACCENT : MUTED);
        statusDetail.setText(on
                ? "-" + db + " dB across every app. Volume keys still control the normal level."
                : "Normal audio. Your media volume is untouched.");
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(background(CARD, 24, HAIRLINE));
        return card;
    }

    private GradientDrawable background(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private ColorStateList switchColors(int checked, int unchecked) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        return new ColorStateList(states, new int[]{checked, unchecked});
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }
}
