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
public final class HudService extends Service implements FrameHudView.Listener, CardView.Listener {

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
    private CardView card;
    private WindowManager.LayoutParams cardLp;
    private FramePath path;
    private float s;
    private int selected = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Config.load(this);
        seedAgents();
        startInForeground();

        int top = dp(6), bottom = dp(6), side = dp(6);
        try {
            android.view.WindowInsets wi = ((WindowManager) getSystemService(WINDOW_SERVICE))
                    .getCurrentWindowMetrics().getWindowInsets();
            android.graphics.Insets sb = wi.getInsets(android.view.WindowInsets.Type.statusBars()
                    | android.view.WindowInsets.Type.navigationBars());
            top = sb.top + dp(6);
            bottom = sb.bottom + dp(6);
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
            stopSelf();
            return START_NOT_STICKY;
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

    private void applyQuestion(boolean isQ, String q, String a, String b) {
        if (agents.isEmpty()) return;
        Agent ag = agents.get(0);
        ag.question = isQ;
        ag.qText = q;
        ag.optA = a;
        ag.optB = b;
        if (isQ) ag.request = q;
        if (card != null) card.invalidate();
    }

    private void applyInfo(String balance, String model, long today) {
        if (agents.isEmpty()) return;
        Agent a = agents.get(0);
        a.balance = balance;
        a.model = model;
        a.todayTokens = today;
        if (card != null) card.invalidate();
    }

    private void applyState(boolean working, String text, String pid, String rid, String tool) {
        if (agents.isEmpty()) return;
        Agent a = agents.get(0);
        a.working = working;
        permId = pid == null || pid.isEmpty() ? null : pid;
        permRunId = rid == null || rid.isEmpty() ? null : rid;
        boolean pending = permId != null;
        a.request = pending ? tool : null;
        a.label = pending ? "等你" : (working ? "干活" : "空闲");
        a.status = pending ? ("等你确认 · " + tool) : (working ? "正在干活" : "空闲");
        if (hud != null) hud.invalidate();
        if (card != null) card.invalidate();
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
            w = h = dp(220);
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
            int m = dp(24);
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
        hideCard();
        hud.setExpanded(false);
        ui.postDelayed(this::updateHudWindow, 220);   // 等收起动画走完再缩窗口
    }

    @Override
    public void onPick(int index) {
        if (!hud.isExpanded()) return;
        if (card != null && selected == index) {
            hideCard();
            return;
        }
        selected = index;
        Agent a = agents.get(index);
        if (card == null) {
            card = new CardView(this);
            card.setListener(this);
            card.setAgent(a);
            cardLp = overlayParams();
            cardLp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(card, cardLp);
            card.show();
        }
        card.setAgent(a);
        placeCard();
    }

    @Override
    public void onSlide(float ds) {
        s = ((s + ds) % path.len + path.len) % path.len;
        hud.setS(s);
        updateHudWindow();
        placeCard();
    }

    @Override
    public void onDragEnd(float velocity) {
        updateHudWindow();
    }

    /** 卡片永远朝屏幕内侧开 */
    private void placeCard() {
        if (card == null) return;
        path.eval(s, ev);
        float ang = ev[2];
        float nx = -(float) Math.sin(ang);
        float ny = (float) Math.cos(ang);
        int cw = dp(218);
        int ch = dp(selected >= 0 && agents.get(selected).request != null ? 232 : 152);
        int x, y;
        if (Math.abs(nx) > Math.abs(ny)) {
            x = Math.round(ev[0] + nx * dp(42)) - (nx > 0 ? 0 : cw);
            y = Math.round(ev[1] - ch / 2f);
        } else {
            y = Math.round(ev[1] + ny * dp(42)) - (ny > 0 ? 0 : ch);
            x = Math.round(ev[0] - cw / 2f);
        }
        x = Math.max(dp(2), Math.min(screenW() - cw - dp(2), x));
        y = Math.max(dp(2), Math.min(screenH() - ch - dp(2), y));
        cardLp.x = x;
        cardLp.y = y;
        wm.updateViewLayout(card, cardLp);
        card.post(() -> {
            if (card == null) return;
            int ch2 = card.getHeight();
            if (ch2 > 0) {
                int y2 = cardLp.y;
                if (cardLp.y + ch2 > screenH() - dp(2)) y2 = screenH() - ch2 - dp(2);
                if (y2 != cardLp.y) {
                    cardLp.y = Math.max(dp(2), y2);
                    wm.updateViewLayout(card, cardLp);
                }
            }
        });
    }

    private void hideCard() {
        if (card == null) return;
        final CardView c = card;
        card = null;
        selected = -1;
        c.hide(() -> {
            try {
                wm.removeView(c);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void onClose() {
        hideCard();
    }

    @Override
    public void onAnswer(int index) {
        final String id = permId, rid = permRunId;
        Agent agent = agents.isEmpty() ? null : agents.get(0);
        final boolean q = agent != null && agent.question;
        final String qText = agent != null ? agent.qText : "";
        final String choice = agent != null ? (index == 0 ? agent.optA : agent.optB) : "";
        if (id != null) {
            new Thread(() -> {
                if (q) Gateway.answerQuestion(id, rid, qText, choice);
                else Gateway.answer(id, rid, index == 0);
            }).start();
        }
        final boolean allow = index == 0;
        permId = null;
        if (agents.isEmpty()) return;
        Agent a = agents.get(0);
        a.request = null;
        a.status = allow ? "已允许 · 继续" : "已拒绝";
        a.label = "…";
        if (hud != null) hud.invalidate();
        if (card != null) {
            card.requestLayout();
            card.invalidate();
            placeCard();
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                boolean working = false;
                String text = "", pid = "", rid = "", tool = "";
                String balance = "--", model = "";
                boolean isQuestion = false;
                String qText = "", optA = "允许", optB = "拒绝";
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
                            optA = "允许";
                            optB = "拒绝";
                            if (isQuestion && input != null) {
                                JSONArray qs = input.optJSONArray("questions");
                                if (qs != null && qs.length() > 0) {
                                    JSONObject q0 = qs.optJSONObject(0);
                                    qText = q0.optString("question", "请选择");
                                    JSONArray opts = q0.optJSONArray("options");
                                    if (opts != null && opts.length() > 0)
                                        optA = opts.optJSONObject(0).optString("label", "选项 1");
                                    if (opts != null && opts.length() > 1)
                                        optB = opts.optJSONObject(1).optString("label", "选项 2");
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                final boolean w = working;
                final String pidF = pid, ridF = rid, toolF = tool;
                final String balF = balance, modelF = model;
                final long todayF = today;
                final boolean qF = isQuestion;
                final String qTextF = qText, optAF = optA, optBF = optB;
                ui.post(() -> {
                    applyState(w, "", pidF, ridF, toolF);
                    applyQuestion(qF, qTextF, optAF, optBF);
                    applyInfo(balF, modelF, todayF);
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
        if (card != null) {
            try {
                wm.removeView(card);
            } catch (Exception ignored) {
            }
            card = null;
        }
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
