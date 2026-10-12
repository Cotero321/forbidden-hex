package com.example.akaishi.forbidden.codex;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 秘典的「节点」：一页可以被研究/学会的知识，同时是知识网络里的一个点。
 *
 * <p><b>门槛的两层结构</b>（用户口径：只看 COG 与首见，<b>不看 SAN</b>）：
 * <ol>
 *   <li><b>节点级</b>：{@link #cog()}（整体认知门槛，{@code <=0} = 不设）、
 *       {@link #firstSeen()}（整体首见要求）、{@link #requiredNodes()}（必须先学完的前置节点）；</li>
 *   <li><b>阶段级</b>：{@link #stages()} 里每一阶自己的要求列表（六种条件任意混合）；</li>
 *   <li>两层都满足，点击才推进一步；走完全部阶段 = {@code learned}。</li>
 * </ol>
 *
 * <p><b>为什么节点级仍保留三个固定字段而不一起改成列表</b>：这三个字段的语义是"整页成立的前提"，
 * 而且 {@link #requiredNodes()} 单独承担了"配不配研究"的第三态判定（见 {@link CodexNodeState}）——
 * 若并进通用列表，判定端就要在列表里反查"哪一条是前置"，得不偿失。
 * 阶段级才是"混合条件"真正的舞台。
 *
 * <p><b>画布数据</b>：{@link #family()} + {@link #x()} / {@link #y()} 由静态表<b>手动指定</b>
 * （不做自动布局：节点少时手摆的位置才符合叙事顺序，同族按"同列、错行"摆放即可保证不重叠）。
 * {@link #icon()} 直接复用物品图标（该节点的产物或某个原版物品/方块），
 * <b>不新增任何贴图资产</b>；以 {@link Supplier} 持有是为了规避注册顺序（同 {@link CodexReward}）。
 *
 * <p>{@link #reward()} 为 {@link CodexReward.Type#RITUAL} 时本节点是<b>可重复节点</b>：
 * 学完（阶段走完）之后按钮改为"举行仪式"，每次扣 SAN、加 SANC，直到次数用尽。
 *
 * <p>语言键由 id 路径派生（{@code codex.forbidden_hex.node.<path>.name/.desc/.hint}），禁止硬编码。
 *
 * @param id            节点 id（进存档，发布后不得改）
 * @param tier          知识三级分类（决定页签）
 * @param family        族 id（画布分簇与配色分组，见 {@link CodexFamily}）
 * @param x             画布坐标 X（手工指定）
 * @param y             画布坐标 Y（手工指定）
 * @param forbidden     是否禁忌条目（供界面做视觉警示）
 * @param icon          节点图标（延迟求值的物品图标，不新增贴图）
 * @param cog           节点级认知门槛；{@code <=0} = 不设
 * @param firstSeen     节点级首见要求（整页都要求满足）
 * @param requiredNodes 前置节点（必须先学完）
 * @param stages        1~3 个研究阶段（≥1）
 * @param pages         条目页列表，<b>顺序即阅读序</b>：各阶段手写正文页 → 条件页 → 奖励页 →〔关联页〕→ 配方页；
 *                      正文页自带 {@link CodexPage#stageIndex()} 划定所属阶段，界面按进度决定
 *                      "显示正文 / 显示思索稿 / 整页隐藏"（见 {@link CodexPage}）
 * @param recipes       学会这条知识能做出的东西（<b>配方 id</b>，如 {@code forbidden_hex:xxx}）；
 *                      空列表 = 配方页只显示"还没写完"的占位。本轮示例节点全为空。
 *                      <p><b>将来填配方的接线点</b>：① 本表给节点写上配方 id；② 配方本体落在
 *                      {@code data/<modid>/recipes/*.json}；③ 若要让"会不会做"成为门槛，须在
 *                      {@link CodexCondition} 新增条件类别并在 {@link CodexGates} 求值
 *                      （本轮<b>不做</b>任何服务端门控，配方页只是展示）。
 * @param reward        学会后的奖励
 * @param rewardKey     奖励页<b>专属主句</b>的语言键<b>后缀</b>（如 {@code reward}；空串 = 不写专属句，
 *                      奖励页回落"按奖励类型的通用故事句"）。与 {@link #pageKey(CodexPage)} 同口径，
 *                      表里只写短后缀，完整键由 {@link #rewardLineKey()} 拼出
 */
public record CodexNode(ResourceLocation id, CodexTier tier, String family, int x, int y,
                        boolean forbidden, Supplier<ItemStack> icon, float cog,
                        List<ResourceLocation> firstSeen, List<ResourceLocation> requiredNodes,
                        List<CodexStage> stages, List<CodexPage> pages, List<ResourceLocation> recipes,
                        CodexReward reward, String rewardKey) {

    /** 节点显示名语言键 */
    public String nameKey() {
        return "codex.forbidden_hex.node." + id.getPath() + ".name";
    }

    /**
     * 节点描述语言键（列表 / 详情头部的短描述）。
     *
     * <p>与 {@link CodexPage.Type#TEXT} 页并存：描述是"一句话概括"（画布与列表都要用），
     * 页是"翻页读的正文"。两者口径不同，故不合并。
     */
    public String descKey() {
        return "codex.forbidden_hex.node." + id.getPath() + ".desc";
    }

    /** 线索语言键：条件未满足时给"方向性"提示（他在暗示你该去哪、该碰什么） */
    public String hintKey() {
        return "codex.forbidden_hex.node." + id.getPath() + ".hint";
    }

    /** 某页的语言键（仅 {@link CodexPage.Type#TEXT} 有正文，其余页由界面现算） */
    public String pageKey(CodexPage page) {
        return "codex.forbidden_hex.node." + id.getPath() + "." + page.suffix();
    }

    /**
     * 某页"所属阶段还没完成"时顶替正文的思索文案语言键。
     *
     * <p>与 {@link #pageKey(CodexPage)} 同口径（{@code codex.forbidden_hex.node.<路径>.<后缀>}）：
     * 表里只写短后缀，完整键在取用时才拼，改模组 id 或节点路径只需改这一处。
     */
    public String speculateKey(CodexPage page) {
        return "codex.forbidden_hex.node." + id.getPath() + "." + page.speculateKey();
    }

    /** 阶段总数 */
    public int stageCount() {
        return stages.size();
    }

    /**
     * 奖励页<b>专属主句</b>的语言键（{@code codex.forbidden_hex.node.<路径>.<后缀>}）。
     *
     * <p>为空 = 该节点没写专属句，奖励页回落"按奖励类型的通用故事句"
     * （见 {@code AkaishiCodexPages#rewardStory}）。与 {@link #pageKey(CodexPage)} 同口径：
     * 表里只写短后缀，完整键在取用时才拼，改模组 id 或节点路径只需改这一处。
     */
    @Nullable
    public String rewardLineKey() {
        return rewardKey == null || rewardKey.isEmpty()
                ? null
                : "codex.forbidden_hex.node." + id.getPath() + "." + rewardKey;
    }

    /** 是否可重复使用（学习完成后仍可反复触发） */
    public boolean repeatable() {
        return reward != null && reward.type() == CodexReward.Type.RITUAL;
    }
}
