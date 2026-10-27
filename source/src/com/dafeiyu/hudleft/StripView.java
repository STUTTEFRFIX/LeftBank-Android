package com.dafeiyu.hudleft;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 贴左边缘的那条面板：图标 + 额度圆环 + 百分比，干活时变成 Bot。 */
final class StripView extends View {

    interface Listener {
        void onPick(int index);

        void onExpand();

        void onCollapse();

        void onDrag(float dx, float dy);

        void onDragEnd(float vx, float vy);

        void onDragCancel();

        void onSettled();   // 展开/收起的动画走完
    }

    private final List<Agent> agents;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Bitmap> icons = new HashMap<>();
    private final float dp;
    private final long t0 = SystemClock.uptimeMillis();

    private Listener listener;
    private int pressed = -1;
    private float downY;
    private float downX;
    private boolean expanded;
    private boolean collapsedInGesture;
    private boolean startedCollapsed;
    private float open;              // 0 = 一条线，1 = 图标条
    private float downRawX, downRawY, lastRawX, lastRawY;
    private boolean dragging;
    private boolean swipeLeft;       // 贴边时的左滑（收回），和拖动区分开
    private VelocityTracker velocity;
    private boolean atEdge = true;

    void setAtEdge(boolean v) {
        atEdge = v;
    }
    private ValueAnimator anim;

