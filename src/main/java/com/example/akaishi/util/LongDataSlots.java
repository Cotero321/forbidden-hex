package com.example.akaishi.util;

import net.minecraft.world.inventory.ContainerData;

/**
 * long 值的菜单数据槽同步辅助。
 * <p>
 * 1.20.1 的数据槽经 {@code ClientboundContainerSetDataPacket} 以 short（writeShort/readShort）
 * 传输，每个槽位仅低 16 位有效——直接写 int 会截断高 16 位。
 * 约定：一个 long 的低 32 位拆成两个 16 位段——位 0..15 写 {@code lowIndex}，位 16..31 写 {@code highIndex}；
 * 超过 32 位的大容量（如串联器 5200 亿）用 4 槽重载同步完整 64 位。
 * int 的完整 32 位（如 UUID 短 ID）用 {@link #writeInt}/{@link #readInt} 拆 2 槽，避免高 16 位丢失。
 */
public final class LongDataSlots {

    private LongDataSlots() {}

    /** 写入 long 低 32 位：位 0..15 → lowIndex，位 16..31 → highIndex */
    public static void write(ContainerData data, int lowIndex, int highIndex, long value) {
        data.set(lowIndex, (int) (value & 0xFFFFL));
        data.set(highIndex, (int) ((value >>> 16) & 0xFFFFL));
    }

    /** 读取 long：由 lowIndex / highIndex 两槽按 16 位段重组（低 32 位） */
    public static long read(ContainerData data, int lowIndex, int highIndex) {
        return (((long) data.get(highIndex) & 0xFFFFL) << 16) | (data.get(lowIndex) & 0xFFFFL);
    }

    /** 写入 int 的完整 32 位：位 0..15 → lowIndex，位 16..31 → highIndex */
    public static void writeInt(ContainerData data, int lowIndex, int highIndex, int value) {
        data.set(lowIndex, value & 0xFFFF);
        data.set(highIndex, (value >>> 16) & 0xFFFF);
    }

    /** 读取完整 32 位 int：由 lowIndex / highIndex 两槽按 16 位段重组 */
    public static int readInt(ContainerData data, int lowIndex, int highIndex) {
        return ((data.get(highIndex) & 0xFFFF) << 16) | (data.get(lowIndex) & 0xFFFF);
    }

    /** 把小数值（槽位数等）钳到单槽可用范围 0..0xFFFF（超范围会被 short 传输截成负数） */
    public static int clampShort(int value) {
        return value < 0 ? 0 : Math.min(value, 0xFFFF);
    }

    /** 写入完整 long 64 位：位 0..15 / 16..31 / 32..47 / 48..63 分别落入 4 槽 */
    public static void write(ContainerData data, int lowIndex, int highIndex, int high2Index, int high3Index, long value) {
        data.set(lowIndex, (int) (value & 0xFFFFL));
        data.set(highIndex, (int) ((value >>> 16) & 0xFFFFL));
        data.set(high2Index, (int) ((value >>> 32) & 0xFFFFL));
        data.set(high3Index, (int) ((value >>> 48) & 0xFFFFL));
    }

    /** 读取完整 long 64 位：由 4 槽按 16 位段重组（支持超 32 位的大容量储能） */
    public static long read(ContainerData data, int lowIndex, int highIndex, int high2Index, int high3Index) {
        return (((long) data.get(high3Index) & 0xFFFFL) << 48)
                | (((long) data.get(high2Index) & 0xFFFFL) << 32)
                | (((long) data.get(highIndex) & 0xFFFFL) << 16)
                | (data.get(lowIndex) & 0xFFFFL);
    }
}
