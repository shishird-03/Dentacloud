package com.dentacloud.app.ui.chart;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dentacloud.app.R;

import java.util.HashMap;
import java.util.Map;

/**
 * FDI odontogram on a Canvas (F5): tap a tooth surface, pinch to zoom, drag to pan.
 * Paths, colours and labels are prepared when the size, dentition or state changes, so onDraw
 * only iterates arrays and allocates nothing (PRD 9 performance rule).
 */
public class ToothChartView extends View {
    public interface OnSurfaceTapListener {
        void onSurfaceTap(int fdi, String surface);
    }

    private static final Map<String, Integer> CONDITION_COLORS = new HashMap<>();

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selected = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private final int soundColor;
    private final float density;

    private boolean child;
    private int[] teeth = ChartGeometry.teeth(false);
    private RectF[] cells = new RectF[0];
    private Path[][] paths = new Path[0][];
    private int[][] colors = new int[0][];
    private String[] labels = new String[0];
    private Map<Integer, String[]> state = new HashMap<>();
    private int selectedIndex = -1;
    private float scale = 1f, tx, ty;
    private float selectedAlpha = 1f;
    private ValueAnimator blink;
    private OnSurfaceTapListener listener;

    public ToothChartView(Context ctx) {
        this(ctx, null);
    }

