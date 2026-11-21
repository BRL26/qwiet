package com.brl.quietmode;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.util.Log;

public final class QuietController {
    private static final String TAG = "QuietMode";
    private static final String PREFS = "quiet_mode";
    public static final String KEY_ON = "on";
    public static final String KEY_DB = "db";

    private static final Object LOCK = new Object();
    private static DynamicsProcessing sDp;
    private static LoudnessEnhancer sLe;
    private static String sEffectStatus = "not started";

    private QuietController() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isOn(Context context) {
        return prefs(context).getBoolean(KEY_ON, false);
    }

    public static int getDb(Context context) {
        return prefs(context).getInt(KEY_DB, 25);
    }

    public static String effectStatus() {
        return sEffectStatus;
    }

    public static void setDb(Context context, int db) {
        int d = clamp(db, 1, 40);
        prefs(context).edit().putInt(KEY_DB, d).apply();
        if (isOn(context)) {
            applyGain(context, d);
        }
    }

    public static boolean setQuietMode(Context context, boolean enable) {
        synchronized (LOCK) {
            if (enable) {
                boolean gainOk = applyGain(context, getDb(context));
                prefs(context).edit().putBoolean(KEY_ON, true).apply();
                return gainOk;
            }
            releaseGain();
            prefs(context).edit().putBoolean(KEY_ON, false).apply();
            return true;
        }
    }

    public static boolean ensureEffect(Context context) {
        synchronized (LOCK) {
            if (!isOn(context)) {
                return true;
            }
            return applyGain(context, getDb(context));
        }
    }

    private static boolean applyGain(Context context, int db) {
        float gain = -Math.abs(db);
        try {
            if (sDp == null) {
                sDp = new DynamicsProcessing(0);
            }
            sDp.setInputGainAllChannelsTo(gain);
            sDp.setEnabled(true);
            sEffectStatus = "DynamicsProcessing " + db + " dB";
            return true;
        } catch (Throwable first) {
            Log.w(TAG, "DynamicsProcessing failed", first);
            releaseDp();
            try {
                if (sLe == null) {
                    sLe = new LoudnessEnhancer(0);
                }
                sLe.setTargetGain(-Math.abs(db) * 100);
                sLe.setEnabled(true);
                sEffectStatus = "LoudnessEnhancer " + db + " dB";
                return true;
            } catch (Throwable second) {
                Log.w(TAG, "LoudnessEnhancer failed", second);
                releaseLe();
                sEffectStatus = "global gain unavailable";
                return false;
            }
        }
    }

    public static void releaseEffect() {
        synchronized (LOCK) {
            releaseGain();
        }
    }

    private static void releaseGain() {
        releaseDp();
        releaseLe();
        sEffectStatus = "off";
    }

    private static void releaseDp() {
        if (sDp != null) {
            try {
                sDp.setEnabled(false);
            } catch (Throwable ignored) {
            }
            try {
                sDp.release();
            } catch (Throwable ignored) {
            }
            sDp = null;
        }
    }

    private static void releaseLe() {
        if (sLe != null) {
            try {
                sLe.setEnabled(false);
            } catch (Throwable ignored) {
            }
            try {
                sLe.release();
            } catch (Throwable ignored) {
            }
            sLe = null;
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
