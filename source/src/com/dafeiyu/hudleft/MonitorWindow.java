package com.dafeiyu.hudleft;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 监察窗：看 AI 在干什么，顺便回答它的请求。
 * 不再是聊天窗——没有消息列表、没有输入框、不往 /api/chat 发东西。
 */
final class MonitorWindow extends LinearLayout {

    interface Host {
        void onAnswer(int index);

        void onClose();
    }

    private final float dp;
    private final TextView dot;
    private final TextView name;
    private final TextView status;
    private final TextView model;
    private final TextView balance;
    private final TextView today;
    private final TextView work;
    private final LinearLayout workRow;
    private final LinearLayout reqBox;
    private final TextView reqTitle;
    private final TextView reqQuestion;
    private final LinearLayout reqButtons;
    private Host host;
    private String lastSig = "";

    MonitorWindow(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21A1918);
        bg.setCornerRadius(px(18));
        bg.setStroke(px(1), 0x33FFFFFF);
        setBackground(bg);
        setPadding(px(12), px(10), px(12), px(10));

        // 标题：● 名字 / 状态 / ×
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        dot = new TextView(c);
        dot.setText("●");
        dot.setTextSize(13);
        dot.setTextColor(0xFFD97757);
        head.addView(dot);
        name = new TextView(c);
        name.setTextSize(13);
        name.setTextColor(0xFFF2EEE8);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nlp.leftMargin = px(5);
        head.addView(name, nlp);
        TextView close = new TextView(c);
        close.setText("×");
        close.setTextSize(17);
        close.setTextColor(0xFF9C948B);
        close.setPadding(px(8), 0, px(2), 0);
        close.setOnClickListener(v -> {
            if (host != null) host.onClose();
        });
        head.addView(close);
        addView(head);

        status = new TextView(c);
        status.setTextSize(11.5f);
        status.setTextColor(0xFFB9B2A9);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = px(5);
        slp.topMargin = px(1);
        addView(status, slp);

        android.view.View line = new android.view.View(c);
        line.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(1));
        llp.topMargin = px(7);
        llp.bottomMargin = px(4);
        addView(line, llp);

        // 可滚动的内容：模型 / 余额 / 今日 / 本轮 / 请求
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        scroll.addView(body);
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        model = addRow(body, "模型");
        balance = addRow(body, "余额");
        today = addRow(body, "今日");

        workRow = new LinearLayout(c);
        workRow.setOrientation(VERTICAL);
        workRow.setPadding(0, px(4), 0, px(3));
        TextView workLabel = new TextView(c);
        workLabel.setText("本轮");
        workLabel.setTextSize(11);
        workLabel.setTextColor(0xFF8F877E);
        workRow.addView(workLabel);
        work = new TextView(c);
        work.setTextSize(11.5f);
        work.setTextColor(0xFFE8E2DA);
        work.setMaxLines(3);
        work.setEllipsize(TextUtils.TruncateAt.END);
        work.setLineSpacing(px(2), 1f);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = px(2);
        workRow.addView(work, wlp);
        body.addView(workRow);

        // 请求块：只在有选择题/权限确认时出现
        reqBox = new LinearLayout(c);
        reqBox.setOrientation(VERTICAL);
        GradientDrawable rbg = new GradientDrawable();
        rbg.setColor(0x14E9B949);
        rbg.setCornerRadius(px(12));
        rbg.setStroke(px(1), 0x55E9B949);
        reqBox.setBackground(rbg);
        reqBox.setPadding(px(9), px(8), px(9), px(8));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = px(4);
        body.addView(reqBox, rlp);

        reqTitle = new TextView(c);
        reqTitle.setTextSize(11.5f);
        reqTitle.setTextColor(0xFFE9B949);
        reqTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        reqBox.addView(reqTitle);

        reqQuestion = new TextView(c);
        reqQuestion.setTextSize(11.5f);
        reqQuestion.setTextColor(0xFFF2EEE8);
        reqQuestion.setMaxLines(4);
        reqQuestion.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = px(3);
        reqBox.addView(reqQuestion, qlp);

        reqButtons = new LinearLayout(c);
        reqButtons.setOrientation(VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = px(3);
        reqBox.addView(reqButtons, blp);

        reqBox.setVisibility(GONE);
        workRow.setVisibility(GONE);
    }

    private TextView addRow(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(4), 0, px(2));
        TextView l = new TextView(getContext());
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(0xFF8F877E);
        l.setWidth(px(44));
        row.addView(l);
        TextView v = new TextView(getContext());
        v.setTextSize(11.5f);
        v.setTextColor(0xFFE8E2DA);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row);
        return v;
    }

    void setHost(Host h) {
        host = h;
    }

    void setAgent(Agent a) {
        dot.setTextColor(a.color);
        name.setText(a.name);
        status.setText(a.status == null ? "" : a.status);
        model.setText(a.model == null ? "" : a.model);
        balance.setText(a.balance == null ? "" : a.balance);
        today.setText(fmtTokens(a.todayTokens));
        work.setText(a.work == null ? "" : a.work);
        workRow.setVisibility(a.work == null || a.work.isEmpty() ? GONE : VISIBLE);

        boolean pending = a.request != null;
        reqBox.setVisibility(pending ? VISIBLE : GONE);
        if (!pending) {
            lastSig = "";
            return;
        }
        reqTitle.setText(a.question ? "需要你选择" : "需要你确认 · " + a.request);
        String q = a.question ? a.qText : "";
        reqQuestion.setText(q);
        reqQuestion.setVisibility(q == null || q.isEmpty() ? GONE : VISIBLE);
        String sig = a.request + "|" + (a.question ? a.qText : "") + "|" + a.options;
        if (!sig.equals(lastSig)) {
            rebuildButtons(a.options);
            lastSig = sig;
        }
    }

    /** 按选项现建按钮：选择题 1..4 个，权限确认是允许 / 拒绝 / 始终允许 */
    private void rebuildButtons(java.util.List<String> options) {
        reqButtons.removeAllViews();
        if (options == null) return;
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            TextView b = new TextView(getContext());
            b.setText(options.get(i));
            b.setTextSize(12);
            b.setGravity(Gravity.CENTER);
            b.setMaxLines(2);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setPadding(px(8), px(9), px(8), px(9));
            boolean primary = i == 0;
            b.setTextColor(primary ? 0xFF14100E : 0xFFE9B949);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(10));
            if (primary) {
                g.setColor(0xFFE9B949);
            } else {
                g.setColor(0x1FFFFFFF);
                g.setStroke(px(1), 0x33FFFFFF);
            }
            b.setBackground(g);
            b.setOnClickListener(v -> {
                if (host != null) host.onAnswer(index);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(5);
            reqButtons.addView(b, lp);
        }
    }

    private String fmtTokens(long n) {
        if (n >= 1_000_000) return String.format("%.2fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.0fk", n / 1000.0);
        return String.valueOf(n);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
