package com.example.akaishi.forbidden.codex;

import com.example.akaishi.forbidden.sanity.SanityState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 秘典门槛求值：把"这个节点/这一阶还差什么"算成一份可显示的条件清单，并给出节点三态。
 *
 * <p><b>为什么求值只在这里做</b>：服务端要用它做<b>权威校验</b>（不满足就拒绝推进），
 * 界面要用它做<b>提示与置灰</b>。若两处各写一遍判断，迟早会出现"按钮亮着、服务端却拒"的不一致。
 * 于是只有一处求值，服务端与快照下发共用同一份结果。
 *
 * <p><b>代价与节流（关键）</b>：六种条件里有两类是"贵查询"——
 * 物品要扫背包，地点可能要查结构。因此本类的求值<b>只在两个时机</b>被调用：
 * ① 打开秘典菜单后推快照时；② 收到一次研究请求时。绝不在玩家 tick 里跑，
 * 所以"每玩家每秒一次结构查询"这类预算根本不会被触及（详见 {@code AkaishiCodexSync} 的触发时机）。
 * 结构判定用 {@code getStructureWithPieceAt}（只读本区块已加载的结构表），
 * 不用跨区块搜索；伤害标签判定只在有记档时才查注册表。
 *
 * <p><b>顺序固定</b>：节点级认知 → 节点级首见 → 前置节点 → 阶段级条件（按表中声明顺序）。
 * 顺序固定是为了让同一节点的条件清单在两次打开界面之间不跳动。
 */
public final class CodexGates {

    private CodexGates() {
    }

    /**
     * 求"推进到第 {@code stage}+1 阶段"所需的全部条件。
     *
     * @param player 求值依据的玩家（背包装物品、位置、注册表都在他身上）；客户端传 null 时退化为"只看存档事实"
     * @param state  玩家理智状态（含首见记档、秘典进度、挨过的伤害）
     * @param stage  当前已完成的阶段数（= 即将研究的阶段下标）；越界时只返回节点级条件
     */
    public static List<CodexCondition> evaluate(ServerPlayer player, SanityState state,
                                                CodexNode node, int stage) {
        List<CodexCondition> conditions = new ArrayList<>();
        if (state == null || node == null) {
            return conditions;
        }
        // 节点级：整页都要满足
        if (node.cog() > 0f) {
            conditions.add(CodexCondition.cog(node.cog(), state.cog()));
        }
        for (ResourceLocation id : node.firstSeen()) {
            conditions.add(firstSeen(state, id));
        }
        for (ResourceLocation prereq : node.requiredNodes()) {
            conditions.add(prereq(state, prereq));
        }
        // 阶段级：只取"即将研究"的那一阶，按表中声明顺序逐条求值
        List<CodexStage> stages = node.stages();
        if (stage >= 0 && stage < stages.size()) {
            for (CodexRequirement requirement : stages.get(stage).requirements()) {
                conditions.add(evaluate(player, state, requirement));
            }
        }
        return conditions;
    }

