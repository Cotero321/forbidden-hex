package com.example.akaishi.forbidden.codex;

/**
 * 秘典节点的「三态」——画布界面据此决定一个节点怎么画（只有三种，不存在第四态）。
 *
 * <p><b>为什么是"前置"决定第三态而不是"条件是否满足"</b>：条件是否满足已经由
 * {@link CodexCondition#satisfied()} 与 {@code canAct} 逐条表达了，若再拿它切一态，
 * 界面就会出现"缺物品"与"缺前置"两种完全不同的语义共用一种画法。
 * 因此第三态只回答一个更粗的问题：<b>这个节点现在配不配被研究</b>——
 * 前置没读完 = 连方向都还没找到（{@link #LOCKED}，画成残缺/模糊并给线索），
 * 前置读完 = 可以研究（{@link #AVAILABLE}，条件还差时画成闪烁等待）。
 *
 * <p>{@link #id()} 是进网络快照的编号（{@link #byId} 反向解析，未知编号一律按最保守的
 * {@link #LOCKED} 处理，畸形包不会让界面把没资格的节点画成亮的）。
 */
public enum CodexNodeState {

    /** 前置节点还没读完：界面画成残缺/模糊，展示线索 */
    LOCKED((byte) 0),
    /** 前置已满足（含"条件也全满足、可以直接研究"）：界面画成可研究 */
    AVAILABLE((byte) 1),
    /** 已学完 */
    LEARNED((byte) 2);

    private final byte id;

    CodexNodeState(byte id) {
        this.id = id;
    }

    /** 网络编号（解码端未知编号回退 {@link #LOCKED}） */
    public byte id() {
        return id;
    }

    /** 网络编号 → 状态；未知编号按 {@link #LOCKED}（最保守：不把没资格的节点画亮） */
    public static CodexNodeState byId(byte id) {
        for (CodexNodeState state : values()) {
            if (state.id == id) {
                return state;
            }
        }
        return LOCKED;
    }
}
