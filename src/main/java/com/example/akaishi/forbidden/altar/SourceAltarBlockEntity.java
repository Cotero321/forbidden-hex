package com.example.akaishi.forbidden.altar;

import com.example.akaishi.forbidden.block.entity.AkaishiForbiddenBlockEntities;
import com.example.akaishi.forbidden.menu.SourceAltarMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SourceAltarBlockEntity extends BlockEntity implements MenuProvider {
    public static final int DATA_COUNT = 28;
    private final ContainerData data = new SimpleContainerData(DATA_COUNT);
    private final SimpleContainer pedestals = new SimpleContainer(4);
    private ItemStack display = ItemStack.EMPTY;
    private long energyUnits;
    private int recoveryTicks;
    private SourceAltarRules.Snapshot snapshot;
    private SourceAltarScanner scanner;
    private SourceAltarEnvironmentCache environment;
    private final Set<BlockPos> changedPositions = new LinkedHashSet<>();
    private final Map<Long, Boolean> changedChunks = new LinkedHashMap<>();
    private final Set<Long> chunksToScan = new LinkedHashSet<>();
    private Long scanningChunk;
    private boolean initialized;
    private boolean statsDirty = true;

    public SourceAltarBlockEntity(BlockPos pos, BlockState state) { super(AkaishiForbiddenBlockEntities.SOURCE_ALTAR.get(), pos, state); }
    public ContainerData data() { return data; }
    public SimpleContainer pedestalDisplay() { return pedestals; }
    public List<ItemStack> pedestalItems() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) result.add(pedestals.getItem(i).copy());
        return List.copyOf(result);
    }
    public ItemStack display() { return display.copy(); }
    public long energyUnits() { return energyUnits; }
    public SourceAltarRules.Snapshot snapshot() { return snapshot; }
    public boolean isFormed() { return getBlockState().getValue(SourceAltarBlock.FORMED); }
    public boolean isMaster() { return isFormed() && getBlockState().getValue(SourceAltarBlock.PART) == 0; }
    public boolean isOperational() {
        return level != null && isMaster() && initialized && snapshot != null && !statsDirty
                && scanner == null && changedPositions.isEmpty() && changedChunks.isEmpty() && chunksToScan.isEmpty()
                && SourceAltarStructure.complete(level, SourceAltarStructure.layout(worldPosition, getBlockState()));
    }
    public SourceAltarBlockEntity controller() {
        if (level == null || !isFormed()) return null;
        BlockPos origin = SourceAltarStructure.layout(worldPosition, getBlockState()).origin();
        return level.hasChunkAt(origin) && level.getBlockEntity(origin) instanceof SourceAltarBlockEntity altar && altar.isMaster() ? altar : null;
    }
    public boolean putDisplay(ItemStack held) {
        if (held.isEmpty() || level == null || level.isClientSide) return false;
        if (!display.isEmpty() && (!ItemStack.isSameItemSameTags(display, held) || display.getCount() >= display.getMaxStackSize())) return false;
        if (display.isEmpty()) display = held.copyWithCount(1); else display.grow(1);
        displayChanged();
        return true;
    }
    public ItemStack takeDisplay() {
        if (display.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = display;
        display = ItemStack.EMPTY;
        displayChanged();
        return taken;
    }
    private void displayChanged() {
        sync();
        SourceAltarBlockEntity controller = controller();
        if (controller != null) controller.invalidateStats();
    }
    public void invalidateStats() { statsDirty = true; }
    public void environmentChanged(BlockPos pos) {
        if (SourceEnergyNetwork.inRange(worldPosition, pos, SourceAltarRules.EXTENDED_HEIGHT)) changedPositions.add(pos.immutable());
    }
    public void chunkChanged(int x, int z, boolean loaded) { changedChunks.put(ChunkPos.asLong(x, z), loaded); }
    public void structureChanged() {
        scanner = null;
        environment = null;
        snapshot = null;
        initialized = false;
        statsDirty = true;
        changedPositions.clear();
        changedChunks.clear();
        chunksToScan.clear();
        scanningChunk = null;
        if (level instanceof ServerLevel server) SourceEnergyNetwork.remove(server, worldPosition);
    }
    public int scanProgress() { return scanner != null ? scanner.progress() : initialized && !statsDirty
            && changedPositions.isEmpty() && changedChunks.isEmpty() && chunksToScan.isEmpty() ? 100 : 0; }
    public int requestedHeight() {
        if (level == null || !isMaster()) return SourceAltarRules.BASE_HEIGHT;
        SourceAltarStructure.Layout layout = SourceAltarStructure.layout(worldPosition, getBlockState());
        for (int i = 0; i < 6; i++) {
            BlockPos member = layout.member(i);
            if (level.hasChunkAt(member) && level.getBlockEntity(member) instanceof SourceAltarBlockEntity altar && SourceAltarRules.isDagger(altar.display))
                return SourceAltarRules.EXTENDED_HEIGHT;
        }
        return SourceAltarRules.BASE_HEIGHT;
    }
    public int scanStep(int budget) {
        if (budget <= 0 || !(level instanceof ServerLevel server) || !isMaster()
                || !SourceAltarStructure.complete(level, SourceAltarStructure.layout(worldPosition, getBlockState()))) return 0;
        SourceEnergyNetwork.add(server, worldPosition);
        if (environment == null) {
            environment = new SourceAltarEnvironmentCache(server, worldPosition);
            scanner = new SourceAltarScanner(server, worldPosition, environment, null);
        }
        int used = 0;
        Iterator<Map.Entry<Long, Boolean>> chunks = changedChunks.entrySet().iterator();
        while (chunks.hasNext() && used < budget) {
            Map.Entry<Long, Boolean> change = chunks.next();
            long key = change.getKey();
            environment.visible(key, false);
            used += Math.max(1, environment.removeChunk(key, budget - used));
            statsDirty = true;
            if (environment.hasChunk(key)) break;
            if (scanningChunk != null && scanningChunk == key) { scanner = null; scanningChunk = null; }
            environment.visible(key, change.getValue());
            if (change.getValue()) chunksToScan.add(key); else chunksToScan.remove(key);
            chunks.remove();
        }
        if (!changedChunks.isEmpty()) { updateData(); return used; }
        Iterator<BlockPos> positions = changedPositions.iterator();
        while (positions.hasNext() && used < budget) {
            environment.update(positions.next());
            positions.remove();
            used++;
            statsDirty = true;
        }
        if (scanner == null && initialized && !chunksToScan.isEmpty()) {
            long key = chunksToScan.iterator().next();
            ChunkPos chunk = new ChunkPos(key);
            if (server.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
                chunksToScan.remove(key);
                scanningChunk = key;
                scanner = new SourceAltarScanner(server, worldPosition, environment, chunk);
            }
        }
        if (scanner != null && used < budget) {
            used += scanner.step(budget - used);
            if (scanner.complete()) {
                if (scanningChunk == null) initialized = true;
                scanner = null;
                scanningChunk = null;
                statsDirty = true;
            }
        }
        if (initialized && scanner == null && changedPositions.isEmpty() && changedChunks.isEmpty() && chunksToScan.isEmpty() && statsDirty) {
            List<ItemStack> items = environment.offerings();
            snapshot = environment.snapshot(this, items);
            long clamped = Math.min(energyUnits, snapshot.capacityUnits());
            if (energyUnits != clamped) { energyUnits = clamped; setChanged(); }
            for (int i = 0; i < 4; i++) pedestals.setItem(i, i < items.size() ? items.get(i).copy() : ItemStack.EMPTY);
            statsDirty = false;
        }
        updateData();
        return used;
    }
    public boolean consume(long units, boolean simulate) {
        if (units <= 0 || !isOperational() || energyUnits < units) return false;
        if (!simulate) { energyUnits -= units; setChanged(); updateData(); }
        return true;
    }
    private void updateData() {
        putLong(0, energyUnits);
        putLong(4, snapshot == null ? 0 : snapshot.capacityUnits());
        putLong(8, snapshot == null ? 0 : snapshot.recoveryUnits());
        data.set(12, snapshot == null ? 1 : snapshot.headMultiplier());
        data.set(13, snapshot == null ? 2 : snapshot.implementHalves());
        data.set(14, snapshot == null ? 1 : snapshot.lightMultiplier());
        data.set(15, snapshot == null ? 0 : snapshot.offeringMask());
        data.set(16, snapshot == null ? 0 : snapshot.duplicateMask());
        data.set(17, snapshot == null ? 0 : snapshot.pedestalCount());
        data.set(18, requestedHeight());
        data.set(19, scanProgress());
        data.set(20, isOperational() ? 1 : 0);
        data.set(21, isFormed() ? 1 : 0);
    }
    private void putLong(int start, long value) {
        for (int i = 0; i < 4; i++) data.set(start + i, (int) (value >>> (i * 16)) & 0xffff);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, SourceAltarBlockEntity altar) {
        if (!(level instanceof ServerLevel server)) return;
        if (server.getGameTime() % 10 == Math.floorMod(pos.asLong(), 10)) SourceAltarStructure.refresh(server, pos);
        if (!altar.isMaster()) return;
        SourceEnergyNetwork.add(server, pos);
        if (!altar.isOperational()) { altar.updateData(); return; }
        if (altar.energyUnits < altar.snapshot.capacityUnits()) {
            altar.recoveryTicks++;
            if (altar.recoveryTicks >= 20) {
                altar.recoveryTicks = 0;
                altar.energyUnits = Math.min(altar.snapshot.capacityUnits(), altar.energyUnits + altar.snapshot.recoveryUnits());
            }
            altar.setChanged();
        } else if (altar.recoveryTicks != 0) {
            altar.recoveryTicks = 0;
            altar.setChanged();
        }
        altar.updateData();
    }
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("SourceEnergyHalfUnits", energyUnits);
        tag.putInt("RecoveryTicks", recoveryTicks);
        if (!display.isEmpty()) tag.put("Display", display.save(new CompoundTag()));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        energyUnits = Math.max(0, Math.min(SourceAltarRules.MAX_UNITS, tag.getLong("SourceEnergyHalfUnits")));
        recoveryTicks = Math.max(0, Math.min(19, tag.getInt("RecoveryTicks")));
        display = ItemStack.of(tag.getCompound("Display"));
        if (!display.isEmpty()) display.setCount(Math.max(0, Math.min(display.getCount(), display.getMaxStackSize())));
        structureChanged();
        SourceAltarBlockEntity controller = controller();
        if (controller != null && controller != this) controller.invalidateStats();
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (!display.isEmpty()) tag.put("Display", display.save(new CompoundTag()));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) structureChanged();
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) SourceEnergyNetwork.remove(server, worldPosition);
        super.setRemoved();
    }
    @Override public Component getDisplayName() { return Component.translatable("container.forbidden_hex.source_altar"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SourceAltarMenu(id, inventory, worldPosition, this);
    }
}
