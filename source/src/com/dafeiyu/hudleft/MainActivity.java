package com.dafeiyu.hudleft;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 测试版的入口：授权、开启面板、看说明。 */
public final class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Config.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        root.setPadding(p, dp(40), p, p);
        root.setBackgroundColor(0xFF14100E);

        TextView title = new TextView(this);
        title.setText("左岸");
        title.setTextSize(30);
        title.setTextColor(0xFFF2EEE8);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Agent HUD · 测试版（假数据）");
        sub.setTextSize(13);
        sub.setTextColor(0xFF9C948B);
        sub.setPadding(0, dp(4), 0, dp(20));
        root.addView(sub);

        TextView desc = new TextView(this);
        desc.setText("贴着屏幕左边的一条面板：每个 agent 一个图标、一圈额度、一个百分比。\n"
                + "· 干活时图标变成 Bot，还会呼吸发光\n"
                + "· 点图标展开卡片：额度、今天的 token、待处理的请求\n"
                + "· 卡片里可以「允许 / 拒绝」，点了就收起\n\n"
                + "现在还没有接真数据——先看位置和手感。");
        desc.setTextSize(14);
        desc.setTextColor(0xFFCFC9C2);
        desc.setLineSpacing(dp(6), 1f);
        root.addView(desc);

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(20), 0, dp(12));
        root.addView(status);

        root.addView(button("显示左岸面板", v -> ensureOverlay()));
        root.addView(button("隐藏面板", v -> {
            Intent i = new Intent(this, HudService.class);
            i.setAction(HudService.ACTION_HIDE);
            startService(i);
        }));
        root.addView(button("打开悬浮窗设置", v -> openOverlaySettings()));

        TextView magnetLabel = new TextView(this);
        magnetLabel.setText("磁吸范围：" + Config.magnetDp + "dp");
        magnetLabel.setTextSize(13);
        magnetLabel.setTextColor(0xFFCFC9C2);
        magnetLabel.setPadding(0, dp(18), 0, dp(4));

        SeekBar magnet = new SeekBar(this);
        magnet.setMax(200);
        magnet.setProgress(Config.magnetDp);
        magnet.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int v, boolean fromUser) {
                magnetLabel.setText("磁吸范围：" + v + "dp");
                if (fromUser) Config.save(MainActivity.this, v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                Intent i = new Intent(MainActivity.this, HudService.class);
                i.setAction(HudService.ACTION_PREVIEW);
                startService(i);
            }
        });
        root.addView(magnetLabel);
        root.addView(magnet);

        // ── 红色闪烁开关与速度 ──
        final Switch flash = new Switch(this);
        flash.setText("红色闪烁（有待回答的请求时）");
        flash.setTextSize(13);
        flash.setTextColor(0xFFCFC9C2);
        flash.setChecked(Config.flashOn);
        flash.setPadding(0, dp(18), 0, 0);

        final TextView flashLabel = new TextView(this);
        flashLabel.setTextSize(13);
        flashLabel.setTextColor(0xFFCFC9C2);
        flashLabel.setPadding(0, dp(12), 0, dp(4));

        final SeekBar speed = new SeekBar(this);
        speed.setMax(2600);
        speed.setProgress(Math.max(0, Math.min(2600, Config.flashMs - 400)));
        speed.setEnabled(Config.flashOn);
        flashLabel.setText("闪烁速度：" + (400 + speed.getProgress()) + "ms");

        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int v, boolean fromUser) {
                flashLabel.setText("闪烁速度：" + (400 + v) + "ms");
                if (fromUser) Config.saveFlash(MainActivity.this, flash.isChecked(), 400 + v);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        flash.setOnCheckedChangeListener((btn, checked) -> {
            Config.saveFlash(MainActivity.this, checked, 400 + speed.getProgress());
            speed.setEnabled(checked);
        });

        root.addView(flash);
        root.addView(flashLabel);
        root.addView(speed);

        // ── 预览：竖线 与 展开后的红辉光 ──
        TextView previewLabel = new TextView(this);
        previewLabel.setText("预览");
        previewLabel.setTextSize(13);
        previewLabel.setTextColor(0xFF9C948B);
        previewLabel.setPadding(0, dp(18), 0, dp(8));
        root.addView(previewLabel);

        List<Agent> demo = new ArrayList<>();
        Agent demoAgent = new Agent("Claude", 0xFFD97757, R.drawable.model_claude, 0.62f, 0.41f, 1_240_000);
        demoAgent.request = "读取 driver.mjs（只读）";
        demoAgent.status = "等你确认";
        demo.add(demoAgent);

        LinearLayout previews = new LinearLayout(this);
        previews.setOrientation(LinearLayout.HORIZONTAL);
        StripView linePrev = new StripView(this, demo);
        linePrev.previewCollapsed();
        StripView iconPrev = new StripView(this, demo);
        iconPrev.previewExpanded();
        previews.addView(previewBox(linePrev), boxParams(dp(70)));
        previews.addView(previewBox(iconPrev), boxParams(dp(70)));
        root.addView(previews);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF14100E);
        scroll.addView(root);
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        if (Settings.canDrawOverlays(this)) startHud();
        updateStatus();
    }

    /** 把小小的预览放大 1.8 倍，放进一个不裁剪的框里 */
    private FrameLayout previewBox(StripView v) {
        FrameLayout box = new FrameLayout(this);
        box.setClipChildren(false);
        v.setPivotX(0);
        v.setPivotY(0);
        v.setScaleX(1.8f);
        v.setScaleY(1.8f);
        box.addView(v, new FrameLayout.LayoutParams(dp(40), dp(170)));
        return box;
    }

    private LinearLayout.LayoutParams boxParams(int widthPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, dp(190));
        lp.rightMargin = dp(16);
        return lp;
    }

    private Button button(String text, android.view.View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(onClick);
        return b;
    }

    private void ensureOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        startHud();
        updateStatus();
    }

    private void startHud() {
        Intent i = new Intent(this, HudService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void openOverlaySettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void updateStatus() {
        if (Settings.canDrawOverlays(this)) {
            status.setText("悬浮窗权限：已授权");
            status.setTextColor(0xFF43B581);
        } else {
            status.setText("悬浮窗权限：未授权 · 点「显示左岸面板」会打开设置");
            status.setTextColor(0xFFE9B949);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
