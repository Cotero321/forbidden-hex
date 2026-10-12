package com.example.akaishi.forbidden.wand;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 法杖三段模块的<b>公共数据形状</b>：一段 = id + 修正词条 + 作用域 + 旗标。
 *
 * <p>三段（端/柄/核）共用本类 —— 它们的差异只在<b>表</b>（哪 68 行）与
 * <b>作用域的解读</b>（杖端的墨/源偏向、杖柄的源恢复），不在数据形状。
 * 共用一个形状，合成引擎就能对三段跑同一套乘算（定案 §10.2-1）。
 *
 * <p><b>实例不可变</b>：表在静态块里一次性构建，运行期只读。
 */
public final class WandPartDef {

    /** 模块段位 */
    public enum Kind { TIP, HANDLE, CORE }

    private final String id;
    private final Kind kind;
    private final Map<WandMod, Float> mods;
    private final Set<WandFlag> flags;
    /** 墨偏向杖端的墨 id（其余为空串） */
    private final String inkId;
    /** 源作用 id（源偏向杖端 / 源杖柄 / 灰骨与绿髓核；空串 = 不限源） */
    private final String sourceId;
    /** 源限定效能作用域：mind / heal / debuff / defense（空串 = 无） */
    private final String powerScope;

    public WandPartDef(String id, Kind kind, Map<WandMod, Float> mods, Set<WandFlag> flags,
            String inkId, String sourceId, String powerScope) {
        this.id = id;
        this.kind = kind;
        this.mods = mods;
        this.flags = flags;
        this.inkId = inkId;
        this.sourceId = sourceId;
        this.powerScope = powerScope;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public Map<WandMod, Float> mods() {
        return mods;
    }

    public Set<WandFlag> flags() {
        return flags;
    }

    public String inkId() {
        return inkId;
    }

    public String sourceId() {
        return sourceId;
    }

    public String powerScope() {
        return powerScope;
    }

    /** 读词条；缺省回 {@link WandMod#defaultValue()}（表里只写非默认项，行才短） */
    public float mod(WandMod mod) {
        return mods.getOrDefault(mod, mod.defaultValue());
    }

    public boolean has(WandFlag flag) {
        return flags.contains(flag);
    }

    /** 构建器：表行用 {@code def(id, kind, COST, 0.9f, …)} 的变长对书写 */
    public static WandPartDef of(String id, Kind kind, String inkId, String sourceId, String powerScope,
            Set<WandFlag> flags, Object... modPairs) {
        Map<WandMod, Float> mods = new EnumMap<>(WandMod.class);
        for (int i = 0; i + 1 < modPairs.length; i += 2) {
            mods.put((WandMod) modPairs[i], (Float) modPairs[i + 1]);
        }
        return new WandPartDef(id, kind, mods, flags, inkId, sourceId, powerScope);
    }
}
