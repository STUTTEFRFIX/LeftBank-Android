package com.dafeiyu.hudleft;

/** 贴着屏幕边框的一圈圆角矩形路径：8 段（4 直 4 弧），位置用弧长表示。 */
final class FramePath {

    final float left, top, right, bottom, r;
    final float len;
    private final float[] start = new float[8];
    private final float[] segLen = new float[8];

    FramePath(int screenW, int screenH, float insetL, float insetT, float insetR, float insetB, float radius) {
        left = insetL;
        top = insetT;
        right = screenW - insetR;
        bottom = screenH - insetB;
        r = radius;
        float W = right - left;
        float H = bottom - top;
        float arc = (float) (Math.PI * r / 2);
        segLen[0] = W - 2 * r;   // 上
        segLen[1] = arc;         // 右上
        segLen[2] = H - 2 * r;   // 右
        segLen[3] = arc;         // 右下
        segLen[4] = W - 2 * r;   // 下
        segLen[5] = arc;         // 左下
        segLen[6] = H - 2 * r;   // 左
        segLen[7] = arc;         // 左上
        float s = 0;
        for (int i = 0; i < 8; i++) {
            start[i] = s;
            s += segLen[i];
        }
        len = Math.max(1, s);
    }

    private float wrap(float s) {
        s %= len;
        return s < 0 ? s + len : s;
    }

    /** out[0]=x, out[1]=y, out[2]=切线角度（弧度，顺时针） */
    void eval(float sIn, float[] out) {
        float s = wrap(sIn);
        int i = 0;
        while (i < 7 && s >= start[i] + segLen[i]) i++;
        float d = s - start[i];
        switch (i) {
            case 0:
                out[0] = left + r + d; out[1] = top; out[2] = 0;
                return;
            case 1: {
                float a = (float) (-Math.PI / 2 + d / r);
                out[0] = (right - r) + r * (float) Math.cos(a);
                out[1] = (top + r) + r * (float) Math.sin(a);
                out[2] = a + (float) Math.PI / 2;
                return;
            }
            case 2:
                out[0] = right; out[1] = top + r + d; out[2] = (float) Math.PI / 2;
                return;
            case 3: {
                float a = (float) (d / r);
                out[0] = (right - r) + r * (float) Math.cos(a);
                out[1] = (bottom - r) + r * (float) Math.sin(a);
                out[2] = a + (float) Math.PI / 2;
                return;
            }
            case 4:
                out[0] = right - r - d; out[1] = bottom; out[2] = (float) Math.PI;
                return;
            case 5: {
                float a = (float) (Math.PI / 2 + d / r);
                out[0] = (left + r) + r * (float) Math.cos(a);
                out[1] = (bottom - r) + r * (float) Math.sin(a);
                out[2] = a + (float) Math.PI / 2;
                return;
            }
            case 6:
                out[0] = left; out[1] = bottom - r - d; out[2] = (float) (-Math.PI / 2);
                return;
            default: {
                float a = (float) (Math.PI + d / r);
                out[0] = (left + r) + r * (float) Math.cos(a);
                out[1] = (top + r) + r * (float) Math.sin(a);
                out[2] = a + (float) Math.PI / 2;
            }
        }
    }

    /** 离 (x,y) 最近的路径弧长：粗采样 + 局部细化 */
    float nearestS(float x, float y) {
        float[] p = new float[3];
        int n = 512;
        float best = 0, bd = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float s = len * i / n;
            eval(s, p);
            float dx = p[0] - x, dy = p[1] - y;
            float d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = s; }
        }
        float step = len / n;
        for (int k = -8; k <= 8; k++) {
            float s = wrap(best + step * k / 8f);
            eval(s, p);
            float dx = p[0] - x, dy = p[1] - y;
            float d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    /** 左边中点的弧长（默认停靠位） */
    float leftMiddleS() {
        return start[6] + segLen[6] / 2f;
    }

    /** 沿路径从 s 到 s2 的带符号短距离（用于比较位置） */
    float delta(float from, float to) {
        float d = wrap(to) - wrap(from);
        if (d > len / 2) d -= len;
        if (d < -len / 2) d += len;
        return d;
    }
}
