package com.example.akaishi.forbidden.codex;

import java.util.List;

/**
 * 一个「研究阶段」：点一次推进一阶，走完全部阶段才算学会这个节点。
 *
 * <p><b>为什么门槛放在阶段上而不是节点上</b>：用户口径是"研究分阶段、每个阶段有自己的门槛（递增）"。
 * 门槛若只挂在节点上，就只能表达"够格 / 不够格"两态；放到阶段上才表达得出
 * "先看懂浅的部分，再回头深挖"这条推进感，也才能让同一节点在不同阶段呈现不同可读性。
 *
 * <p><b>为什么阶段条件是一个"要求列表"而不是固定的认知+首见两个字段</b>：
 * 六种条件（认知 / 首见 / 前置 / 物品 / 地点 / 伤害）会混在同一阶段里
 * （例："备齐 3 个碎片，并且人站在那座城里"），固定字段每加一种条件就要改一次类与所有调用点；
 * 换成列表后，新条件只是列表里多一项，阶段结构不变。
 * 顺序即界面显示顺序（求值端按声明顺序产出条件清单，两次打开界面不会跳动）。
 *
 * <p><b>本类不引入计时 / 服务端排队</b>（用户明确）：阶段推进只由"点击 + 服务端校验"驱动，
 * 不存在后台进度条，因此本记录是纯静态数据，可安全跨线程共享。
 *
 * @param requirements 本阶段要求的条件（空列表 = 本阶段无门槛，点一下就过）
 */
public record CodexStage(List<CodexRequirement> requirements) {

    /** 由若干条件构成一个阶段（顺序即显示顺序） */
    public static CodexStage of(CodexRequirement... requirements) {
        return new CodexStage(List.of(requirements));
    }
}
