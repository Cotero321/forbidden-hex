package com.example.akaishi.forbidden.block.entity;


import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlock;
import com.example.akaishi.config.ModConfig;
import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.life.altar.AkaishiAltarDrain;
import com.example.akaishi.forbidden.life.altar.AkaishiAltarRitual;
import com.example.akaishi.forbidden.menu.AkaishiMotherAltarMenu;
import com.example.akaishi.forbidden.multiblock.AkaishiAltarFormation;
import com.example.akaishi.forbidden.multiblock.AkaishiGoatAltarTiersStructure;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import com.example.akaishi.sound.MachineHum;
import com.example.akaishi.util.LongDataSlots;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 母神祭坛方块实体：承载唯一"供奉物"（单格）。
 * <ul>
 *   <li>未成型：持"生命造物"右键供奉，供奉物悬浮旋转；数据随方块 NBT 保存（实现 {@link IDataCarrier}）</li>
 *   <li>成型（≥1 级）：中心 2×2 合并为巨坛，四座中仅"主座"（西北象限）承载合并祭坛界面；
 *       界面供奉槽是 {@link #offering} 的只读视图，避免双份状态导致渲染与容器不同步</li>
 *   <li>周期识别结构等级：变化时同步客户端，并交由 {@link AkaishiAltarFormation} 执行成型/还原</li>
 *   <li>进度模式（参照植物魔法符文祭坛）：自身不储存能量，仅接收发射器弹体注入的进度；
 *       进度蓄满后由 {@link AkaishiAltarRitual} 校验祭品配方，齐备即消耗祭品凝出产物。
 *       不实现 IEnergyProvider，故管道无法连接、无法灌能——能量只能经发射器注入。</li>
 * </ul>
 */
public class AkaishiMotherAltarBlockEntity extends BlockEntity
        implements ExtendedMenuProvider {

    private static final String TAG_OFFERING = "Offering";
    private static final String TAG_TIER = "StructureTier";
    private static final String TAG_PROGRESS = "RitualProgress";

    /** 合并祭坛界面的结构等级数据槽下标 */
    public static final int DATA_TIER = 0;
    /** 仪式进度：连续 4 槽承载完整 64 位（低位在前） */
    public static final int DATA_PROGRESS = 1;
    /** 祭品配方是否齐备：1=齐备（真正在合成，界面才显示所需能量），0=未齐备 */
    public static final int DATA_READY = 5;
    /** 当前配方的蓄能阈值：旧配方 80K / 新配方 800K，占 1 槽（int 足够承载），供界面显示进度上限 */
    public static final int DATA_PROGRESS_MAX = 6;
    /** 数据槽总数：等级 1 + 进度 4 + 齐备标志 1 + 蓄能阈值 1 */
    public static final int DATA_SLOTS = 7;

    /** 结构检测节流计数（每 20 tick 检测一次，结构变化不频繁） */
    private int tick;

    private ItemStack offering = ItemStack.EMPTY;
    /** 黑山羊三级祭坛等级：0=未成型，1/2/3=对应等级（中心 2×2 四座同步同一值） */
    private int structureTier;
    /** 主座坐标缓存：随结构扫描（每 20 tick）刷新，避免发射器逐 tick 查询时反复全量扫描 */
    @Nullable
    private BlockPos primaryPos;
    /** 仪式进度（0 ~ {@link #progressMax}），满值后等待配方齐备结算，期间不再接收注入 */
    private long progress;
    /** 当前配方的蓄能阈值（旧 80K / 新 800K）：随 {@link #refreshRecipeReady()} 每 20 tick 重算 */
    private long progressMax = ModConfig.altarLegacyProgressMax;
    /** 祭品配方是否齐备（= 真正在合成）：每 20 tick 重算一次，驱动界面显示与氛围音档位 */
    private boolean recipeReady;

    private final SimpleContainerData data = new SimpleContainerData(DATA_SLOTS);

    /** 成型后未工作的氛围音：虚空的心跳声（素材 5s，间隔取 100 tick 实现连续无缝循环） */
    private final MachineHum heartbeatHum = new MachineHum(AkaishiForbiddenSounds.VOID_HEARTBEAT, 0.4F, 1.0F, 100);
    /** 仪式进行中的氛围音：虚空呓语声（素材 8s，间隔取 160 tick） */
    private final MachineHum whisperHum = new MachineHum(AkaishiForbiddenSounds.VOID_WHISPER, 0.4F, 1.0F, 160);

    /** 氛围音档位：未成型 / 非主座，静默 */
    private static final int AMBIENCE_SILENT = 0;
    /** 氛围音档位：成型未工作 → 虚空的心跳声 */
    private static final int AMBIENCE_IDLE = 1;
    /** 氛围音档位：仪式进行中 → 虚空呓语声 */
    private static final int AMBIENCE_WORKING = 2;

    /** 当前氛围音档位，用于检测切换并重置目标音效冷却 */
    private int ambienceMode = AMBIENCE_SILENT;

    /** 「不可名状」减益的施加半径（格）：仅主座生效 */
    private static final double AFFLICT_RANGE = 48.0D;
    /** 减益刷新间隔（tick）：20 tick 刷新一次，配合 3s 时长实现无缝持续 */
    private static final int AFFLICT_INTERVAL = 20;
    /** 单次施加的效果时长（tick）：3s，仪式停止后效果自行消退 */
    private static final int AFFLICT_DURATION = 60;
    /** 耳中呓语的重播间隔（tick）：5s 一次，稀疏出现才不吵 */
    private static final int WHISPER_INTERVAL = 100;

    /** 减益/呓语节流计数 */
    private int afflictTick;

    /** 仪式吸取节流计数（间隔取配置 {@code altarDrainIntervalTicks}） */
    private int drainTick;

    /**
     * 合并祭坛供奉槽视图：读写直接落回 {@link #offering}，使界面槽位与悬浮渲染共用同一份数据。
     * 单槽上限 1（需求：仅能放置一个物品）。
     */
    private final Container altarSlot = new Container() {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return offering.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return offering;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack split = offering.split(amount);
            if (offering.isEmpty()) {
                offering = ItemStack.EMPTY;
            }
            sync();
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack back = offering;
            offering = ItemStack.EMPTY;
            sync();
            return back;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            offering = stack;
            sync();
        }

        @Override
        public void setChanged() {
            AkaishiMotherAltarBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            setItem(0, ItemStack.EMPTY);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    public AkaishiMotherAltarBlockEntity(BlockPos pos, BlockState state) {
        super(AkaishiForbiddenBlockEntities.CHISHI_MOTHER_ALTAR.get(), pos, state);
    }

    public ItemStack getOffering() {
        return offering;
    }

    public boolean hasOffering() {
        return !offering.isEmpty();
    }

    /** 当前黑山羊三级祭坛等级（0=未成型） */
    public int getStructureTier() {
        return structureTier;
    }

    /** 合并祭坛供奉槽（供界面容器使用） */
    public Container altarSlot() {
        return altarSlot;
    }

    public ContainerData data() {
        return data;
    }

    /** 供奉成功：写入并同步客户端（仅服务端调用） */
    public void setOffering(ItemStack stack) {
        this.offering = stack.copy();
        sync();
    }

    /** 取回供奉物（仅服务端调用） */
    public ItemStack takeOffering() {
        ItemStack back = offering;
        offering = ItemStack.EMPTY;
        sync();
        return back;
    }

    /** 合并成型前把本座供奉弹出归还世界，避免"凭空消失"（仅服务端调用） */
    public void ejectOffering() {
        if (offering.isEmpty()) {
            return;
        }
        if (level != null && !level.isClientSide) {
            Block.popResource(level, worldPosition.above(), offering);
        }
        offering = ItemStack.EMPTY;
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AkaishiMotherAltarBlockEntity be) {
        be.tickServer();
    }

    /** 定期识别黑山羊三级祭坛结构：等级变化时写入并同步，并驱动成型/还原（四座各自持有同一等级） */
    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 进度仅由弹体注入改变，逐 tick 刷新数据槽（4 槽 64 位）保证界面进度条实时
        LongDataSlots.write(data, DATA_PROGRESS, DATA_PROGRESS + 1, DATA_PROGRESS + 2, DATA_PROGRESS + 3, progress);
        // 氛围音需逐 tick 驱动（重播间隔等于素材时长），因此必须早于下方 20 tick 节流返回
        tickAmbience();
        // 仪式进行中向附近玩家施加「不可名状」（含耳中呓语），同样逐 tick 参与节流
        tickAffliction();
        // 仪式吸取：配方齐备期间持续抽取周边生物的生命折算为进度（D146）
        tickDrain();
        // 先取模后自增：首 tick 即执行一次完整检测，避免重启后等级与齐备标志空等 20 tick 才恢复
        if (tick++ % 20 != 0) {
            return;
        }
        int tier = AkaishiGoatAltarTiersStructure.scan(level, worldPosition);
        if (tier != structureTier) {
            structureTier = tier;
            data.set(DATA_TIER, tier);
            sync();
        }
        // 主座坐标与等级同轮刷新：未成型（含外圈 8 座）恒为 null，注能与配方判定据此一律拒收
        primaryPos = tier >= AkaishiGoatAltarTiersStructure.FORMED_TIER
                ? AkaishiGoatAltarTiersStructure.findPrimary(level, worldPosition)
                : null;
        AkaishiAltarFormation.refresh(level, worldPosition, tier);
        refreshRecipeReady();
    }

    /**
     * 重算"祭品配方是否齐备"。齐备 = 真正在合成，是界面显示所需能量与播放呓语声的唯一条件；
     * 结果变化时写入数据槽并同步客户端（每 20 tick 一次，避免逐 tick 扫描 8 座子祭坛的开销）。
     * <p>同时刷新当前配方的蓄能阈值 {@link #progressMax}（旧 80K / 新 800K）并写入数据槽，
     * 供界面进度条按正确上限显示。
     */
    private void refreshRecipeReady() {
        // 统一以主座为准：供品、界面与氛围音都由主座承载，非主座自身供品恒空，直接判定会误报"不齐备"
        AkaishiMotherAltarBlockEntity host = primaryHost();
        boolean ready;
        long max;
        if (host == null || host.structureTier < AkaishiGoatAltarTiersStructure.FORMED_TIER) {
            ready = false;
            max = ModConfig.altarLegacyProgressMax;
        } else if (host == this) {
            AkaishiAltarRitual.Match match = level instanceof ServerLevel serverLevel
                    ? AkaishiAltarRitual.match(serverLevel, worldPosition, this) : null;
            ready = match != null;
            max = match != null ? match.recipe().progressMax() : ModConfig.altarLegacyProgressMax;
        } else {
            // 非主座直接复用主座本轮的判定结果，省去"四座各扫一遍外圈 8 座"的重复开销
            ready = host.recipeReady;
            max = host.progressMax;
        }
        if (max != progressMax) {
            progressMax = max;
            data.set(DATA_PROGRESS_MAX, (int) Math.min(Integer.MAX_VALUE, max));
            sync();
        }
        if (ready != recipeReady) {
            recipeReady = ready;
            data.set(DATA_READY, ready ? 1 : 0);
            sync();
        }
    }

    /**
     * 氛围音切换：成型后未合成 → 虚空的心跳声；祭品齐备、真正在合成中 → 虚空呓语声。
     * <p>
     * 仅在主座（西北象限 {@code CORNER == 0}）播放：成型后中心 2×2 四座共享同一结构，
     * 若四座同时发声会因音源叠加导致音量翻倍、且相互干涉形成拍频。
     * <p>
     * 两档音效均为整段无缝循环素材，重播间隔等于素材时长（100 / 160 tick），
     * 因此必须逐 tick 驱动；档位切换时重置冷却以免延迟出声。
     */
    private void tickAmbience() {
        BlockState state = getBlockState();
        int mode = !state.getValue(AkaishiMotherAltarBlock.FORMED)
                || state.getValue(AkaishiMotherAltarBlock.CORNER) != 0
                ? AMBIENCE_SILENT
                : (recipeReady ? AMBIENCE_WORKING : AMBIENCE_IDLE);
        if (mode != ambienceMode) {
            ambienceMode = mode;
            // 切档时重置目标音效冷却，避免停用期间冻结的冷却让新档空等整段素材时长才出声
            if (mode == AMBIENCE_WORKING) {
                whisperHum.restart();
            } else if (mode == AMBIENCE_IDLE) {
                heartbeatHum.restart();
            }
        }
        if (mode == AMBIENCE_WORKING) {
            whisperHum.tick(level, worldPosition);
        } else if (mode == AMBIENCE_IDLE) {
            heartbeatHum.tick(level, worldPosition);
        }
    }

    /**
     * 仪式进行中向巨坛附近玩家持续施加「不可名状」减益（仅主座）。
     * <p>生效条件：主座已成型且<b>祭品齐备、真正处于合成中</b>（{@link #recipeReady}），
     * 与氛围音 {@code AMBIENCE_WORKING} 档位口径一致——仅注入进度而祭品不齐时不施加。
     * 每 {@link #AFFLICT_INTERVAL} tick 刷新一次、单次时长 {@link #AFFLICT_DURATION}，
     * 故仪式停止后 3s 内效果自然消退，无需额外的清理逻辑。
     * <p>同步播放"耳中呓语"：以玩家自身坐标为音源（{@link SoundSource#PLAYERS}），
     * 每 {@link #WHISPER_INTERVAL} tick 一次并抖动音调，避免机械重复感。
     * <p>"完成爆发"的强化效果由 {@link AkaishiAltarRitual#tryComplete} 负责叠加。
     */
    private void tickAffliction() {
        if (++afflictTick % AFFLICT_INTERVAL != 0) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.getValue(AkaishiMotherAltarBlock.FORMED)
                || state.getValue(AkaishiMotherAltarBlock.CORNER) != 0
                || !recipeReady
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean whisper = afflictTick % WHISPER_INTERVAL == 0;
        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY() + 0.5D;
        double cz = worldPosition.getZ() + 0.5D;
        double rangeSqr = AFFLICT_RANGE * AFFLICT_RANGE;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(cx, cy, cz) > rangeSqr) {
                continue;
            }
            // ambient=true 降低粒子密度、visible=false 不冒粒子、showIcon=true 保留 HUD 图标
            player.addEffect(new MobEffectInstance(AkaishiForbiddenEffects.UNNAMEABLE.get(),
                    AFFLICT_DURATION, 0, true, false, true));
            if (whisper) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                        AkaishiForbiddenSounds.UNNAMEABLE_WHISPER.get(), SoundSource.PLAYERS,
                        0.7F, 0.9F + serverLevel.random.nextFloat() * 0.2F);
            }
        }
    }

    /**
     * 仪式吸取：配方齐备期间由主座持续抽取周边生物的生命，折算成生命能量灌入本坛（D146~D182）。
     * <p>仅主座、仅成型且祭品齐备时生效，间隔取配置 {@code altarDrainIntervalTicks}；
     * 等级复查由 {@link #refreshRecipeReady()} 每 20 tick 承担——等级跌破配方门槛即 {@code recipeReady}
     * 转 false，吸取与减益一并冻结，回升后自动恢复（D182）。
     */
    private void tickDrain() {
        int interval = Math.max(1, ModConfig.altarDrainIntervalTicks);
        if (drainTick++ % interval != 0) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.getValue(AkaishiMotherAltarBlock.FORMED)
                || state.getValue(AkaishiMotherAltarBlock.CORNER) != 0
                || !recipeReady
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AkaishiAltarDrain.drain(serverLevel, worldPosition, this);
    }

    /** 数据变更 → 落盘 + 向在线玩家广播方块实体数据包（悬浮渲染 / 界面依赖客户端持有最新数据） */
    private void sync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
            for (ServerPlayer player : serverLevel.players()) {
                player.connection.send(packet);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.forbidden_hex.forbidden_hex_mother_altar");
    }

    /** 当前仪式进度（0 ~ {@link #progressMax}）；祭品不齐时仅冻结（不涨不跌），不归零 */
    public long getProgress() {
        return progress;
    }

    public boolean needsLifeEnergy() {
        // 未成型（外圈 8 座扫描恒为 0）一律拒收：既杜绝"不摆结构也能完成仪式"，也避免发射器对无效目标白抽能
        if (structureTier < AkaishiGoatAltarTiersStructure.FORMED_TIER) {
            return false;
        }
        // 中心 2×2 四座统一以主座进度为准：发射器绑到任意象限都能正常注能，不再出现"进度界面恒 0 且永不结算"
        AkaishiMotherAltarBlockEntity host = primaryHost();
        if (host == null) {
            return false;
        }
        // 祭品未齐备（recipeReady=false）一律拒收：进度与祭品齐备同源，否则发射器会在玩家摆齐祭品前把进度灌满，
        // 导致"放上最后一件祭品的下个检测周期直接结算"——玩家看不到蓄能阶段、界面进度条也来不及出现。
        // 齐备后才开闸注能，进度从 0 起涨，界面按 recipeReady 同步显示"所需能量"。
        // 撤下祭品期间进度只冻结（D161 全局统一语义），重新摆齐后从冻结值继续蓄能。
        return host.recipeReady && host.progress < host.progressMax;
    }

    public long receiveLifeEnergy(long amount) {
        if (amount <= 0 || level == null || level.isClientSide) {
            return 0;
        }
        // 进度只归属主座：中心 2×2 其余三座收到的注入一律转交主座累计
        AkaishiMotherAltarBlockEntity host = primaryHost();
        if (host == null || host.structureTier < AkaishiGoatAltarTiersStructure.FORMED_TIER) {
            return 0;
        }
        return host.absorbLifeEnergy(amount);
    }

    /** 主座累计进度并结清本轮：跨 10% 档位落雷，满值尝试结算（仅主座自身调用，故无需再次定位主座） */
    public long absorbLifeEnergy(long amount) {
        // 阈值按当前匹配配方取（旧 80K / 新 800K）；Math.min 同时承担"满值溢出丢弃"语义（D173）
        long max = Math.max(1L, progressMax);
        long before = progress;
        progress = Math.min(max, progress + amount);
        long absorbed = progress - before;
        // 每跨过 10% 档位落一道雷（子祭坛轮流，进度满落巨坛中央）
        if (absorbed > 0 && level instanceof ServerLevel serverLevel) {
            AkaishiAltarRitual.strikeProgressBolts(serverLevel, worldPosition, before, progress, max);
        }
        if (progress >= max) {
            trySettle();
        }
        sync();
        return absorbed;
    }

    /**
     * 本座所属巨坛的主座（西北象限 corner=0）：本座即主座时返回自身，无主座（未成型 / 外圈 / 主座缺失）时返回 null。
     * <p>使用 {@link #primaryPos} 缓存，不逐次扫描结构。
     */
    @Nullable
    private AkaishiMotherAltarBlockEntity primaryHost() {
        if (level == null || primaryPos == null) {
            return null;
        }
        if (primaryPos.equals(worldPosition)) {
            return this;
        }
        return level.getBlockEntity(primaryPos) instanceof AkaishiMotherAltarBlockEntity host ? host : null;
    }

    /** 进度蓄满时结算：注能已受 recipeReady 约束，故此处祭品必然齐备，成功后进度清零并产出 */
    private void trySettle() {
        if (level instanceof ServerLevel serverLevel
                && AkaishiAltarRitual.tryComplete(serverLevel, worldPosition, this)) {
            progress = 0;
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AkaishiMotherAltarMenu(id, inv, altarSlot, data);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // 始终写入 Offering 键：空供奉也会写入空 ItemStack。
        // 否则 getUpdateTag() 返回空 tag，ClientboundBlockEntityDataPacket 会把它转成 null，
        // 客户端 onDataPacket 跳过 load，导致"取回后供奉物悬浮模型不消失"。
        tag.put(TAG_OFFERING, offering.save(new CompoundTag()));
        tag.putInt(TAG_TIER, structureTier);
        // 同样始终写入进度键（含 0），保证客户端同步链路字段完整
        tag.putLong(TAG_PROGRESS, progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        offering = tag.contains(TAG_OFFERING)
                ? ItemStack.of(tag.getCompound(TAG_OFFERING)) : ItemStack.EMPTY;
        structureTier = tag.getInt(TAG_TIER);
        data.set(DATA_TIER, structureTier);
        // 满值进度不予恢复：满值必在达成当 tick 结算并清零，残留满值只可能来自旧存档/异常中断，
        // 照搬会跳过注能阶段被瞬间结算，故一律清零，由玩家重新摆放祭品注能
        long storedProgress = tag.getLong(TAG_PROGRESS);
        long upper = Math.max(ModConfig.altarLegacyProgressMax, ModConfig.altarNewRecipeProgressMax);
        progress = (storedProgress <= 0L || storedProgress >= upper) ? 0L : storedProgress;
        // load 由 notifyBlockUpdate 触发时数据槽未必已写入，补一次保证界面初值正确
        LongDataSlots.write(data, DATA_PROGRESS, DATA_PROGRESS + 1, DATA_PROGRESS + 2, DATA_PROGRESS + 3, progress);
        data.set(DATA_PROGRESS_MAX, (int) Math.min(Integer.MAX_VALUE, progressMax));
    }
}
