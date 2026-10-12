package com.example.akaishi.forbidden.codex;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.item.ForbiddenMaterialItems;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinFirstEncounters;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 禁忌秘典的<b>节点静态表</b>（本轮仍是 5 个示例节点，但已补齐知识网络的全部结构：
 * 族、画布坐标、图标、禁忌标记、线索、条目页，以及混合六种条件的阶段）。
 *
 * <p><b>为什么是静态表 + 延迟求值 lambda</b>：与 {@code AkaishiAltarRecipe} 同一套做法 ——
 * 表在类初始化期构建，此时 {@code ForbiddenMaterialItems.register()} 可能还没跑完，
 * 直接取 {@code ForbiddenMaterialItems.xxx.get()} 会读到 null；把物品（奖励与图标都是）放到 {@code Supplier} 里，
 * 到真正发放/渲染时才求值，注册顺序就不再是约束。
 *
 * <p><b>画布布局</b>：族 = 列（{@link CodexFamily}），行内自上而下摆同族的节点。
 * 列距 88、行距 44 都是<b>常量</b>，且每个族只用自己那一列，因此同族内天然不重叠。
 *
 * <p><b>为什么"名与视"那一格要放在下面一行</b>：全图只有两条前置线，其中
 * {@code quiet_thought}（静与暗 · 第 1 行）→ {@code cold_sympathy}（疼与血 · 第 1 行）
 * 是一条横穿整个画布的直线（y 恒为第 1 行的节点中心）。若把"名与视"的节点也摆在第 1 行，
 * 这条线就会从它的框里穿过去。把它下移一行后，该线在它上方 20px 处通过，
 * 于是<b>跨族前置连线不穿过任何节点</b>（另一条 {@code the_watchers_name} → {@code blood_offering}
 * 是同行的短横线，中间无节点）。
 *
 * <p><b>族按内容重分</b>（本轮用户拍板：族 = 感知通道，且族即"独立分册"）：
 * <ul>
 *   <li><b>静与暗</b>（耳：先安静、再往暗处）→ {@code quiet_thought}、{@code breath_of_dark}；</li>
 *   <li><b>名与视</b>（眼：被看、被念名）→ {@code the_watchers_name}；</li>
 *   <li><b>疼与血</b>（身：替它疼、放血供奉）→ {@code cold_sympathy}、{@code blood_offering}。</li>
 * </ul>
 *
 * <p><b>示例覆盖矩阵</b>（六种条件全数演示，LOCATION 三个子类型、DAMAGE 两种形式都在内）：
 * <ol>
 *   <li>{@code quiet_thought} —— 单阶纯 COG，SANC 奖励；</li>
 *   <li>{@code breath_of_dark} —— 两阶：COG → COG + 首见 + <b>地点（群系 id）</b>，GIFT 回理智；</li>
 *   <li>{@code cold_sympathy} —— 前置节点 + 两阶（COG + 首见 → COG + <b>伤害（类型 id）</b>），GIFT 给物品；</li>
 *   <li>{@code the_watchers_name} —— 节点级 COG + 首见；阶段里 <b>地点（结构）+ 物品（3 个，完成时消耗）</b>，UNLOCK；</li>
 *   <li>{@code blood_offering} —— 禁忌级：节点级 COG + 首见 + 前置；两阶 <b>地点（维度）</b> → COG + <b>伤害（标签）</b>，RITUAL。</li>
 * </ol>
 *
 * <p><b>门槛只看 COG 与首见，不看 SAN</b>（用户明确）。首见 id 直接复用理智系统的内置条目，
 * 因此"要求见过监守者"这件事不需要秘典另立一套记档；"挨过的伤害"则读 {@code SanityState.damage_seen}
 * （平台侧在 {@code LivingHurtEvent} 里记）。
 *
 * <p><b>配方页（本轮新增，全部留空）</b>：每个节点的页序末尾都挂了一页 {@link CodexPage#recipes()}，
 * 而 {@link CodexNode#recipes()} 本轮一律给 {@code List.of()} ⇒ 界面上这一页渲染的是
 * "这一页还没写完"的占位。等某个节点真有配方了，只要把该节点的 {@code recipes} 填上配方 id
 * （并落地对应的 {@code data/forbidden_hex/recipes/*.json}），这一页立刻变成合成表 —— 页面本身不用再动。
 * 若某节点不想要这一页，把该节点 {@code pages} 里的 {@code CodexPage.recipes()} 删掉即可（单行）。
 */
public final class CodexTable {

    /**
     * 仪式的 SAN 代价。
     *
     * <p><b>待确认（SAN 代价由用户未指定）</b>：用户原话只给了"降 SAN 提 SANC 的仪式 +5、可用 5 次"，
     * 没有给出扣多少 SAN。此处取一个明确常量 10（待调手感值），改这一处即全表生效。
     */
    public static final float RITUAL_SAN_COST = 10f;

    // ===== 画布坐标常量（列 = 族，行 = 族内位次；同族内不重叠）=====

    /** 静与暗族的列 */
    private static final int COL_HUSH = 0;
    /** 名与视族的列 */
    private static final int COL_GAZE = 88;
    /** 疼与血族的列 */
    private static final int COL_BLOOD = 176;
    /** 族内第一行 */
    private static final int ROW_TOP = 0;
    /** 族内第二行 */
    private static final int ROW_BOTTOM = 44;

    /** 示例里"备齐会学舌的碎片"这条条件要求的个数 */
    private static final int WATCHER_SHARD_COUNT = 3;

    /**
     * 奖励页<b>专属主句</b>的语言键后缀（5 个节点各写一条：
     * {@code codex.forbidden_hex.node.<路径>.reward}）。留空即回落按奖励类型的通用故事句。
     */
    private static final String REWARD_LINE = "reward";

    private static final List<CodexNode> NODES = build();

    private CodexTable() {
    }

    /** 全部节点（顺序即界面列表顺序；分类过滤由 {@link #byTier} 负责） */
    public static List<CodexNode> all() {
        return NODES;
    }

    /** 按 id 取节点；未注册的 id 返回 null（存档里的未知节点按"原样保留但界面不显示"处理） */
    @Nullable
    public static CodexNode get(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        for (CodexNode node : NODES) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }

    /** 某一级分类下的节点（页签用） */
    public static List<CodexNode> byTier(CodexTier tier) {
        List<CodexNode> found = new ArrayList<>();
        for (CodexNode node : NODES) {
            if (node.tier() == tier) {
                found.add(node);
            }
        }
        return List.copyOf(found);
    }

    /**
     * 把 {@code nodeId} 当前置的<b>后继节点</b>（"读完这页之后能顺着往下读什么"）。
     *
     * <p>关系页（{@link CodexPage.Type#RELATIONS}）要列"前置 + 后继"，
     * 而前置可由节点自带字段直读、后继必须反查——反查只有本表做得到（图是私有的），
     * 故在这里开一个只读入口，免得界面自己遍历全表重建一张图。
     */
    public static List<CodexNode> dependentsOf(@Nullable ResourceLocation nodeId) {
        if (nodeId == null) {
            return List.of();
        }
        List<CodexNode> found = new ArrayList<>();
        for (CodexNode node : NODES) {
            if (node.requiredNodes().contains(nodeId)) {
                found.add(node);
            }
        }
        return List.copyOf(found);
    }

    private static List<CodexNode> build() {
        // 1. 简单的想法：一口气能读完，读完只是轻轻提一口气（单阶：1 页思索 + 2 页正文）
        //    族 = 静与暗：全篇的钥匙是"先安静下来"，听见它贴着你耳朵叹气（耳）
        CodexNode quietThought = new CodexNode(
                id("quiet_thought"), CodexTier.SIMPLE, CodexFamily.HUSH, COL_HUSH, ROW_TOP, false,
                () -> new ItemStack(Items.ECHO_SHARD),
                0f, List.of(), List.of(),
                List.of(CodexStage.of(CodexRequirement.cog(10f))),
                List.of(
                        CodexPage.text(0, "stage1.p1", "think1"),
                        CodexPage.text(0, "stage1.p2", "think1"),
                        CodexPage.conditions(), CodexPage.rewards(), CodexPage.recipes()),
                List.of(),
                CodexReward.sanc(3f), REWARD_LINE);

        // 2. 简单的想法：两阶段，第二阶要求"人在幽匿之地"（2 页思索 + 3 页正文）
        //    族 = 静与暗：接着上一页的"安静"往下走，走到光不肯跟着的地方，让石头替你数心跳（仍是"听"）
        CodexNode breathOfDark = new CodexNode(
                id("breath_of_dark"), CodexTier.SIMPLE, CodexFamily.HUSH, COL_HUSH, ROW_BOTTOM, false,
                () -> new ItemStack(Blocks.SCULK),
                0f, List.of(), List.of(),
                List.of(
                        CodexStage.of(CodexRequirement.cog(20f)),
                        CodexStage.of(
                                CodexRequirement.cog(35f),
                                CodexRequirement.firstSeen(SanityBuiltinFirstEncounters.DEEP_DARK),
                                CodexRequirement.biome(mc("deep_dark")))),
                List.of(
                        CodexPage.text(0, "stage1.p1", "think1"),
                        CodexPage.text(1, "stage2.p1", "think2"),
                        CodexPage.text(1, "stage2.p2", "think2"),
                        CodexPage.conditions(), CodexPage.rewards(), CodexPage.recipes()),
                List.of(),
                CodexReward.giftSan(15f), REWARD_LINE);

        // 3. 疯狂的想法：先读完"静默的念头"，再挨一次凋零（替它疼）
        //    族 = 疼与血：全篇讲"用疼去听"——替它凉、替它烂，疼过的地方留下它的一小块（身）
        CodexNode coldSympathy = new CodexNode(
                id("cold_sympathy"), CodexTier.MAD, CodexFamily.BLOOD, COL_BLOOD, ROW_TOP, false,
                () -> new ItemStack(ForbiddenMaterialItems.sculkLifeform.get()),
                0f, List.of(), List.of(id("quiet_thought")),
                List.of(
                        CodexStage.of(
                                CodexRequirement.cog(30f),
                                CodexRequirement.firstSeen(SanityBuiltinFirstEncounters.SUFFER_UNNAMEABLE)),
                        CodexStage.of(
                                CodexRequirement.cog(50f),
                                CodexRequirement.damageType(mc("wither")))),
                List.of(
                        CodexPage.text(0, "stage1.p1", "think1"),
                        CodexPage.text(0, "stage1.p2", "think1"),
                        CodexPage.text(1, "stage2.p1", "think2"),
                        CodexPage.text(1, "stage2.p2", "think2"),
                        CodexPage.conditions(), CodexPage.rewards(), CodexPage.relations(), CodexPage.recipes()),
                List.of(),
                CodexReward.gift(10f, () -> ForbiddenMaterialItems.sculkLifeform.get(), 1), REWARD_LINE);

        // 4. 疯狂的想法：站在那座城里，带上三个会学舌的碎片（读完它们就碎了）
        //    族 = 名与视：全篇讲"没有眼睛却会看"——名字从血里长出来，念出来就被认得（眼）
        //    坐标特意放在下一行：让"静与暗 → 疼与血"那条横线从它上方通过（见类注释）
        CodexNode watchersName = new CodexNode(
                id("the_watchers_name"), CodexTier.MAD, CodexFamily.GAZE, COL_GAZE, ROW_BOTTOM, false,
                () -> new ItemStack(Blocks.SCULK_SHRIEKER),
                25f, List.of(SanityBuiltinFirstEncounters.WARDEN), List.of(),
                List.of(
                        CodexStage.of(
                                CodexRequirement.structure(mc("ancient_city")),
                                CodexRequirement.item(() -> Items.ECHO_SHARD, WATCHER_SHARD_COUNT, true)),
                        CodexStage.of(CodexRequirement.cog(60f))),
                List.of(
                        CodexPage.text(0, "stage1.p1", "think1"),
                        CodexPage.text(0, "stage1.p2", "think1"),
                        CodexPage.text(1, "stage2.p1", "think2"),
                        CodexPage.text(1, "stage2.p2", "think2"),
                        CodexPage.conditions(), CodexPage.rewards(), CodexPage.recipes()),
                List.of(),
                CodexReward.unlock(id("codex/the_watchers_name")), REWARD_LINE);

        // 5. 禁忌的知识：先在祂家站过，再让火认识你；可重复仪式（+5 SANC / 每次扣 SAN / 5 次上限）
        //    族 = 疼与血：全篇讲"供奉是把东西从自己身上拿下来"，火先认识你、血放出去祂才笑（身）
        CodexNode bloodOffering = new CodexNode(
                id("blood_offering"), CodexTier.FORBIDDEN, CodexFamily.BLOOD, COL_BLOOD, ROW_BOTTOM, true,
                () -> new ItemStack(Items.WITHER_ROSE),
                40f, List.of(SanityBuiltinFirstEncounters.MOTHER_ALTAR), List.of(id("the_watchers_name")),
                List.of(
                        CodexStage.of(CodexRequirement.dimension(mc("the_nether"))),
                        CodexStage.of(
                                CodexRequirement.cog(55f),
                                CodexRequirement.damageTag(DamageTypeTags.IS_FIRE))),
                List.of(
                        CodexPage.text(0, "stage1.p1", "think1"),
                        CodexPage.text(1, "stage2.p1", "think2"),
                        CodexPage.text(1, "stage2.p2", "think2"),
                        CodexPage.conditions(), CodexPage.rewards(), CodexPage.relations(), CodexPage.recipes()),
                List.of(),
                CodexReward.ritual(5f, RITUAL_SAN_COST, 5), REWARD_LINE);

        return List.of(quietThought, breathOfDark, coldSympathy, watchersName, bloodOffering);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(AkaishiMod.MOD_ID, path);
    }

    /** 原版 id（示例条件要指向原版群系 / 结构 / 维度 / 伤害类型） */
    private static ResourceLocation mc(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
