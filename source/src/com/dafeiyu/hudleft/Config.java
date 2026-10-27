package com.dafeiyu.hudleft;

import android.content.Context;

/** 一个进程内可见、也存得住的极简配置。测试版只有磁吸范围。 */
final class Config {
    private static final String PREF = "leftbank";
    private static final String KEY_MAGNET = "magnet_dp";
    private static final String KEY_FLASH = "flash_on";
    private static final String KEY_FLASH_MS = "flash_ms";

    static int magnetDp = 40;   // 默认磁吸范围（dp），主人调过之后定为 40
    static boolean flashOn = true;
    static int flashMs = 1200;  // 红光辉光的一次呼吸周期

    static void load(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences(PREF, 0);
        magnetDp = sp.getInt(KEY_MAGNET, 40);
        flashOn = sp.getBoolean(KEY_FLASH, true);
        flashMs = sp.getInt(KEY_FLASH_MS, 1200);
    }

    static void save(Context c, int v) {
        magnetDp = v;
        c.getSharedPreferences(PREF, 0).edit().putInt(KEY_MAGNET, v).apply();
    }

    static void saveFlash(Context c, boolean on, int ms) {
        flashOn = on;
        flashMs = ms;
        c.getSharedPreferences(PREF, 0).edit()
                .putBoolean(KEY_FLASH, on).putInt(KEY_FLASH_MS, ms).apply();
    }
}
