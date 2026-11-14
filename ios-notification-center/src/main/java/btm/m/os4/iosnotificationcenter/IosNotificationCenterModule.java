package btm.m.os4.iosnotificationcenter;

import io.github.libxposed.api.XposedModule;

/**
 * Standalone libxposed entry point.
 *
 * The actual notification hooks will be attached here only after their dependencies have been
 * separated from HyperChanger's music-cover and legacy shade implementation.
 */
public final class IosNotificationCenterModule extends XposedModule {
    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log("iOS Notification Center module loaded");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) {
            return;
        }

        // Intentionally a no-op in this scaffold. The notification-only hook installer will be
        // connected after IosShadeController and the local Xposed helper are extracted.
        log("SystemUI attached; notification hooks are not enabled in this scaffold yet");
    }
}
