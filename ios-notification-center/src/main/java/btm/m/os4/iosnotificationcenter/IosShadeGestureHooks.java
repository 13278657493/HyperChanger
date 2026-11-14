package btm.m.os4.iosnotificationcenter;

import java.util.List;

/**
 * Minimal notification-only SystemUI gesture bridge.
 *
 * This first extraction observes native shade expansion and panel ownership without changing
 * OEM visibility, animation, touch, or rendering behavior. Visual takeover remains disabled
 * until the standalone presentation layer is ported.
 */
public final class IosShadeGestureHooks {
    private static final IosShadeController CONTROLLER = new IosShadeController();
    private static boolean installed;

    private IosShadeGestureHooks() {}

    public static void install(ClassLoader loader) {
        if (installed) return;
        CONTROLLER.setEnabled(true);

        // Install the isolated MIUI blur observer/controller. It remains passive until a
        // future presentation host explicitly claims a measured notification root.
        IosShadeBackdropBlur.install(loader);
        IosShadeMaterial.install();

        try {
            Class<?> expand = Xp.findClass(
                    "com.android.systemui.shade.NotificationPanelExpandController", loader);
            Xp.hookAll(expand, "notifyExpandHeightChanged", chain -> {
                Object result = chain.proceed();
                List<Object> args = chain.getArgs();
                if (args.size() == 4 && CONTROLLER.isEnabled()) {
                    Object tracking = Xp.callMethod(chain.getThisObject(), "getTracking");
                    boolean dragging = Boolean.TRUE.equals(Xp.callMethod(tracking, "getValue"));
                    // The standalone observer does not own a shade root yet; let the
                    // state machine fall back to the native threshold until presentation
                    // attaches its measured panel height.
                    CONTROLLER.onRawExpansion(((Number) args.get(0)).floatValue(),
                            ((Number) args.get(1)).floatValue(), 0f, dragging);
                }
                return result;
            });
            Xp.log("[IOSNotificationCenter] native expansion observer installed");
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] expansion observer unavailable: " + error);
        }

        try {
            Class<?> switcher = Xp.findClass(
                    "com.miui.systemui.shade.ShadeSwitchControllerImpl", loader);
            io.github.libxposed.api.XposedInterface.Hooker switchProgress = chain -> {
                Object result = chain.proceed();
                Object controller = chain.getThisObject();
                Object animator = Xp.getObjectField(controller, "progressAnimator");
                CONTROLLER.onSwitchProgress(
                        ((Number) Xp.getObjectField(animator, "_progress")).floatValue(),
                        Boolean.TRUE.equals(Xp.getObjectField(controller, "switching")));
                return result;
            };
            Xp.hookAll(switcher, "performProgressChanged", switchProgress);
            Xp.hookAll(switcher, "setSwitching", switchProgress);
            Xp.log("[IOSNotificationCenter] native panel switch observer installed");
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] switch observer unavailable: " + error);
        }

        try {
            Class<?> injector = Xp.findClass(
                    "com.android.systemui.shade.NotificationPanelViewControllerInjector", loader);
            Xp.hookAll(injector, "onControlCenterAppearChanged", chain -> {
                Object result = chain.proceed();
                CONTROLLER.onControlCenterAppearance(
                        Boolean.TRUE.equals(chain.getArgs().get(0)));
                return result;
            });
            Xp.hookAll(injector, "onNotificationAppearChanged", chain -> {
                Object result = chain.proceed();
                CONTROLLER.onNotificationAppearance(
                        Boolean.TRUE.equals(chain.getArgs().get(0)));
                return result;
            });
            Xp.log("[IOSNotificationCenter] panel ownership observers installed");
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] panel ownership observers unavailable: " + error);
        }

        installed = true;
    }

    public static IosShadeController controller() {
        return CONTROLLER;
    }
}
