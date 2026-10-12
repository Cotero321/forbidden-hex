package com.example.akaishi.forbidden.codex;

/**
 * 秘典节点的一「页」（节点内可翻页的条目）。
 *
 * <p><b>为什么页要分类型而不是一段长文本</b>：手写描述、条件清单、奖励清单、相互关系这四样
 * 的<b>数据源完全不同</b>——描述来自语言文件，后三者来自静态表与玩家存档。
 * 若把它们拼成一段文本塞进 lang，条件一改就要改两份语言文件，且永远跟不上服务端的真实判定。
 * 分成类型后：只有 {@link Type#TEXT} 读语言文件，另外三种由界面在渲染时<b>现算</b>
 * （条件用服务端下发的 {@link CodexCondition} 清单，奖励读 {@link CodexReward}，
 * 关系读静态表的前置/后继），于是"文案"与"事实"永不脱节。
 *
 * <p><b>为什么页还要带"阶段归属"与"思索文案"</b>：一个节点现在是一篇<b>分阶段</b>的短篇——
 * 每完成一个阶段才多解锁一页正文；<b>未完成</b>的阶段不显示正文，改显示一页"思索"
 * （作者犹豫/猜测的口吻，给玩家方向性线索）。于是每一页必须自己说清两件事：
 * 它属于哪个阶段（{@link #stageIndex()}），以及它没被解锁时该由哪段话顶替（{@link #speculateKey()}）。
 * 这两件事若挂在节点上，就成了"节点 → 页"的反查表，表一长必然与 {@link CodexNode#stages()}
 * 的顺序脱节；挂在页上则页序 = 阅读序，永不打架。
 *
 * <p>{@link Type#TEXT} 的正文放双语 lang，键由 {@link CodexNode#pageKey(CodexPage)} /
 * {@link CodexNode#speculateKey(CodexPage)} 派生（{@code codex.forbidden_hex.node.<节点路径>.<后缀>}）
 * ——本类只存短<b>后缀</b>，不存完整键，免得把模组 id 与节点路径在表里重复 5 遍还容易写错。
 *
 * @param type         页类型
 * @param suffix       仅 {@link Type#TEXT} 使用：正文语言键后缀（如 {@code stage1.p1}）；其余类型为空串
 * @param stageIndex   仅 {@link Type#TEXT} 使用：所属阶段<b>下标</b>（0 基，即 {@link CodexNode#stages()} 的下标）；
 *                     不隶属任何阶段的页（条件/奖励/关联）用 {@link #NO_STAGE}
 * @param speculateKey 仅 {@link Type#TEXT} 使用：本阶段<b>未完成</b>时顶替正文的思索文案后缀；
 *                     空串 = 该页没有思索稿，未完成时整页不出现（正常数据不会走到这一步）
 */
public record CodexPage(Type type, String suffix, int stageIndex, String speculateKey) {

    /** 不隶属任何阶段的页（条件 / 奖励 / 关联）的 {@link #stageIndex} 取值 */
    public static final int NO_STAGE = -1;

    /** 页类型 */
    public enum Type {
        /** 手写描述（正文在双语 lang 里，可多页） */
        TEXT,
        /** 条件清单（由本节点（含当前阶段）的条件自动生成） */
        CONDITIONS,
        /** 奖励清单（由 {@link CodexReward} 自动生成） */
        REWARDS,
        /** 相互关系（自动生成：列出前置节点与把本节点当前置的后继节点） */
        RELATIONS,
        /**
         * 配方页（展示 {@link CodexNode#recipes()} 指向的配方；空列表时只渲染一句占位文案）。
         *
         * <p>与前三类"自动生成"的页不同，这一页的数据源是<b>配方注册表</b>（客户端本地就有），
         * 故渲染端自己查配方即可，不需要服务端下发；页的可见性与条件页同口径（恒可见）。
         */
        RECIPES
    }

    /**
     * 某阶段的正文页。
     *
     * @param stageIndex   所属阶段下标（0 基，与 {@link CodexNode#stages()} 下标一致）
     * @param suffix       正文语言键后缀（建议写成 {@code stage1.p1} 这种"人看得懂"的形状）
     * @param speculateKey 同阶段未完成时顶替正文的思索文案后缀
     */
    public static CodexPage text(int stageIndex, String suffix, String speculateKey) {
        return new CodexPage(Type.TEXT, suffix, stageIndex, speculateKey);
    }

    /** 条件页（自动生成，始终可见：否则玩家不知道该怎么解锁） */
    public static CodexPage conditions() {
        return new CodexPage(Type.CONDITIONS, "", NO_STAGE, "");
    }

    /** 奖励页（自动生成，学完之后才可见） */
    public static CodexPage rewards() {
        return new CodexPage(Type.REWARDS, "", NO_STAGE, "");
    }

    /** 关系页（自动生成，学完之后才可见） */
    public static CodexPage relations() {
        return new CodexPage(Type.RELATIONS, "", NO_STAGE, "");
    }

    /** 配方页（读 {@link CodexNode#recipes()}；恒可见，空列表时渲染占位文案） */
    public static CodexPage recipes() {
        return new CodexPage(Type.RECIPES, "", NO_STAGE, "");
    }

    /** 是否为"隶属某阶段的手写正文页"（界面据此决定是否按阶段进度取舍） */
    public boolean stagedText() {
        return type == Type.TEXT && stageIndex >= 0;
    }
}
