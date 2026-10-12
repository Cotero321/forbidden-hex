package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.magic.cast.MagicCarrierHook;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 载体钩子的法杖实现（装到 {@link com.example.akaishi.forbidden.magic.cast.MagicPayHooks} 上）。
 *
 * <p>四个职责，全部只认「那根真杖」：
 * <b>额外池先扣</b>（持续段逐段）、<b>循柄施法后返还</b>（只认自然完成）、
 * <b>主池恢复修正</b>（永动翻倍 / MAIN_REGEN 词条 / 蓄的溢流改道）、<b>寂的散热乘子</b>。
 * 每个入口都判空兜底 —— 载体缺席 / 链接丢失时回原版语义，绝不吞钱也不刷钱。
 */
public final class WandMagicHook implements MagicCarrierHook {

    static final WandMagicHook INSTANCE = new WandMagicHook();

    private WandMagicHook() {
    }

    @Override
    public float payFromExtra(Player player, MagicSource source, float amount) {
        var account = paymentAccount(player, source);
        if (account == null || amount <= 0f) return 0f;
        float balance = account.available();
        float paid = Math.min(balance, amount);
        account.setBalance(balance - paid);
        return paid;
    }

    @Override
    public com.example.akaishi.forbidden.magic.cast.PaymentReceipt.CarrierAccount paymentAccount(
            Player player, MagicSource source) {
        ItemStack wand = WandSessionLinks.original(player.getUUID());
        if (wand.isEmpty() || WandSessionLinks.source(player.getUUID()) != source) return null;
        float ratio = WandSessionLinks.refundRatio(player.getUUID());
        float capacity = extraCapacity(player, wand, source);
        return new com.example.akaishi.forbidden.magic.cast.PaymentReceipt.CarrierAccount() {
            public ItemStack carrier() { return wand; }
            public float available() {
                float balance = clampBalance(WandItem.extra(wand, source),
                        Math.min(capacity, extraCapacity(player, wand, source)));
                return balance;
            }
            public void setBalance(float amount) {
                WandItem.setExtra(wand, source, clampBalance(amount,
                        Math.min(capacity, extraCapacity(player, wand, source))));
            }
            public float refundRatio() { return ratio; }
        };
    }

    @Override
    public void onCastEnded(Player player, float totalPaid, boolean completed, float cooldownSeconds) {
        // 旧回调没有实付凭据，不据推算金额返还。
        onCastEnded(player, List.of(), null, completed, cooldownSeconds);
    }

    @Override
    public void onCastEnded(Player player,
            List<com.example.akaishi.forbidden.magic.cast.PaymentReceipt> successfulPayments,
            com.example.akaishi.forbidden.magic.cast.PaymentReceipt firstPayment,
            boolean completed, float cooldownSeconds) {
        try {
            if (completed && firstPayment != null && firstPayment.account() != null) {
                var account = firstPayment.account();
                float total = (float) successfulPayments.stream()
                        .filter(receipt -> receipt.account() == account && receipt.source() == firstPayment.source())
                        .mapToDouble(com.example.akaishi.forbidden.magic.cast.PaymentReceipt::totalPaid).sum();
                if (total > 0f && account.refundRatio() > 0f)
                    account.setBalance(account.available() + total * account.refundRatio());
            }
            ItemStack wand = firstPayment == null ? WandSessionLinks.original(player.getUUID())
                    : firstPayment.originalCarrier();
            // 冷却显示可回退当前手持杖，资金结算始终使用原账户。
            if (wand.isEmpty() && !heldStaffs(player).isEmpty()) wand = heldStaffs(player).get(0);
            if (!wand.isEmpty() && cooldownSeconds > 0f) {
                WandItem.setCooldownSeconds(wand, cooldownSeconds);
                player.getCooldowns().addCooldown(wand.getItem(), Math.max(1, Math.round(cooldownSeconds * 20f)));
            }
        } finally {
            WandSessionLinks.unlink(player.getUUID());
        }
    }

    private static float clampBalance(float balance, float cap) {
        return Float.isFinite(balance) ? Math.max(0f, Math.min(Math.max(0f, cap), balance)) : 0f;
    }

    @Override
    public float mainRegenMultiplier(Player player) {
        float mul = 1f;
        for (ItemStack held : heldStaffs(player)) {
            WandModifiers mods = WandModifiers.resolve(held, MagicSource.DEFAULT, List.of(), 0f, 1);
            mul *= mods.mainRegenMul();
            // 永动核：额外池耗尽（四源全空）时主池恢复翻倍
            if (mods.mainRegenDoubleWhenExtraEmpty() && allExtrasEmpty(held)) {
                mul *= 2f;
            }
        }
        return mul;
    }

    @Override
    public float overflowToExtra(Player player, MagicSource source, float amount) {
        if (amount <= 0f) {
            return 0f;
        }
        float storedTotal = 0f;
        for (ItemStack held : heldStaffs(player)) {
            WandModifiers mods = WandModifiers.resolve(held, MagicSource.DEFAULT, List.of(), 0f, 1);
            if (!mods.overflowToExtra()) {
                continue;
            }
            float cap = extraCapacity(player, held, source);
            float current = clampBalance(WandItem.extra(held, source), cap);
            WandItem.setExtra(held, source, current);
            float stored = Math.min(Math.max(0f, cap - current), amount - storedTotal);
            if (stored > 0f) {
                WandItem.setExtra(held, source, current + stored);
                storedTotal += stored;
            }
            if (storedTotal >= amount) {
                break;
            }
        }
        return storedTotal;
    }

    @Override
    public float heatDissipationMultiplier(Player player) {
        float mul = 1f;
        for (ItemStack held : heldStaffs(player)) {
            mul *= WandModifiers.resolve(held, MagicSource.DEFAULT, List.of(), 0f, 1).heatDissipationMul();
        }
        return mul;
    }

    // ==================== 内部工具 ====================

    /** 主手 + 副手的法杖（常驻词条只认"拿在手里的"—— 收起不生效） */
    private static List<ItemStack> heldStaffs(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (main.getItem() instanceof WandItem && off.getItem() instanceof WandItem) {
            return List.of(main, off);
        }
        if (main.getItem() instanceof WandItem) {
            return List.of(main);
        }
        if (off.getItem() instanceof WandItem) {
            return List.of(off);
        }
        return List.of();
    }

    /** 该源额外池容量（主池上限取玩家当前等级；无状态时按 Lv1 基准兜底） */
    private static float extraCapacity(Player player, ItemStack wand, MagicSource source) {
        MagicState state = MagicStates.state(player);
        float mainCap = state == null ? MagicState.BASE_POOL_CAPACITY : state.poolCapacity();
        WandModifiers mods = WandModifiers.resolve(wand, MagicSource.DEFAULT, List.of(), 0f, 1);
        return WandModifiers.extraPoolCapacity(mods.parts(), mainCap, source);
    }

    private static boolean allExtrasEmpty(ItemStack wand) {
        for (MagicSource source : MagicSource.values()) {
            if (WandItem.extra(wand, source) > 0f) {
                return false;
            }
        }
        return true;
    }

    /** 供初始化安装 */
    public static void install() {
        com.example.akaishi.forbidden.magic.cast.MagicPayHooks.install(INSTANCE);
    }
}
