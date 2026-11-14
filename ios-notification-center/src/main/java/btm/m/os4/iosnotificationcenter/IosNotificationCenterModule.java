package btm.m.os4.iosnotificationcenter;

import io.github.libxposed.api.XposedModule;

/**
 * Standalone libxposed entry point.
 *
 * Notification hooks are intentionally not installed until their dependencies have been
 * separated from HyperChanger's music-cover and legacy shade implementation.
 */
public final class IosNotificationCenterModule extends XposedModule {
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) {
            return;
        }

        Xp.attach(this);

        // Observe native shade state only. The standalone visual takeover is not enabled yet.
        IosShadeGestureHooks.install(param.getClassLoader());
    }
}
