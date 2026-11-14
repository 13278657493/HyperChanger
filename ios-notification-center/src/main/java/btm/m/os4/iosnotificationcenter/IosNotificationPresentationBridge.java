package btm.m.os4.iosnotificationcenter;

import android.view.View;

/**
 * Narrow seam between SystemUI notification hooks and the independent presentation host.
 * No OEM view is claimed until a host explicitly registers and owns it.
 */
public final class IosNotificationPresentationBridge {
    public interface Host {
        boolean isActive();
        default void syncShortcuts() {}
        default boolean isKeyguardLocked() { return false; }
        boolean ownsStack(View view);
        boolean ownsRow(View view);
        float viewportBottom(View view);
        float viewportBottomForDraw(View view);
        float stackOffset(View view, float nativeStackY);
        int landscapeStackWidth(View view);
        int extraScrollRange(View view);
        float panelStackAlpha(View view, float nativeAlpha);
    }

    private static volatile Host host;
    private IosNotificationPresentationBridge() {}

    public static void attach(Host value) { host = value; }
    public static void detach(Host value) { if (host == value) host = null; }

    public static boolean isKeyguardLocked() { Host h = host; return h != null && h.isKeyguardLocked(); }
    public static void syncShortcuts() { Host h = host; if (h != null) h.syncShortcuts(); }
    public static boolean isActive() { Host h = host; return h != null && h.isActive(); }
    public static boolean ownsStack(View v) { Host h = host; return h != null && h.ownsStack(v); }
    public static boolean ownsRow(View v) { Host h = host; return h != null && h.ownsRow(v); }
    public static float viewportBottom(View v) { Host h = host; return h == null ? 0f : h.viewportBottom(v); }
    public static float viewportBottomForDraw(View v) { Host h = host; return h == null ? 0f : h.viewportBottomForDraw(v); }
    public static float stackOffset(View v, float y) { Host h = host; return h == null ? 0f : h.stackOffset(v, y); }
    public static int landscapeStackWidth(View v) { Host h = host; return h == null ? 0 : h.landscapeStackWidth(v); }
    public static int extraScrollRange(View v) { Host h = host; return h == null ? 0 : h.extraScrollRange(v); }
    public static float panelStackAlpha(View v, float alpha) { Host h = host; return h == null ? alpha : h.panelStackAlpha(v, alpha); }
}
