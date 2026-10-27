package com.dafeiyu.hudleft;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 灯带版：条子只活在屏幕边框上，沿整圈路径滑动，拐角自动弯折。 */
public final class HudService extends Service implements FrameHudView.Listener {

    static final String ACTION_HIDE = "com.dafeiyu.hudleft.HIDE";
    static final String ACTION_PREVIEW = "com.dafeiyu.hudleft.PREVIEW";

    private static final String CHANNEL = "leftbank";
    private static final int NOTIFICATION_ID = 41;

    private final List<Agent> agents = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float[] ev = new float[3];

    private WindowManager wm;
    private FrameHudView hud;
    private WindowManager.LayoutParams hudLp;
    private MonitorWindow panel;
    private WindowManager.LayoutParams panelLp;
    private FramePath path;
    private float s;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Config.load(this);
        writePanelSwitch("1");   // 告诉看门狗：面板该活着
        seedAgents();
        startInForeground();

        // 底边不能只算 navigationBars：沉浸模式下它返回 0，底部手势区却有 32dp。
        // 只用它，胶囊就会滑进上滑手势区（用户看到的「贴到底部有重叠」）。
        int top = dp(6), bottom = dp(6), side = dp(6);
        try {
            android.view.WindowInsets wi = ((WindowManager) getSystemService(WINDOW_SERVICE))
                    .getCurrentWindowMetrics().getWindowInsets();
            android.graphics.Insets bars;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                bars = wi.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
            } else {
                bars = wi.getInsets(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
            }
            int gestureBottom = wi.getInsets(
                    android.view.WindowInsets.Type.mandatorySystemGestures()).bottom;
            top = bars.top + dp(6);
            bottom = Math.max(bars.bottom, gestureBottom) + dp(6);
        } catch (Throwable ignored) {}
        path = new FramePath(screenW(), screenH(), side, top, side, bottom, dp(40));
        s = path.leftMiddleS();
        hud = new FrameHudView(this, path, agents);
        hud.setListener(this);
        hud.setS(s);
        hudLp = overlayParams();
        hudLp.gravity = Gravity.TOP | Gravity.START;
        layoutHud();
        wm.addView(hud, hudLp);
        hud.setOrigin(hudLp.x, hudLp.y);

