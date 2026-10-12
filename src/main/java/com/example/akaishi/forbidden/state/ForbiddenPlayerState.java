package com.example.akaishi.forbidden.state;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.sanity.SanityState;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID)
public final class ForbiddenPlayerState {
    public static final Capability<ForbiddenPlayerState> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() { });
    public static final ResourceLocation ID = new ResourceLocation("wuzhou_forbidden", "player_state");

    private final SanityState sanity = new SanityState();
    private final MagicState magic = new MagicState();
    private long psychicUntil;

    public static ForbiddenPlayerState of(Player player) {
        return player == null ? null : player.getCapability(CAPABILITY).orElse(null);
    }

    public SanityState sanity() {
        return sanity;
    }

    public MagicState magic() {
        return magic;
    }

    public long getPsychicUntil() {
        return psychicUntil;
    }

    public void setPsychicUntil(long value) {
        psychicUntil = Math.max(0L, value);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("sanity", sanity.save());
        tag.put(MagicState.TAG_ROOT, magic.save());
        if (psychicUntil != 0L) {
            tag.putLong("psychic_until", psychicUntil);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        sanity.load(tag.getCompound("sanity"));
        magic.load(tag.getCompound(MagicState.TAG_ROOT));
        psychicUntil = Math.max(0L, tag.getLong("psychic_until"));
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            Provider provider = new Provider();
            event.addCapability(ID, provider);
            event.addListener(provider.optional::invalidate);
        }
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            ForbiddenPlayerState source = of(original);
            ForbiddenPlayerState destination = of(event.getEntity());
            if (source != null && destination != null) {
                destination.load(source.save());
                destination.sanity.clearTemporaries();
                destination.magic.clearTemporaries();
                destination.sanity.markDirty();
                destination.magic.markDirty();
            }
        } finally {
            original.invalidateCaps();
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        markDirty(event.getEntity());
    }

    private static void markDirty(Player player) {
        ForbiddenPlayerState state = of(player);
        if (state != null) {
            state.sanity.markDirty();
            state.magic.markDirty();
        }
    }

    @Mod.EventBusSubscriber(modid = AkaishiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(ForbiddenPlayerState.class);
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final ForbiddenPlayerState state = new ForbiddenPlayerState();
        private final LazyOptional<ForbiddenPlayerState> optional = LazyOptional.of(() -> state);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return state.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            state.load(tag);
        }
    }
}
