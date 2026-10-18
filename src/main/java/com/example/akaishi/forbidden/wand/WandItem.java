package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.ForbiddenIds;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import net.minecraft.resources.ResourceLocation;
import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.visual.ItemTooltipMode;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 法杖本体（法杖记忆 §1）：杖端 + 杖柄 + 杖核 <b>合成时定型</b>（定案 12，不可拆卸），
 * 另有 1 个咒核法术槽（镶嵌页装卸，定案 3）。
 *
 * <p><b>NBT 即身份</b>：三段 id + 咒核 + 四源额外池都在物品栈内（定案 3.2）——
 * 额外池是"这根杖"的账本而不是玩家的，换杖不共享。数值合成（§10 引擎）由
 * 施法链按三段 id 现查现算，不在 NBT 里存结果（表改了旧杖自动跟着新规则走）。
 *
 * <p><b>显示名</b>（定案 11）：{@code 法杖·<端>·<柄>·<核>}，短名由 lang 键承担（双语各一套）。
 */
public class WandItem extends Item {

    public static final String TAG_TIP = "Tip";
    public static final String TAG_HANDLE = "Handle";
    public static final String TAG_CORE = "Core";
    /** 咒核（完整物品栈 tag；装卸由铭文台镶嵌页承担，定案 3） */
    public static final String TAG_SPELL = "Spell";
    /** 四源额外池现值（float；容量 = 主池上限 ×10% × Π(1+修正) ＋ 绝对项，现算现夹） */
    public static final String TAG_EXTRA_PREFIX = "Extra";
    /** 下次额外池恢复时刻（不落盘语义：缺键 = 立即排程） */
    public static final String TAG_EXTRA_REGEN_AT = "ExtraRegenAt";
    /** 本次冷却<b>总时长</b>（秒）—— HUD 拿它乘原版冷却剩余比例，得到"实际剩余秒数" */
    public static final String TAG_COOLDOWN_SECONDS = "CooldownSeconds";

    public WandItem(Properties properties) {
        super(properties);
    }

