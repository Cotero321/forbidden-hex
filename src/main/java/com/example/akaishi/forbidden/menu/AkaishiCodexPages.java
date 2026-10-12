package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.codex.CodexCondition;
import com.example.akaishi.forbidden.codex.CodexNode;
import com.example.akaishi.forbidden.codex.CodexNodeState;
import com.example.akaishi.forbidden.codex.CodexPage;
import com.example.akaishi.forbidden.codex.CodexReward;
import com.example.akaishi.forbidden.codex.CodexTable;

import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 秘典的「内容层」：把静态表里的一页算成一份<b>行</b>，并决定一篇里哪些页现在能看见。
 *
 * <p><b>为什么单独一层</b>：秘典界面这一轮变成"摊开的双页书"——书页几何、翻页、族栏是
 * {@code AkaishiCodexScreen} 的事；而"这篇现在该显示几页、每页写什么"是纯数据推导，
 * 与画在哪一页无关。两者混在一个类里，任何版式微调都会碰到内容逻辑（反之亦然）。
 * 于是内容层做成无状态静态方法：输入（节点 + 服务端快照 + 字体 + 版心宽度）→ 输出（行表）。
 *
 * <p><b>分阶段可见性（核心规则，只有这一处实现）</b>：
 * <ul>
 *   <li>阶段已完成的页 → 显示<b>正文</b>（{@link CodexNode#pageKey(CodexPage)}）；</li>
 *   <li>阶段未完成的页 → 该阶段<b>整段正文换成一页思索</b>
 *       （{@link CodexNode#speculateKey(CodexPage)}，同阶段多页正文只留第一页作思索位）；
 *       思索稿是给玩家的方向性线索，不是说明书写法，故用紫色墨（{@link #INK_HINT}）；</li>
 *   <li>{@link CodexNodeState#LOCKED}（前置没读完）→ 整篇只给<b>第 0 阶那一页思索</b>，正文一律不可见；</li>
 *   <li>条件页<b>始终可见</b>：它就是"该怎么解锁"的说明书，藏了玩家只能干瞪眼；</li>
 *   <li>奖励页 / 关联页<b>学完之后才可见</b>：没学完时它们只是剧透，且奖励还没结算，写出来是与事实脱节的空头承诺。</li>
 * </ul>
 *
 * <p><b>条件/奖励/关联三类页为什么不写进 lang</b>：见 {@link CodexPage} 的类注释——它们的真源是
 * 服务端快照与静态表，写进 lang 就会与事实脱节。这里只负责把它们拼成可读的行。
 */
final class AkaishiCodexPages {

    // ===== 纸面墨色（浅色纸张上必须用深色墨，待调手感值） =====

    /** 正文墨（深棕，比纯黑柔和，像旧钢笔） */
    static final int INK = 0xFF3A2E22;
    /** 次要墨（页眉、说明行） */
    static final int INK_DIM = 0xFF7A6A56;
    /** 小字数据行的墨（比 {@link #INK_DIM} 再淡一档，"数值退居次位"） */
    private static final int INK_DATA = 0xFF8A7A64;
    /** 满足 / 已完成 */
    static final int INK_ON = 0xFF2E6B3A;
    /** 满足时的小字数据墨（与主句同色系、更淡） */
    private static final int INK_ON_DATA = 0xFF5F7D66;
    /** 未满足 / 警告 */
    static final int INK_BAD = 0xFF9E2B2B;
    /** 未满足时的小字数据墨（与主句同色系、更淡） */
    private static final int INK_BAD_DATA = 0xFFA87373;
    /** 线索与思索（紫：它在给你指方向，不是报错） */
    static final int INK_HINT = 0xFF5B3A7A;
    /** 可点击的节点名 */
    static final int INK_LINK = 0xFF2A4FB0;
    /** 旁注（第三种墨色：暗青，与正文深棕、思索紫都拉得开） */
    static final int INK_NOTE = 0xFF2F5F5A;
    /** 被划掉的字压暗比例（保留墨色色相，只是"旧了"） */
    private static final float STRIKE_MIX = 0.62f;

    // ===== 书式排版（待调手感值） =====

    /** 段落首行缩进（像素；约两个汉字宽） */
    static final int FIRST_LINE_INDENT = 12;
    /** 旁注前的引导记号（一两笔的短横，像随手划的引线） */
    private static final String NOTE_MARK = "— ";
    /** 旁注距页外侧的留白（像素） */
    static final int NOTE_OUTER_PAD = 6;

    // ===== 小字数据行（奖励页 / 条件页；待调手感值） =====

    /** 小字数据行的缩进（比正文更靠里，读作"附注"；首行与续行同缩进 = 悬挂缩进） */
    private static final int DATA_INDENT = 14;
    /** 小字数据行的外括弧（数值行统一裹起来，一眼区分"这是数字，不是叙述"） */
    private static final String DATA_WRAP_KEY = "gui.forbidden_hex.codex.data.wrap";

    // ===== 中文避头尾（断行） =====

    /**
     * <b>行首禁则</b>标点：断行时若下一行行首会是这些字符，就把它<b>留在上一行</b>
     * （上一行允许超出 1 字宽；行尾留白 {@code AkaishiCodexRender.LINE_TAIL_PAD} ≥ 一个汉字宽，
     * 故仍在正文区之内）。
     *
     * <p>只列中文标点 —— <b>英文/数字的断行口径完全不受影响</b>（它们本来就按空格断词）。
     */
    private static final String NO_LINE_START = "。，、；：！？）》」』〕】｝”’…—·";

    // ===== 行内标记（极简两枚，容错解析见 {@link #parseInline}） =====

    /** 删除线：{@code ~~被划掉~~} */
    private static final String MARK_STRIKE = "~~";
    /** 旁注：{@code [[旁注内容]]} */
    private static final String MARK_NOTE_OPEN = "[[";
    private static final String MARK_NOTE_CLOSE = "]]";

    private AkaishiCodexPages() {
    }

    /**
     * 一页里的一行。
     *
     * @param text   该行文本（已按版心宽度切好；带删除线的片段自带 {@code strikethrough} 样式）
     * @param color  基础墨色
     * @param jump   可跳转目标（关联页用；旁注与普通行恒为 null）
     * @param indent 额外左缩进（段落首行用 {@link #FIRST_LINE_INDENT}）
     * @param note   是否旁注行（贴页边外侧绘制，见屏幕；与正文同字号，仅墨色不同）
     * @param spacer 是否段距占位（只占一行高、不画字）
     */
    record Line(FormattedCharSequence text, int color, @Nullable ResourceLocation jump,
                int indent, boolean note, boolean spacer) {

        /** 段距占位行（比行距大出一整行 = "段落间距大于行距"） */
        static Line gap() {
            return new Line(FormattedCharSequence.EMPTY, 0, null, 0, false, true);
        }
    }

    // ===== 可见性 =====

    /** 已完成的阶段数（服务端权威：快照里带；学完 = 全阶段完成，存档自相矛盾时也按全完成显示正文） */
    static int completedStages(CodexNode node, @Nullable AkaishiCodexSync.NodeView view) {
        if (view == null) {
            return 0;
        }
        if (view.learned()) {
            return node.stageCount();
        }
        return Math.max(0, Math.min(view.stage(), node.stageCount()));
    }

    /**
     * 这篇现在能读到的页（顺序即阅读序）。快照未到时按最保守口径：整篇只有第 0 阶思索 + 条件页。
     */
    static List<CodexPage> visible(CodexNode node, @Nullable AkaishiCodexSync.NodeView view) {
        int done = completedStages(node, view);
        // 前置没读完 = 连方向都还没找到：正文一律不可见（快照未到同样按沉睡处理）
        boolean locked = view == null || CodexNodeState.byId(view.state()) == CodexNodeState.LOCKED;
        boolean learned = view != null && view.learned();
        List<CodexPage> out = new ArrayList<>();
        int lastStage = Integer.MIN_VALUE;
        for (CodexPage page : node.pages()) {
            if (page.stagedText()) {
                // 同阶段的第一页才算"这一页的思索位"（表里同阶段多页正文共用一个思索稿）
                boolean firstOfStage = page.stageIndex() != lastStage;
                lastStage = page.stageIndex();
                if (page.stageIndex() < done) {
                    out.add(page);
                } else if (firstOfStage && !page.speculateKey().isEmpty() && (!locked || page.stageIndex() == 0)) {
                    out.add(page);
                }
            } else if (page.type() == CodexPage.Type.CONDITIONS || page.type() == CodexPage.Type.RECIPES) {
                // 条件页 = "怎么解锁"的说明书；配方页 = "能做出什么"的参考表 —— 两页都必须随时可查
                out.add(page);
            } else if (learned) {
                out.add(page);
            }
        }
        return out;
    }

    /** 这一页此刻是否"以思索稿顶替正文"显示（未完成的阶段） */
    static boolean speculation(CodexPage page, CodexNode node, @Nullable AkaishiCodexSync.NodeView view) {
        return page.stagedText() && page.stageIndex() >= completedStages(node, view);
    }

    /** 需要给线索吗：学完不给（不剧透）；沉睡必给；可研究但条件还差也给（差什么就写在条件页上） */
    static boolean needsHint(CodexNode node, @Nullable AkaishiCodexSync.NodeView view) {
        if (view == null) {
            return true;
        }
        if (view.learned()) {
            return false;
        }
        return CodexNodeState.byId(view.state()) == CodexNodeState.LOCKED || firstMissing(view) != null;
    }

    /** 线索文案（缺键时回落"条件未满足，再等等。"，绝不显示裸 key） */
    static Component hintText(CodexNode node) {
        return Component.translatableWithFallback(node.hintKey(),
                Component.translatable("gui.forbidden_hex.codex.hint.locked").getString());
    }

    /** 第一条没满足的条件（页脚红字与条件页共用同一口径） */
    @Nullable
    static CodexCondition firstMissing(AkaishiCodexSync.NodeView view) {
        for (CodexCondition condition : view.conditions()) {
            if (!condition.satisfied()) {
                return condition;
            }
        }
        return null;
    }

    // ===== 行构建 =====

    /** 一页 → 行表（长文本按版心宽度自动换行） */
    static List<Line> lines(CodexNode node, CodexPage page, @Nullable AkaishiCodexSync.NodeView view,
                            Font font, int width) {
        List<Line> out = new ArrayList<>();
        switch (page.type()) {
            case TEXT -> {
                boolean speculate = speculation(page, node, view);
                String key = speculate ? node.speculateKey(page) : node.pageKey(page);
                // 缺键回落"这一页没有条目。"：绝不把裸 key 印在书页上
                String fallback = Component.translatable("gui.forbidden_hex.codex.page.empty").getString();
                appendProse(out, font, Component.translatableWithFallback(key, fallback),
                        speculate ? INK_HINT : INK, width);
            }
            case CONDITIONS -> appendConditions(out, node, view, font, width);
            case REWARDS -> appendRewards(out, node, view, font, width);
            case RELATIONS -> appendRelations(out, node, font, width);
            // 配方页没有可翻的行：合成表/占位由渲染框架直接画（见 AkaishiCodexRender#recipePage）
            case RECIPES -> { }
        }
        return out;
    }

    /**
     * 条件页：线索（该给时）→ 逐条条件。
     *
     * <p><b>每条条件 = 故事化主句 + 小字准确数据</b>（用户拍板"也故事化，数值退居次位"）：
     * <ul>
     *   <li>主句按 {@link CodexCondition#kind() kind}（必要时按 variant）取通用故事句，
     *       <b>满足与未满足各一套口吻</b> —— 不会出现"绿字还在说'我还没…'"；</li>
     *   <li>小字行给<b>准确数据</b>：沿用既有 {@code gui.forbidden_hex.codex.cond.*} 数值模板（外裹括弧），
     *       暗墨 + 悬挂缩进，比主句更不显眼；</li>
     *   <li>一眼可辨：主句绿/红 + 小字同色系更淡；</li>
     *   <li><b>可点击跳转照旧</b>：未满足的前置条件行（主句与小字行<b>都</b>可点）跳到该节点。</li>
     * </ul>
     */
    private static void appendConditions(List<Line> out, CodexNode node,
                                         @Nullable AkaishiCodexSync.NodeView view, Font font, int width) {
        if (needsHint(node, view)) {
            appendWrapped(out, font, Component.translatable("gui.forbidden_hex.codex.page.hint", hintText(node)),
                    INK_HINT, width, 0, 0, null, false);
        }
        if (view != null && view.learned()) {
            // 学完之后仍给一句收束，但改成笔记口吻（不再是"✓ 这一页你已经读完了。"那种说明书句）
            appendWrapped(out, font, Component.translatable("gui.forbidden_hex.codex.detail.learned"),
                    INK_ON, width, 0, 0, null, false);
            return;
        }
        if (view == null) {
            append(out, font, Component.translatable("gui.forbidden_hex.codex.cond.pending"), INK_DIM, width);
            return;
        }
        List<CodexCondition> conditions = view.conditions();
        if (conditions.isEmpty()) {
            append(out, font, Component.translatable("gui.forbidden_hex.codex.cond.none"), INK_DIM, width);
            return;
        }
        for (CodexCondition condition : conditions) {
            boolean satisfied = condition.satisfied();
            // 未满足的"前置"仍是唯一的跨节点显式跳转路径（翻页本身不跨篇），故主句与小字行都可点
            ResourceLocation jump = jumpTarget(condition, satisfied);
            appendWrapped(out, font, storyLine(condition, satisfied),
                    satisfied ? INK_ON : INK_BAD, width, 0, 0, jump, false);
            appendWrapped(out, font, Component.translatable(DATA_WRAP_KEY, conditionText(condition)),
                    satisfied ? INK_ON_DATA : INK_BAD_DATA, width, DATA_INDENT, DATA_INDENT, jump, false);
        }
    }

    /**
     * 条件的故事化主句（按 kind，必要时按 variant）。
     *
     * <p><b>为什么满足/未满足要两套</b>：主句是<b>叙述</b>（"我还没见过那样的东西。"），
     * 同一句话若在已满足时染成绿色会自相矛盾；两套口吻后，颜色只做强调，语义由句子本身承担。
     */
    private static Component storyLine(CodexCondition condition, boolean satisfied) {
        return Component.translatable(satisfied
                ? satisfiedStoryKey(condition.kind())
                : unsatisfiedStoryKey(condition));
    }

    /** 已满足的主句键（按 kind；variant 不必再分） */
    private static String satisfiedStoryKey(byte kind) {
        return switch (kind) {
            case CodexCondition.KIND_COG -> "gui.forbidden_hex.codex.cond.line.cog.ok";
            case CodexCondition.KIND_FIRST_SEEN -> "gui.forbidden_hex.codex.cond.line.first_seen.ok";
            case CodexCondition.KIND_PREREQ -> "gui.forbidden_hex.codex.cond.line.prereq.ok";
            case CodexCondition.KIND_ITEM -> "gui.forbidden_hex.codex.cond.line.item.ok";
            case CodexCondition.KIND_LOCATION -> "gui.forbidden_hex.codex.cond.line.location.ok";
            case CodexCondition.KIND_DAMAGE -> "gui.forbidden_hex.codex.cond.line.damage.ok";
            default -> "gui.forbidden_hex.codex.cond.line.unknown.ok";
        };
    }

    /** 未满足的主句键（按 kind；ITEM 的"会烧掉"与 LOCATION/DAMAGE 的四种形式各有措辞） */
    private static String unsatisfiedStoryKey(CodexCondition condition) {
        return switch (condition.kind()) {
            case CodexCondition.KIND_COG -> "gui.forbidden_hex.codex.cond.line.cog";
            case CodexCondition.KIND_FIRST_SEEN -> "gui.forbidden_hex.codex.cond.line.first_seen";
            case CodexCondition.KIND_PREREQ -> "gui.forbidden_hex.codex.cond.line.prereq";
            case CodexCondition.KIND_ITEM -> condition.variant() == CodexCondition.ITEM_CONSUME
                    ? "gui.forbidden_hex.codex.cond.line.item_consume" : "gui.forbidden_hex.codex.cond.line.item";
            case CodexCondition.KIND_LOCATION -> switch (condition.variant()) {
                case CodexCondition.LOC_BIOME_TAG -> "gui.forbidden_hex.codex.cond.line.location_biome_tag";
                case CodexCondition.LOC_DIMENSION -> "gui.forbidden_hex.codex.cond.line.location_dimension";
                case CodexCondition.LOC_STRUCTURE -> "gui.forbidden_hex.codex.cond.line.location_structure";
                default -> "gui.forbidden_hex.codex.cond.line.location_biome";
            };
            case CodexCondition.KIND_DAMAGE -> condition.variant() == CodexCondition.DAMAGE_TAG
                    ? "gui.forbidden_hex.codex.cond.line.damage_tag" : "gui.forbidden_hex.codex.cond.line.damage";
            default -> "gui.forbidden_hex.codex.cond.line.unknown";
        };
    }

    /** 未满足的前置条件 → 可跳转目标（静态表里没有该 id 则不可跳，仍按普通条件显示） */
    @Nullable
    private static ResourceLocation jumpTarget(CodexCondition condition, boolean satisfied) {
        if (satisfied || condition.kind() != CodexCondition.KIND_PREREQ) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(condition.arg());
        return id != null && CodexTable.get(id) != null ? id : null;
    }

    /**
     * 奖励页：<b>一句故事化主句 + 若干行小字数据</b>（用户拍板"故事化 + 小字数据"）。
     *
     * <p><b>结构</b>：
     * <ol>
     *   <li><b>主句</b>：节点专属文案键（{@link CodexNode#rewardLineKey()}）优先，缺键回落
     *       "按奖励类型的通用故事句"（{@link #genericRewardKey}）——第一人称、笔记口吻；</li>
     *   <li><b>小字数据</b>：每条奖励一行（SANC / 回理智 / 物品 各自一行；RITUAL 带上"已用 n/m"），
     *       外裹括弧、暗墨、悬挂缩进，比正文更不显眼；</li>
     *   <li>没有可给的奖励时给一句笔记口吻的收尾，而不是说明书写法。</li>
     * </ol>
     */
    private static void appendRewards(List<Line> out, CodexNode node,
                                      @Nullable AkaishiCodexSync.NodeView view, Font font, int width) {
        CodexReward reward = node.reward();
        if (reward == null) {
            append(out, font, Component.translatable("gui.forbidden_hex.codex.page.rewards.none"), INK_DIM, width);
            return;
        }
        append(out, font, rewardStory(node, reward), INK, width);
        List<Component> data = rewardData(reward, view);
        if (data.isEmpty()) {
            append(out, font, Component.translatable("gui.forbidden_hex.codex.page.rewards.none"), INK_DIM, width);
            return;
        }
        for (Component line : data) {
            appendWrapped(out, font, Component.translatable(DATA_WRAP_KEY, line),
                    INK_DATA, width, DATA_INDENT, DATA_INDENT, null, false);
        }
    }

    /** 奖励主句：节点专属键优先、缺键回落按奖励类型的通用故事句（绝不把裸 key 印在书页上） */
    private static Component rewardStory(CodexNode node, CodexReward reward) {
        String lineKey = node.rewardLineKey();
        String fallback = Component.translatable(genericRewardKey(reward)).getString();
        return lineKey == null ? Component.literal(fallback)
                : Component.translatableWithFallback(lineKey, fallback);
    }

    /**
     * 通用故事句键（按奖励类型）。
     *
     * <p>GIFT 兼给物品时取"给物品"那一条（物品是实物，比"回了多少理智"更值得当主句）；
     * 只判 {@code item() != null} / {@code itemCount()}，<b>不在此处求值</b> Supplier（规避注册顺序）。
     */
    private static String genericRewardKey(CodexReward reward) {
        return switch (reward.type()) {
            case SANC -> "gui.forbidden_hex.codex.reward.line.sanc";
            case UNLOCK -> "gui.forbidden_hex.codex.reward.line.unlock";
            case RITUAL -> "gui.forbidden_hex.codex.reward.line.ritual";
            case GIFT -> reward.item() != null && reward.itemCount() > 0
                    ? "gui.forbidden_hex.codex.reward.line.gift_item" : "gui.forbidden_hex.codex.reward.line.gift_san";
        };
    }

    /** 小字数据行（数值口径与机制层一致；RITUAL 额外带"已用 n / m"） */
    private static List<Component> rewardData(CodexReward reward, @Nullable AkaishiCodexSync.NodeView view) {
        List<Component> out = new ArrayList<>();
        switch (reward.type()) {
            case SANC -> {
                if (reward.sanc() != 0f) {
                    out.add(Component.translatable("gui.forbidden_hex.codex.reward.sanc", fmt(reward.sanc())));
                }
            }
            case GIFT -> {
                if (reward.sanRestore() != 0f) {
                    out.add(Component.translatable("gui.forbidden_hex.codex.reward.san", fmt(reward.sanRestore())));
                }
                Item item = itemOf(reward);
                if (item != null && reward.itemCount() > 0) {
                    out.add(Component.translatable("gui.forbidden_hex.codex.reward.item",
                            new ItemStack(item).getHoverName(), reward.itemCount()));
                }
            }
            case UNLOCK -> out.add(Component.translatable("gui.forbidden_hex.codex.reward.unlock",
                    reward.unlockKey() == null ? "-" : reward.unlockKey().toString()));
            case RITUAL -> out.add(Component.translatable("gui.forbidden_hex.codex.reward.ritual",
                    fmt(reward.sanc()), fmt(reward.ritualSanCost()),
                    rewardUses(view), reward.ritualMaxUses()));
        }
        return out;
    }

    /** 奖励物品（延迟求值；注册表尚未就绪时只让这一行不出现，不把界面拖崩） */
    @Nullable
    private static Item itemOf(CodexReward reward) {
        if (reward.item() == null) {
            return null;
        }
        try {
            return reward.item().get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 可重复节点的已用次数（与界面按钮"已用 n/m"同源；快照未到按 0） */
    private static int rewardUses(@Nullable AkaishiCodexSync.NodeView view) {
        return view == null ? 0 : Math.max(0, view.uses());
    }

    /** 关联页：前置（节点自带）+ 后继（反查静态表），两者都可点击跳转 */
    private static void appendRelations(List<Line> out, CodexNode node, Font font, int width) {
        List<CodexNode> dependents = CodexTable.dependentsOf(node.id());
        boolean any = false;
        if (!node.requiredNodes().isEmpty()) {
            append(out, font, Component.translatable("gui.forbidden_hex.codex.page.relations.prereq"), INK_DIM, width);
            for (ResourceLocation id : node.requiredNodes()) {
                appendJumpLine(out, font, CodexTable.get(id), id, width);
                any = true;
            }
        }
        if (!dependents.isEmpty()) {
            append(out, font, Component.translatable("gui.forbidden_hex.codex.page.relations.dependent"), INK_DIM, width);
            for (CodexNode target : dependents) {
                appendJumpLine(out, font, target, target.id(), width);
                any = true;
            }
        }
        if (!any) {
            append(out, font, Component.translatable("gui.forbidden_hex.codex.page.relations.none"), INK_DIM, width);
        }
    }

    /** 可跳转行（静态表里没有的 id 只显示 id 本身，点了也不跳） */
    private static void appendJumpLine(List<Line> out, Font font, @Nullable CodexNode target,
                                       ResourceLocation id, int width) {
        String name = target == null ? id.toString() : Component.translatable(target.nameKey()).getString();
        appendWrapped(out, font, Component.literal("» " + name),
                target == null ? INK_DIM : INK_LINK, width, 0, 0, target == null ? null : id, false);
    }

    /** 清单式一行（条件 / 奖励 / 关联这类"条目"的通用入口：不缩进、不解析标记） */
    private static void append(List<Line> out, Font font, Component text, int color, int width) {
        appendWrapped(out, font, text, color, width, 0, 0, null, false);
    }

    // ===== 断行（中文避头尾的唯一实现） =====

    /** 行内片段：一段文字 + 它的样式（{@link #parseInline} 与通用 {@link Component} 都压成它） */
    private record Piece(String text, Style style) {
    }

    /**
     * 把一段文本按可用宽断行并落成 {@link Line}（<b>全内容层唯一的断行入口</b>）。
     *
     * @param firstIndent   首行额外左缩进（像素）
     * @param hangingIndent 续行左缩进（像素；小字数据行用悬挂缩进）
     * @param jump          可跳转目标（换行后的<b>每一段</b>都可点；null = 普通行）
     * @param note          是否旁注行（贴页外侧绘制）
     */
    private static void appendWrapped(List<Line> out, Font font, Component text, int color, int width,
                                      int firstIndent, int hangingIndent,
                                      @Nullable ResourceLocation jump, boolean note) {
        appendPieces(out, font, flatten(text), color, width, firstIndent, hangingIndent, jump, note);
    }

    /** 同 {@link #appendWrapped}，但直接吃已解析好的片段表（散文正文走这条，省一次往返转换） */
    private static void appendPieces(List<Line> out, Font font, List<Piece> pieces, int color, int width,
                                     int firstIndent, int hangingIndent,
                                     @Nullable ResourceLocation jump, boolean note) {
        List<List<Piece>> wrapped = wrap(pieces, font, width, firstIndent, hangingIndent);
        for (int i = 0; i < wrapped.size(); i++) {
            out.add(new Line(render(wrapped.get(i)).getVisualOrderText(), color, jump,
                    i == 0 ? firstIndent : hangingIndent, note, false));
        }
    }

    /**
     * 断行（含<b>中文避头尾</b>）。
     *
     * <p><b>为什么不用 {@code Font#split}</b>：原版断行对无空格的中文是"到宽就断"，
     * 于是句号/逗号会被挤到下一行行首（用户实测截图）。这里自己按字符宽度贪心装行，
     * 并在断行点做一次禁则判定。
     *
     * <p><b>规则（只作用于中文标点，英文/数字不受影响）</b>：若下一行行首会是
     * {@link #NO_LINE_START} 里的标点，就把它<b>留在上一行</b>（上一行最多再宽一个汉字）——
     * 选"留在上一行"而不是"把上一行末字一起下移"：不动已经排好的字，读者视线不会被拽回头，
     * 且行尾留白 {@code LINE_TAIL_PAD}（≥ 一个汉字宽）已按此预留，绝不会挤到滚动条或纸缘。
     *
     * <p><b>兜底（不得死循环 / 不得吞字）</b>：
     * <ul>
     *   <li>光标每轮至少前进 1 个码点（一个字符都装不下时也强吃一个）⇒ 循环必然终止；</li>
     *   <li>只按码点切分（不拆代理对），且消费区间连续无重叠 ⇒ 字符一个不丢；</li>
     *   <li>标点已是最后一个字符时不做"吸"（没有下一行），它自己独占一行也照常显示。</li>
     * </ul>
     */
    private static List<List<Piece>> wrap(List<Piece> pieces, Font font, int width,
                                          int firstIndent, int hangingIndent) {
        // 压平成"一个码点一片段"（样式留给 render 合并，这里只关心宽度与禁则）
        List<Piece> units = new ArrayList<>();
        for (Piece piece : pieces) {
            int i = 0;
            while (i < piece.text().length()) {
                int codePoint = piece.text().codePointAt(i);
                int size = Character.charCount(codePoint);
                units.add(new Piece(piece.text().substring(i, i + size), piece.style()));
                i += size;
            }
        }
        List<List<Piece>> lines = new ArrayList<>();
        if (units.isEmpty()) {
            return lines;
        }
        int[] widths = new int[units.size()];
        for (int k = 0; k < units.size(); k++) {
            widths[k] = Math.max(1, font.width(units.get(k).text()));
        }
        int i = 0;
        int index = 0;
        while (i < units.size()) {
            int limit = Math.max(1, width - (index == 0 ? firstIndent : hangingIndent));
            int used = 0;
            int j = i;
            while (j < units.size() && used + widths[j] <= limit) {
                used += widths[j];
                j++;
            }
            if (j == i) {
                // 兜底①：一个字符都放不下（极窄版心/超宽单元）也要吃掉一个
                j = i + 1;
            }
            // 避头尾：下一行行首是禁则标点 ⇒ 留在本行
            if (j < units.size() && isNoLineStart(units.get(j))) {
                j++;
            }
            lines.add(new ArrayList<>(units.subList(i, j)));
            i = j;
            index++;
        }
        return lines;
    }

    /** 该单元是否为"行首禁则"标点（只认中文标点；英文/数字的断行口径不受影响） */
    private static boolean isNoLineStart(Piece unit) {
        return unit.text().length() == 1 && NO_LINE_START.indexOf(unit.text().charAt(0)) >= 0;
    }

    /** 通用 {@link Component} → 片段表（保留每个片段的样式；空片段丢掉） */
    private static List<Piece> flatten(Component text) {
        List<Piece> out = new ArrayList<>();
        text.visit((style, content) -> {
            if (!content.isEmpty()) {
                out.add(new Piece(content, style));
            }
            return Optional.<Object>empty();
        }, Style.EMPTY);
        return out;
    }

    /** 片段表 → 带样式的文本（相邻同样式合并，免得一个字符一个 child） */
    private static Component render(List<Piece> line) {
        MutableComponent out = Component.empty();
        StringBuilder buffer = new StringBuilder();
        Style current = null;
        for (Piece piece : line) {
            if (current == null || !current.equals(piece.style())) {
                if (current != null && buffer.length() > 0) {
                    out.append(Component.literal(buffer.toString()).withStyle(current));
                }
                buffer.setLength(0);
                current = piece.style();
            }
            buffer.append(piece.text());
        }
        if (current != null && buffer.length() > 0) {
            out.append(Component.literal(buffer.toString()).withStyle(current));
        }
        return out;
    }

    // ===== 书式正文（首行缩进 + 段距 + 行内标记） =====

    /**
     * 散文式正文：<b>首行缩进</b>（段首缩进 {@link #FIRST_LINE_INDENT}）、<b>段落间距</b>
     * （段与段之间插一行占位 = 段距为 2 倍行距，明显大于行距）、<b>行内标记</b>（见 {@link #parseInline}）。
     *
     * <p>换行口径：<b>首行</b>按"可用行宽 − 首行缩进"，<b>续行</b>吃满可用行宽（版心宽与可用行宽已分开，
     * 涨出来的那一档见 {@code AkaishiCodexRender#LINE_TAIL_PAD}）；断行含中文避头尾，见 {@link #wrap}。
     */
    static void appendProse(List<Line> out, Font font, Component text, int color, int width) {
        String raw = text.getString();
        boolean first = out.isEmpty();
        for (String rawParagraph : raw.split("\n", -1)) {
            String paragraph = rawParagraph.strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            List<String> notes = new ArrayList<>();
            List<Piece> pieces = parseInline(paragraph, notes, color);
            if (!first) {
                out.add(Line.gap());
            }
            first = false;
            appendPieces(out, font, pieces, color, width, FIRST_LINE_INDENT, 0, null, false);
            // 旁注排在它所批的那段之后，贴页边另起一行（与正文同字号，靠墨色与贴边位置区分）
            for (String note : notes) {
                appendWrapped(out, font, Component.literal(NOTE_MARK + note), INK_NOTE,
                        noteWrapWidth(width), 0, 0, null, true);
            }
        }
    }

    /** 旁注行的换行宽度：再让出一份贴边留白，保证右对齐画到页外侧时也不会越出版心 */
    private static int noteWrapWidth(int width) {
        return Math.max(24, width - NOTE_OUTER_PAD);
    }

    /**
     * 解析一行的行内标记，返回<b>带样式的片段表</b>，并把旁注收进 {@code notes}。
     *
     * <p><b>容错（硬要求）</b>：标记不闭合时按<b>普通文字</b>处理 ——
     * 未闭合的 {@code ~~} / {@code [[} 原样落进正文（一个字符都不吞，也绝不抛异常）。
     * 所以解析全程只有 {@code indexOf} 与下标推进，没有正则、没有异常路径。
     *
     * <ul>
     *   <li>{@code ~~字~~} → 删除线（样式带 {@code strikethrough}，墨色按 {@link #STRIKE_MIX} 压暗）；</li>
     *   <li>{@code [[...]]} → 旁注（内容原样取出，不参与删除线解析）。</li>
     * </ul>
     */
    private static List<Piece> parseInline(String raw, List<String> notes, int baseColor) {
        List<Piece> out = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        boolean strike = false;
        int i = 0;
        while (i < raw.length()) {
            if (raw.startsWith(MARK_NOTE_OPEN, i)) {
                int close = raw.indexOf(MARK_NOTE_CLOSE, i + MARK_NOTE_OPEN.length());
                if (close >= 0) {
                    flush(out, plain, strike, baseColor);
                    notes.add(raw.substring(i + MARK_NOTE_OPEN.length(), close));
                    i = close + MARK_NOTE_CLOSE.length();
                    continue;
                }
                plain.append(MARK_NOTE_OPEN);
                i += MARK_NOTE_OPEN.length();
                continue;
            }
            if (raw.startsWith(MARK_STRIKE, i)) {
                if (raw.indexOf(MARK_STRIKE, i + MARK_STRIKE.length()) >= 0) {
                    flush(out, plain, strike, baseColor);
                    strike = !strike;
                    i += MARK_STRIKE.length();
                    continue;
                }
                plain.append(MARK_STRIKE);
                i += MARK_STRIKE.length();
                continue;
            }
            plain.append(raw.charAt(i));
            i++;
        }
        flush(out, plain, strike, baseColor);
        return out;
    }

    /** 把累积的普通文字作为一段片段追加（删除线段落自带压暗色与删除线样式） */
    private static void flush(List<Piece> out, StringBuilder plain, boolean strike, int baseColor) {
        if (plain.length() == 0) {
            return;
        }
        String piece = plain.toString();
        plain.setLength(0);
        Style style = strike
                ? Style.EMPTY.withStrikethrough(true)
                        .withColor(TextColor.fromRgb(darken(baseColor, STRIKE_MIX) & 0xFFFFFF))
                : Style.EMPTY;
        out.add(new Piece(piece, style));
    }

    /** 压暗一色（保留色相；被划掉的字用） */
    private static int darken(int argb, float mix) {
        int r = (int) (((argb >> 16) & 0xFF) * mix);
        int g = (int) (((argb >> 8) & 0xFF) * mix);
        int b = (int) ((argb & 0xFF) * mix);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ===== 条件文案（六种 kind 全数覆盖，arg 指什么见 CodexCondition 的 javadoc） =====

    static Component conditionText(CodexCondition condition) {
        return switch (condition.kind()) {
            case CodexCondition.KIND_COG -> Component.translatable("gui.forbidden_hex.codex.cond.cog",
                    fmt(condition.required()), fmt(condition.current()));
            case CodexCondition.KIND_FIRST_SEEN -> Component.translatable("gui.forbidden_hex.codex.cond.first_seen",
                    firstSeenName(condition.arg()));
            case CodexCondition.KIND_PREREQ -> Component.translatable("gui.forbidden_hex.codex.cond.prereq",
                    nodeName(condition.arg()));
            case CodexCondition.KIND_ITEM -> Component.translatable(
                    condition.variant() == CodexCondition.ITEM_CONSUME
                            ? "gui.forbidden_hex.codex.cond.item_consume" : "gui.forbidden_hex.codex.cond.item",
                    itemName(condition.arg()), fmt(condition.required()), fmt(condition.current()));
            case CodexCondition.KIND_LOCATION -> Component.translatable(locationKey(condition.variant()),
                    placeName(condition.arg()));
            case CodexCondition.KIND_DAMAGE -> Component.translatable(
                    condition.variant() == CodexCondition.DAMAGE_TAG
                            ? "gui.forbidden_hex.codex.cond.damage_tag" : "gui.forbidden_hex.codex.cond.damage",
                    damageName(condition.arg(), condition.variant()));
            default -> Component.translatable("gui.forbidden_hex.codex.cond.unknown", Byte.toString(condition.kind()));
        };
    }

    /** 地点条件的四个子类型各有各的措辞（标签形式也要能读出来） */
    private static String locationKey(byte variant) {
        return switch (variant) {
            case CodexCondition.LOC_BIOME_TAG -> "gui.forbidden_hex.codex.cond.location_biome_tag";
            case CodexCondition.LOC_DIMENSION -> "gui.forbidden_hex.codex.cond.location_dimension";
            case CodexCondition.LOC_STRUCTURE -> "gui.forbidden_hex.codex.cond.location_structure";
            default -> "gui.forbidden_hex.codex.cond.location_biome";
        };
    }

    /** 节点 id 字符串 → 显示名（本地静态表；未知 id 原样显示，绝不空白） */
    private static Component nodeName(String nodeId) {
        ResourceLocation id = ResourceLocation.tryParse(nodeId);
        CodexNode node = CodexTable.get(id);
        return node == null ? Component.literal(nodeId) : Component.translatable(node.nameKey());
    }

    /** 首见 id 路径 → 短名（缺键时回落路径本身） */
    private static Component firstSeenName(String path) {
        return Component.translatableWithFallback("codex.forbidden_hex.first_seen." + path, path);
    }

    /**
     * 地点 id → 显示名：走 {@code codex.forbidden_hex.place.<path>}（本模组的双语短名），
     * 缺键时<b>回落完整 id</b>（如 {@code minecraft:deep_dark}）—— 绝不显示裸 key，也不显示空白。
     */
    private static Component placeName(String targetId) {
        ResourceLocation id = ResourceLocation.tryParse(targetId);
        if (id == null) {
            return Component.literal(targetId);
        }
        return Component.translatableWithFallback("codex.forbidden_hex.place." + id.getPath(), targetId);
    }

    /** 伤害 id / 标签 → 显示名（两种形式两套键前缀，缺键回落完整 id） */
    private static Component damageName(String targetId, byte variant) {
        ResourceLocation id = ResourceLocation.tryParse(targetId);
        if (id == null) {
            return Component.literal(targetId);
        }
        String prefix = variant == CodexCondition.DAMAGE_TAG
                ? "codex.forbidden_hex.damage_tag." : "codex.forbidden_hex.damage.";
        return Component.translatableWithFallback(prefix + id.getPath(), targetId);
    }

    /** 物品 id → 当前语言物品名；未注册的 id 原样显示（存档里的旧 id 不该让界面崩，也不该显示空白） */
    private static Component itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Component.literal(itemId);
        }
        Item item = BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
        return item == null || item == Items.AIR ? Component.literal(itemId) : new ItemStack(item).getHoverName();
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
