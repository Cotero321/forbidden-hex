package com.example.akaishi.forbidden.transmutation.source_furnace;

import com.example.akaishi.forbidden.altar.SourceEnergyNetwork;
import com.example.akaishi.forbidden.transmutation.TransmutationEnergyTiers;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative two-slot source-energy transmutation processor. */
public final class SourceTransmutationFurnaceBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int SLOTS = 2;
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_WAITING_ENERGY = 2;
    public static final int STATUS_INVALID_INPUT = 3;
    public static final int STATUS_OUTPUT_BLOCKED = 4;
    public static final String OUTPUT_TAG = "TransmutationOutput";
    public static final String ENERGY_TAG = "TransmutationEnergy";
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int progress;
    private int duration;
    private int tier;
    private int status = STATUS_IDLE;
    @Nullable private ItemStack pendingOutput;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> duration;
                case 2 -> tier;
                case 3 -> status;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = Math.max(0, value);
                case 1 -> duration = Math.max(0, value);
                case 2 -> tier = Math.max(0, Math.min(2, value));
                case 3 -> status = Math.max(0, value);
            }
        }
        @Override public int getCount() { return 4; }
    };

    public SourceTransmutationFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(SourceTransmutationFurnaceContent.BLOCK_ENTITY.get(), pos, state);
        this.tier = state.hasProperty(SourceTransmutationFurnaceBlock.TIER) ? state.getValue(SourceTransmutationFurnaceBlock.TIER) : 0;
    }

    public ContainerData data() { return data; }
    public int progress() { return progress; }
    public int duration() { return duration; }
    public int tier() { return tier; }
    public int status() { return status; }

    public void cycleTier() {
        if (status == STATUS_PROCESSING) return;
        tier = (tier + 1) % TransmutationEnergyTiers.values().length;
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(SourceTransmutationFurnaceBlock.TIER)) {
                level.setBlock(worldPosition, state.setValue(SourceTransmutationFurnaceBlock.TIER, tier), 3);
            }
        }
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SourceTransmutationFurnaceBlockEntity furnace) {
        if (!(level instanceof ServerLevel server)) return;
        if (furnace.status == STATUS_PROCESSING) {
            furnace.advance(server);
        } else {
            furnace.tryStart(server);
        }
        boolean working = furnace.status == STATUS_PROCESSING;
        if (state.getValue(SourceTransmutationFurnaceBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(SourceTransmutationFurnaceBlock.WORKING, working), 3);
        }
    }

    private void tryStart(ServerLevel server) {
        if (pendingOutput != null || progress != 0) return;
        ItemStack input = items.get(INPUT);
        if (input.isEmpty()) { status = STATUS_IDLE; return; }
        ItemStack output = resolveOutput(input);
        if (output.isEmpty()) { status = STATUS_INVALID_INPUT; return; }
        if (!outputFits(output)) { status = STATUS_OUTPUT_BLOCKED; return; }
        TransmutationEnergyTiers energyTier = TransmutationEnergyTiers.values()[Math.max(0, Math.min(2, tier))];
        if (!SourceEnergyNetwork.request(server, worldPosition, energyTier.energy(), true)) {
            status = STATUS_WAITING_ENERGY;
            return;
        }
        if (!SourceEnergyNetwork.request(server, worldPosition, energyTier.energy(), false)) {
            status = STATUS_WAITING_ENERGY;
            return;
        }
        items.get(INPUT).shrink(1);
        pendingOutput = output;
        progress = 0;
        duration = energyTier.processingTicks();
        status = STATUS_PROCESSING;
        setChanged();
    }

    private void advance(ServerLevel server) {
        if (pendingOutput == null) {
            progress = 0;
            duration = 0;
            status = STATUS_INVALID_INPUT;
            return;
        }
        if (!outputFits(pendingOutput)) {
            status = STATUS_OUTPUT_BLOCKED;
            return;
        }
        progress = Math.min(duration, progress + 1);
        if (progress < duration) {
            setChanged();
            return;
        }
        addOutput(pendingOutput);
        pendingOutput = null;
        progress = 0;
        duration = 0;
        status = STATUS_IDLE;
        setChanged();
    }

    private ItemStack resolveOutput(ItemStack input) {
        if (!input.hasTag() || !input.getTag().contains(OUTPUT_TAG, Tag.TAG_STRING)) return ItemStack.EMPTY;
        String id = input.getTag().getString(OUTPUT_TAG);
        if (id.isBlank()) return ItemStack.EMPTY;
        final ResourceLocation outputId;
        try {
            outputId = new ResourceLocation(id);
        } catch (IllegalArgumentException invalidId) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(outputId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack output = new ItemStack(item, 1);
        if (input.getTag().contains("TransmutationOutputTag", Tag.TAG_COMPOUND)) {
            output.setTag(input.getTag().getCompound("TransmutationOutputTag").copy());
        }
        output.getOrCreateTag().putInt(ENERGY_TAG, TransmutationEnergyTiers.values()[tier].energy());
        return output;
    }

    private boolean outputFits(ItemStack addition) {
        ItemStack existing = items.get(OUTPUT);
        int limit = Math.min(64, addition.getMaxStackSize());
        return existing.isEmpty() || ItemStack.isSameItemSameTags(existing, addition)
                && existing.getCount() + addition.getCount() <= limit;
    }

    private void addOutput(ItemStack addition) {
        ItemStack existing = items.get(OUTPUT);
        if (existing.isEmpty()) items.set(OUTPUT, addition.copy());
        else existing.grow(addition.getCount());
    }

    @Override public Component getDisplayName() { return Component.translatable("block.forbidden_hex.source_transmutation_furnace"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new SourceTransmutationFurnaceMenu(id, inventory, this, data); }
    @Override public void saveExtraData(FriendlyByteBuf buffer) { buffer.writeBlockPos(worldPosition); }
    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) setChanged();
        return stack;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS || slot == OUTPUT || status == STATUS_PROCESSING) return;
        items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), 64)));
        setChanged();
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64.0D;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == INPUT && status != STATUS_PROCESSING && !stack.isEmpty(); }
    @Override public int[] getSlotsForFace(Direction face) {
        return face == Direction.DOWN ? new int[]{OUTPUT} : face == Direction.UP ? new int[]{INPUT} : new int[0];
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction face) {
        return slot == INPUT && face == Direction.UP && canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return slot == OUTPUT && face == Direction.DOWN; }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Progress", progress);
        tag.putInt("Duration", duration);
        tag.putInt("Tier", tier);
        tag.putInt("Status", status);
        if (pendingOutput != null) tag.put("PendingOutput", pendingOutput.save(new CompoundTag()));
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        progress = Math.max(0, tag.getInt("Progress"));
        duration = Math.max(0, tag.getInt("Duration"));
        tier = Math.max(0, Math.min(2, tag.getInt("Tier")));
        status = Math.max(0, Math.min(STATUS_OUTPUT_BLOCKED, tag.getInt("Status")));
        pendingOutput = tag.contains("PendingOutput", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("PendingOutput")) : null;
        if (pendingOutput != null && pendingOutput.isEmpty()) pendingOutput = null;
    }
}
