package com.brl.quietmod.xposed;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "QuietPanel";
    private static final String APP = "com.brl.quietmode";
    private static final String SERVICE = APP + ".QuietModeService";
    private static final String RECEIVER = APP + ".ControlReceiver";
    private static final String ACTION_TOGGLE = APP + ".TOGGLE";
    private static final String ACTION_QUERY = APP + ".QUERY";
    private static final String ACTION_STATE = APP + ".STATE";

    private static final WeakHashMap<TextView, Boolean> BUTTONS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> PREPARED = new WeakHashMap<>();
    private static final int ICON_COLOR = 0xFFA9CAE8;
    private static final int ICON_COLOR_ON = 0xFF6F8FAE;
    private static boolean stateReceiverRegistered;
    private static boolean serviceBound;
    private static TextView quietButton;
    private static Dialog quietDialog;

    private static final ServiceConnection SERVICE_CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            serviceBound = true;
            XposedBridge.log(TAG + ": quiet service bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            XposedBridge.log(TAG + ": quiet service disconnected");
        }
    };

    private static final BroadcastReceiver STATE_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_STATE.equals(intent.getAction())) {
                return;
            }
            boolean on = intent.getBooleanExtra("on", false);
            ArrayList<TextView> buttons = new ArrayList<>(BUTTONS.keySet());
            for (TextView button : buttons) {
                if (button != null) {
                    BUTTONS.put(button, on);
                    styleButton(button, on);
                }
            }
        }
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log(TAG + ": loading in SystemUI");
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Dialog",
                    lpparam.classLoader,
                    "show",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object object = param.thisObject;
                            if (!(object instanceof Dialog)) {
                                return;
                            }
                            Dialog dialog = (Dialog) object;
                            if (!isVolumeDialog(dialog)) {
                                return;
                            }
                            try {
                                prepare(dialog);
                            } catch (Throwable t) {
                                XposedBridge.log(t);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static boolean isVolumeDialog(Dialog dialog) {
        String className = dialog.getClass().getName().toLowerCase(Locale.US);
        String title = "";
        try {
            if (dialog.getWindow() != null) {
                CharSequence value = dialog.getWindow().getAttributes().getTitle();
                if (value != null) {
                    title = value.toString().toLowerCase(Locale.US);
                }
            }
        } catch (Throwable ignored) {
        }
        return className.contains("volume") || title.contains("volume");
    }

    private static void prepare(final Dialog dialog) {
        if (quietDialog == dialog && quietButton != null && quietButton.getParent() != null) {
            return;
        }
        if (dialog.getWindow() == null) {
            return;
        }
        View decor = dialog.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) {
            return;
        }
        if (inject(dialog)) {
            XposedBridge.log(TAG + ": injected immediately after show");
            return;
        }

        final ViewTreeObserver observer = decor.getViewTreeObserver();
        final boolean[] finished = {false};
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            private int attempts;

            @Override
            public boolean onPreDraw() {
                if (finished[0]) {
                    if (observer.isAlive()) {
                        observer.removeOnPreDrawListener(this);
                    }
                    return true;
                }
                attempts++;
                boolean ok = false;
                try {
                    ok = inject(dialog);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
                if (ok) {
                    finished[0] = true;
                    ViewTreeObserver active = decor.getViewTreeObserver();
                    if (active.isAlive()) {
                        active.removeOnPreDrawListener(this);
                    }
                    if (observer.isAlive() && observer != active) {
                        observer.removeOnPreDrawListener(this);
                    }
                    XposedBridge.log(TAG + ": injected before first draw in " + attempts + " pass");
                    return false;
                }
                if (attempts >= 30) {
                    finished[0] = true;
                    if (observer.isAlive()) {
                        observer.removeOnPreDrawListener(this);
                    }
                    XposedBridge.log(TAG + ": injection gave up after " + attempts + " passes");
                    return true;
                }
                return false;
            }
        });
    }

    private static boolean inject(Dialog dialog) {
        if (dialog.getWindow() == null) {
            return false;
        }
        View decor = dialog.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) {
            return false;
        }
        ViewGroup root = (ViewGroup) decor;
        ViewGroup slider = findMediaSlider(root);
        if (slider == null || slider.getWidth() <= 0 || slider.getHeight() <= 0) {
            return false;
        }
        View compose = findDirectComposeChild(slider);
        if (compose == null) {
            return false;
        }

        Context context = dialog.getContext();
        int gap = dp(context, 20);
        int buttonSize = dp(context, 30);
        int bottomMargin = 0;
        if (!Boolean.TRUE.equals(PREPARED.get(slider))) {
            int contentHeight = Math.max(dp(context, 160),
                    slider.getHeight() - buttonSize - gap);
            ViewGroup.LayoutParams params = compose.getLayoutParams();
            params.height = contentHeight;
            compose.setLayoutParams(params);
            compose.requestLayout();
            PREPARED.put(slider, Boolean.TRUE);
            XposedBridge.log(TAG + ": media slider content -> " + contentHeight
                    + " inside frame " + slider.getHeight());
        }

        if (quietDialog != dialog || quietButton == null || quietButton.getParent() == null) {
            TextView button = makeButton(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    buttonSize, buttonSize);
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.bottomMargin = bottomMargin;
            slider.setClipChildren(false);
            slider.setClipToPadding(false);
            button.setTranslationY(dp(context, 9));
            slider.addView(button, params);
            slider.requestLayout();
            button.postDelayed(new Runnable() {
                @Override
                public void run() {
                    int[] location = new int[2];
                    button.getLocationOnScreen(location);
                    XposedBridge.log(TAG + ": quiet button screen " + location[0] + ","
                            + location[1] + " size=" + button.getWidth() + "x"
                            + button.getHeight());
                }
            }, 250);
            quietButton = button;
            quietDialog = dialog;
            BUTTONS.put(button, false);
            styleButton(button, false);
            registerStateReceiver(context);
            bindQuietService(context);
            queryState(context);
            XposedBridge.log(TAG + ": quiet circle added inside media slider");
        }
        return true;
    }

    private static ViewGroup findMediaSlider(ViewGroup root) {
        ViewGroup panel = findPanelRoot(root);
        if (panel == null) {
            return null;
        }
        ViewGroup best = null;
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (child instanceof FrameLayout
                    && child.getWidth() > 0
                    && child.getHeight() > 0
                    && hasComposeView((ViewGroup) child)) {
                if (best == null || child.getHeight() > best.getHeight()) {
                    best = (ViewGroup) child;
                }
            }
        }
        return best;
    }

    private static ViewGroup findPanelRoot(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof ViewGroup)) {
                continue;
            }
            ViewGroup childGroup = (ViewGroup) child;
            if (child.getClass().getName().contains("ConstraintLayout")
                    && hasDirectComposeFrame(childGroup)) {
                return childGroup;
            }
            ViewGroup found = findPanelRoot(childGroup);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean hasDirectComposeFrame(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof FrameLayout && hasComposeView((ViewGroup) child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasComposeView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getClass().getName().contains("ComposeView")) {
                return true;
            }
            if (child instanceof ViewGroup && hasComposeView((ViewGroup) child)) {
                return true;
            }
        }
        return false;
    }

    private static View findDirectComposeChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getClass().getName().contains("ComposeView")) {
                return child;
            }
        }
        return null;
    }

    private static TextView makeButton(Context context) {
        TextView button = new TextView(context);
        button.setBackground(new MoonDrawable(ICON_COLOR));
        button.setContentDescription("Quiet mode off");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle((TextView) view);
            }
        });
        return button;
    }

    private static void toggle(TextView button) {
        Context context = button.getContext();
        boolean target = !Boolean.TRUE.equals(BUTTONS.get(button));
        XposedBridge.log(TAG + ": click target=" + target);
        ArrayList<TextView> buttons = new ArrayList<>(BUTTONS.keySet());
        for (TextView item : buttons) {
            if (item != null) {
                BUTTONS.put(item, target);
                styleButton(item, target);
            }
        }
        try {
            Intent receiver = new Intent(ACTION_TOGGLE);
            receiver.setComponent(new ComponentName(APP, RECEIVER));
            context.sendBroadcast(receiver);
            bindQuietService(context);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": control broadcast failed: " + t);
            Toast.makeText(context, "qwiet app unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private static void bindQuietService(Context context) {
        if (serviceBound) {
            return;
        }
        try {
            Intent service = new Intent();
            service.setComponent(new ComponentName(APP, SERVICE));
            boolean requested = context.bindService(
                    service, SERVICE_CONNECTION, Context.BIND_AUTO_CREATE);
            if (requested) {
                serviceBound = true;
            }
            XposedBridge.log(TAG + ": quiet service bind requested=" + requested);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": quiet service bind failed: " + t);
        }
    }

    private static void registerStateReceiver(Context context) {
        if (stateReceiverRegistered) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(ACTION_STATE);
            if (Build.VERSION.SDK_INT >= 33) {
                context.getApplicationContext().registerReceiver(
                        STATE_RECEIVER, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.getApplicationContext().registerReceiver(STATE_RECEIVER, filter);
            }
            stateReceiverRegistered = true;
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void queryState(Context context) {
        try {
            Intent query = new Intent(ACTION_QUERY);
            query.setComponent(new ComponentName(APP, RECEIVER));
            context.sendBroadcast(query);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": query failed: " + t);
        }
    }

    private static void styleButton(TextView button, boolean on) {
        button.setBackground(new MoonDrawable(on ? ICON_COLOR_ON : ICON_COLOR));
        button.setContentDescription(on ? "Quiet mode on" : "Quiet mode off");
    }

    private static final class MoonDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;

        MoonDrawable(int color) {
            this.color = color;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float radius = Math.min(bounds.width(), bounds.height()) * 0.28f;
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            Path moon = new Path();
            moon.addCircle(cx, cy, radius, Path.Direction.CW);
            Path cut = new Path();
            cut.addCircle(cx + radius * 0.55f, cy - radius * 0.12f,
                    radius * 0.92f, Path.Direction.CW);
            moon.op(cut, Path.Op.DIFFERENCE);
            RectF moonBounds = new RectF();
            moon.computeBounds(moonBounds, true);
            paint.setColor(color);
            canvas.save();
            canvas.translate(bounds.exactCenterX() - moonBounds.centerX(),
                    bounds.exactCenterY() - moonBounds.centerY());
            canvas.drawPath(moon, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
