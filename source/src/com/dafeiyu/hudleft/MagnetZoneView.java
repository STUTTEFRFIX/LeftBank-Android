package com.dafeiyu.hudleft;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** 把「磁吸范围」画出来：左边一条渐隐的光带，边界上一条线和范围文字。 */
final class MagnetZoneView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private int rangeDp = 80;

    MagnetZoneView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
    }

    void setRangeDp(int v) {
        rangeDp = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = rangeDp * dp;
        if (w <= 1) return;
        float h = getHeight();

        paint.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0x4DE9B949, 0x14E9B949, 0x00E9B949},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * dp);
        paint.setColor(0x99E9B949);
        c.drawLine(w, 0, w, h, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDDE9B949);
        paint.setTextSize(11 * dp);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText("磁吸 " + rangeDp + "dp", w + 6 * dp, 180 * dp, paint);
    }
}
