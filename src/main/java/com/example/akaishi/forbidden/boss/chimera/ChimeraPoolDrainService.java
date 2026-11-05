package com.example.akaishi.forbidden.boss.chimera;

import com.example.akaishi.forbidden.api.sanity.SanityChangeSource;
import com.example.akaishi.forbidden.magic.MagicState;
import com.example.akaishi.forbidden.magic.MagicStates;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanityState;
import net.minecraft.world.entity.player.Player;

/** Server-side resource effects shared by Chimera's greed and wrath titles. */
public final class ChimeraPoolDrainService {
    public static final float WRATH_CURRENT_POOL_FRACTION = 0.25F;
    private ChimeraPoolDrainService() { }
    public static DrainResult drainCurrentPools(Player player, float fraction) {
        if (player == null || player.level() == null || player.level().isClientSide || !Float.isFinite(fraction) || fraction <= 0F) return DrainResult.EMPTY;
        float clamped = Math.min(1F, fraction);
        MagicState magic = MagicStates.state(player);
        SanityState sanity = SanityServiceImpl.state(player);
        if (magic == null && sanity == null) return DrainResult.EMPTY;
        float life = drainMagicPool(magic, MagicStates.POOL_LIFE, clamped);
        float dark = drainMagicPool(magic, MagicStates.POOL_DARK, clamped);
        float white = drainMagicPool(magic, MagicStates.POOL_WHITE, clamped);
        float beforeSan = sanity == null ? 0F : sanity.san();
        float san = beforeSan * clamped;
        if (san > 0F) {
            float absorbed = Math.min(sanity.protection(), san);
            SanityServiceImpl service = SanityServiceImpl.instance();
            if (absorbed > 0F) service.addProtectionInternal(player, -absorbed, SanityChangeSource.EXTERNAL);
            if (san > absorbed) service.addSanInternal(player, -(san - absorbed), SanityChangeSource.EXTERNAL);
        }
        return new DrainResult(life, dark, white, sanity == null ? 0F : Math.max(0F, beforeSan - sanity.san()));
    }
    private static float drainMagicPool(MagicState state, int index, float fraction) {
        if (state == null) return 0F;
        float before = state.pool(index);
        float removed = before * fraction;
        state.setPool(index, before - removed);
        return removed;
    }
    public record DrainResult(float life, float dark, float white, float san) {
        private static final DrainResult EMPTY = new DrainResult(0F, 0F, 0F, 0F);
        public float total() { return life + dark + white + san; }
    }
}
