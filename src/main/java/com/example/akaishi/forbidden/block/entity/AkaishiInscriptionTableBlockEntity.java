package com.example.akaishi.forbidden.block.entity;

import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import com.example.akaishi.forbidden.menu.AkaishiInscriptionTableMenu;
import com.example.akaishi.forbidden.block.AkaishiForbiddenBlocks;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AkaishiInscriptionTableBlockEntity extends BlockEntity
        implements ExtendedMenuProvider, Container, ContainerData {
    public static final int CORE_SLOT = 0;
    public static final int BRUSH_SLOT = 1;
    public static final int MAIN_INK_SLOT = 2;
    public static final int OVERLAY_INK_START = 3;
    public static final int OVERLAY_INK_COUNT = 3;
    /** 墨输入槽数量（投料口，投入即被吸收）。 */
    public static final int INK_INPUT_COUNT = 4;
    public static final int INK_INPUT_END = MAIN_INK_SLOT + INK_INPUT_COUNT;
    /** 产物槽：雕刻完成后刻好的核心出现在这里，只能取出、不能放入。 */
    public static final int PRODUCT_SLOT = 6;
    public static final int SLOT_COUNT = 7;
    public static final int MAX_WORDS = 8;

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT);
    private final String[] selectedWords = new String[MAX_WORDS];
    /** 逐词使用的墨（选取该词时冻结）；空 = 沿用当前选中的墨。 */
    private final String[] selectedInks = new String[MAX_WORDS];
    private int selectedSlot;
    private boolean overlayMode;
    private String selectedInk = "";
    /** 上一次回填用的核心签名（词序列）；用于「换核才回填」。 */
    private String lastPrefillSignature = "";

    public AkaishiInscriptionTableBlockEntity(BlockPos pos, BlockState state) {
        super(AkaishiForbiddenBlockEntities.INSCRIPTION_TABLE.get(), pos, state);
    }

    public SimpleContainer inventory() { return inventory; }
    public String selectedWord(int slot) { return slot >= 0 && slot < MAX_WORDS && selectedWords[slot] != null ? selectedWords[slot] : ""; }
    public int selectedSlot() { return selectedSlot; }
    public boolean overlayMode() { return overlayMode; }
    public String selectedInk() { return selectedInk; }
    /** 某词位<b>冻结</b>的墨（选取该词时的墨）；空 = 该位还没有词或未记录。 */
    public String selectedWordInk(int slot) {
        return slot >= 0 && slot < MAX_WORDS && selectedInks[slot] != null ? selectedInks[slot] : "";
    }

    public void selectSlot(int slot) {
        if (slot >= 0 && slot < MAX_WORDS) {
            selectedSlot = slot;
            setChanged();
        }
    }

    public boolean setDraft(String word, Player player) {
        MagicState state = magicState(player);
        int open = com.example.akaishi.forbidden.menu.CoreLayout.openSlotCount(
                state == null ? MagicState.DEFAULT_LEVEL : state.level());
        var reason = com.example.akaishi.forbidden.menu.InscriptionSelection.reason(
                draftWords(), draftInks(), selectedSlot, word, selectedInk,
                MagicCoreItem.source(inventory.getItem(CORE_SLOT)).id(), open,
                unlockedWords(player), unlockedInks(player));
        if (reason != null) return false;
        selectedWords[selectedSlot] = word;
        selectedInks[selectedSlot] = selectedInk;
        setChanged();
        return true;
    }

    public java.util.List<String> draftWords() {
        return java.util.stream.IntStream.range(0, MAX_WORDS).mapToObj(this::selectedWord).toList();
    }
    public java.util.List<String> draftInks() {
        return java.util.stream.IntStream.range(0, MAX_WORDS)
                .mapToObj(i -> selectedWord(i).isEmpty() ? "" : resolvedInk(i)).toList();
    }

    public void setOverlayMode(boolean value) { overlayMode = value; setChanged(); }
    public void selectInk(String id, Player player) {
        if (MagicInkTable.get(id) != null && unlockedInks(player).contains(id)) {
            selectedInk = id;
            setChanged();
        }
    }

    // ===== 墨累计（按墨种存储；输入槽只作投料口，槽位本身恒为空）=====
    /** 墨 id 的有序表：累计只存下标，便于用 ContainerData 同步到客户端。 */
    public static final String[] INK_IDS = MagicInkTable.all().keySet().toArray(new String[0]);
    /** 逻辑槽数 = 墨种数：每种墨都有自己的累计位，容量永远够。 */
    public static final int INK_SLOTS = INK_IDS.length;
    private final int[] inkTypeIndex = new int[INK_SLOTS];
    private final int[] inkCharges = new int[INK_SLOTS];

    {
        java.util.Arrays.fill(inkTypeIndex, -1);
    }

    /** 墨 id → 下标；未知名返回 -1。 */
    public static int typeIndexOf(String inkId) {
        for (int i = 0; i < INK_IDS.length; i++) if (INK_IDS[i].equals(inkId)) return i;
        return -1;
    }

    /** 该墨种的累计耐久。 */
    public int inkChargeOf(String inkId) {
        int ti = typeIndexOf(inkId);
        if (ti < 0) return 0;
        int total = 0;
        for (int i = 0; i < INK_SLOTS; i++) if (inkTypeIndex[i] == ti) total += inkCharges[i];
        return total;
    }

    /** 是否还能接收该种墨：只要是合法墨就永远能收（每种墨有独立累计位）。 */
    public boolean canAccept(String inkId) {
        return typeIndexOf(inkId) >= 0;
    }

    /**
     * 服务端每 tick：把落在输入槽里的墨瓶吸收掉。
     * 让墨瓶先按原版规则进入槽位（客户端/服务端天然一致），再由这里消耗并清空，
     * 避免“写入拦截 + 槽位恒空”造成的容量判断不一致与预测残影。
     */
    public void tickAbsorbInk() {
        for (int slot = MAIN_INK_SLOT; slot < INK_INPUT_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (inkTypeId(stack).isEmpty()) continue;      // 非墨：不回吞
            if (absorbInk(slot, stack)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 输入槽行为：墨瓶投入即被消耗，耐久并入「该种墨」的累计（上方总面板显示）。
     * 每种墨固定占自己的累计位（下标 = 墨种下标），因此同类天然合并、容量永远够。
     */
    public boolean absorbInk(int slot, ItemStack incoming) {
        if (incoming.isEmpty()) return false;
        String incomingType = inkTypeId(incoming);
        int ti = typeIndexOf(incomingType);
        if (ti < 0) return false;
        int add = Math.max(0, incoming.getMaxDamage() - incoming.getDamageValue());
        inkTypeIndex[ti] = ti;
        inkCharges[ti] += add;                 // 相加不封顶
        // 不自动切换当前选中的墨：投料只累计耐久，选墨由玩家点选，避免投一瓶就顶掉手上的选择
        setChanged();
        return true;
    }

    /** 反查该物品对应的墨 id；非墨返回空串。 */
    public static String inkTypeId(ItemStack stack) {
        if (stack.isEmpty()) return "";
        for (var e : AkaishiMagicItems.inks().entrySet()) {
            if (stack.is(e.getValue().get())) return e.getKey();
        }
        return "";
    }

    // ===== ContainerData：把墨、当前选择与词序同步到客户端 =====
    // 布局（长度随 INK_SLOTS / MAX_WORDS 变，别硬背数字）：
    //   0 .. INK_SLOTS-1              墨种下标
    //   INK_SLOTS .. 2*INK_SLOTS-1    各墨累计耐久
    //   2*INK_SLOTS                   目标词位
    //   2*INK_SLOTS+1                 当前选中的墨
    //   2*INK_SLOTS+2                 覆墨模式（当前无界面入口）
    //   后接 MAX_WORDS 个词下标，再接 MAX_WORDS 个逐词墨下标（见 WORD_INK_BASE）
    /** 词 id 的有序表：词序只存下标，便于同步。 */
    public static final String[] WORD_IDS = MagicWord.byIdView().keySet().toArray(new String[0]);

    /** 词 id → 下标；未知名返回 -1。 */
    public static int wordIndexOf(String wordId) {
        for (int i = 0; i < WORD_IDS.length; i++) if (WORD_IDS[i].equals(wordId)) return i;
        return -1;
    }

    private static final int DATA_COUNT = INK_SLOTS * 2 + 3 + MAX_WORDS;
    /** 逐词墨在数据槽里的起始下标（每词位一个墨下标，-1 = 未冻结）。 */
    private static final int WORD_INK_BASE = INK_SLOTS * 2 + 3 + MAX_WORDS;
    /** 本体数据槽数量（菜单在其后追加解锁掩码）。 */
    public static final int INSCRIPTION_DATA_COUNT = DATA_COUNT + MAX_WORDS;

    @Override public int getCount() { return INSCRIPTION_DATA_COUNT; }
    @Override public int get(int i) {
        if (i < INK_SLOTS) return inkTypeIndex[i];
        if (i < INK_SLOTS * 2) return inkCharges[i - INK_SLOTS];
        if (i == INK_SLOTS * 2) return selectedSlot;
        if (i == INK_SLOTS * 2 + 1) return typeIndexOf(selectedInk);
        if (i == INK_SLOTS * 2 + 2) return overlayMode ? 1 : 0;
        if (i < WORD_INK_BASE) return wordIndexOf(selectedWord(i - INK_SLOTS * 2 - 3));
        return typeIndexOf(selectedWordInk(i - WORD_INK_BASE));
    }
    @Override public void set(int i, int value) {
        // 客户端由同步包调用，仅写本地镜像，不标脏
        if (i < INK_SLOTS) { inkTypeIndex[i] = value; return; }
        if (i < INK_SLOTS * 2) { inkCharges[i - INK_SLOTS] = value; return; }
        if (i == INK_SLOTS * 2) { selectedSlot = value; return; }
        if (i == INK_SLOTS * 2 + 1) { selectedInk = value >= 0 && value < INK_IDS.length ? INK_IDS[value] : ""; return; }
        if (i == INK_SLOTS * 2 + 2) { overlayMode = value != 0; return; }
        if (i < WORD_INK_BASE) {
            int w = i - INK_SLOTS * 2 - 3;
            if (w >= 0 && w < MAX_WORDS) selectedWords[w] = value >= 0 && value < WORD_IDS.length ? WORD_IDS[value] : "";
            return;
        }
        int s = i - WORD_INK_BASE;
        if (s >= 0 && s < MAX_WORDS) selectedInks[s] = value >= 0 && value < INK_IDS.length ? INK_IDS[value] : "";
    }

    public Set<String> unlockedWords(Player player) {
        MagicState state = magicState(player);
        return state == null ? Set.of() : new LinkedHashSet<>(state.words());
    }

    public Set<String> unlockedInks(Player player) {
        Set<String> result = new LinkedHashSet<>();
        MagicState state = magicState(player);
        int level = state == null ? MagicState.DEFAULT_LEVEL : state.level();
        int maxTier = Math.max(1, Math.min(6, level));
        int index = 0;
        for (String id : MagicInkTable.all().keySet()) {
            if (tier(index) <= maxTier) result.add(id);     // 墨只按等级梯度解锁，与词无关
            index++;
        }
        return result;
    }

    private static int tier(int index) { return index < 3 ? 1 : index < 5 ? 2 : index < 7 ? 3 : index < 8 ? 4 : index < 10 ? 5 : 6; }

    private static MagicState magicState(Player player) {
        return MagicStates.state(player);
    }

    /**
     * 尝试雕刻。
     *
     * <p><b>为什么返回原因而不是 boolean</b>：雕刻有 8 道前置校验，任何一道不过都只是"点了没反应"，
     * 玩家无从判断是缺料、缺等级还是产物位没腾 —— 这是最容易让人以为"功能坏了"的地方。
     *
     * @return {@code null} = 成功；否则为失败原因（可直接发给玩家）
     */
    public Component tryEngrave(Player player) {
        ItemStack core = inventory.getItem(CORE_SLOT);
        ItemStack brush = inventory.getItem(BRUSH_SLOT);
        if (!(core.getItem() instanceof MagicCoreItem)) return key("fail.core");
        if (brush.isEmpty() || brush.getItem() != AkaishiMagicItems.magicBrush.get()) return key("fail.brush");
        if (brush.getDamageValue() >= brush.getMaxDamage()) return key("fail.brush_broken");
        if (!hasValidWords(player)) return key("fail.words");
        if (hasDuplicateWords()) return key("fail.dup");
        String planInk = draftInks().stream().filter(id -> !id.isEmpty()).findFirst().orElse("");
        var compiled = com.example.akaishi.forbidden.api.magic.SpellPlans.compile(
                new com.example.akaishi.forbidden.api.magic.SpellInput(draftWords(), draftInks(), planInk,
                        MagicCoreItem.source(core).id(), 0f, 0));
        if (!compiled.ok()) return compiled.diagnostics().stream()
                .filter(d -> d.severity() == com.example.akaishi.forbidden.api.magic.CompileDiagnostic.Severity.ERROR)
                .<Component>map(d -> Component.translatable("item.forbidden_hex.magic_core.plan_error." + d.code()))
                .findFirst().orElse(key("fail.words"));
        int open = com.example.akaishi.forbidden.menu.CoreLayout.openSlotCount(
                magicState(player) == null ? MagicState.DEFAULT_LEVEL : magicState(player).level());
        for (int i = open; i < MAX_WORDS; i++) if (!selectedWord(i).isEmpty()) return key("block_room");
        if (!inventory.getItem(PRODUCT_SLOT).isEmpty()) return key("fail.product");
        MagicState state = magicState(player);
        int need = requiredLevel();
        if (state != null && state.level() < need) return key("fail.level", need);
        Set<String> usedInks = new LinkedHashSet<>();
        Set<String> unlocked = unlockedInks(player);
        for (int i = 0; i < MAX_WORDS; i++) {
            if (selectedWord(i).isEmpty()) continue;
            String ink = resolvedInk(i);
            if (ink.isEmpty()) return key("fail.no_ink");
            if (!unlocked.contains(ink)) return key("fail.ink_locked", inkLabel(ink));
            usedInks.add(ink);
        }
        if (usedInks.isEmpty()) return key("fail.empty");
        for (String ink : usedInks) {
            if (inkChargeOf(ink) <= 0) return key("fail.ink_empty", inkLabel(ink));
        }
        // 写入产物：不动输入槽的物品本体，改完副本后把产物放到产物槽（避免复制/污染）
        ItemStack product = core.copy();
        CompoundTag tag = product.getOrCreateTag();
        // 核的等级 = 其词数换算出的阶位（§44.4「写入与施放同一口径」），
        // 不与「刻它的人当时几级」挂钩：Lv6 玩家只刻 1 个词，这块核仍是 Lv1 阶。
        tag.putInt(MagicCoreItem.LEVEL_TAG, requiredLevel());
        ListTag words = new ListTag();
        ListTag inks = new ListTag();
        for (int i = 0; i < MAX_WORDS; i++) {
            String word = selectedWord(i);
            words.add(StringTag.valueOf(word));
            inks.add(StringTag.valueOf(word.isEmpty() ? "" : resolvedInk(i)));   // 空位不写墨，避免回填出幽灵墨
        }
        tag.put(MagicCoreItem.WORDS_TAG, words);
        tag.put(MagicCoreItem.WORD_INKS_TAG, inks);
        brush.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
        for (String ink : usedInks) consumeInk(ink);      // 每种用到的墨 −1 耐久
        inventory.setItem(CORE_SLOT, ItemStack.EMPTY);
        inventory.setItem(PRODUCT_SLOT, product);
        clearDrafts();
        setChanged();
        // 成功反馈：形态取主墨、颜色取主墨，在台面上方撒一圈（服务端广播，附近玩家都看得到）。
        // 载体传 null —— 雕刻不是施法、没有交付方式，形态按"源"兜底（见 AkaishiMagicParticles.options）。
        String mainInkId = usedInks.isEmpty() ? "" : usedInks.iterator().next();
        MagicInkDef mainInk = MagicInkTable.get(mainInkId);
        AkaishiMagicParticles.burst(level, MagicCoreItem.source(core), mainInkId, null,
                mainInk == null ? 0xFFFFFF : mainInk.rgb(),
                getBlockPos().getX() + 0.5, getBlockPos().getY() + 1.15, getBlockPos().getZ() + 0.5, 18);
        return null;
    }

    private static Component key(String suffix, Object... args) {
        return Component.translatable("gui.forbidden_hex.inscription." + suffix, args);
    }

    private static String inkLabel(String inkId) {
        MagicInkDef def = MagicInkTable.get(inkId);
        return def == null ? inkId : def.label();
    }

    /**
     * 输入槽放上（新的）核心时，把核内已有的词回填进八个草稿槽，便于就地改写。
     *
     * <p>回填后<b>草稿就是唯一真源</b>：圆环、汇总、雕刻都只看草稿，
     * 于是不会出现「核里的旧 NBT 一直压着新草稿、看着不刷新」。
     * 用签名比对，只有换核 / 取出核才回填，绝不会每 tick 覆盖玩家正在改的内容。
     */
    public void prefillDraftsFromCore() {
        ItemStack core = inventory.getItem(CORE_SLOT);
        boolean hasCore = core.getItem() instanceof MagicCoreItem;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; hasCore && i < MAX_WORDS; i++) sb.append(MagicCoreItem.word(core, i)).append('|');
        if (hasCore) {
            for (int i = 0; i < MAX_WORDS; i++) sb.append('|').append(MagicCoreItem.ink(core, i));
        }
        String signature = sb.toString();
        if (signature.equals(lastPrefillSignature)) return;
        lastPrefillSignature = signature;
        for (int i = 0; i < MAX_WORDS; i++) selectedWords[i] = "";
        for (int i = 0; i < MAX_WORDS; i++) selectedInks[i] = "";
        if (hasCore) {
            for (int i = 0; i < MAX_WORDS; i++) {
                selectedWords[i] = MagicCoreItem.word(core, i);
                selectedInks[i] = MagicCoreItem.ink(core, i);   // 逐词墨一并回填
            }
            for (int i = 0; i < MAX_WORDS; i++) {
                String ink = MagicCoreItem.ink(core, i);
                if (!ink.isEmpty()) { selectedInk = ink; break; }
            }
        }
        setChanged();
    }

    /** 提交后清空草稿，避免下一块核心还带着上一次选的词。 */
    public void clearDrafts() {
        for (int i = 0; i < MAX_WORDS; i++) selectedWords[i] = "";
        for (int i = 0; i < MAX_WORDS; i++) selectedInks[i] = "";
        setChanged();
    }

    /** 消耗该种墨 1 点累计耐久。 */
    private void consumeInk(String inkId) {
        int ti = typeIndexOf(inkId);
        if (ti >= 0 && inkTypeIndex[ti] == ti && inkCharges[ti] > 0) {
            inkCharges[ti]--;
        }
    }

    /** 同一个词只能占一个槽位（§1 判定：同词重复属冲突）。 */
    private boolean hasDuplicateWords() {
        Set<String> seen = new LinkedHashSet<>();
        for (String word : selectedWords) {
            if (word != null && !word.isEmpty() && !seen.add(word)) return true;
        }
        return false;
    }

    private boolean hasValidWords(Player player) {
        MagicState state = magicState(player);
        for (String word : selectedWords) {
            if (word == null || word.isEmpty()) continue;
            if (!MagicWord.exists(word)) return false;
            if (state != null && !state.hasWord(word)) return false;
        }
        return true;
    }
    private int requiredLevel() { int count = 0; for (String word : selectedWords) if (word != null && !word.isEmpty()) count++; return count <= 1 ? 1 : count <= 2 ? 2 : count <= 3 ? 3 : count <= 5 ? 4 : count <= 7 ? 5 : 6; }
    /** 每个词位实际使用的墨：优先用该词冻结的墨，没有才回落到当前选中的墨。 */
    private String resolvedInk(int wordSlot) {
        String frozen = selectedWordInk(wordSlot);
        return frozen.isEmpty() ? selectedInk : frozen;
    }

    @Override public Component getDisplayName() { return Component.translatable("block.forbidden_hex.forbidden_hex_inscription_table"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new AkaishiInscriptionTableMenu(id, inv, this); }
    @Override public void saveExtraData(FriendlyByteBuf buf) { buf.writeBlockPos(worldPosition); }
    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public ItemStack getItem(int index) { return inventory.getItem(index); }
    @Override public ItemStack removeItem(int index, int count) { return inventory.removeItem(index, count); }
    @Override public ItemStack removeItemNoUpdate(int index) { return inventory.removeItemNoUpdate(index); }
    @Override public void setItem(int index, ItemStack stack) { inventory.setItem(index, stack); setChanged(); }
    @Override public boolean stillValid(Player player) { return player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64; }
    @Override public void clearContent() { inventory.clearContent(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", saveItems());
        for (int i = 0; i < MAX_WORDS; i++) tag.putString("Word" + i, selectedWord(i));
        for (int i = 0; i < MAX_WORDS; i++) tag.putString("WordInk" + i, selectedWordInk(i));
        tag.putInt("SelectedSlot", selectedSlot);
        tag.putBoolean("OverlayMode", overlayMode);
        tag.putString("SelectedInk", selectedInk);
        for (int i = 0; i < INK_SLOTS; i++) { tag.putInt("InkType" + i, inkTypeIndex[i]); tag.putInt("InkCharge" + i, inkCharges[i]); }
    }

    /**
     * 逐槽存物品：<b>每条显式带上 "Slot" 下标</b>。
     *
     * <p><b>为什么不直接用容器自带的 createTag/fromTag</b>：那套约定由实现类决定，
     * 一旦读端按"列表里的第几条"而不是按下标还原，物品就会<b>整体错位</b> ——
     * 空着核心槽、只放了毛笔时，毛笔会被塞进核心位（"毛笔替代了核心"就是这么来的）。
     * 自己写下标，读写两端由构造保证一致，不吃实现细节的亏。
     */
    private ListTag saveItems() {
        ListTag list = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            stack.save(entry);
            list.add(entry);
        }
        return list;
    }

    /**
     * 与 {@link #saveItems()} 配对：严格按下标还原。
     *
     * <p><b>只写"存里有的格子"，绝不先清空整箱</b>：BE 构造出来本来就是空的，
     * 清空是多余动作；一旦哪天在非空状态被调一次（重载 / 复用），先清就等于把东西直接销毁。
     * 宁可少做一步，也不要留一个会吞物品的路径。
     *
     * <p><b>这里也刻意不做任何"自动归位"</b>：判断"该在哪一格"要读目标格状态，
     * 而目标格可能正是它自己（核心放在核心位时被判成"放错"→ 搬去产物位），
     * 且任何搬迁都可能覆盖别的物品。槽位错了玩家手动挪一下就完事，搬丢了就是真丢了。
     */
    private void loadItems(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.contains("Slot", Tag.TAG_ANY_NUMERIC) ? entry.getInt("Slot") : i;
            if (slot >= 0 && slot < SLOT_COUNT) inventory.setItem(slot, ItemStack.of(entry));
        }
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        loadItems(tag.getList("Inventory", 10));
        for (int i = 0; i < MAX_WORDS; i++) selectedWords[i] = tag.getString("Word" + i);
        for (int i = 0; i < MAX_WORDS; i++) selectedInks[i] = tag.getString("WordInk" + i);
        selectedSlot = tag.getInt("SelectedSlot");
        overlayMode = tag.getBoolean("OverlayMode");
        selectedInk = tag.getString("SelectedInk");
        for (int i = 0; i < INK_SLOTS; i++) { inkTypeIndex[i] = tag.contains("InkType" + i) ? tag.getInt("InkType" + i) : -1; inkCharges[i] = tag.getInt("InkCharge" + i); }
    }

    /** 第三方物流（漏斗/管道）也只能往对得上的格里放：核心位不收毛笔、毛笔位不收核心。 */
    @Override public boolean canPlaceItem(int index, ItemStack stack) {
        return switch (index) {
            case CORE_SLOT -> stack.getItem() instanceof MagicCoreItem;
            case BRUSH_SLOT -> stack.is(AkaishiMagicItems.magicBrush.get());
            case PRODUCT_SLOT -> false;
            default -> !inkTypeId(stack).isEmpty();
        };
    }
}
