package btm.m.os4.iosnotificationcenter;

/**
 * Notification-only state controller.
 *
 * SystemUI hooks should report native gesture/switch callbacks here; rendering components
 * consume the immutable-style snapshot getters below. No music-cover or legacy ShadeLayer
 * state is reachable from this controller.
 */
public final class IosShadeController {
    private final IosShadeState state = new IosShadeState();
    private boolean enabled;

    public void setEnabled(boolean value) {
        enabled = value;
        if (!value) state.clear();
    }

    public boolean isEnabled() { return enabled; }

    public float onRawExpansion(float height, float threshold, float panelHeight,
            boolean tracking) {
        if (!enabled) return 0f;
        return state.gestureTarget(height, threshold, panelHeight, tracking);
    }

    public void onControlCenterAppearance(boolean visible) {
        if (!enabled) return;
        state.controlCenter(visible);
    }

    public void onNotificationAppearance(boolean visible) {
        if (!enabled) return;
        if (visible) state.notification();
        else state.move(0f);
    }

    public boolean onSwitchProgress(float progress, boolean switching) {
        return enabled && state.switchProgress(progress, switching);
    }

    public void setProgress(float progress) {
        if (enabled) state.move(progress);
    }

    public float progress() { return state.progress; }
    public float switchFraction() { return state.switchFraction; }
    public boolean isControlCenter() { return state.controlCenter; }
    public boolean isSwitching() { return state.switching; }
    public boolean isNotificationPanelActive() {
        return enabled && !state.controlCenter && state.progress > 0f;
    }
    public boolean isNotificationPanelIntercepting() {
        return enabled && !state.controlCenter && !state.switching;
    }

    public void reset() { state.clear(); }
}
