package btm.m.os4.iosnotificationcenter;

/**
 * Gesture state for the standalone iOS notification shade.
 *
 * This class deliberately has no dependency on HyperChanger's music-cover ShadeLayer.
 * It tracks native vertical expansion and the horizontal notification/control-center switch.
 */
public final class IosShadeState {
    private boolean dragging;
    private float nativeHeight;
    private float nativeThreshold;
    private float dragDistance;

    public float progress;
    public boolean controlCenter;
    public boolean switching;
    public float switchFraction = 1f;

    public float gestureTarget(float height, float threshold, float panelHeight, boolean tracking) {
        if (Float.isFinite(threshold) && threshold > 0f) nativeThreshold = threshold;
        float next = Float.isFinite(height) ? height : nativeHeight;
        if (tracking) {
            if (!dragging) {
                dragDistance = Float.isFinite(panelHeight) && panelHeight > 0f
                        ? panelHeight : threshold;
            }
            dragging = true;
            float target = progress + (dragDistance > 0f
                    ? (next - nativeHeight) / dragDistance : 0f);
            nativeHeight = next;
            return fraction(target, 1f);
        }
        dragging = false;
        nativeHeight = next;
        return fraction(next, threshold);
    }

    public boolean switchProgress(float value, boolean inSwitch) {
        float next = fraction(value, 1f);
        if (!inSwitch && !switching && next == switchFraction) return false;
        switching = inSwitch;
        switchFraction = next;
        controlCenter(!inSwitch && next <= 0f);
        if (!inSwitch && next >= 1f) nativeHeight = nativeThreshold;
        if (inSwitch) {
            dragging = false;
            move(1f);
        }
        return true;
    }

    public static float fraction(float height, float threshold) {
        if (!Float.isFinite(height) || !Float.isFinite(threshold) || threshold <= 0f) return 0f;
        return Math.max(0f, Math.min(1f, height / threshold));
    }

    public void move(float value) {
        progress = controlCenter ? 0f : fraction(value, 1f);
    }

    public void controlCenter(boolean visible) {
        controlCenter = visible;
        if (visible) {
            progress = 0f;
            dragging = false;
            nativeHeight = 0f;
        }
    }

    public void notification() {
        controlCenter = false;
        nativeHeight = nativeThreshold;
    }

    public void clear() {
        progress = 0f;
        controlCenter = false;
        switching = false;
        switchFraction = 1f;
        dragging = false;
        nativeHeight = 0f;
        nativeThreshold = 0f;
        dragDistance = 0f;
    }
}