    public ToothChartView(Context ctx, @Nullable AttributeSet attrs) {
        super(ctx, attrs);
        density = getResources().getDisplayMetrics().density;
        soundColor = ContextCompat.getColor(ctx, R.color.cond_sound);
        if (CONDITION_COLORS.isEmpty()) {
            CONDITION_COLORS.put("CARIES", ContextCompat.getColor(ctx, R.color.cond_caries));
            CONDITION_COLORS.put("FILLED", ContextCompat.getColor(ctx, R.color.cond_filled));
            CONDITION_COLORS.put("CROWN", ContextCompat.getColor(ctx, R.color.cond_crown));
            CONDITION_COLORS.put("MISSING", ContextCompat.getColor(ctx, R.color.cond_missing));
            CONDITION_COLORS.put("RCT", ContextCompat.getColor(ctx, R.color.cond_rct));
            CONDITION_COLORS.put("EXTRACT_PLANNED", ContextCompat.getColor(ctx, R.color.cond_extract));
            CONDITION_COLORS.put("IMPLANT", ContextCompat.getColor(ctx, R.color.cond_implant));
            CONDITION_COLORS.put("FRACTURED", ContextCompat.getColor(ctx, R.color.cond_fractured));
            CONDITION_COLORS.put("BRIDGE", ContextCompat.getColor(ctx, R.color.cond_bridge));
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        stroke.setColor(ContextCompat.getColor(ctx, R.color.outline));
        selected.setStyle(Paint.Style.STROKE);
        selected.setStrokeWidth(3 * density);
        selected.setColor(ContextCompat.getColor(ctx, R.color.primary));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(10 * density);
        label.setColor(ContextCompat.getColor(ctx, R.color.on_surface_variant));

        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector d) {
                float next = Math.max(1f, Math.min(4f, scale * d.getScaleFactor()));
                float f = next / scale;
                tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                scale = next;
                clampPan();
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                if (scale <= 1f) return false;
                tx -= dx;
                ty -= dy;
                clampPan();
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                return tap(e.getX(), e.getY());
            }
        });
        setContentDescription(ctx.getString(R.string.chart_description));
    }

    public void setOnSurfaceTapListener(OnSurfaceTapListener l) {
        listener = l;
    }

    public void setChild(boolean child) {
        if (this.child == child) return;
        this.child = child;
        teeth = ChartGeometry.teeth(child);
        selectedIndex = -1;
        layoutTeeth(getWidth(), getHeight());
    }

    public boolean isChild() {
        return child;
    }

    /** fdi -> condition per surface, as produced by {@link ChartState#at}. */
    public void setState(Map<Integer, String[]> state) {
        this.state = state;
        recolor();
        invalidate();
    }

    public void select(int fdi) {
        selectedIndex = -1;
        for (int i = 0; i < teeth.length; i++) if (teeth[i] == fdi) selectedIndex = i;
        // Part B #10: the selected tooth blinks.
        if (blink != null) blink.cancel();
        blink = ValueAnimator.ofFloat(1f, 0.2f);
        blink.setDuration(300);
        blink.setRepeatCount(3);
        blink.setRepeatMode(ValueAnimator.REVERSE);
        blink.addUpdateListener(a -> {
            selectedAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        blink.start();
    }

    /** Centre of a tooth in view coordinates (unzoomed); used by tests and accessibility. */
    public float[] centerOf(int fdi) {
        for (int i = 0; i < teeth.length; i++) {
            if (teeth[i] == fdi && cells.length > i) return new float[]{cells[i].centerX(), cells[i].centerY()};
        }
        return null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutTeeth(w, h);
    }

    private void layoutTeeth(int w, int h) {
        if (w == 0 || h == 0) return;
        int n = ChartGeometry.perRow(child);
        float pad = 8 * density, labelSpace = 14 * density, archGap = 12 * density, inset = 2 * density;
        float cell = Math.min((w - 2 * pad) / n, (h - 2 * pad - 2 * labelSpace - archGap) / 2);
        float left = (w - n * cell) / 2;
        float[] rowTop = {pad + labelSpace, pad + labelSpace + cell + archGap};
        cells = new RectF[teeth.length];
        paths = new Path[teeth.length][];
        labels = new String[teeth.length];
        for (int i = 0; i < teeth.length; i++) {
            int row = i / n, col = i % n;
            float x0 = left + col * cell + inset, y0 = rowTop[row] + inset;
            float x1 = left + (col + 1) * cell - inset, y1 = rowTop[row] + cell - inset;
            cells[i] = new RectF(x0, y0, x1, y1);
            paths[i] = surfacePaths(teeth[i], x0, y0, x1, y1);
            labels[i] = String.valueOf(teeth[i]);
        }
        label.setTextSize(Math.min(10 * density, cell * 0.35f));
        recolor();
        invalidate();
    }

    /** Five polygons: centre square plus four trapezoids, assigned to M/O/D/B/L for this tooth. */
    private static Path[] surfacePaths(int fdi, float x0, float y0, float x1, float y1) {
        float w = x1 - x0, h = y1 - y0;
        float ix0 = x0 + 0.3f * w, ix1 = x0 + 0.7f * w, iy0 = y0 + 0.3f * h, iy1 = y0 + 0.7f * h;
        Path top = poly(x0, y0, x1, y0, ix1, iy0, ix0, iy0);
        Path bottom = poly(x0, y1, x1, y1, ix1, iy1, ix0, iy1);
        Path leftP = poly(x0, y0, ix0, iy0, ix0, iy1, x0, y1);
        Path rightP = poly(x1, y0, ix1, iy0, ix1, iy1, x1, y1);
        Path centre = poly(ix0, iy0, ix1, iy0, ix1, iy1, ix0, iy1);
        Path[] p = new Path[5];
        boolean upper = ChartGeometry.upper(fdi), leftHalf = ChartGeometry.leftHalf(fdi);
        p[ChartGeometry.O] = centre;
        p[ChartGeometry.B] = upper ? top : bottom;
        p[ChartGeometry.L] = upper ? bottom : top;
        p[ChartGeometry.M] = leftHalf ? rightP : leftP;
        p[ChartGeometry.D] = leftHalf ? leftP : rightP;
        return p;
    }

    private static Path poly(float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.close();
        return p;
    }

    private void recolor() {
        colors = new int[teeth.length][5];
        for (int i = 0; i < teeth.length; i++) {
            String[] s = state.get(teeth[i]);
            for (int k = 0; k < 5; k++) {
                Integer c = s == null || s[k] == null ? null : CONDITION_COLORS.get(s[k]);
                colors[i][k] = c == null ? soundColor : c;
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int n = ChartGeometry.perRow(child);
        canvas.save();
        canvas.translate(tx, ty);
        canvas.scale(scale, scale);
        for (int i = 0; i < cells.length; i++) {
            for (int k = 0; k < 5; k++) {
                fill.setColor(colors[i][k]);
                canvas.drawPath(paths[i][k], fill);
                canvas.drawPath(paths[i][k], stroke);
            }
            RectF c = cells[i];
            boolean upperRow = i < n;
            canvas.drawText(labels[i], c.centerX(), upperRow ? c.top - 4 * density : c.bottom + 12 * density, label);
        }
        if (selectedIndex >= 0 && selectedIndex < cells.length) {
            selected.setAlpha((int) (255 * selectedAlpha));
            canvas.drawRect(cells[selectedIndex], selected);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean handled = scaleDetector.onTouchEvent(e);
        handled |= gestures.onTouchEvent(e);
        if (e.getAction() == MotionEvent.ACTION_UP) performClick();
        return handled || super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Maps a touch to a tooth and surface through the inverse of the zoom/pan transform. */
    boolean tap(float x, float y) {
        float cx = (x - tx) / scale, cy = (y - ty) / scale;
        for (int i = 0; i < cells.length; i++) {
            RectF c = cells[i];
            if (!c.contains(cx, cy)) continue;
            int surface = ChartGeometry.surfaceAt(teeth[i], (cx - c.left) / c.width(), (cy - c.top) / c.height());
            select(teeth[i]);
            String s = ChartGeometry.SURFACES[surface];
            announceForAccessibility(getContext().getString(R.string.tooth_surface, teeth[i], s));
            if (listener != null) listener.onSurfaceTap(teeth[i], s);
            return true;
        }
        return false;
    }

    private void clampPan() {
        float maxX = 0, minX = getWidth() - getWidth() * scale;
        float maxY = 0, minY = getHeight() - getHeight() * scale;
        tx = Math.max(minX, Math.min(maxX, tx));
        ty = Math.max(minY, Math.min(maxY, ty));
    }
}
