package btm.m.os4.iosnotificationcenter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Standalone rounded glass primitive for the notification sheet.
 *
 * Uses the MIUI glass material API when available and keeps a conservative translucent
 * fallback when the ROM does not expose that API. This view never owns touch input.
 */
public final class IosNotificationGlassView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private float cornerRadius;
    private boolean materialReady;
    private boolean active = true;

    public IosNotificationGlassView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        fill.setColor(0x18FFFFFF);
        stroke.setColor(0x55FFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(context.getResources().getDisplayMetrics().density);
        setClipToOutline(true);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                float r = Math.min(cornerRadius, Math.min(view.getWidth(), view.getHeight()) * .5f);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Math.max(0f, r));
            }
        });
    }

    /** Configure the glass radius in physical pixels. */
    public void setCornerRadius(float radius) {
        float next = Float.isFinite(radius) ? Math.max(0f, radius) : 0f;
        if (cornerRadius == next) return;
        cornerRadius = next;
        invalidateOutline();
        invalidate();
    }

    public void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if (value) initializeMaterial();
        else releaseMaterial();
        invalidate();
    }

    public boolean hasNativeMaterial() { return materialReady; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initializeMaterial();
    }

    @Override protected void onDetachedFromWindow() {
        releaseMaterial();
        super.onDetachedFromWindow();
    }

    private void initializeMaterial() {
        if (!active || materialReady) return;
        try {
            Xp.callMethod(this, "setPassWindowBlurEnabled", false);
            Xp.callMethod(this, "setMiBackgroundBlurMode", 0);
            Xp.callMethod(this, "setMiViewBlurMode", 3);
            Xp.callMethod(this, "setMiViewMaterialType", 1);
            try { Xp.callMethod(this, "setMiCustomSurfaceColorType", 16); }
            catch (Throwable ignored) {}
            Xp.callMethod(this, "setMiGlassBlurRadius", 8, 24);
            Xp.callMethod(this, "setMiGlass", (Object) glassRecipe());
            updateClip();
            materialReady = true;
        } catch (Throwable error) {
            // The fallback remains usable on builds without the vendor glass extension.
            materialReady = false;
            Xp.log("[IOSNotificationCenter] native glass API unavailable: " + error);
        }
    }

    private void updateClip() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        try {
            Xp.callMethod(this, "setMiGlassClip", 0f, 0f,
                    (float) getWidth(), (float) getHeight());
        } catch (Throwable ignored) {}
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateClip();
    }

    private static float[] glassRecipe() {
        float[] values = {
                .5f, 1f, 0f, 1f, .2f, 2f, .2f, 0f, .6f, .8f, .04f, .9f, .9f, .9f,
                1f, 0f, 1f, 1f, 1f, 30f, 2f, 80f, 400f, 1f, .8f, -.4f, .6f, -.8f,
                1.4f, .7f, .8f, 1.15f, 1.33f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f
        };
        values[19] = 30f;
        values[20] = 2f;
        values[21] = 80f;
        values[22] = 400f;
        values[32] = 1.33f;
        return values;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active) return;
        float inset = stroke.getStrokeWidth() * .5f;
        float radius = Math.max(0f, Math.min(cornerRadius,
                Math.min(getWidth(), getHeight()) * .5f));
        shape.reset();
        shape.addRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                Math.max(0f, radius - inset), Math.max(0f, radius - inset), Path.Direction.CW);
        if (!materialReady) canvas.drawPath(shape, fill);
        canvas.drawPath(shape, stroke);
    }

    private void releaseMaterial() {
        if (!materialReady) return;
        try { Xp.callMethod(this, "setMiGlass", (Object) null); } catch (Throwable ignored) {}
        try { Xp.callMethod(this, "setMiViewMaterialType", 0); } catch (Throwable ignored) {}
        try { Xp.callMethod(this, "setMiViewBlurMode", 0); } catch (Throwable ignored) {}
        try { Xp.callMethod(this, "setMiGlassBlurRadius", 0, 0); } catch (Throwable ignored) {}
        materialReady = false;
    }
}
