# qwiet

**qwiet** adds a global quiet mode to the Nothing Phone (3a): lower every app's output by a configurable amount without touching the normal media volume, and toggle it from a moon inside the system volume panel.

The default reduction is **25 dB**. The slider goes from 5 to 40 dB. Media volume stays exactly where you left it.

## Download

- [qwiet.apk](https://github.com/BRL26/qwiet/releases/latest/download/qwiet.apk) — the app
- [qwiet-panel-hook.apk](https://github.com/BRL26/qwiet/releases/latest/download/qwiet-panel-hook.apk) — the LSPosed module

## Requirements

- Nothing Phone (3a) on a custom ROM
- Root with **LSPosed**
- Android 15/16 (tested on Android 16)

## Install

1. Install the app:

   ```bash
   adb install qwiet.apk
   ```

2. Install the LSPosed module:

   ```bash
   adb install qwiet-panel-hook.apk
   ```

3. Open **LSPosed**, enable the **Quiet panel hook** module, and add **SystemUI** (`com.android.systemui`) to its scope.
4. Reboot SystemUI or the phone.
5. Open **qwiet** and turn on Quiet mode. The moon under the media slider is the quick toggle; it turns darker when quiet mode is active.

The Quick Settings tile and the app switch control the same state.

## How it works

- `QuietModeService` applies a negative global input gain using `DynamicsProcessing`, with `LoudnessEnhancer` as a fallback.
- `QuietController` stores the on/off state and reduction in `SharedPreferences`.
- `QuietPanelHook` injects a flat moon `TextView` into the SystemUI volume dialog under the media slider. It does not replace or modify SystemUI.
- `ControlReceiver` and `QuietTileService` provide the broadcast and Quick Settings entry points.
- No notification is posted.

## Build

The project builds without Gradle, using `aapt2`, `javac`, `d8`, `zipalign`, and `apksigner` from Android build-tools 37.0.0.

```bash
./build.sh          # app -> QuietMode.apk
./build-xposed.sh   # LSPosed module -> QuietPanelHook.apk
```

Both scripts create a local `quietmode.keystore` if one is missing. The release APKs are signed with the maintainer's local key; if you build your own, uninstall the existing qwiet apps before installing your builds (Android will reject a different signature).

## Notes

- Quiet mode changes the global output gain, not the volume slider. Calls, alarms, and media all pass through the same reduction while it is on.
- The volume-panel moon is only injected when the SystemUI hook is active and scoped in LSPosed.
- Tested on a Nothing Phone (3a) running a custom Android 16 ROM with KernelSU + LSPosed.
