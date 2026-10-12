package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.block.entity.AkaishiInscriptionTableBlockEntity;
import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.ClientMagicData;
import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AkaishiInscriptionTableMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 380, IMAGE_HEIGHT = 232;
    /** 核心槽：位于中央核心区正中心（红框位置）。 */
    public static final int CORE_X = 182, CORE_Y = 68;
    /** 左下输入槽：毛笔 / 墨（主墨 + 3 覆墨）。 */
    public static final int BRUSH_X = 10, BRUSH_Y = 98;
    public static final int INK_X = 28, INK_Y = 98;
    /** 右下产物槽：雕刻完成后刻好的核心出现在这里（只能取出）。 */
    public static final int PRODUCT_X = 317, PRODUCT_Y = 144;
    public static final int PLAYER_X = 109, PLAYER_INV_Y = 138, HOTBAR_Y = 196;
    public static final int BUTTON_ENGRAVE = 100;
    public static final int BUTTON_SLOT_BASE = 110;
    /**
     * 词按钮区间（+0..63，词表 51）。
     *
     * <p><b>为什么不复用紧凑编号</b>：旧值是 词 130 / 墨 160，而词按钮 id = 130 + 词下标（下标可达 50）
     * ⇒ 第 30 个之后的词会落进墨区间（160..170）被当成"选墨"、更后面的直接无人认领。
     * 表现就是"点某些词没反应 / 选中的墨莫名其妙变了"。三组区间必须彻底不相交。
     */
    public static final int BUTTON_WORD_BASE = 200;
    /** 墨按钮区间（+0..15，墨 11）。 */
    public static final int BUTTON_INK_BASE = 300;
    public static final int BUTTON_MODE = 400;
    public static final int PAGE_SIZE = 4;
    private final Container container;
    private final AkaishiInscriptionTableBlockEntity table;
    private final Player player;
    /** 同步掩码：0/1 = 已解锁词位掩码，2 = 已解锁墨位掩码（服务端算、客户端读）。 */
    private final int[] syncMasks = new int[3];

    /** 组合数据槽：前段来自方块实体，末 3 位是解锁掩码。 */
    private final ContainerData data = new ContainerData() {
        @Override public int getCount() {
            return AkaishiInscriptionTableBlockEntity.INSCRIPTION_DATA_COUNT + 3;
        }
        @Override public int get(int i) {
            int base = AkaishiInscriptionTableBlockEntity.INSCRIPTION_DATA_COUNT;
            if (i < base) return table == null ? 0 : table.get(i);
            return syncMasks[i - base];
        }
        @Override public void set(int i, int value) {
            int base = AkaishiInscriptionTableBlockEntity.INSCRIPTION_DATA_COUNT;
            if (i < base) {
                if (table != null) table.set(i, value);
            } else {
                syncMasks[i - base] = value;
            }
        }
    };

    /** 服务端重算解锁掩码（供数据槽同步）。 */
    private void refreshSyncMasks() {
        int m0 = 0, m1 = 0, mi = 0;
        if (table != null && player != null) {
            for (String id : table.unlockedWords(player)) {
                int idx = AkaishiInscriptionTableBlockEntity.wordIndexOf(id);
                if (idx < 0) continue;
                if (idx < 32) m0 |= 1 << idx; else m1 |= 1 << (idx - 32);
            }
            for (String id : table.unlockedInks(player)) {
                int idx = AkaishiInscriptionTableBlockEntity.typeIndexOf(id);
                if (idx >= 0) mi |= 1 << idx;
            }
        }
        syncMasks[0] = m0;
        syncMasks[1] = m1;
        syncMasks[2] = mi;
    }

    public int tableSlot() { return table == null ? 0 : table.selectedSlot(); }
    public String selectedWord(int slot) { return table == null ? "" : table.selectedWord(slot); }
    /** 某词位冻结的墨；空 = 该位没词或未记录（渲染时回落到当前选中的墨）。 */
    public String selectedWordInk(int slot) { return table == null ? "" : table.selectedWordInk(slot); }
    public boolean overlayMode() { return table != null && table.overlayMode(); }
    public String selectedInk() { return table == null ? "" : table.selectedInk(); }
    public String coreWord(int slot) { return coreStack().getItem() instanceof MagicCoreItem ? MagicCoreItem.word(coreStack(), slot) : ""; }
    public String coreInk(int slot) { return coreStack().getItem() instanceof MagicCoreItem ? MagicCoreItem.ink(coreStack(), slot) : ""; }
    public int coreLevel() { return coreStack().getItem() instanceof MagicCoreItem ? MagicCoreItem.level(coreStack()) : 0; }
    /**
     * 玩家魔法师等级。写入容量看它，不看<b>待刻核</b>的等级 ——
     * 空白核是 0 级（还没有词），若拿它算容量，八位会全封，永远写不进去。
     */
    public int playerLevel() {
        if (player != null && !player.level().isClientSide) {
            MagicState state = MagicStates.state(player);
            return state == null ? MagicState.DEFAULT_LEVEL : state.level();
        }
        return ClientMagicData.snapshot().level();
    }
    public int openSlotCount() { return CoreLayout.openSlotCount(playerLevel()); }
    /** 已解锁墨（由同步掩码得出，客户端同样可用）。 */
    public Set<String> unlockedInks() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i < AkaishiInscriptionTableBlockEntity.INK_IDS.length; i++) {
            if ((syncMasks[2] >>> i & 1) != 0) out.add(AkaishiInscriptionTableBlockEntity.INK_IDS[i]);
        }
        return out;
    }

    /** 已解锁词（由同步掩码得出，客户端同样可用）。 */
    public Set<String> unlockedWords() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i < AkaishiInscriptionTableBlockEntity.WORD_IDS.length; i++) {
            int bit = i < 32 ? (syncMasks[0] >>> i) : (syncMasks[1] >>> (i - 32));
            if ((bit & 1) != 0) out.add(AkaishiInscriptionTableBlockEntity.WORD_IDS[i]);
        }
        return out;
    }
    /** 输入槽里的核心（待刻）。 */
    public ItemStack inputCore() { return slotStack(AkaishiInscriptionTableBlockEntity.CORE_SLOT); }
    /** 产物槽里的核心（已刻完的产物）。 */
    public ItemStack productCore() { return slotStack(AkaishiInscriptionTableBlockEntity.PRODUCT_SLOT); }
    private ItemStack slotStack(int index) { return index < slots.size() ? slots.get(index).getItem() : ItemStack.EMPTY; }
    /** 兼容旧调用：默认指输入槽的核心。 */
    public ItemStack coreStack() { return inputCore(); }

    public ItemStack draftCore() {
        ItemStack core = inputCore().copy();
        if (!(core.getItem() instanceof MagicCoreItem)) return ItemStack.EMPTY;
        var words = new net.minecraft.nbt.ListTag();
        var inks = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 8; i++) {
            String word = selectedWord(i);
            String ink = selectedWordInk(i);
            words.add(net.minecraft.nbt.StringTag.valueOf(word));
            inks.add(net.minecraft.nbt.StringTag.valueOf(word.isEmpty() ? "" : ink.isEmpty() ? selectedInk() : ink));
        }
        core.getOrCreateTag().put(MagicCoreItem.WORDS_TAG, words);
        core.getOrCreateTag().put(MagicCoreItem.WORD_INKS_TAG, inks);
        return core;
    }

    /** 该种墨的累计耐久（列表显示用，不含背包）。 */
    public int inkStock(String inkId) { return table == null ? 0 : table.inkChargeOf(inkId); }

    /** 机器自身槽位数量；玩家槽从该下标开始。 */
    public int machineSlotCount() { return AkaishiInscriptionTableBlockEntity.SLOT_COUNT; }

    public AkaishiInscriptionTableMenu(int id, Inventory inv, AkaishiInscriptionTableBlockEntity table) {
        super(AkaishiForbiddenMenuRegs.INSCRIPTION_TABLE.get(), id);
        player = inv.player; this.table = table;
        // 容器直接用方块实体：所有写入都会经过其 setItem（墨槽在此吸收消耗）
        container = table == null ? new SimpleContainer(AkaishiInscriptionTableBlockEntity.SLOT_COUNT) : table;
        if (table != null) addDataSlots(data);   // 同步墨数据 + 当前选择 + 解锁掩码
        refreshSyncMasks();
        addSlot(new Slot(container, AkaishiInscriptionTableBlockEntity.CORE_SLOT, CORE_X, CORE_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof MagicCoreItem; }
        });
        addSlot(new Slot(container, AkaishiInscriptionTableBlockEntity.BRUSH_SLOT, BRUSH_X, BRUSH_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(AkaishiMagicItems.magicBrush.get()); }
        });
        for (int i = AkaishiInscriptionTableBlockEntity.MAIN_INK_SLOT; i < AkaishiInscriptionTableBlockEntity.INK_INPUT_END; i++) {
            final int slot = i;
            addSlot(new Slot(container, slot,
                    INK_X + (i - AkaishiInscriptionTableBlockEntity.MAIN_INK_SLOT) * 18, INK_Y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    // 输入槽：任何墨都可投入（会按种类自动合并），但要有可容纳的槽
                    return isInk(stack) && table != null
                            && table.canAccept(AkaishiInscriptionTableBlockEntity.inkTypeId(stack));
                }
                @Override public boolean mayPickup(Player p) { return false; }   // 只进不出
            });
        }
        // 产物槽：只能取出，不能放入
        addSlot(new Slot(container, AkaishiInscriptionTableBlockEntity.PRODUCT_SLOT, PRODUCT_X, PRODUCT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, PLAYER_X + col * 18, HOTBAR_Y));
    }

    private static boolean isInk(ItemStack stack) {
        for (var ink : AkaishiMagicItems.inks().values()) if (stack.is(ink.get())) return true;
        return false;
    }
    private List<String> words() { return new ArrayList<>(unlockedWords()); }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (table == null || player.level().isClientSide || !stillValid(player)) return false;
        if (id == BUTTON_ENGRAVE) {
            Component fail = table.tryEngrave(player);
            if (fail != null) player.displayClientMessage(fail.copy().withStyle(ChatFormatting.RED), true);
            return true;
        }
        if (id >= BUTTON_SLOT_BASE && id < BUTTON_SLOT_BASE + 8) { table.selectSlot(id - BUTTON_SLOT_BASE); return true; }
        if (id >= BUTTON_WORD_BASE && id < BUTTON_WORD_BASE + 64) {
            List<String> words = words(); int index = id - BUTTON_WORD_BASE;
            if (index >= 0 && index < words.size() && MagicWord.exists(words.get(index))) { return table.setDraft(words.get(index), player); }
        }
        if (id >= BUTTON_INK_BASE && id < BUTTON_INK_BASE + 16) {
            // 与客户端同序：都取掩码得出的 unlockedInks（INK_IDS 序），避免服务端另取一份列表而错位
            List<String> inks = new ArrayList<>(unlockedInks()); int index = id - BUTTON_INK_BASE;
            if (index >= 0 && index < inks.size()) { table.selectInk(inks.get(index), player); return true; }
        }
        if (id == BUTTON_MODE) { table.setOverlayMode(!table.overlayMode()); return true; }
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY; Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;
        ItemStack current = slot.getItem(); result = current.copy();
        if (index < AkaishiInscriptionTableBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(current, AkaishiInscriptionTableBlockEntity.SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (current.getItem() instanceof MagicCoreItem) {
            if (!moveItemStackTo(current, 0, 1, false)) return ItemStack.EMPTY;
        } else if (current.is(AkaishiMagicItems.magicBrush.get())) {
            if (!moveItemStackTo(current, 1, 2, false)) return ItemStack.EMPTY;
        } else if (isInk(current)) {
            if (!moveItemStackTo(current, AkaishiInscriptionTableBlockEntity.MAIN_INK_SLOT,
                    AkaishiInscriptionTableBlockEntity.INK_INPUT_END, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (current.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

    /** 服务端每 tick 吸收输入槽里的墨瓶，并重算解锁掩码（走原版槽位同步，不会留下预测残影）。 */
    @Override public void broadcastChanges() {
        if (table != null && player != null && !player.level().isClientSide) {
            table.tickAbsorbInk();
            table.prefillDraftsFromCore();
            refreshSyncMasks();
        }
        super.broadcastChanges();
    }

    /** 关界面时再吸收一次，避免“放进去还没到 tick 就关闭”而残留。 */
    @Override public void removed(Player player) {
        if (table != null && !player.level().isClientSide) {
            table.tickAbsorbInk();
        }
        super.removed(player);
    }
}
