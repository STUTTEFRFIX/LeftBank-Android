package com.dafeiyu.hudleft;

/** 一个 agent 在左岸面板上的全部状态。测试版用假数据填。 */
final class Agent {
    String name;
    int color;
    int iconRes;

    float pct;      // 5 小时窗口，0..1
    float weekPct;  // 本周窗口，0..1
    long tokens;    // 今天用了多少 token
    boolean working;
    String status = "";
    String request; // 待处理的请求文案；null 表示没有
    String label;   // 图标下面显示的字；null 则显示百分比
    String balance = "--";
    long todayTokens;
    String model = "";
    boolean question;
    String qText = "";
    String optA = "允许";
    String optB = "拒绝";
    boolean canAlways;
    String work = "";                                  // 这一轮在做什么（runs[0].text）
    final java.util.List<String> options = new java.util.ArrayList<>(); // 选择题选项 / 权限按钮

    Agent(String name, int color, int iconRes, float pct, float weekPct, long tokens) {
        this.name = name;
        this.color = color;
        this.iconRes = iconRes;
        this.pct = pct;
        this.weekPct = weekPct;
        this.tokens = tokens;
    }
}
