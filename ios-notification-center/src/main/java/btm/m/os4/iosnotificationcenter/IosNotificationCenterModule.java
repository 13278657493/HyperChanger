package btm.m.os4.iosnotificationcenter;

import io.github.libxposed.api.XposedModule;

/**
 * Standalone libxposed entry point.
 *
 * Notification hooks are isolated in this module. Presentation ownership remains dormant until
 * an independent host attaches to IosNotificationPresentationBridge.
 */
public final class IosNotificationCenterModule extends XposedModule {
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) {
            return;
        }

        Xp.attach(this);

        // Observe native shade state and install isolated row lifecycle hooks. The presentation
        // bridge remains detached, so OEM notification rendering is still left untouched.
        IosShadeGestureHooks.install(param.getClassLoader());
        IosNotificationStackHooks.install(param.getClassLoader());
        IosNotificationPresentationHooks.install(param.getClassLoader());
    }
}
