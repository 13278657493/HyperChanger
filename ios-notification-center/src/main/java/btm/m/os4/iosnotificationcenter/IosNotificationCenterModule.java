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
        IosShadeGestureHooks.install(param.getClassLoader());
        IosNotificationStackHooks.install(param.getClassLoader());
        IosNotificationPresentationHooks.install(param.getClassLoader());
    }
}
