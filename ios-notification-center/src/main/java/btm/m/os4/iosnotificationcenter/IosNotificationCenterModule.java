package btm.m.os4.iosnotificationcenter;

import io.github.libxposed.api.XposedModule;

/**
 * Standalone libxposed entry point.
 *
 * Notification hooks and the independent presentation host are isolated in this module.
 * The host attaches only while the native SystemUI shade window is alive.
 */
public final class IosNotificationCenterModule extends XposedModule {
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) {
            return;
        }

        Xp.attach(this);

        // Observe native shade state, notification rows, and the native window lifecycle.
        // The presentation bridge is attached only after the shade window is captured.
        // Keep each hook family independent: one OEM class/API mismatch must not prevent
        // the remaining notification-center observers from loading in SystemUI.
        ClassLoader loader = param.getClassLoader();
        try {
            IosShadeGestureHooks.install(loader);
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] shade gesture hooks failed to initialize: " + error);
        }
        try {
            IosNotificationStackHooks.install(loader);
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] notification stack hooks failed to initialize: " + error);
        }
        try {
            IosNotificationPresentationHooks.install(loader);
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] presentation lifecycle hooks failed to initialize: " + error);
        }
    }
}