        ui.postDelayed(tick, 2000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) {
            writePanelSwitch("0");   // 用户主动收起：看门狗别拉
            stopSelf();
            return START_NOT_STICKY;
        }
        // 调试/真机验证入口：把胶囊放到指定屏幕坐标并打开对话窗
        if (intent != null && "com.dafeiyu.hudleft.TEST".equals(intent.getAction())) {
            float tx = intent.getFloatExtra("x", Float.NaN);
            float ty = intent.getFloatExtra("y", Float.NaN);
            if (!Float.isNaN(tx) && !Float.isNaN(ty)) {
                s = path.nearestS(tx, ty);
                hud.setS(s);
                updateHudWindow();
                placePanel();
                path.eval(s, ev);
            }
            if (intent.getBooleanExtra("expand", false)) onExpand();
            if (intent.getBooleanExtra("collapse", false)) onCollapse();
            if (intent.getBooleanExtra("chat", false)) showPanel();
            if (intent.getBooleanExtra("closechat", false)) hidePanel();
            if (intent.getBooleanExtra("demo", false)) {
                Agent a = agents.get(0);
                a.request = "Bash";
                a.question = true;
                a.qText = "这是一道测试题：选项应该排得下，也不压到胶囊。";
                a.options.clear();
                a.options.add("面板 A");
                a.options.add("面板 B");
                if (panel != null) panel.setAgent(a);
            }
            return START_STICKY;
        }
        return START_STICKY;
    }

    private void seedAgents() {
        Agent claude = new Agent("Claude", 0xFFD97757, R.drawable.model_claude, 0f, 0f, 0);
        claude.status = "连接网关…";
        claude.label = "…";
        agents.add(claude);
    }

    private String permId;
    private String permRunId;

    private void applyQuestion(boolean isQ, String q, java.util.List<String> opts) {
        if (agents.isEmpty()) return;
        Agent ag = agents.get(0);
        ag.question = isQ;
        ag.qText = q;
        ag.options.clear();
        ag.options.addAll(opts);
        if (isQ) ag.request = q;
        if (panel != null) panel.setAgent(ag);
    }

    /** 模型的图标、名字、颜色跟真实 provider 走 */
    private void applyProvider(String provider) {
        if (agents.isEmpty()) return;
        Agent a = agents.get(0);
        if ("deepseek".equalsIgnoreCase(provider)) {
            a.name = "DeepSeek";
            a.color = 0xFF4D6BFE;
            a.iconRes = R.drawable.model_deepseek;
        } else if ("anthropic".equalsIgnoreCase(provider) || "claude".equalsIgnoreCase(provider)) {
            a.name = "Claude";
            a.color = 0xFFD97757;
            a.iconRes = R.drawable.model_claude;
        } else if ("openai".equalsIgnoreCase(provider)) {
            a.name = "ChatGPT";
            a.color = 0xFF10A37F;
            a.iconRes = R.drawable.model_chatgpt;
        }
        if (hud != null) hud.invalidate();
        if (panel != null) panel.setAgent(a);
    }

    private void applyInfo(String balance, String model, long today) {
        if (agents.isEmpty()) return;
        Agent a = agents.get(0);
        a.balance = balance;
        a.model = model;
        a.todayTokens = today;
        if (panel != null) panel.setAgent(a);
    }

    private void applyState(boolean working, String text, String pid, String rid, String tool) {
        if (agents.isEmpty()) return;
        Agent a = agents.get(0);
        a.working = working;
        a.work = text == null ? "" : text;
        permId = pid == null || pid.isEmpty() ? null : pid;
        permRunId = rid == null || rid.isEmpty() ? null : rid;
        boolean pending = permId != null;
        a.request = pending ? tool : null;
        a.label = pending ? "等你" : (working ? "干活" : "空闲");
        a.status = pending ? ("等你确认 · " + tool) : (working ? "正在干活" : "空闲");
        if (hud != null) hud.invalidate();
        if (panel != null) panel.setAgent(a);
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(getString(R.string.channel_desc));
        nm.createNotificationChannel(ch);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("左岸面板正在显示")
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, n);
    }

    private WindowManager.LayoutParams overlayParams() {
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
    }

    /** 写面板开关，看门狗在 cccodroid 那边读 */
    private void writePanelSwitch(String v) {
        try {
            java.io.File d = getExternalMediaDirs()[0];
            if (d == null) return;
            d.mkdirs();
            java.io.FileOutputStream fo = new java.io.FileOutputStream(new java.io.File(d, "panel.conf"));
            fo.write(v.getBytes());
            fo.close();
        } catch (Exception ignored) {
        }
    }

    private int screenW() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int screenH() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 根据当前状态给窗口定尺寸和位置：收起时一条窄框，展开时 220dp 的方块 */
    private void layoutHud() {
        path.eval(s, ev);
        int px = Math.round(ev[0]);
        int py = Math.round(ev[1]);
        boolean exp = hud != null && hud.isExpanded();
        int w, h, x, y;
        if (exp) {
            w = h = dp(140);
            x = Math.max(0, Math.min(screenW() - w, px - w / 2));
            y = Math.max(0, Math.min(screenH() - h, py - h / 2));
        } else {
            // 收起时按整条线（含拐角弯折）的实际包围盒给窗口
            float half = dp(30);
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int k = 0; k <= 12; k++) {
                path.eval(s - half + (2 * half) * k / 12f, ev);
                minX = Math.min(minX, ev[0]);
                maxX = Math.max(maxX, ev[0]);
                minY = Math.min(minY, ev[1]);
                maxY = Math.max(maxY, ev[1]);
            }
            int m = dp(10);
            w = Math.round(maxX - minX) + 2 * m;
            h = Math.round(maxY - minY) + 2 * m;
            x = Math.round(minX) - m;
            y = Math.round(minY) - m;
            x = Math.max(0, Math.min(screenW() - w, x));
            y = Math.max(0, Math.min(screenH() - h, y));
        }
        hudLp.width = w;
        hudLp.height = h;
        hudLp.x = x;
        hudLp.y = y;
    }

    private void updateHudWindow() {
        layoutHud();
        wm.updateViewLayout(hud, hudLp);
        hud.setOrigin(hudLp.x, hudLp.y);
    }

    // ── FrameHudView 回调 ──────────────────────────────

    @Override
    public void onExpand() {
        hud.setExpanded(true);
        updateHudWindow();
    }

    @Override
    public void onCollapse() {
        hidePanel();
        hud.setExpanded(false);
        ui.postDelayed(this::updateHudWindow, 220);   // 等收起动画走完再缩窗口
    }

    @Override
    public void onPick(int index) {
        if (!hud.isExpanded()) return;
        if (panel != null) hidePanel();
        else showPanel();
    }

    /** 打开监察窗：看状态/工作，有请求时在这里作答 */
    private void showPanel() {
        if (panel != null) return;
        panel = new MonitorWindow(this);
        panel.setHost(new MonitorWindow.Host() {
            @Override
            public void onAnswer(int index) {
                HudService.this.onAnswer(index);
            }

            @Override
            public void onClose() {
                hidePanel();
            }
        });
        panelLp = new WindowManager.LayoutParams(dp(248), dp(330),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        panelLp.x = 0;
        panelLp.y = 0;
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panel.setAgent(agents.get(0));
        wm.addView(panel, panelLp);
        placePanel();            // 打开时就先按胶囊位置放好，别停在 0,0 压住胶囊
        applyGrowPivot();        // 借 agent-hud-open 的做法：从胶囊那一侧长出来
        panel.setAlpha(0f);
        panel.setScaleX(0.86f);
        panel.setScaleY(0.86f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    /** 缩放支点放在离胶囊最近的边/角上：展开像从胶囊后面长出来，收起缩回去 */
    private void applyGrowPivot() {
        if (panel == null || panelLp == null) return;
        path.eval(s, ev);
        float nx = -(float) Math.sin(ev[2]);
        float ny = (float) Math.cos(ev[2]);
        panel.setPivotX(Math.abs(nx) < 0.2f ? panelLp.width / 2f
                : (nx > 0 ? 0f : panelLp.width));
        panel.setPivotY(Math.abs(ny) < 0.2f ? panelLp.height / 2f
                : (ny > 0 ? 0f : panelLp.height));
    }

    /**
     * 监察窗绕着胶囊走，像月亮绕着地球：沿胶囊朝屏幕内侧的法线，
     * 与胶囊灯带保持固定 14dp；胶囊滑到哪个边、哪个角，窗就绕到哪，不会重叠。
     */
    private void placePanel() {
        if (panel == null || panelLp == null) return;
        int cw = panelLp.width, chh = panelLp.height;
        path.eval(s, ev);
        float nx = -(float) Math.sin(ev[2]);   // 指向屏幕内侧的单位法线
        float ny = (float) Math.cos(ev[2]);
        float capHalf = dp(30);                // 胶囊灯带从中心线往里 30dp
        float gap = dp(14);                    // 地球—月亮的固定距离
        float capX = ev[0], capY = ev[1];
        // 屏幕轴对齐矩形沿法线方向的支撑半径：直边上最近的那条边/角正好隔 gap
        float support = (cw / 2f) * Math.abs(nx) + (chh / 2f) * Math.abs(ny);
        float dist = capHalf + gap + support;
        int x = Math.round(capX + nx * dist - cw / 2f);
        int y = Math.round(capY + ny * dist - chh / 2f);
        // 拐角处灯带是弯的，支撑半径只保证法线方向；再用灯带采样做一次碰撞，
        // 不够就顺着「从胶囊指向面板」的方向退开。直边 14dp 不动，角上自动让路。
        float need = dp(15) + gap;             // 灯带中心线偏移 15dp、半径 15dp
        float span = hud != null ? hud.capsuleSpan() : dp(68);
        for (int iter = 0; iter < 8; iter++) {
            float minD = Float.MAX_VALUE;
            for (int k = 0; k <= 24; k++) {
                path.eval(s - span / 2f + span * k / 24f, ev);
                float px = ev[0] - (float) Math.sin(ev[2]) * dp(15);
                float py = ev[1] + (float) Math.cos(ev[2]) * dp(15);
                minD = Math.min(minD, distToRect(px, py, x, y, cw, chh));
            }
            if (minD >= need) break;
            float dx = (x + cw / 2f) - capX;
            float dy = (y + chh / 2f) - capY;
            float len = (float) Math.hypot(dx, dy);
            if (len < 1f) {
                dx = nx;
                dy = ny;
                len = 1f;
            }
            float push = need - minD + dp(2);
            x += Math.round(dx / len * push);
            y += Math.round(dy / len * push);
        }

        // 只有屏幕实在放不下才收回
        int leftLimit = Math.round(path.left) + dp(6);
        int rightLimit = Math.round(path.right) - dp(6);
        int topLimit = Math.round(path.top) + dp(6);
        int bottomLimit = Math.round(path.bottom) - dp(6);
        x = Math.max(leftLimit, Math.min(rightLimit - cw, x));
        y = Math.max(topLimit, Math.min(bottomLimit - chh, y));
        if (x == panelLp.x && y == panelLp.y) return;
        panelLp.x = x;
        panelLp.y = y;
        wm.updateViewLayout(panel, panelLp);
    }

    /** 点到矩形的最短距离；点在矩形内为 0。 */
    private static float distToRect(float px, float py, int x, int y, int w, int h) {
        float dx = Math.max(Math.max(x - px, 0), px - (x + w));
        float dy = Math.max(Math.max(y - py, 0), py - (y + h));
        return (float) Math.hypot(dx, dy);
    }

    private void hidePanel() {
        final MonitorWindow p = panel;
        if (p == null) return;
        applyGrowPivot();
        panel = null;
        p.animate().alpha(0f).scaleX(0.86f).scaleY(0.86f).setDuration(150)
                .withEndAction(() -> {
                    try { wm.removeView(p); } catch (Exception ignored) {}
                }).start();
    }

    @Override
    public void onSlide(float ds) {
        s = ((s + ds) % path.len + path.len) % path.len;
        hud.setS(s);
        updateHudWindow();
        placePanel();
    }

    @Override
    public void onDragEnd(float velocity) {
        updateHudWindow();
    }

    /** 监察窗上的作答：选择题回选项，权限回允许/拒绝/始终允许 */
    void onAnswer(int index) {
        Agent agent = agents.isEmpty() ? null : agents.get(0);
        if (agent == null) return;
        final String id = permId, rid = permRunId;
        final boolean q = agent.question;
        final String qText = agent.qText;
        final String choice = index >= 0 && index < agent.options.size()
                ? agent.options.get(index) : "";
        final boolean always = !q && index == 2;
        if (id != null) {
            new Thread(() -> {
                if (q) Gateway.answerQuestion(id, rid, qText, choice);
                else Gateway.answer(id, rid, index == 0, always);
            }).start();
        }
        permId = null;
        agent.request = null;
        agent.options.clear();
        agent.status = q ? ("已选择 · " + choice)
                : (index == 0 ? "已允许 · 继续" : always ? "始终允许" : "已拒绝");
        agent.label = "…";
        if (hud != null) hud.invalidate();
        if (panel != null) panel.setAgent(agent);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                boolean working = false;
                String text = "", pid = "", rid = "", tool = "";
                String balance = "--", model = "", provider = "";
                boolean isQuestion = false;
                String qText = "";
                java.util.List<String> opts = new ArrayList<>();
                long today = 0;
                try {
                    try {
                        JSONObject b = Gateway.get("/api/billing");
                        balance = String.format("¥%.2f", b.optDouble("balance", 0));
                    } catch (Exception ignored) {
                    }
                    try {
                        JSONObject u = Gateway.get("/api/usage");
                        JSONObject hours = u.optJSONObject("hours");
                        long nowH = System.currentTimeMillis() / 3600000L;
                        if (hours != null) {
                            java.util.Iterator<String> it = hours.keys();
                            while (it.hasNext()) {
                                String k = it.next();
                                long hh;
                                try { hh = Long.parseLong(k); } catch (Exception e) { continue; }
                                if (hh >= nowH - 24) {
                                    JSONObject o = hours.optJSONObject(k);
                                    today += o.optLong("i") + o.optLong("o");
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    JSONObject st = Gateway.get("/api/state");
                    model = st.optString("model", "");
                    provider = st.optString("provider", "");
                    JSONArray runs = st.optJSONArray("runs");
                    working = runs != null && runs.length() > 0;
                    JSONObject run = working ? runs.optJSONObject(0) : null;
                    if (run != null) {
                        text = run.optString("text", "");
                        rid = run.optString("id", "");
                        JSONArray perms = run.optJSONArray("perms");
                        JSONObject perm = perms != null && perms.length() > 0 ? perms.optJSONObject(0) : null;
                        if (perm != null) {
                            pid = perm.optString("id", "");
                            tool = perm.optString("tool", "需要确认");
                            JSONObject input = perm.optJSONObject("input");
                            isQuestion = "AskUserQuestion".equals(tool);
                            qText = tool;
                            if (isQuestion) {
                                if (input != null) {
                                    JSONArray qs = input.optJSONArray("questions");
                                    if (qs != null && qs.length() > 0) {
                                        JSONObject q0 = qs.optJSONObject(0);
                                        qText = q0.optString("question", "请选择");
                                        JSONArray qopts = q0.optJSONArray("options");
                                        if (qopts != null) {
                                            for (int k = 0; k < qopts.length() && k < 4; k++) {
                                                JSONObject o = qopts.optJSONObject(k);
                                                if (o != null)
                                                    opts.add(o.optString("label", "选项 " + (k + 1)));
                                            }
                                        }
                                    }
                                }
                            } else {
                                opts.add("允许");
                                opts.add("拒绝");
                                opts.add("始终允许");
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                final boolean w = working;
                final String textF = text;
                final String pidF = pid, ridF = rid, toolF = tool;
                final String balF = balance, modelF = model, provF = provider;
                final long todayF = today;
                final boolean qF = isQuestion;
                final String qTextF = qText;
                final java.util.List<String> optsF = opts;
                ui.post(() -> {
                    applyState(w, textF, pidF, ridF, toolF);
                    applyQuestion(qF, qTextF, optsF);
                    applyInfo(balF, modelF, todayF);
                    applyProvider(provF);
                });
            }).start();
            ui.postDelayed(this, 2000);
        }
    };

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        hidePanel();
        if (hud != null) {
            try {
                wm.removeView(hud);
            } catch (Exception ignored) {
            }
            hud = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
