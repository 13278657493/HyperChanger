package btm.m.os4.iosnotificationcenter;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/**
 * Owns the lifecycle and bounds of the standalone glass surface.
 * Bounds are measured in the target parent's local coordinates.
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
            ViewGroup.LayoutParams params = target.generateDefaultLayoutParams();
            if (params == null) params = new ViewGroup.LayoutParams(bounds.width(), bounds.height());
            params.width = bounds.width();
            params.height = bounds.height();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                margins.leftMargin = bounds.left;
                margins.topMargin = bounds.top;
            }
            surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            surface.setClickable(false);
            surface.setFocusable(false);
            target.addView(surface, 0, params);
        }
        ViewGroup.LayoutParams params = surface.getLayoutParams();
        boolean changed = params.width != bounds.width() || params.height != bounds.height();
        params.width = bounds.width();
        params.height = bounds.height();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            changed |= margins.leftMargin != bounds.left || margins.topMargin != bounds.top;
            margins.leftMargin = bounds.left;
            margins.topMargin = bounds.top;
        }
        if (changed) surface.setLayoutParams(params);
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
