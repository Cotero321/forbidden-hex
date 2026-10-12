package com.example.akaishi.forbidden.magic.exec.effect;

import com.example.akaishi.forbidden.api.magic.exec.IPrimitiveExecutor;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import com.example.akaishi.forbidden.api.magic.exec.MagicExecRegistry;
import com.example.akaishi.forbidden.magic.exec.ExecSupport;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.ArrayList;
import java.util.List;

/** 开锁、驱散和破盾仅在世界状态确实改变后报告成功。 */
public final class BreakExecutors {
    private BreakExecutors() {}
    public static void register() {
        MagicExecRegistry.register(new Unlock());
        MagicExecRegistry.register(new Terminate());
        MagicExecRegistry.register(new Shield());
    }

    static final class Unlock implements IPrimitiveExecutor {
        @Override public String effectId() { return MagicIds.Effect.BREAK_UNLOCK; }
        @Override public boolean worksWithoutTarget() { return true; }
        @Override public void apply(MagicContext context, Entity target) { applyResult(context, target); }
        @Override public boolean applyResult(MagicContext context, Entity target) {
            var level = context.level();
            var hit = ExecSupport.aimBlock(context, context.snapshot().range());
            if (level == null || hit == null || !level.isLoaded(hit.getBlockPos())) return false;
            var pos = hit.getBlockPos();
            if (context.caster() instanceof net.minecraft.world.entity.player.Player player
                    && (!level.mayInteract(player, pos) || !player.mayBuild())) return false;
            var state = level.getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN)) return false;
            return level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
        }
    }

    static final class Terminate implements IPrimitiveExecutor {
        @Override public String effectId() { return MagicIds.Effect.BREAK_TERMINATE; }
        @Override public void apply(MagicContext context, Entity target) { applyResult(context, target); }
        @Override public boolean applyResult(MagicContext context, Entity target) {
            if (!(target instanceof LivingEntity living)) return false;
            var operation = context.snapshot().operation();
            boolean purify = operation != null
                    ? operation.traits().contains("purify_negative")
                    : ExecSupport.hasWord(context, MagicWord.JING) || target == context.caster();
            boolean terminate = operation != null
                    ? operation.traits().contains("terminate_beneficial")
                    : !purify;
            int max = ExecSupport.layersFromRatio(ExecSupport.pRatio(context, effectId()));
            List<MobEffect> chosen = new ArrayList<>();
            int removedNegative = 0;
            int removedBeneficial = 0;
            for (MobEffectInstance instance : living.getActiveEffects()) {
                boolean beneficial = instance.getEffect().isBeneficial();
                if (beneficial ? terminate && removedBeneficial < max : purify && removedNegative < max) {
                    chosen.add(instance.getEffect());
                    if (beneficial) removedBeneficial++;
                    else removedNegative++;
                }
            }
            boolean changed = false;
            for (MobEffect effect : chosen) changed |= living.removeEffect(effect);
            return changed;
        }
    }

    static final class Shield implements IPrimitiveExecutor {
        @Override public String effectId() { return MagicIds.Effect.BREAK_SHIELD; }
        @Override public boolean worksWithoutTarget() { return true; }
        @Override public void apply(MagicContext context, Entity target) { applyResult(context, target); }
        @Override public boolean applyResult(MagicContext context, Entity target) {
            boolean changed = target instanceof LivingEntity living && living.removeEffect(MobEffects.ABSORPTION);
            if (!(context.level() instanceof ServerLevel level)) return changed;
            var hit = ExecSupport.aimBlock(context, context.snapshot().range());
            if (hit == null || !level.isLoaded(hit.getBlockPos())) return changed;
            var pos = hit.getBlockPos();
            var state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (state.isAir() || hardness < 0f || hardness > 3f) return changed;
            if (context.caster() instanceof net.minecraft.world.entity.player.Player player
                    && (!level.mayInteract(player, pos) || !player.mayBuild())) return changed;
            return level.destroyBlock(pos, false, context.caster()) || changed;
        }
    }
}
