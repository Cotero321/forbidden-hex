package com.example.akaishi.forbidden.brewing;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class BrewingPotionItem extends Item {
    public BrewingPotionItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<BrewingPotionData> data = BrewingPotionData.read(stack);
        if (data.isEmpty()) return InteractionResultHolder.fail(stack);
        if (data.get().delivery() == BrewingPotionData.Delivery.DRINK) {
            player.startUsingItem(hand);
        } else if (!level.isClientSide) {
            BrewingPotionProjectile projectile = BrewingEntities.POTION.get().create(level);
            if (projectile == null) return InteractionResultHolder.fail(stack);
            projectile.setOwner(player);
            projectile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            projectile.setItem(stack.copyWithCount(1));
            projectile.setNoGravity(data.get().delivery() == BrewingPotionData.Delivery.DIRECT);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(),
                    data.get().delivery() == BrewingPotionData.Delivery.DIRECT ? 0 : -20, 0.5F, 0);
            if (!level.addFreshEntity(projectile)) return InteractionResultHolder.fail(stack);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Optional<BrewingPotionData> data = BrewingPotionData.read(stack);
        if (level.isClientSide || data.isEmpty() || data.get().delivery() != BrewingPotionData.Delivery.DRINK) return stack;
        data.get().apply(entity, 1);
        if (entity instanceof Player player && player.getAbilities().instabuild) return stack;
        stack.shrink(1);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) return bottle;
        if (entity instanceof Player player && !player.getInventory().add(bottle)) player.drop(bottle, false);
        return stack;
    }

    @Override public int getUseDuration(ItemStack stack) { return 32; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BrewingPotionData.read(stack).ifPresentOrElse(data -> {
            tooltip.add(Component.translatable("tooltip.forbidden_hex.brewing.volume", BrewingPotionData.VOLUME));
            tooltip.add(Component.translatable("tooltip.forbidden_hex.brewing.attributes", data.capacity(), data.strength(), data.duration()));
            tooltip.add(Component.translatable("tooltip.forbidden_hex.brewing.delivery." + data.delivery().name().toLowerCase(java.util.Locale.ROOT)));
            if (data.hideParticles()) tooltip.add(Component.translatable("tooltip.forbidden_hex.brewing.hide_particles"));
            if (data.triggerPressurePlates()) tooltip.add(Component.translatable("tooltip.forbidden_hex.brewing.pressure_plates"));
            for (var effect : data.effects()) {
                tooltip.add(Component.translatable(effect.getDescriptionId()).append(" " + (effect.getAmplifier() + 1)));
            }
        }, () -> tooltip.add(Component.translatable("tooltip.forbidden_hex.brewing.empty")));
    }
}
