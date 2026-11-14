package btm.m.os4.iosnotificationcenter;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Owns the lifecycle and bounds of the standalone glass surface.
 *
 * Callers must provide bounds measured in the target parent's local coordinates. The host
 * does not guess a panel position or intercept touches; it only places a non-interactive
 * visual surface behind the notification content.
 */
public final class IosNotificationGlassHost {
    private ViewGroup parent;
    private IosNotificationGlassView surface;

    public void show(ViewGroup target, Rect bounds, float cornerRadius) {
        if (target == null || bounds == null || bounds.isEmpty()) {
            hide();
            return;
        }
        if (parent != target) {
            hide();
            parent = target;
            surface = new IosNotificationGlassView(target.getContext());
            surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            surface.setClickable(false);
            surface.setFocusable(false);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    bounds.width(), bounds.height());
            params.leftMargin = bounds.left;
            params.topMargin = bounds.top;
            if (target instanceof FrameLayout) {
                target.addView(surface, 0, params);
            } else {
                target.addView(surface, 0, params);
            }
        }
        ViewGroup.LayoutParams raw = surface.getLayoutParams();
        if (raw instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
            if (params.width != bounds.width() || params.height != bounds.height()
                    || params.leftMargin != bounds.left || params.topMargin != bounds.top) {
                params.width = bounds.width();
                params.height = bounds.height();
                params.leftMargin = bounds.left;
                params.topMargin = bounds.top;
                surface.setLayoutParams(params);
            }
        } else {
            raw.width = bounds.width();
            raw.height = bounds.height();
            surface.setLayoutParams(raw);
        }
        surface.setCornerRadius(cornerRadius);
        surface.setVisibility(View.VISIBLE);
    }

    public void hide() {
        if (surface != null) {
            surface.setActive(false);
            if (surface.getParent() instanceof ViewGroup) {
                ((ViewGroup) surface.getParent()).removeView(surface);
            }
        }
        surface = null;
        parent = null;
    }

    public IosNotificationGlassView surface() { return surface; }
}
