package btm.m.os4.iosnotificationcenter;

import android.view.View;
import android.view.ViewGroup;

/** Captures the native SystemUI window and notification stack for the independent host. */
public final class IosNotificationPresentationHooks {
    private static boolean installed;
    private IosNotificationPresentationHooks() {}

    public static void install(ClassLoader loader) {
        if (installed) return;
        try {
            Class<?> window = Xp.findClass(
                    "com.android.systemui.shade.NotificationShadeWindowView", loader);
            io.github.libxposed.api.XposedInterface.Hooker attach = chain -> {
                Object result = chain.proceed();
                try {
                    Object self = chain.getThisObject();
                    if (self instanceof ViewGroup) {
                        IosNotificationCenterPresentation.attach((ViewGroup) self);
                    }
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] notification window attach observation failed: " + error);
                }
                return result;
            };
            Xp.hookAll(window, "onFinishInflate", attach);
            Xp.hookAll(window, "onAttachedToWindow", attach);
            Xp.log("[IOSNotificationCenter] notification window capture installed");
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] notification window capture unavailable: " + error);
        }
        try {
            Class<?> window = Xp.findClass(
                    "com.android.systemui.shade.NotificationShadeWindowView", loader);
            Xp.hook(Xp.findMethodExact(window, "onDetachedFromWindow"), chain -> {
                Object self = chain.getThisObject();
                Object result = chain.proceed();
                try {
                    if (self instanceof ViewGroup) {
                        IosNotificationCenterPresentation.detachWindow((ViewGroup) self);
                    }
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] notification window detach observation failed: " + error);
                }
                return result;
            });
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] notification window detach observer unavailable: " + error);
        }
        try {
            Class<?> stack = Xp.findClass(
                    "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",
                    loader);
            io.github.libxposed.api.XposedInterface.Hooker capture = chain -> {
                Object result = chain.proceed();
                try {
                    if (chain.getThisObject() instanceof View) {
                        IosNotificationCenterPresentation.captureStack((View) chain.getThisObject());
                    }
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] stack capture observation failed: " + error);
                }
                return result;
            };
            Xp.hookAll(stack, "onAttachedToWindow", capture);
            Xp.hookAll(stack, "onFinishInflate", capture);
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] notification stack capture unavailable: " + error);
        }
        try {
            Class<?> header = Xp.findClass("com.android.systemui.qs.MiuiNotificationHeaderView", loader);
            Xp.hookAll(header, "onFinishInflate", chain -> {
                Object result = chain.proceed();
                try {
                    if (chain.getThisObject() instanceof View) {
                        IosNotificationCenterPresentation.captureHeader((View) chain.getThisObject());
                    }
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] header capture observation failed: " + error);
                }
                return result;
            });
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] notification header capture unavailable: " + error);
        }
        try {
            Class<?> keyguard = Xp.findClass(
                    "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", loader);
            Xp.hookAll(keyguard, "notifyKeyguardState", chain -> {
                Object result = chain.proceed();
                try {
                    if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Boolean) {
                        IosNotificationCenterPresentation.onKeyguardStateChanged(
                                (Boolean) chain.getArgs().get(0));
                    }
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] keyguard state observation failed: " + error);
                }
                return result;
            });
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] keyguard controller observer unavailable: " + error);
        }
        try {
            Class<?> monitor = Xp.findClass("com.android.keyguard.KeyguardUpdateMonitor", loader);
            Xp.hookAll(monitor, "setKeyguardShowing", chain -> {
                Object result = chain.proceed();
                try {
                    if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Boolean) {
                        IosNotificationCenterPresentation.onKeyguardStateChanged(
                                (Boolean) chain.getArgs().get(0));
                    }
                } catch (Throwable error) {
                    Xp.log("[IOSNotificationCenter] keyguard monitor observation failed: " + error);
                }
                return result;
            });
        } catch (Throwable error) {
            Xp.log("[IOSNotificationCenter] keyguard monitor observer unavailable: " + error);
        }
        installed = true;
    }
}
