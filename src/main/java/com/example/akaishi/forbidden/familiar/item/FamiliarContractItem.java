package com.example.akaishi.forbidden.familiar.item;

import com.example.akaishi.forbidden.familiar.core.FamiliarData;
import com.example.akaishi.forbidden.familiar.core.FamiliarMode;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import com.example.akaishi.forbidden.familiar.info.FamiliarInfoMenu;
import com.example.akaishi.forbidden.familiar.FamiliarRuntimeService;
import com.example.akaishi.forbidden.soul.SoulAffinity;
import com.example.akaishi.forbidden.transmutation.life_furnace.LifeFurnaceContent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/** Bound familiar contract; registration and interaction routing are wired by the root integration. */
public class FamiliarContractItem extends Item {
    public static final String DATA_KEY = "FamiliarData";

    public FamiliarContractItem(Properties properties) {
        super(properties);
    }

    public static FamiliarData readData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new FamiliarData();
        CompoundTag root = stack.getTag();
        return root == null || !root.contains(DATA_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? new FamiliarData()
                : FamiliarData.load(root.getCompound(DATA_KEY));
    }

    public static void writeData(ItemStack stack, FamiliarData data) {
        if (stack == null || stack.isEmpty() || data == null) return;
        stack.getOrCreateTag().put(DATA_KEY, data.save());
    }

    public static void bind(ItemStack stack, UUID owner) {
        FamiliarData data = readData(stack);
        data.setOwner(owner);
        writeData(stack, data);
    }

    public static boolean isBoundTo(ItemStack stack, UUID owner) {
        return owner != null && readData(stack).isOwnedBy(owner);
    }

    public static boolean canUse(ItemStack stack, Player player) {
        if (player == null) return false;
        FamiliarData data = readData(stack);
        return data.owner() == null || data.isOwnedBy(player.getUUID());
    }

    public static void markActive(ItemStack stack, boolean active) {
        FamiliarData data = readData(stack);
        data.setActive(active);
        writeData(stack, data);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        FamiliarRuntimeService.reconcileContract(stack, level);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        FamiliarData data = readData(stack);
        if (data.owner() == null) {
            tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.owner_bound").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (data.template().isConfigured()) {
            tooltip.add(Component.translatable(data.template().translationKey()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.level", data.level()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.alignment", data.alignment()).withStyle(alignmentColor(data.alignment())));
            if (!data.affinity().isBlank()) {
                Component affinityName = SoulAffinity.byId(data.affinity())
                        .map(affinity -> Component.translatable(affinity.translationKey()))
                        .orElseGet(() -> Component.literal(data.affinity()));
                tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.affinity", affinityName).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.mode",
                    Component.translatable(data.mode().translationKey())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.mood", data.mood()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.hunger", data.hunger(), FamiliarData.MAX_HUNGER).withStyle(ChatFormatting.GRAY));
            if (data.deathPending()) {
                tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.revival_pending").withStyle(ChatFormatting.RED));
            }
        } else {
            tooltip.add(Component.translatable("item.forbidden_hex.familiar_contract.empty").withStyle(ChatFormatting.GRAY));
        }
    }

    private static ChatFormatting alignmentColor(int alignment) {
        if (alignment <= -5) return ChatFormatting.DARK_PURPLE;
        if (alignment < 0) return ChatFormatting.RED;
        if (alignment >= 5) return ChatFormatting.GOLD;
        if (alignment > 0) return ChatFormatting.YELLOW;
        return ChatFormatting.GRAY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        FamiliarRuntimeService.reconcileContract(stack, level);
        if (!canUse(stack, player)) return InteractionResultHolder.fail(stack);
        FamiliarData data = readData(stack);
        if (!data.template().isConfigured() || data.active()) {
            return InteractionResultHolder.fail(stack);
        }
        if (data.deathPending()) {
            int cost = Math.max(1, data.materialBiomassCost());
            if (!consumeBiomass(player, cost)) {
                player.displayClientMessage(Component.translatable("familiar.forbidden_hex.revival.insufficient_biomass", cost), true);
                return InteractionResultHolder.fail(stack);
            }
            data.setDeathPending(false);
            // A dead entity persisted zero health. Let the freshly created entity use its
            // template baseline, then apply level scaling and material modifiers.
            data.setHealth(0.0F);
        }
        if (data.activeEntity() != null && player.getServer() != null) {
            for (var serverLevel : player.getServer().getAllLevels()) {
                if (serverLevel.getEntity(data.activeEntity()) instanceof FamiliarEntity) {
                    return InteractionResultHolder.fail(stack);
                }
            }
        }
        FamiliarEntity familiar = AkaishiForbiddenEntities.FAMILIAR.get().create(level);
        if (familiar == null) return InteractionResultHolder.fail(stack);
        familiar.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        familiar.applyFamiliarData(data);
        familiar.bindOwner(player.getUUID());
        familiar.setPersistenceRequired();
        if (!level.addFreshEntity(familiar)) return InteractionResultHolder.fail(stack);
        data.setOwner(player.getUUID());
        data.setActive(true);
        data.setActiveEntity(familiar.getUUID());
        writeData(stack, data);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** Right-clicking a released familiar opens its panel; crouch-right-click recalls it. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        FamiliarRuntimeService.reconcileContract(stack, player.level());
        if (!canUse(stack, player)) return InteractionResult.FAIL;
        if (target instanceof FamiliarEntity familiar && familiar.isOwnedBy(player)) {
            if (player.isShiftKeyDown()) {
                if (!recall(familiar, player)) return InteractionResult.FAIL;
            } else {
                FamiliarInfoMenu.open(serverPlayer, familiar);
            }
            return InteractionResult.CONSUME;
        }
        FamiliarEntity active = findActiveFamiliar(serverPlayer, readData(stack));
        if (active != null) FamiliarInfoMenu.open(serverPlayer, active);
        return active == null ? InteractionResult.PASS : InteractionResult.CONSUME;
    }

    /** Using the contract on a block or other non-living target also opens the active familiar panel. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        FamiliarRuntimeService.reconcileContract(stack, player.level());
        if (!canUse(stack, player)) return InteractionResult.FAIL;
        FamiliarEntity active = findActiveFamiliar(serverPlayer, readData(stack));
        if (active == null) return InteractionResult.PASS;
        FamiliarInfoMenu.open(serverPlayer, active);
        return InteractionResult.CONSUME;
    }

    private static boolean recall(FamiliarEntity familiar, Player player) {
        ItemStack contract = FamiliarRuntimeService.findContractForEntity(player, familiar.getUUID());
        if (contract == null) return false;
        FamiliarData data = familiar.toFamiliarData();
        data.setActive(false);
        data.setActiveEntity(null);
        writeData(contract, data);
        familiar.discard();
        return true;
    }

    private static FamiliarEntity findActiveFamiliar(ServerPlayer player, FamiliarData data) {
        if (data == null || data.activeEntity() == null || player.getServer() == null) return null;
        for (var level : player.getServer().getAllLevels()) {
            if (level.getEntity(data.activeEntity()) instanceof FamiliarEntity entity
                    && entity.isOwnedBy(player) && entity.isAlive()) return entity;
        }
        return null;
    }

    private static boolean consumeBiomass(Player player, int amount) {
        int available = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(LifeFurnaceContent.BIOMASS.get())) available += candidate.getCount();
        }
        if (available < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (!candidate.is(LifeFurnaceContent.BIOMASS.get())) continue;
            int taken = Math.min(remaining, candidate.getCount());
            candidate.shrink(taken);
            remaining -= taken;
        }
        return true;
    }
}
