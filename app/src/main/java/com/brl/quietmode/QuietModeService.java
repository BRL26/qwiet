package com.brl.quietmode;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class QuietModeService extends Service {
    private final IBinder binder = new Binder();

    @Override
    public void onCreate() {
        super.onCreate();
        if (QuietController.isOn(this)) {
            QuietController.ensureEffect(this);
        }
        ControlReceiver.sendState(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (QuietController.isOn(this)) {
            QuietController.ensureEffect(this);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        QuietController.releaseEffect();
        super.onDestroy();
    }
}
