package com.example.akaishi.forbidden.item;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.sanity.content.SanityTonicService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 理智剂（{@code forbidden_hex:forbidden_hex_sanity_tonic} 理智回复剂 / {@code forbidden_hex:forbidden_hex_greater_sanity_tonic} 高级理智恢复剂）：
 * 饮下后在窗口内<b>分批</b>补回 SAN，随后进入长冷却。
 *
 * <p><b>为什么走"饮用动画 + finishUsingItem"而不是右键瞬发</b>：
 * <ol>
 *   <li>用完一口的时点在 Forge 里统一为 {@code LivingEntityUseItemEvent.Finish} ——
 *       与食补、SANC 首用挂在同一个事件上（见 forge 侧 {@code AkaishiSanityFoodHandler}），
 *       因此"喝下一瓶"天然同时触发"窗口补量"与"首用抬升上限"两条链路，不需要第二套钩子；</li>
 *   <li>饮用途中的动画/音效由原版 {@link UseAnim#DRINK} 免费提供（零新增音效资源）。</li>
 * </ol>
 *
 * <p><b>冷却为什么在 {@link #use} 里拦、而不是在喝完时判定</b>：在 {@code use} 阶段拦下才能
 * <b>不消耗物品</b>（喝到一半才被判"冷却中"会白白吞掉一瓶）。冷却权威是玩家落盘的时间戳
 * （见 {@link SanityTonicService}），不依赖物品 NBT，故"带一背包多瓶"绕不过去。
 *
 * <p>物品本身<b>不含任何数值口径</b>：窗口总量/冷却时长由注册处按档位传入，口径集中在
 * {@link SanityTonicService} 的常量里（待调手感值）。
 */
public class SanityTonicItem extends Item {

    /** 饮用耗时（tick）：32 = 原版药水同款 1.6s（待调手感值） */
    private static final int USE_DURATION_TICKS = 32;

    /** 窗口/冷却的档位键（= 物品注册 id 字符串，见 {@link #restoreId()}） */
    private final String restoreId;
    private final float windowSan;
    private final int windowTicks;
    private final int cooldownTicks;

    /**
     * @param idPath        物品注册 id（路径部分），用于拼出窗口/冷却的档位键
     * @param windowSan     窗口内补回的 SAN 总量
     * @param windowTicks   窗口长度（tick）
     * @param cooldownTicks 冷却时长（tick）
     */
    public SanityTonicItem(Properties properties, String idPath, float windowSan, int windowTicks, int cooldownTicks) {
        super(properties);
        this.restoreId = new ResourceLocation(AkaishiMod.MOD_ID, idPath).toString();
        this.windowSan = windowSan;
        this.windowTicks = windowTicks;
        this.cooldownTicks = cooldownTicks;
    }

    /** 档位键：窗口（SanityState.FoodRuntime）与冷却（SanityState 物品冷却表）共用的唯一定位符 */
    public String restoreId() {
        return restoreId;
    }

    public float windowSan() {
        return windowSan;
    }

    public int windowTicks() {
        return windowTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /**
     * 右键：冷却中直接拒绝（不消耗、无收益、提示剩余时间）；否则进入饮用。
     *
     * <p>客户端用原版冷却表做<b>预测拦截</b>（服务端设置冷却时经 {@code ClientboundCooldownPacket} 同步，
     * 故客户端知道自己在冷却）：否则本地会先播一遍饮用动画，再被服务端否决，表现为"喝了但没反应"。
     * 服务端以落盘时间戳为准（客户端数据不可信）。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }
        } else {
            int remaining = SanityTonicService.cooldownRemainingTicks(player, this);
            if (remaining > 0) {
                player.displayClientMessage(Component.translatable("message.forbidden_hex.sanity.tonic_cooldown",
                        SanityTonicService.formatSeconds(remaining)), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 喝完一口：开窗口 + 落冷却（服务端），并按创造模式规则消耗物品（照原版 {@code LivingEntity#eat} 口径） */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) {
            if (!level.isClientSide) {
                SanityTonicService.drink(player, this);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    /** 说明行：把"补多少 / 多久补完 / 冷却多久"写在物品上，避免"看得到用不到"（数值来自注册处传入的档位） */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.forbidden_hex.forbidden_hex_sanity_tonic.tooltip",
                trim(windowSan), windowTicks / 20, cooldownTicks / 20 / 60));
    }

    /** 去掉小数尾（30.0 → "30"），避免 tooltip 出现 "30.0 点" */
    private static String trim(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : Float.toString(value);
    }
}
