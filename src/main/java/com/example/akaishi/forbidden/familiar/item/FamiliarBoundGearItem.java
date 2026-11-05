package com.example.akaishi.forbidden.familiar.item;

import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/** Non-durable, familiar-bound item accepted by exactly one of the familiar gear slots. */
public final class FamiliarBoundGearItem extends Item {
    public enum SlotType { EQUIPMENT, ACCESSORY }

    public static final String OWNER_TAG = "FamiliarGearOwner";
    public static final String FAMILIAR_TAG = "FamiliarGearId";

    private final SlotType slotType;

    public FamiliarBoundGearItem(Properties properties, SlotType slotType) {
        super(properties.stacksTo(1));
        this.slotType = slotType;
    }

    public SlotType slotType() {
        return slotType;
    }

    public boolean canBindTo(ItemStack stack, FamiliarEntity familiar, Player player) {
        if (stack == null || stack.isEmpty() || !familiar.isOwnedBy(player) || familiar.familiarId() == null) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null) return true;
        boolean hasOwner = tag.hasUUID(OWNER_TAG);
        boolean hasFamiliar = tag.hasUUID(FAMILIAR_TAG);
        if (!hasOwner && !hasFamiliar) return true;
        return hasOwner && hasFamiliar
                && tag.getUUID(OWNER_TAG).equals(player.getUUID())
                && tag.getUUID(FAMILIAR_TAG).equals(familiar.familiarId());
    }

    public void bindTo(ItemStack stack, FamiliarEntity familiar, Player player) {
        if (!canBindTo(stack, familiar, player)) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(OWNER_TAG, player.getUUID());
        tag.putUUID(FAMILIAR_TAG, familiar.familiarId());
    }

    public static boolean isBoundTo(ItemStack stack, UUID owner, UUID familiar) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return false;
        CompoundTag tag = stack.getTag();
        return tag.hasUUID(OWNER_TAG) && tag.hasUUID(FAMILIAR_TAG)
                && tag.getUUID(OWNER_TAG).equals(owner) && tag.getUUID(FAMILIAR_TAG).equals(familiar);
    }

    @Nullable
    public static FamiliarBoundGearItem of(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof FamiliarBoundGearItem gear
                ? gear : null;
    }
}
