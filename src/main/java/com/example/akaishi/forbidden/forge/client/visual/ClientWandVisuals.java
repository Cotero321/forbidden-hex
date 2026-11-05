package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.item.MagicCoreItem;
import com.example.akaishi.forbidden.magic.ClientCastState;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.visual.CastVisualTimeline;
import com.example.akaishi.forbidden.wand.WandItem;
import com.example.akaishi.forbidden.wand.WandModifiers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Shared, read-only presentation state; server state remains authoritative. */
@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, value = Dist.CLIENT)
public final class ClientWandVisuals {
    private static final CastVisualTimeline TIMELINE = new CastVisualTimeline();
    private static ClientLevel world;
    private static LocalPlayer owner;
    private static ItemStack selectedCore = ItemStack.EMPTY;
    private static ItemStack wand = ItemStack.EMPTY;
    private static String assembly = "";
    private static MagicSource source;
    private static int inkRgb = 0xFFFFFF;
    private static String inkId = "";
    private static List<String> slotWords = List.of();
    private static List<String> slotInks = List.of();
    private static float chargeTicks;
    private static float segmentTicks;
    private static int selectedSlot = -2;
    private static boolean valid;
    private static boolean channel;
    private static int selectionAge = 20;
    private static int failureTicks;

    private ClientWandVisuals() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != world || minecraft.player != owner) {
            reset();
            world = minecraft.level;
            owner = minecraft.player;
        }
        if (owner == null || world == null || minecraft.isPaused()) return;
        if (failureTicks > 0) failureTicks--;
        selectionAge = Math.min(20, selectionAge + 1);
        boolean usingWand = owner.isUsingItem() && owner.getUseItem().getItem() instanceof WandItem;
        MagicCastState serverState = ClientCastState.state();
        ItemStack previousHand = selectedSlot == -1 ? owner.getOffhandItem()
                : selectedSlot == owner.getInventory().selected ? owner.getMainHandItem() : ItemStack.EMPTY;
        wand = usingWand ? owner.getUseItem()
                : serverState.isActive() && previousHand.getItem() instanceof WandItem ? previousHand
                : WandItem.heldWand(owner);
        ItemStack core = wand.isEmpty() ? ItemStack.EMPTY : WandItem.spell(wand);
        int slot = wand.isEmpty() ? -2 : usingWand
                ? owner.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? owner.getInventory().selected : -1
                : wand == owner.getMainHandItem() ? owner.getInventory().selected : -1;
        String parts = WandItem.tipId(wand) + '/' + WandItem.handleId(wand) + '/' + WandItem.coreId(wand);
        if (selectedSlot != slot || !ItemStack.matches(selectedCore, core) || !assembly.equals(parts)) {
            selectedSlot = slot;
            selectedCore = core.copy();
            assembly = parts;
            selectionAge = 0;
            failureTicks = 0;
            TIMELINE.reset();
            source = WandItem.socketedSource(wand);
            inkId = WandItem.mainInkId(core);
            inkRgb = WandItem.mainInkRgb(core);
            var summary = core.getItem() instanceof MagicCoreItem ? MagicCoreItem.summarize(core) : null;
            valid = summary != null && summary.result() != null && WandItem.parts(wand).size() == 3;
            chargeTicks = 0f;
            segmentTicks = 1f;
            channel = valid && MagicIds.Delivery.CHANNEL.equals(summary.result().delivery().deliveryId());
            slotWords = List.of();
            slotInks = List.of();
            if (valid) {
                List<String> inks = new ArrayList<>();
                List<String> wordsBySlot = new ArrayList<>();
                List<String> inksBySlot = new ArrayList<>();
                for (int wordSlot = 0; wordSlot < MagicCoreItem.SLOTS; wordSlot++) {
                    String word = MagicCoreItem.word(core, wordSlot);
                    String wordInk = MagicCoreItem.ink(core, wordSlot);
                    wordsBySlot.add(word);
                    inksBySlot.add(wordInk);
                    if (!word.isEmpty()) inks.add(wordInk);
                }
                slotWords = List.copyOf(wordsBySlot);
                slotInks = List.copyOf(inksBySlot);
                var plan = SpellPlanAdapter.adjusted(summary.result(), WandModifiers.resolve(wand, source, inks,
                        WandItem.extra(wand, source), 1));
                chargeTicks = plan.delivery().castTimeSeconds() * 20f;
                segmentTicks = Math.max(1f, plan.schedule().intervalSeconds() * 20f);
            }
        }
        MagicCastState state = wand.isEmpty() || source == null || !valid || !owner.isAlive() || owner.isSpectator()
                ? MagicCastState.IDLE : serverState;
        TIMELINE.tick(state, channel, usingWand);
    }

    @SubscribeEvent
    public static void feedback(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay() || WandItem.heldWand(Minecraft.getInstance().player).isEmpty()
                || !(event.getMessage().getContents() instanceof TranslatableContents message)) return;
        String key = message.getKey();
        if (key.startsWith("gui.forbidden_hex.wand.cast.") && !key.endsWith(".success") && !key.endsWith(".busy")) {
            failureTicks = 16;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static ItemStack wand() { return wand; }
    public static MagicSource source() { return source; }
    public static int inkRgb() { return inkRgb; }
    public static String inkId() { return inkId; }
    public static List<String> slotWords() { return slotWords; }
    public static List<String> slotInks() { return slotInks; }
    public static boolean valid() { return valid; }
    public static boolean denied() { return failureTicks > 0; }
    public static MagicCastState state() { return TIMELINE.state(); }
    public static float age(float partialTick) { return TIMELINE.age(partialTick); }
    public static float chargeProgress(float partialTick) { return TIMELINE.chargeProgress(chargeTicks, partialTick); }
    public static float transition(float partialTick) { return TIMELINE.transition(partialTick); }
    public static float segmentPhase(float partialTick) { return TIMELINE.segmentPhase(segmentTicks, partialTick); }
    public static float selection(float partialTick) { return Math.max(0f, 1f - (selectionAge + partialTick) / 12f); }

    private static void reset() {
        ClientCastState.clear();
        TIMELINE.reset();
        selectedCore = ItemStack.EMPTY;
        wand = ItemStack.EMPTY;
        assembly = "";
        selectedSlot = -2;
        source = null;
        slotWords = List.of();
        slotInks = List.of();
        valid = false;
        channel = false;
        chargeTicks = 0f;
        segmentTicks = 1f;
        selectionAge = 20;
        failureTicks = 0;
        owner = null;
        world = null;
    }
}
