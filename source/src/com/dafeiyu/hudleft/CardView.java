package com.dafeiyu.hudleft;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/** 点开图标后伸出来的卡片：额度、今天的 token、待处理的请求。测试版用假数据。 */
final class CardView extends View {

    interface Listener {
        void onClose();

        void onAnswer(int index);
    }

    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Bitmap> icons = new HashMap<>();
    private final RectF btnAllow = new RectF();
    private final RectF btnDeny = new RectF();
    private final RectF btnClose = new RectF();

    private Agent agent;
    private Listener listener;
    private float reveal = 1f;
    private ValueAnimator anim;

    CardView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        paint.setFilterBitmap(true);
    }

    void setAgent(Agent a) {
        agent = a;
        requestLayout();
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** 从左边「伸出来」：淡入 + 轻微回弹 */
    void show() {
        if (anim != null) anim.cancel();
        reveal = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(240);
        anim.setInterpolator(new OvershootInterpolator(0.7f));
        anim.addUpdateListener(a -> {
            reveal = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    void hide(Runnable end) {
        if (anim != null) anim.cancel();
        final float from = Math.min(1f, reveal);
        anim = ValueAnimator.ofFloat(from, 0f);
        anim.setDuration(140);
        anim.setInterpolator(new AccelerateInterpolator());
        anim.addUpdateListener(a -> {
            reveal = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (end != null) end.run();
            }
        });
        anim.start();
    }

    private int w() { return Math.round(218 * dp); }
    private int h() { return Math.round((agent != null && agent.request != null ? 232 : 152) * dp); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(w(), h());
    }

    @Override
    protected void onDraw(Canvas c) {
        if (agent == null) return;
        float w = w();
        float h = h();
        float rv = Math.max(0f, Math.min(1f, reveal));
        int layer = c.saveLayerAlpha(0, 0, w, h, (int) (255 * rv));
        c.translate(-(1 - rv) * 10 * dp, 0);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF21A1918);
        c.drawRoundRect(new RectF(0, 0, w, h), 20 * dp, 20 * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp);
        paint.setColor(0x1FFFFFFF);
        c.drawRoundRect(new RectF(dp / 2, dp / 2, w - dp / 2, h - dp / 2), 20 * dp, 20 * dp, paint);

        // 关闭
        btnClose.set(w - 42 * dp, 8 * dp, w - 8 * dp, 42 * dp);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x14FFFFFF);
        c.drawCircle(w - 21 * dp, 21 * dp, 10 * dp, paint);
        paint.setColor(0xFFCFC9C2);
        paint.setTextSize(14 * dp);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        c.drawText("×", w - 21 * dp, 25f * dp, paint);

        // 头部
        float ix = 28 * dp;
        float iy = 30 * dp;
        float ir = 12 * dp;
        drawIcon(c, agent, ix, iy, ir);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(14.5f * dp);
        paint.setColor(0xFFF2EEE8);
        c.drawText(agent.name, ix + 18 * dp, iy + 2 * dp, paint);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(10 * dp);
        paint.setColor(agent.working ? 0xFFE9B949 : 0xFF9C948B);
        c.drawText(agent.status, ix + 18 * dp, iy + 15 * dp, paint);

        // 真实数据三行
        textRow(c, "余额", agent.balance, 62 * dp, w);
        textRow(c, "今日", fmtTokens(agent.todayTokens), 88 * dp, w);
        textRow(c, "模型", agent.model, 114 * dp, w);

        // 待处理请求
        if (agent.request != null) {
            float ry = 136 * dp;
            RectF box = new RectF(14 * dp, ry, w - 14 * dp, h - 14 * dp);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x14E9B949);
            c.drawRoundRect(box, 12 * dp, 12 * dp, paint);

            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setColor(0xFFE9B949);
            paint.setTextSize(11 * dp);
            c.drawText("等你确认", 22 * dp, ry + 18 * dp, paint);

            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(0xFFD8D2CA);
            paint.setTextSize(11 * dp);
            c.drawText(ellipsis(agent.request, 24), 22 * dp, ry + 32 * dp, paint);

            btnAllow.set(22 * dp, h - 42 * dp, 22 * dp + 82 * dp, h - 14 * dp);
            btnDeny.set(22 * dp + 90 * dp, h - 42 * dp, 22 * dp + 172 * dp, h - 14 * dp);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFE9B949);
            c.drawRoundRect(btnAllow, 10 * dp, 10 * dp, paint);
            paint.setColor(0xFF14100E);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(12 * dp);
            String a = agent.question ? agent.optA : "允许";
            int sa = c.save();
            c.clipRect(btnAllow);
            c.drawText(fit(a, btnAllow.width() - 14 * dp), btnAllow.centerX(), btnAllow.centerY() + 4 * dp, paint);
            c.restoreToCount(sa);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp);
            paint.setColor(0x33FFFFFF);
            c.drawRoundRect(btnDeny, 10 * dp, 10 * dp, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFCFC9C2);
            paint.setTypeface(Typeface.DEFAULT);
            String b = agent.question ? agent.optB : "拒绝";
            int sb = c.save();
            c.clipRect(btnDeny);
            c.drawText(fit(b, btnDeny.width() - 14 * dp), btnDeny.centerX(), btnDeny.centerY() + 4 * dp, paint);
            c.restoreToCount(sb);
        }
        c.restoreToCount(layer);
    }

    private void textRow(Canvas c, String label, String value, float y, float w) {
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(10.5f * dp);
        paint.setColor(0xFF9C948B);
        c.drawText(label, 18 * dp, y, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(0xFFF2EEE8);
        c.drawText(value, w - 18 * dp, y, paint);
    }

    private void row(Canvas c, String label, float pct, float y, float w) {
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(10.5f * dp);
        paint.setColor(0xFF9C948B);
        c.drawText(label, 18 * dp, y, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(0xFFF2EEE8);
        c.drawText(Math.round(pct * 100) + "%", w - 18 * dp, y, paint);

        RectF bar = new RectF(18 * dp, y + 7 * dp, w - 18 * dp, y + 12 * dp);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0x1FFFFFFF);
        c.drawRoundRect(bar, 3 * dp, 3 * dp, paint);
        RectF fill = new RectF(bar.left, bar.top, bar.left + bar.width() * pct, bar.bottom);
        paint.setColor(pctColor(pct));
        c.drawRoundRect(fill, 3 * dp, 3 * dp, paint);
    }

    private void drawIcon(Canvas c, Agent a, float x, float y, float r) {
        Bitmap b = icons.get(a.iconRes);
        if (b == null) {
            b = BitmapFactory.decodeResource(getResources(), a.iconRes);
            icons.put(a.iconRes, b);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(StripView.withAlpha(a.color, 70));
        c.drawCircle(x, y, r, paint);
        if (b == null) return;
        int save = c.save();
        Path clip = new Path();
        clip.addCircle(x, y, r - dp, Path.Direction.CW);
        c.clipPath(clip);
        float s = r * 0.84f;
        c.drawBitmap(b, null, new RectF(x - s, y - s, x + s, y + s), paint);
        c.restoreToCount(save);
    }

    private int pctColor(float p) {
        if (p >= 0.9f) return 0xFFE5484D;
        if (p >= 0.7f) return 0xFFE9B949;
        return 0xFF43B581;
    }

    private static String fmtTokens(long n) {
        if (n >= 1_000_000) return String.format("%.2fM tokens", n / 1_000_000f);
        if (n >= 1_000) return String.format("%.1fk tokens", n / 1_000f);
        return n + " tokens";
    }

    /** 按当前画笔宽度把文字缩到框里，超出就截断加省略号 */
    private String fit(String s, float maxW) {
        if (paint.measureText(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && paint.measureText(t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static String ellipsis(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && listener != null) {
            float x = e.getX();
            float y = e.getY();
            if (btnClose.contains(x, y)) {
                listener.onClose();
                return true;
            }
            if (agent != null && agent.request != null) {
                if (btnAllow.contains(x, y)) {
                    listener.onAnswer(0);
                    return true;
                }
                if (btnDeny.contains(x, y)) {
                    listener.onAnswer(1);
                    return true;
                }
            }
        }
        return true;
    }
}