    /**
     * 右键施法（C4 正式入口，§44.4）：服务端走 {@link WandCasting}（读法术槽咒核 → 载体调整 → 施法链）。
     * 空槽 / 拒绝原因由动作栏反馈；成功则进入前摇（与调试入口同一状态机）。
     */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (level.isClientSide()) {
            // 客户端先进入举杖状态，服务端会用施法状态包确认或清空表现；失败时不会结算玩法。
            player.startUsingItem(hand);
            return net.minecraft.world.InteractionResultHolder.consume(wand);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            if (WandCasting.castFromHand(server, wand)) {
                player.startUsingItem(hand);
                return net.minecraft.world.InteractionResultHolder.consume(wand);
            }
            com.example.akaishi.forbidden.magic.cast.MagicCastManager.syncState(server);
        }
        return net.minecraft.world.InteractionResultHolder.fail(wand);
    }

    @Override
    public int getUseDuration(ItemStack stack) { return 72000; }

    @Override
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity user, int remaining) {
        if (!level.isClientSide && user instanceof Player player) {
            com.example.akaishi.forbidden.magic.cast.MagicCastManager.stop(player);
        }
    }

    // ==================== 三段 ====================

    public static String tipId(ItemStack stack) {
        return partId(stack, TAG_TIP);
    }

    public static String handleId(ItemStack stack) {
        return partId(stack, TAG_HANDLE);
    }

    public static String coreId(ItemStack stack) {
        return partId(stack, TAG_CORE);
    }

    /** 合成定型：写入三段（只在配方组装时调用一次） */
    public static void setParts(ItemStack stack, String tipId, String handleId, String coreId) {
        stack.getOrCreateTag().putString(TAG_TIP, tipId);
        stack.getOrCreateTag().putString(TAG_HANDLE, handleId);
        stack.getOrCreateTag().putString(TAG_CORE, coreId);
    }

    private static String partId(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : ForbiddenIds.itemPath(tag.getString(key));
    }

    // ==================== 咒核法术槽 ====================

    /** 法术槽内的咒核；空槽或数据损坏返回 {@link ItemStack#EMPTY} */
    public static ItemStack spell(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SPELL)) return ItemStack.EMPTY;
        CompoundTag spellTag = tag.getCompound(TAG_SPELL);
        ResourceLocation oldId = ResourceLocation.tryParse(spellTag.getString("id"));
        if (oldId != null) {
            ResourceLocation newId = ForbiddenIds.current(oldId);
            if (!newId.equals(oldId)) {
                spellTag = spellTag.copy();
                spellTag.putString("id", newId.toString());
            }
        }
        return ItemStack.of(spellTag);
    }

    /** 装入/替换咒核（空栈 = 卸下） */
    public static void setSpell(ItemStack stack, ItemStack spell) {
        if (spell.isEmpty()) {
            stack.removeTagKey(TAG_SPELL);
        } else {
            CompoundTag spellTag = new CompoundTag();
            spell.save(spellTag);
            stack.getOrCreateTag().put(TAG_SPELL, spellTag);
        }
    }

    // ==================== 手持 / 咒核读数（HUD 与施法表现共用）====================

    /** 手中法杖：主手优先、其次副手；都不是法杖返回 {@link ItemStack#EMPTY} */
    public static ItemStack heldWand(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof WandItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof WandItem ? off : ItemStack.EMPTY;
    }

    /** 杖内咒核的源；未装核 / 不是咒核返回 {@code null}（表现层据此整体不显示） */
    public static MagicSource socketedSource(ItemStack wand) {
        ItemStack spell = spell(wand);
        return spell.getItem() instanceof MagicCoreItem core ? core.source() : null;
    }

    /**
     * 杖内咒核的<b>主墨 id</b>（槽位序第一个有墨的）；无墨返回空串。
     *
     * <p>与施法链取主墨的口径一致（首个非空）—— 表现层要靠它选粒子的"形"。
     */
    public static String mainInkId(ItemStack spell) {
        return MagicCoreItem.mainInkId(spell);
    }

    /**
     * 杖内咒核的<b>主墨识别色</b>；无墨返回白色。
     *
     * <p>表现层需要"这一发的身份色"而手上只有物品栈时用它。
     */
    public static int mainInkRgb(ItemStack spell) {
        var ink = com.example.akaishi.forbidden.magic.table.MagicInkTable.get(mainInkId(spell));
        return ink == null ? 0xFFFFFF : ink.rgb();
    }

    // ==================== 额外池（四源分开，定案 §0-10）====================

    /** 额外池现值（float：消耗与恢复都有小数；容量 = 主池上限 ×10% × Π(1+修正) ＋ 绝对项，现算现夹） */
    public static float extra(ItemStack stack, MagicSource source) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0f : tag.getFloat(TAG_EXTRA_PREFIX + source.id());
    }

    public static void setExtra(ItemStack stack, MagicSource source, float amount) {
        stack.getOrCreateTag().putFloat(TAG_EXTRA_PREFIX + source.id(), Math.max(0f, amount));
    }

    /** 下次额外池恢复时刻（{@code getGameTime()}；0 = 未排程，结算层会立即补上） */
    public static long extraRegenAt(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0L : tag.getLong(TAG_EXTRA_REGEN_AT);
    }

    public static void setExtraRegenAt(ItemStack stack, long gameTime) {
        stack.getOrCreateTag().putLong(TAG_EXTRA_REGEN_AT, gameTime);
    }

    // ==================== 冷却总时长（HUD 环内秒数的分母）====================

    /** 本次冷却总秒数；0 = 无记录（HUD 环内不写数字，宁可不写也不编假数） */
    public static float cooldownSeconds(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0f : Math.max(0f, tag.getFloat(TAG_COOLDOWN_SECONDS));
    }

    /** 冷却起算时写入（服务端；与原版物品冷却同一时刻打，两者语义一致） */
    public static void setCooldownSeconds(ItemStack stack, float seconds) {
        stack.getOrCreateTag().putFloat(TAG_COOLDOWN_SECONDS, Math.max(0f, seconds));
    }

    // ==================== 显示 ====================

    @Override
    public Component getName(ItemStack stack) {
        String tip = tipId(stack);
        if (tip.isEmpty()) {
            return super.getName(stack);
        }
        return Component.translatable("item.forbidden_hex.forbidden_hex_wand",
                Component.translatable(shortKey(tip)),
                Component.translatable(shortKey(handleId(stack))),
                Component.translatable(shortKey(coreId(stack))));
    }

    private static String shortKey(String partId) {
        return "item.forbidden_hex." + partId + ".short";
    }

    /** 空槽提示里的按键名（客户端取当前绑定显示；服务端退通用文案） */
    private static Component socketKeyHint() {
        return com.example.akaishi.forbidden.wand.WandSocketC2S.keybindHint();
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemTooltipMode mode = Platform.getEnvironment() == Env.CLIENT
                ? ItemTooltipMode.select(Screen.hasShiftDown(), Screen.hasControlDown()) : ItemTooltipMode.SUMMARY;
        appendDetails(stack, tooltip, mode);
    }

    public static void appendDetails(ItemStack stack, List<Component> tooltip, ItemTooltipMode mode) {
        WandPartDef tipDef = WandTipTable.get(tipId(stack));
        WandPartDef handleDef = WandHandleTable.get(handleId(stack));
        WandPartDef coreDef = WandCoreTable.get(coreId(stack));
        List<WandPartDef> parts = parts(stack);
        if (mode == ItemTooltipMode.SUMMARY) WandTooltips.appendAggregated(tooltip, parts);
        if (parts.size() != 3) {
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.invalid_parts").withStyle(ChatFormatting.RED));
        }
        ItemStack spell = spell(stack);
        tooltip.add(Component.translatable(spell.isEmpty()
                        ? "gui.forbidden_hex.wand.staff.spell_empty" : "gui.forbidden_hex.wand.staff.spell",
                spell.isEmpty() ? socketKeyHint() : spell.getHoverName())
                .withStyle(ChatFormatting.GRAY));
        if (mode == ItemTooltipMode.USAGE) {
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.section.usage").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.use").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.socket", socketKeyHint()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.assembly").withStyle(ChatFormatting.GRAY));
        } else if (mode == ItemTooltipMode.TECHNICAL) {
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.section.technical").withStyle(ChatFormatting.GOLD));
        }
        if (!spell.isEmpty()) WandTooltips.appendPreview(tooltip, stack, spell, mode);
        if (mode == ItemTooltipMode.SUMMARY) {
            for (MagicSource source : MagicSource.values()) {
                float amount = extra(stack, source);
                if (amount > 0f) {
                    tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.extra",
                            source.label(), Math.round(amount)).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else if (mode == ItemTooltipMode.TECHNICAL) {
            MagicSource activeSource = socketedSource(stack);
            if (activeSource == null) activeSource = MagicSource.DEFAULT;
            WandModifiers mods = WandModifiers.resolve(stack, activeSource, List.of(), 0f, 1);
            float mainCap = clientMainPoolCapacity();
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.section.reserve").withStyle(ChatFormatting.GOLD));
            for (MagicSource source : MagicSource.values()) {
                tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.reserve_source", source.label(),
                        trimNum(extra(stack, source)), trimNum(WandModifiers.extraPoolCapacity(parts, mainCap, source)),
                        trimNum(WandModifiers.extraRegenAmount(parts, source))).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.regen_interval", trimNum(mods.extraRegenInterval()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.capacity_preview").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.heat",
                    WandTooltips.signed(mods.heatThresholdMul()), WandTooltips.signed(mods.heatDissipationMul()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            appendSection(tooltip, "tip", tipDef);
            appendSection(tooltip, "handle", handleDef);
            appendSection(tooltip, "core", coreDef);
            if (spell.getItem() instanceof MagicCoreItem) {
                tooltip.add(Component.translatable("item.forbidden_hex.magic_core.tip.section.slots").withStyle(ChatFormatting.GOLD));
                MagicCoreItem.appendSlots(spell, tooltip);
            }
        }
        tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.more").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 三段定义（缺失段跳过 —— 表外 id 不炸 tooltip / HUD）；tooltip 与数值引擎共用此一份 */
    public static List<WandPartDef> parts(ItemStack stack) {
        List<WandPartDef> parts = new ArrayList<>(3);
        WandPartDef tipDef = WandTipTable.get(tipId(stack));
        WandPartDef handleDef = WandHandleTable.get(handleId(stack));
        WandPartDef coreDef = WandCoreTable.get(coreId(stack));
        if (tipDef != null) parts.add(tipDef);
        if (handleDef != null) parts.add(handleDef);
        if (coreDef != null) parts.add(coreDef);
        return parts;
    }

    private static void appendSection(List<Component> tooltip, String sectionKey, WandPartDef def) {
        if (def == null) return;
        tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.section." + sectionKey)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("gui.forbidden_hex.wand.staff.part_identity",
                Component.translatable(shortKey(def.id())), def.id()).withStyle(ChatFormatting.DARK_GRAY));
        WandTooltips.appendPart(tooltip, def);
    }

    /**
     * 客户端取玩家等级推主池容量（额外池容量 = 主池 ×10% 的基数）。
     * 只读取同步镜像；取不到快照退 Lv1 基准。
     */
    private static float clientMainPoolCapacity() {
        if (com.example.akaishi.forbidden.magic.ClientMagicData.hasData()) {
            return MagicState.BASE_POOL_CAPACITY
                    + MagicState.POOL_CAPACITY_PER_LEVEL
                            * (com.example.akaishi.forbidden.magic.ClientMagicData.snapshot().level()
                                    - MagicState.LEVEL_MIN);
        }
        return MagicState.BASE_POOL_CAPACITY;
    }

    /** 数值去尾零（1.0 → 1、2.5 → 2.5），工程行显示用 */
    private static String trimNum(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }
}