    /** 条件是否全部满足（任一不满足即不可推进） */
    public static boolean allSatisfied(List<CodexCondition> conditions) {
        for (CodexCondition condition : conditions) {
            if (!condition.satisfied()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 节点三态（界面据此决定画法；只有三种，不存在第四态）。
     *
     * <p>第三态只看<b>前置节点</b>：前置没读完 = {@link CodexNodeState#LOCKED}；
     * 前置读完（此时可能条件已全满足、也可能还差）= {@link CodexNodeState#AVAILABLE}。
     * "条件还差"不另立一态——它已经由逐条条件的 satisfied + canAct 表达了。
     */
    public static CodexNodeState nodeState(SanityState state, CodexNode node) {
        if (state != null && node != null && state.hasCodexNode(node.id().toString())) {
            return CodexNodeState.LEARNED;
        }
        return prerequisitesMet(state, node) ? CodexNodeState.AVAILABLE : CodexNodeState.LOCKED;
    }

    /** 前置节点是否都已学完（"可推断"的判据） */
    public static boolean prerequisitesMet(SanityState state, CodexNode node) {
        if (state == null || node == null) {
            return false;
        }
        for (ResourceLocation prereq : node.requiredNodes()) {
            if (prereq == null || !state.hasCodexNode(prereq.toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 背包内某物品的总个数（含护甲与副手槽，不含外部容器）。
     *
     * <p>逐槽累加而不是用 {@code Inventory} 的检索方法：口径（哪些槽算"持有"）由本类独占，
     * 校验端（{@link CodexService} 的扣物）与本类必须用同一口径，否则会出现
     * "判定够了、扣的时候却扣不出"的偏差。
     */
    public static int countItems(Player player, Item item) {
        if (player == null || item == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------
    // 六种条件的求值
    // ------------------------------------------------------------------

    private static CodexCondition evaluate(ServerPlayer player, SanityState state,
                                           CodexRequirement requirement) {
        return switch (requirement.kind()) {
            case CodexCondition.KIND_FIRST_SEEN -> firstSeen(state, requirement.id());
            case CodexCondition.KIND_PREREQ -> prereq(state, requirement.id());
            case CodexCondition.KIND_ITEM -> item(player, requirement);
            case CodexCondition.KIND_LOCATION -> location(player, requirement);
            case CodexCondition.KIND_DAMAGE -> damage(player, state, requirement);
            default -> CodexCondition.cog(requirement.cog(), state.cog());
        };
    }

    /** 首见条件：只比 id 字符串（与 {@link SanityState#hasFirstSeen} 同一套记档，不查注册表） */
    private static CodexCondition firstSeen(SanityState state, ResourceLocation id) {
        return CodexCondition.firstSeen(id == null ? "" : id.getPath(),
                id != null && state.hasFirstSeen(id.toString()));
    }

    /** 前置节点条件：{@code arg} 为节点完整 id（同样只比字符串，界面从静态表取显示名） */
    private static CodexCondition prereq(SanityState state, ResourceLocation nodeId) {
        return CodexCondition.prereq(nodeId == null ? "" : nodeId.toString(),
                nodeId != null && state.hasCodexNode(nodeId.toString()));
    }

    /** 物品条件：数背包里现在有几个（缺物品实例时按"0 个、未满足"处理，不抛异常） */
    private static CodexCondition item(ServerPlayer player, CodexRequirement requirement) {
        Item item = requirement.item() == null ? null : requirement.item().get();
        if (item == null) {
            return CodexCondition.item("", requirement.count(), 0, requirement.consume());
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        int current = countItems(player, item);
        return CodexCondition.item(id == null ? "" : id.toString(),
                requirement.count(), current, requirement.consume());
    }

    /**
     * 地点条件：按子类型分派。
     *
     * <p><b>"去过"的落地口径</b>：用户要求"拿 {@code ServerPlayer} 现算即可"，
     * 故此处判据是"求值这一刻玩家人在该处"（不额外记轨迹；轨迹会引入一套新的落盘与容错）。
     * 求值时机被 {@link AkaishiCodexSync} 限定在"打开界面 / 收到请求"，因此玩家站在洞里翻开秘典
     * 就能满足"去过幽匿之地"。
     */
    private static CodexCondition location(ServerPlayer player, CodexRequirement requirement) {
        byte variant = requirement.variant();
        ResourceLocation target = requirement.id();
        boolean satisfied = false;
        if (player != null && target != null) {
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();
            satisfied = switch (variant) {
                case CodexCondition.LOC_BIOME ->
                        level.getBiome(pos).is(ResourceKey.create(Registries.BIOME, target));
                case CodexCondition.LOC_BIOME_TAG -> biomeTag(level, pos, requirement.tag());
                case CodexCondition.LOC_DIMENSION -> level.dimension().location().equals(target);
                case CodexCondition.LOC_STRUCTURE -> level.structureManager()
                        .getStructureWithPieceAt(pos, CodexRequirement.structureKey(target))
                        .isValid();
                default -> false;
            };
        }
        return CodexCondition.location(variant, target == null ? "" : target.toString(), satisfied);
    }

    /** 伤害条件：单个类型按 id 直查；标签形式遍历已记档的 id 反查注册表成员 */
    private static CodexCondition damage(ServerPlayer player, SanityState state,
                                         CodexRequirement requirement) {
        ResourceLocation target = requirement.id();
        boolean satisfied = false;
        if (target != null) {
            if (requirement.variant() == CodexCondition.DAMAGE_TYPE) {
                satisfied = state.hasDamageSeen(target.toString());
            } else if (requirement.variant() == CodexCondition.DAMAGE_TAG && player != null) {
                satisfied = damageSeenTag(player, state, requirement.tag());
            }
        }
        return CodexCondition.damage(requirement.variant(),
                target == null ? "" : target.toString(), satisfied);
    }

    /** 群系标签判定（{@code TagKey<?>} 按 variant 断定泛型，转换只在这一处） */
    private static boolean biomeTag(ServerLevel level, BlockPos pos, TagKey<?> tag) {
        if (tag == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        TagKey<Biome> biomeTag = (TagKey<Biome>) tag;
        return level.getBiome(pos).is(biomeTag);
    }

    /**
     * 伤害类型标签判定：遍历玩家已记档的伤害类型 id，看是否有成员落在该标签内。
     *
     * <p>为什么反着遍历（遍历记档而不是遍历标签）：记档通常只有几条，而标签可能很大；
     * 且记档里的未知 id（附属卸载后）在这里被自然跳过，不需要额外容错分支。
     */
    private static boolean damageSeenTag(ServerPlayer player, SanityState state, TagKey<?> tag) {
        if (tag == null || state.damageSeen().isEmpty()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        TagKey<DamageType> damageTag = (TagKey<DamageType>) tag;
        Registry<DamageType> registry = player.serverLevel()
                .registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Optional<HolderSet.Named<DamageType>> members = registry.getTag(damageTag);
        if (members.isEmpty()) {
            return false;
        }
        for (String id : state.damageSeen()) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            DamageType type = key == null ? null : registry.get(key);
            if (type == null) {
                continue;
            }
            for (Holder<DamageType> holder : members.get()) {
                if (holder.value() == type) {
                    return true;
                }
            }
        }
        return false;
    }
}