    StripView(Context c, List<Agent> agents) {
        super(c);
        this.agents = agents;
        this.dp = c.getResources().getDisplayMetrics().density;
        paint.setFilterBitmap(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isExpanded() {
        return expanded;
    }

    void setExpanded(boolean v) {
        if (expanded == v && ((v && open == 1f) || (!v && open == 0f))) return;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (anim != null) anim.cancel();

        if (v) {
            expanded = true;
            open = Math.min(open, 0.02f);
        }
        anim = ValueAnimator.ofFloat(open, v ? 1f : 0f);
        anim.setDuration(v ? 240 : 180);
        anim.setInterpolator(v ? new OvershootInterpolator(0.9f) : new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> {
            open = Math.max(0f, Math.min(1f, (float) a.getAnimatedValue()));
            invalidate();
        });
        if (v) {
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    if (listener != null) listener.onSettled();
                }
            });
        }
        if (!v) {
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    expanded = false;
                    open = 0f;
                    invalidate();
                    if (listener != null) listener.onSettled();
                }
            });
        }
        anim.start();
    }

    private int padV() { return Math.round(14 * dp); }
    private int itemH() { return Math.round(50 * dp); }
    private int touchPad;                    // 离开边框时，右侧/下侧多给一圈触摸区

    void setTouchPad(int px) {
        if (touchPad == px) return;
        touchPad = px;
        requestLayout();
        invalidate();
    }

    private int baseW() { return Math.round(34 * dp); }
    private int baseH() { return padV() * 2 + itemH() * agents.size(); }
    private int widthPx() { return baseW() + touchPad; }
    private int heightPx() { return baseH() + touchPad; }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::applyGestureExclusion);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::applyGestureExclusion);
    }

    private void applyGestureExclusion() {
        if (!isAttachedToWindow()) return;
        setSystemGestureExclusionRects(
                Collections.singletonList(new Rect(0, 0, getWidth(), getHeight())));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(widthPx(), heightPx());
    }

    private float cx() { return baseW() / 2f; }
    private float cy(int i) { return padV() + itemH() * i + Math.round(20 * dp); }
    private float iconR() { return Math.round(9.5f * dp); }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float w = baseW();
        float h = baseH();
        float a = Math.max(0f, Math.min(1f, open));

        // 收起时的那条线：展开过程中淡出
        drawLine(c, now, w, h, 1f - a);

        if (a > 0.01f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(0x1A1918, (int) (0xE6 * a)));
            c.drawRoundRect(new RectF(0, 0, w, h), 20 * dp, 20 * dp, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp);
            paint.setColor(withAlpha(0xFFFFFF, (int) (0x1F * a)));
            c.drawRoundRect(new RectF(dp / 2, dp / 2, w - dp / 2, h - dp / 2), 20 * dp, 20 * dp, paint);

            // 图标整体淡入 + 轻微放大（带一点回弹，像 ColorOS 的展开）
            int layer = c.saveLayerAlpha(0, 0, w, h, (int) (255 * a));
            float s = 0.72f + 0.28f * open;
            for (int i = 0; i < agents.size(); i++) {
                float x = cx();
                float y = cy(i);
                c.save();
                c.scale(s, s, x, y);
                drawAgent(c, agents.get(i), x, y, iconR(), now);
                c.restore();
            }
            c.restoreToCount(layer);
        }

        if (anyWorking() || anyPending()) postInvalidateOnAnimation();   // 关了闪烁也要重绘一帧，切回常亮
    }

    private void drawAgent(Canvas c, Agent a, float x, float y, float r, long now) {
        if (a.request != null) {
            // 红色辉光：不断闪烁，提醒有人等你回答
            float rp = redPulse(now);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(x, y, r * 3.0f,
                    new int[]{(int) (235 * rp) << 24 | 0xFF5A5A,
                            (int) (120 * rp) << 24 | 0xFF5A5A,
                            0x00FF5A5A},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r * 3.0f, paint);
            paint.setShader(null);
        }
        if (a.working) {
            float pulse = 0.5f + 0.35f * (float) Math.sin((now - t0) / 700.0);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(x, y, r * 2.1f,
                    new int[]{withAlpha(a.color, (int) (140 * pulse)), withAlpha(a.color, 0)},
                    null, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r * 2.1f, paint);
            paint.setShader(null);
            drawBot(c, x, y, r, a.color, now);
        } else {
            drawIcon(c, a, x, y, r);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * dp);
        paint.setStrokeCap(Paint.Cap.ROUND);
        RectF ring = new RectF(x - r - 1.6f * dp, y - r - 1.6f * dp,
                x + r + 1.6f * dp, y + r + 1.6f * dp);
        paint.setColor(0x26FFFFFF);
        c.drawArc(ring, 0, 360, false, paint);
        paint.setColor(pctColor(a.pct));
        c.drawArc(ring, -90, Math.max(8, a.pct * 360), false, paint);

        if (a.request != null) {
            float bx = x + r * 0.95f;
            float by = y - r * 0.92f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFE9B949);
            c.drawCircle(bx, by, 4.2f * dp, paint);
            paint.setColor(0xFF14100E);
            paint.setTextSize(5.8f * dp);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("!", bx, by + 2.0f * dp, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFCFC9C2);
        paint.setTextSize(7.5f * dp);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(Math.round(a.pct * 100) + "%", x, y + r + 10 * dp, paint);
    }

    private void drawIcon(Canvas c, Agent a, float x, float y, float r) {
        Bitmap b = icons.get(a.iconRes);
        if (b == null) {
            b = BitmapFactory.decodeResource(getResources(), a.iconRes);
            icons.put(a.iconRes, b);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(a.color, 46));
        c.drawCircle(x, y, r, paint);
        if (b == null) return;

        int save = c.save();
        Path clip = new Path();
        clip.addCircle(x, y, r - 1.5f * dp, Path.Direction.CW);
        c.clipPath(clip);
        float s = r * 0.86f;
        c.drawBitmap(b, null, new RectF(x - s, y - s, x + s, y + s), paint);
        c.restoreToCount(save);
    }

    private void drawBot(Canvas c, float x, float y, float r, int color, long now) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(lerpColor(color, 0xFFFFFFFF, 0.18f));
        RectF head = new RectF(x - r * 0.82f, y - r * 0.72f, x + r * 0.82f, y + r * 0.82f);
        c.drawRoundRect(head, r * 0.5f, r * 0.5f, paint);

        long blink = (now / 380) % 11;
        float open = (blink == 0 || blink == 1) ? 0.12f : 1f;
        paint.setColor(0xFF14100E);
        float ew = r * 0.15f;
        float eh = r * 0.30f * open;
        float off = r * 0.30f;
        float ey = y - r * 0.03f;
        c.drawRoundRect(new RectF(x - off - ew, ey - eh, x - off + ew, ey + eh), ew, ew, paint);
        c.drawRoundRect(new RectF(x + off - ew, ey - eh, x + off + ew, ey + eh), ew, ew, paint);
    }

    /** 收起时只剩一条竖线：按 agent 分段，颜色就是各自额度，干活的那段会呼吸。 */
    private void drawLine(Canvas c, long now, float w, float h, float alpha) {
        if (alpha <= 0.01f) return;
        float x = 1.6f * dp;                     // 贴着屏幕左边框，不留悬浮感
        float lh = Math.min(h, Math.round(150 * dp));   // 线的高度不跟着窗口长
        float top = (h - lh) / 2f + 10 * dp;
        float bottom = (h + lh) / 2f - 10 * dp;
        float seg = (bottom - top) / agents.size();
        int a255 = (int) (255 * Math.min(1f, alpha));
        boolean pending = anyPending();
        // 有待回答的请求：整条线变红，像有人在等你
        float redPulse = redPulse(now);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            float y0 = top + seg * i + 3 * dp;
            float y1 = top + seg * (i + 1) - 3 * dp;

            if (a.working || a.request != null) {
                float pulse = 0.5f + 0.5f * (float) Math.sin((now - t0) / 600.0);
                int glow = a.request != null ? 0xFFE5484D : a.color;
                int ga = a.request != null
                        ? (int) (120 * redPulse * alpha)
                        : (int) (70 * pulse * alpha);
                paint.setStrokeWidth(7 * dp);
                paint.setColor(withAlpha(glow, ga));
                c.drawLine(x, y0, x, y1, paint);
            }
            paint.setStrokeWidth(3 * dp);
            if (pending) {
                paint.setColor(withAlpha(0xFFE5484D, a255));   // 线的本体保持实红，只让光晕呼吸
            } else {
                paint.setColor(withAlpha(pctColor(a.pct), a255));
            }
            c.drawLine(x, y0, x, y1, paint);
        }
    }

    private boolean anyWorking() {
        for (Agent a : agents) if (a.working) return true;
        return false;
    }

    /** 红色闪烁的相位：关掉时保持常亮，速度按周期（ms）算 */
    private float redPulse(long now) {
        if (!Config.flashOn) return 1f;
        double period = Math.max(240, Config.flashMs);
        return 0.775f + 0.225f * (float) Math.sin((now - t0) * 2 * Math.PI / period);   // 0.55..1.0：一直看得见，只是明暗呼吸
    }

    void previewCollapsed() {
        expanded = false;
        open = 0f;
        setTouchPad(0);
        invalidate();
    }

    void previewExpanded() {
        expanded = true;
        open = 1f;
        setTouchPad(0);
        invalidate();
    }

    private boolean anyPending() {
        for (Agent a : agents) if (a.request != null) return true;
        return false;
    }

    private int pctColor(float p) {
        if (p >= 0.9f) return 0xFFE5484D;
        if (p >= 0.7f) return 0xFFE9B949;
        return 0xFF43B581;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private int indexAt(float y) {
        if (y < padV()) return -1;                      // 顶部把手区
        int i = (int) ((y - padV()) / itemH());
        return (i >= 0 && i < agents.size()) ? i : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (open > 0.02f && open < 0.98f) return true;   // 动画中先别点

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                swipeLeft = false;
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                lastRawX = downRawX;
                lastRawY = downRawY;
                startedCollapsed = !expanded;
                pressed = expanded ? indexAt(e.getY()) : -1;
                downY = e.getY();
                downX = e.getX();
                return true;

            case MotionEvent.ACTION_MOVE: {
                float mdx = e.getRawX() - downRawX;
                float mdy = e.getRawY() - downRawY;
                if (!dragging && !swipeLeft && (Math.abs(mdx) > 8 * dp || Math.abs(mdy) > 8 * dp)) {
                    // 贴在边上的时候，往左划 = 收回；其余方向都算拖
                    if (atEdge && mdx < 0 && Math.abs(mdx) > Math.abs(mdy) && expanded) {
                        swipeLeft = true;
                    } else {
                        dragging = true;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                }
                if (velocity != null) velocity.addMovement(e);
                if (dragging) {
                    if (listener != null) listener.onDrag(e.getRawX() - lastRawX, e.getRawY() - lastRawY);
                    lastRawX = e.getRawX();
                    lastRawY = e.getRawY();
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (swipeLeft) {
                    swipeLeft = false;
                    if (listener != null) listener.onCollapse();
                    pressed = -1;
                    return true;
                }
                if (dragging) {
                    dragging = false;
                    pressed = -1;
                    float vx = 0, vy = 0;
                    if (velocity != null) {
                        velocity.computeCurrentVelocity(1000);
                        vx = velocity.getXVelocity();
                        vy = velocity.getYVelocity();
                    }
                    if (listener != null) listener.onDragEnd(vx, vy);
                    return true;
                }
                if (!expanded) {
                    if (startedCollapsed && listener != null) listener.onExpand();
                    startedCollapsed = false;
                    return true;
                }
                int up = indexAt(e.getY());
                float dx = downX - e.getX();
                float dy = e.getY() - downY;
                if (dx > 10 * dp && Math.abs(dy) < 80 * dp) {
                    if (listener != null) listener.onCollapse();
                } else if (up >= 0 && up == pressed && Math.abs(dy) < 24 * dp) {
                    if (listener != null) listener.onPick(up);
                } else if (listener != null) {
                    listener.onCollapse();
                }
                pressed = -1;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    if (listener != null) listener.onDragCancel();   // 手势被打断：停在原地，别吸/别滑
                }
                swipeLeft = false;
                pressed = -1;
                return true;

            default:
                return true;
        }
    }
}
