package com.brl.quietmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ControlReceiver extends BroadcastReceiver {
    public static final String ACTION_TOGGLE = "com.brl.quietmode.TOGGLE";
    public static final String ACTION_QUERY = "com.brl.quietmode.QUERY";
    public static final String ACTION_STATE = "com.brl.quietmode.STATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            boolean target = !QuietController.isOn(context);
            QuietController.setQuietMode(context, target);
            sendState(context);
        } else if (ACTION_QUERY.equals(intent.getAction())) {
            sendState(context);
        }
    }

    public static void sendState(Context context) {
        Intent state = new Intent(ACTION_STATE);
        state.setPackage("com.android.systemui");
        state.putExtra("on", QuietController.isOn(context));
        state.putExtra("db", QuietController.getDb(context));
        context.sendBroadcast(state);
    }
}
