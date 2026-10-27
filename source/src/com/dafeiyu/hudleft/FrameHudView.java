package com.dafeiyu.hudleft;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 沿屏幕边框滑动的灯带：收起是一条线，展开是一串图标；拐角自动沿圆弧转过去。 */
final class FrameHudView extends View {

    interface Listener {
        void onExpand();

        void onCollapse();

        void onPick(int index);

        void onSlide(float ds);

        void onDragEnd(float velocity);
    }

    private final FramePath path;
    private final List<Agent> agents;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Bitmap> icons = new HashMap<>();
    private final float dp;
    private final long t0 = SystemClock.uptimeMillis();
    private final float[] ev = new float[3];

    private Listener listener;
    private float s;                 // 条带中点在路径上的弧长
    private boolean expanded;
    private float open;              // 0 线 / 1 图标
    private ValueAnimator anim;
    private double angleDeg;         // 当前切线角（度）
    private float originX, originY;  // 窗口左上角在屏幕里的位置

    private float downRawX, downRawY, lastRawX, lastRawY, downY;
    private boolean dragging;
    private boolean moved;
    private boolean armed;          // 手指按下时是否落在胶囊上
    private int pressed = -1;

    FrameHudView(Context c, FramePath path, List<Agent> agents) {
        super(c);
        this.path = path;
        this.agents = agents;
        this.dp = c.getResources().getDisplayMetrics().density;
        paint.setFilterBitmap(true);
        s = path.leftMiddleS();
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setOrigin(float x, float y) {
        originX = x;
        originY = y;
    }

    float centerS() {
        return s;
    }

    void setS(float v) {
        s = v;
        invalidate();
    }

    boolean isExpanded() {
        return expanded;
    }

    /** 展开/收起：带一点回弹的动画 */
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
            open = Math.max(0f, Math.min(1.05f, (float) a.getAnimatedValue()));
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (!v) {
                    expanded = false;
                    open = 0f;
                }
                if (listener == null) return;
                // 交给服务调整窗口尺寸
                if (expanded) listener.onDragEnd(0);   // 尺寸已定，重算窗口
            }
        });
        anim.start();
    }

    private float step() {
        return 44 * dp;
    }

    /** 展开胶囊的总跨度（px）。加号已经去掉，只按模型图标数算，两头各留一点圆头 */
    float capsuleSpan() {
        return Math.max(1, agents.size()) * step() + 24 * dp;
    }

    private float iconInset() {
        return 15 * dp;
    }

    private float iconR() {
        return 8.5f * dp;
    }

    private boolean anyWorking() {
        for (Agent a : agents) if (a.working) return true;
        return false;
    }

    private boolean anyPending() {
        for (Agent a : agents) if (a.request != null) return true;
        return false;
    }

    private float redPulse(long now) {
        if (!Config.flashOn) return 1f;
        double period = Math.max(240, Config.flashMs);
        return 0.775f + 0.225f * (float) Math.sin((now - t0) * 2 * Math.PI / period);
    }

    // ── 绘制 ─────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float a = Math.max(0f, Math.min(1f, open));
        c.save();
        c.translate(-originX, -originY);
        if (a < 0.99f) drawLine(c, now, 1f - a);
        if (a > 0.01f) {
            drawCapsule(c, a);
            drawIcons(c, now, a);
        }
        c.restore();
        if (anyWorking() || anyPending()) postInvalidateOnAnimation();
    }

    private void drawLine(Canvas c, long now, float alpha) {
        if (alpha <= 0.01f) return;
        float half = 30 * dp;
        float s0 = s - half, s1 = s + half;
        int n = Math.max(1, agents.size());
        float segLen = (s1 - s0) / n;
        float rp = redPulse(now);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < n; i++) {
            Agent ag = agents.get(i);
            int color = ag.request != null ? 0xFFE5484D
                    : (ag.working ? ag.color : pctColor(ag.pct));   // 干活时线变成 agent 自己的颜色
            int a255 = (int) (255 * alpha);
            // 光晕
            float from = s0 + segLen * i - dp * 0.8f;
            float to = s0 + segLen * (i + 1) + dp * 0.8f;
            if (i == 0) from = s0;
            if (i == n - 1) to = s1;
            paint.setStrokeWidth(7 * dp);
            int glowCol = ag.request != null ? 0xFFE5484D : ag.color;
            float glowAmt = ag.request != null ? 120 * rp : (ag.working ? 70 * (0.6f + 0.4f * rp) : 0);
            paint.setColor(withAlpha(glowCol, (int) (glowAmt * alpha)));
            polyline(c, s0 + segLen * i, s0 + segLen * (i + 1), paint);   // 光晕按原段画，避免叠色
            paint.setStrokeWidth(3 * dp);
            paint.setColor(withAlpha(color, a255));
            polyline(c, from, to, paint);
        }
    }

    /** 沿路径画一小段折线 */
    private void polyline(Canvas c, float from, float to, Paint p) {
        float lenPx = Math.abs(to - from);
        int steps = Math.max(2, (int) (lenPx / (2 * dp)));
        Path pp = new Path();
        for (int k = 0; k <= steps; k++) {
            float sAt = from + (to - from) * k / steps;
            path.eval(sAt, ev);
            if (k == 0) pp.moveTo(ev[0], ev[1]);
            else pp.lineTo(ev[0], ev[1]);
        }
        c.drawPath(pp, p);
    }

    /** 展开时的胶囊：贴着边框的一条 34dp 药丸，沿路径走，拐角会跟着弯 */
    private void drawCapsule(Canvas c, float a) {
        float span = capsuleSpan();
        float s0 = s - span / 2f;
        float s1 = s + span / 2f;
        float thick = 30 * dp;
        float off = 15 * dp;   // 中心线往里偏 15dp，胶囊正好贴着边框 0..30dp

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(thick + 1.4f * dp);
        paint.setColor(withAlpha(0xFFFFFF, (int) (0x33 * a)));
        polylineOffset(c, s0, s1, off, paint);

        paint.setStrokeWidth(thick);
        paint.setColor(withAlpha(0x1E1D1B, (int) (0xF0 * a)));
        polylineOffset(c, s0, s1, off, paint);
    }

    /** 沿路径画一条向内偏移的折线 */
    private void polylineOffset(Canvas c, float from, float to, float off, Paint p) {
        float lenPx = Math.abs(to - from);
        int steps = Math.max(2, (int) (lenPx / (2 * dp)));
        Path pp = new Path();
        for (int k = 0; k <= steps; k++) {
            float sAt = from + (to - from) * k / steps;
            path.eval(sAt, ev);
            float x = ev[0] - (float) Math.sin(ev[2]) * off;
            float y = ev[1] + (float) Math.cos(ev[2]) * off;
            if (k == 0) pp.moveTo(x, y);
            else pp.lineTo(x, y);
        }
        c.drawPath(pp, p);
    }

    private void drawIcons(Canvas c, long now, float a) {
        int n = agents.size();
        for (int i = 0; i < n; i++) {
            float si = s + (i - (n - 1) / 2f) * step();
            path.eval(si, ev);
            double ang = ev[2];
            float x = ev[0] - (float) Math.sin(ang) * iconInset();
            float y = ev[1] + (float) Math.cos(ang) * iconInset();
            c.save();
            c.scale(0.72f + 0.28f * open, 0.72f + 0.28f * open, x, y);
            // 底边时向内是「上方」，状态字要画在图标上面，不然会压到屏幕边缘
            drawAgent(c, agents.get(i), x, y, iconR(), now, Math.cos(ang) < -0.5);
            c.restore();
        }
    }

    private void drawAgent(Canvas c, Agent a, float x, float y, float r, long now, boolean bottomEdge) {
        if (a.request != null) {
            float rp = redPulse(now);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(x, y, r * 3.0f,
                    new int[]{(int) (235 * rp) << 24 | 0xFF5A5A,
                            (int) (120 * rp) << 24 | 0xFF5A5A, 0x00FF5A5A},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r * 3.0f, paint);
            paint.setShader(null);
        }

        // 模型自己的底色
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(a.color, a.request != null ? 110 : 80));
        c.drawCircle(x, y, r * 1.18f, paint);

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
        paint.setStrokeWidth(1.6f * dp);
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
            c.drawCircle(bx, by, 3.8f * dp, paint);
            paint.setColor(0xFF14100E);
            paint.setTextSize(5.4f * dp);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("!", bx, by + 2.0f * dp, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFCFC9C2);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        float labelY = bottomEdge ? y - r - 3 * dp : y + r + 10 * dp;
        if (a.label != null) {
            paint.setTextSize(6.6f * dp);
            c.drawText(a.label, x, labelY, paint);
        } else {
            paint.setTextSize(7f * dp);
            c.drawText(Math.round(a.pct * 100) + "%", x, labelY, paint);
        }
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
        float sc = r * 0.92f;
        c.drawBitmap(b, null, new RectF(x - sc, y - sc, x + sc, y + sc), paint);
        c.restoreToCount(save);
    }

    private void drawBot(Canvas c, float x, float y, float r, int color, long now) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(lerpColor(color, 0xFFFFFFFF, 0.18f));
        RectF head = new RectF(x - r * 0.92f, y - r * 0.82f, x + r * 0.92f, y + r * 0.92f);
        c.drawRoundRect(head, r * 0.5f, r * 0.5f, paint);
        long blink = (now / 380) % 11;
        float open = (blink == 0 || blink == 1) ? 0.12f : 1f;
        paint.setColor(0xFF14100E);
        float ew = r * 0.15f, eh = r * 0.30f * open, off = r * 0.30f, ey = y - r * 0.03f;
        c.drawRoundRect(new RectF(x - off - ew, ey - eh, x - off + ew, ey + eh), ew, ew, paint);
        c.drawRoundRect(new RectF(x + off - ew, ey - eh, x + off + ew, ey + eh), ew, ew, paint);
    }

    private int pctColor(float p) {
        if (p >= 0.9f) return 0xFFE5484D;
        if (p >= 0.7f) return 0xFFE9B949;
        return 0xFF43B581;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ── 触摸 ─────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (open > 0.02f && open < 0.98f) return true;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                moved = false;
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                lastRawX = downRawX;
                lastRawY = downRawY;
                downY = e.getRawY();
                armed = nearCapsule(e.getRawX(), e.getRawY());
                pressed = expanded ? iconAt(e.getRawX(), e.getRawY()) : -1;
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (!armed) return true;
                if (!expanded) {                      // 竖线状态不能拖，动了就不算点
                    if (Math.hypot(e.getRawX() - downRawX, e.getRawY() - downRawY) > 12 * dp) moved = true;
                    return true;
                }
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                if (!dragging && Math.hypot(dx, dy) > 8 * dp) {
                    dragging = true;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                if (dragging && listener != null) {
                    path.eval(s, ev);
                    float tx = (float) Math.cos(ev[2]);
                    float ty = (float) Math.sin(ev[2]);
                    float ds = (e.getRawX() - lastRawX) * tx + (e.getRawY() - lastRawY) * ty;
                    listener.onSlide(ds);
                    lastRawX = e.getRawX();
                    lastRawY = e.getRawY();
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (!armed) {
                    armed = false;
                    return true;
                }
                if (dragging) {
                    dragging = false;
                    if (listener != null) listener.onDragEnd(0);
                    pressed = -1;
                    return true;
                }
                if (!expanded) {
                    if (!moved && listener != null) listener.onExpand();
                } else {
                    int at = iconAt(e.getRawX(), e.getRawY());
                    if (listener != null) {
                        if (at >= 0) listener.onPick(at);
                        else listener.onCollapse();
                    }
                }
                pressed = -1;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                pressed = -1;
                return true;
            default:
                return true;
        }
    }

    /** 手指是不是落在胶囊上：展开按灯带 30dp + 4dp 余量，收起按那条线 18dp */
    private boolean nearCapsule(float rawX, float rawY) {
        float touchS = path.nearestS(rawX, rawY);
        // 先看沿路径方向有没有超出胶囊两端，再看离灯带多远——不是整条边框都算
        float half = expanded ? capsuleSpan() / 2f : 30 * dp;
        if (Math.abs(path.delta(s, touchS)) > half + 10 * dp) return false;
        path.eval(touchS, ev);
        float dx = ev[0] - rawX, dy = ev[1] - rawY;
        return Math.hypot(dx, dy) <= (expanded ? 34 : 18) * dp;
    }

    /** 触摸点对应哪个图标；不在图标上返回 -1 */
    private int iconAt(float rawX, float rawY) {
        float touchS = path.nearestS(rawX, rawY);
        path.eval(touchS, ev);
        float dx = ev[0] - rawX, dy = ev[1] - rawY;
        if (Math.hypot(dx, dy) > 34 * dp) return -1;      // 离胶囊太远算空白
        int n = agents.size();
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float si = s + (i - (n - 1) / 2f) * step();
            float d = Math.abs(path.delta(si, touchS));
            if (d < bestD) { bestD = d; best = i; }
        }
        return bestD <= step() * 0.55f ? best : -1;
    }
}
