package com.example.akaishi.menu;

import java.util.Locale;

/**
 * GUI 能量数值格式化工具：大数值缩写显示（T/B/M/K）。
 * 由多个机器界面共用，避免各 Screen 重复实现。
 */
public final class EnergyFormat {

    private EnergyFormat() {
    }

    /** >=1T 万亿，>=1B 十亿，>=1M 百万，>=1K 千，否则原样输出 */
    public static String format(long v) {
        if (v >= 1_000_000_000_000L) {
            return trim(v / 1.0e12) + "T";
        }
        if (v >= 1_000_000_000L) {
            return trim(v / 1.0e9) + "B";
        }
        if (v >= 1_000_000L) {
            return trim(v / 1.0e6) + "M";
        }
        if (v >= 1_000L) {
            return trim(v / 1.0e3) + "K";
        }
        return String.valueOf(v);
    }

    /**
     * 只读量专用（已占用 / 容量 / 单笔上限）：缩写小数**向下取整**。
     * <p>
     * 显示值恒 ≤ 真值，绝不暗示"还有余量"——否则玩家按显示值操作会被服务器整笔拒绝。
     */
    public static String formatFloor(long v) {
        return scaled(v, -1);
    }

    /**
     * 应付量专用（存入费 / 取出费）：缩写小数**向上取整**。
     * <p>
     * 显示值恒 ≥ 真值，绝不误导少备能量。
     */
    public static String formatCeil(long v) {
        return scaled(v, 1);
    }

    /** 千分位精确值：tooltip 兜底用（缩写只占主显示位） */
    public static String exact(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** 按方向缩放的公共实现：dir < 0 向下、dir > 0 向上；<1K 原样输出（本就精确） */
    private static String scaled(long v, int dir) {
        if (v >= 1_000_000_000_000L) {
            return trim(v / 1.0e12, dir) + "T";
        }
        if (v >= 1_000_000_000L) {
            return trim(v / 1.0e9, dir) + "B";
        }
        if (v >= 1_000_000L) {
            return trim(v / 1.0e6, dir) + "M";
        }
        if (v >= 1_000L) {
            return trim(v / 1.0e3, dir) + "K";
        }
        return String.valueOf(v);
    }

    /** 保留 1 位小数，整数时去掉小数部分（2.0 → 2） */
    private static String trim(double d) {
        if (Math.abs(d - Math.round(d)) < 0.05) {
            return String.valueOf((long) Math.round(d));
        }
        return String.format(Locale.ROOT, "%.1f", d);
    }

    /** 定向取整到 1 位小数：dir < 0 向下、dir > 0 向上；整数结果去小数尾巴 */
    private static String trim(double d, int dir) {
        double r = dir < 0 ? Math.floor(d * 10.0) / 10.0 : Math.ceil(d * 10.0) / 10.0;
        if (Math.abs(r - Math.round(r)) < 1.0e-9) {
            return String.valueOf((long) Math.round(r));
        }
        return String.format(Locale.ROOT, "%.1f", r);
    }
}
