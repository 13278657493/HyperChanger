package btm.m.os4.iosnotificationcenter;

import java.util.List;

/**
 * Notification-only SystemUI gesture bridge.
 *
 * Observes native shade expansion and panel ownership, then drives the independent presentation.
 * The OEM stack is only claimed after the standalone presentation captures its native root.
 */
public final class IosShadeGestureHooks {
    private static final IosShadeController CONTROLLER = new IosShadeController();
    private static boolean installed;

    private IosShadeGestureHooks() {}

    public static void install(ClassLoader loader) {
        if (installed) return;
        boolean expansionInstalled = false;
        boolean switchInstalled = false;
        boolean panelInstalled = false;

        // Install isolated MIUI material and blur observers. Presentation applies its own
        // visual layers only after the native notification root has been captured.
        // These enhancements are optional on a ROM-by-ROM basis. A missing OEM
        // class or hidden API must not prevent the core shade observers from installing.
        try {
            IosShadeBackdropBlur.install(loader);
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] backdrop blur observer unavailable: " + error);
        }
        try {
            IosShadeMaterial.install();
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] material observer unavailable: " + error);
        }
        try {
            IosShadeClearGesture.install(Xp.api(), loader);
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] clear gesture observer unavailable: " + error);
        }

        try {
            Class<?> expand = Xp.findClass(
                    "com.android.systemui.shade.NotificationPanelExpandController", loader);
            Xp.hookAll(expand, "notifyExpandHeightChanged", chain -> {
                Object result = chain.proceed();
                List<Object> args = chain.getArgs();
                if (args.size() == 4 && CONTROLLER.isEnabled()) {
                    try {
                        Object tracking = Xp.callMethod(chain.getThisObject(), "getTracking");
                        boolean dragging = Boolean.TRUE.equals(Xp.callMethod(tracking, "getValue"));
                        // The standalone observer does not own a shade root yet; let the
                        // state machine fall back to the native threshold until presentation
                        // attaches its measured panel height.
                        float target = CONTROLLER.onRawExpansion(((Number) args.get(0)).floatValue(),
                                ((Number) args.get(1)).floatValue(), 0f, dragging);
                        CONTROLLER.setProgress(target);
                        IosNotificationCenterPresentation.update(target, target, true, null);
                    } catch (Throwable error) {
                        Xp.log("[IOSNotificationCenter] expansion state observation failed: " + error);
                    }
                }
                return result;
            });
            expansionInstalled = true;
            Xp.log("[IOSNotificationCenter] native expansion observer installed");
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] expansion observer unavailable: " + error);
        }

        try {
            Class<?> switcher = Xp.findClass(
                    "com.miui.systemui.shade.ShadeSwitchControllerImpl", loader);
            io.github.libxposed.api.XposedInterface.Hooker switchProgress = chain -> {
                Object result = chain.proceed();
                // Both methods are hooked by name across all overloads. Vendor implementations
                // can change fields or call setSwitching before progressAnimator is initialized;
                // observation must never turn a successful OEM call into a SystemUI crash.
                try {
                    Object controller = chain.getThisObject();
                    Object animator = Xp.getObjectField(controller, "progressAnimator");
                    Object rawProgress = animator == null ? null : Xp.getObjectField(animator, "_progress");
                    Object rawSwitching = Xp.getObjectField(controller, "switching");
                    if (!(rawProgress instanceof Number) || !(rawSwitching instanceof Boolean)) {
                        return result;
                    }
                    boolean inSwitch = (Boolean) rawSwitching;
                    CONTROLLER.onSwitchProgress(((Number) rawProgress).floatValue(), inSwitch);
                    IosNotificationCenterPresentation.setSwitching(inSwitch);
                    IosNotificationCenterPresentation.update(CONTROLLER.progress(),
                            CONTROLLER.progress(), true, null);
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] switch state observation failed: " + error);
                }
                return result;
            };
            Xp.hookAll(switcher, "performProgressChanged", switchProgress);
            Xp.hookAll(switcher, "setSwitching", switchProgress);
            switchInstalled = true;
            Xp.log("[IOSNotificationCenter] native panel switch observer installed");
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] switch observer unavailable: " + error);
        }

        try {
            Class<?> injector = Xp.findClass(
                    "com.android.systemui.shade.NotificationPanelViewControllerInjector", loader);
            Xp.hookAll(injector, "onControlCenterAppearChanged", chain -> {
                Object result = chain.proceed();
                try {
                    if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Boolean) {
                        CONTROLLER.onControlCenterAppearance((Boolean) chain.getArgs().get(0));
                        IosNotificationCenterPresentation.update(CONTROLLER.progress(),
                                CONTROLLER.progress(), true, null);
                    }
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] control-center state observation failed: " + error);
                }
                return result;
            });
            Xp.hookAll(injector, "onNotificationAppearChanged", chain -> {
                Object result = chain.proceed();
                try {
                    if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Boolean) {
                        CONTROLLER.onNotificationAppearance((Boolean) chain.getArgs().get(0));
                        IosNotificationCenterPresentation.update(CONTROLLER.progress(),
                                CONTROLLER.progress(), true, null);
                    }
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] notification-panel state observation failed: " + error);
                }
                return result;
            });
            panelInstalled = true;
            Xp.log("[IOSNotificationCenter] panel ownership observers installed");
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] panel ownership observers unavailable: " + error);
        }

        // Do not advertise the feature as active when every native state source failed.
        // Presentation.update() uses this gate to restore native UI instead of claiming
        // notification ownership with a controller that can never receive progress.
        boolean hasCoreObserver = expansionInstalled || switchInstalled || panelInstalled;
        CONTROLLER.setEnabled(hasCoreObserver);
        Xp.log("[IOSNotificationCenter] core observer status: expansion=" + expansionInstalled
                + ", switch=" + switchInstalled + ", panel=" + panelInstalled
                + ", enabled=" + hasCoreObserver);
        installed = true;
    }

    public static IosShadeController controller() {
        return CONTROLLER;
    }
}
