package com.example.akaishi.forbidden.soul;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/** Registry entry point for data-less soul fragments and NBT-bearing soul crystals. */
public final class SoulContent {
    public static RegistrySupplier<Item> SOUL_FRAGMENT;
    public static RegistrySupplier<Item> SOUL_CRYSTAL;

    private SoulContent() {
    }

    public static void register() {
        var items = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ITEM);
        SOUL_FRAGMENT = items.register(new ResourceLocation(AkaishiMod.MOD_ID, "soul_fragment"),
                SoulFragmentItem::new);
        SOUL_CRYSTAL = items.register(new ResourceLocation(AkaishiMod.MOD_ID, "soul_crystal"), SoulCrystalItem::new);
    }

    /** Four data-less fragments combine on use; the resulting crystal receives fresh random data. */
    public static final class SoulFragmentItem extends Item {
        private SoulFragmentItem() {
            super(new Properties());
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack fragments = player.getItemInHand(hand);
            if (fragments.getCount() < 4) return InteractionResultHolder.fail(fragments);
            if (level.isClientSide) return InteractionResultHolder.sidedSuccess(fragments, true);

            fragments.shrink(4);
            ItemStack crystal = new ItemStack(SOUL_CRYSTAL.get());
            SoulRandomizer.randomize(crystal.getOrCreateTag(), level.random);
            if (!player.getInventory().add(crystal)) player.drop(crystal, false);
            return InteractionResultHolder.sidedSuccess(fragments, false);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.forbidden_hex.soul_fragment.combine_hint")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static final class SoulCrystalItem extends Item {
        private SoulCrystalItem() {
            super(new Properties());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            SoulCrystalData.Data data = SoulCrystalData.read(stack.getOrCreateTag());
            if (!data.hasAffinity()) {
                tooltip.add(Component.translatable("item.forbidden_hex.soul_crystal.unformed")
                        .withStyle(ChatFormatting.GRAY));
                if (data.neutralized()) {
                    tooltip.add(Component.translatable("item.forbidden_hex.soul_crystal.neutralized")
                            .withStyle(ChatFormatting.AQUA));
                }
                return;
            }
            tooltip.add(Component.translatable("item.forbidden_hex.soul_crystal.affinity", data.affinity().id())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("item.forbidden_hex.soul_crystal.alignment", data.alignment())
                    .withStyle(data.alignment() < 0 ? ChatFormatting.RED : ChatFormatting.GOLD));
            if (data.neutralized()) {
                tooltip.add(Component.translatable("item.forbidden_hex.soul_crystal.neutralized")
                        .withStyle(ChatFormatting.AQUA));
            }
            if (data.randomized()) {
                tooltip.add(Component.translatable("item.forbidden_hex.soul_crystal.randomized")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
