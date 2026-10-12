package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.api.magic.exec.*;
import com.example.akaishi.forbidden.magic.*;
import com.example.akaishi.forbidden.magic.cast.MagicPayment;
import com.example.akaishi.forbidden.magic.exec.MagicPipeline;
import com.example.akaishi.forbidden.magic.exec.MagicScheduler;
import com.example.akaishi.forbidden.magic.formula.SpellPlanAdapter;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/** dev专服真实实体回归；仅显式启用时运行，不覆盖执行注册表。 */
public final class MagicPlanWorldCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagicPlanWorldCheck.class);
    private MagicPlanWorldCheck() {}

    private static MagicContext context(ServerLevel level, Cow caster, String ink, String source, String... words) {
        var result = SpellPlans.compile(new SpellInput(List.of(words), java.util.Collections.nCopies(words.length, ink), ink, source, 0, 0));
        if (!result.ok()) throw new IllegalStateException(result.diagnostics().toString());
        return new MagicContext(caster, level, caster.position(), source, ink, List.of(ink), List.of(words), 6,
                SpellPlanAdapter.snapshot(result.plan()));
    }
    private static Cow cow(ServerLevel level, Vec3 at) {
        Cow cow = new Cow(EntityType.COW, level);
        cow.setPos(at);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        cow.getAttribute(Attributes.ARMOR).setBaseValue(20);
        cow.setHealth(100);
        return cow;
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    public static void run(ServerLevel level) {
        Cow caster = cow(level, new Vec3(0, -40, 0));
        Cow normal = cow(level, new Vec3(0, -40, 2));
        Cow mental = cow(level, normal.position());
        Cow star = cow(level, normal.position());
        try {
            var normalContext = context(level, caster, "chi", "white", "tou");
            var sanContext = context(level, caster, "chi", "san", "tou");
            var starContext = context(level, caster, "xing", "san", "tou");
            MagicPipeline.actionFor(normalContext).fire(normal, normal.position(), new Vec3(0, 0, 1));
            MagicPipeline.actionFor(sanContext).fire(mental, mental.position(), new Vec3(0, 0, 1));
            MagicPipeline.actionFor(starContext).fire(star, star.position(), new Vec3(0, 0, 1));
            float normalDamage = 100 - normal.getHealth(), mentalDamage = 100 - mental.getHealth(), starDamage = 100 - star.getHealth();
            require(normalDamage > 0 && mentalDamage > normalDamage && Math.abs(starDamage / mentalDamage - 1.75f) < 0.001f,
                    "real hurt / armor / star multiplier failed");
            require(MagicDamageTypes.psychic(level, caster).is(com.example.akaishi.effect.ModDamageTypes.PSYCHIC), "psychic type missing");
            var ward = context(level, caster, "rang", "life", "hu");
            var applied = MagicPipeline.castResult(ward);
            float absorption = caster.getAbsorptionAmount();
            require(applied.successfulTargets() > 0 && absorption > 0, "ward did not grant absorption");
            caster.hurt(level.damageSources().generic(), 2);
            require(caster.getAbsorptionAmount() < absorption, "ward did not consume absorption");
            var player = FakePlayerFactory.getMinecraft(level);
            var state = MagicStates.state(player);
            require(state != null, "fake player has no magic state");
            float beforePool = state.pool(MagicStates.POOL_WHITE);
            float beforeHealth = player.getHealth();
            state.setPool(MagicStates.POOL_WHITE, 100);
            var receipt = MagicPayment.pay(player, MagicSource.WHITE, 10, null);
            require(receipt != null && state.pool(MagicStates.POOL_WHITE) == 90 && player.getHealth() == beforeHealth, "real payment failed");
            MagicPayment.refund(player, receipt, 1);
            MagicPayment.refund(player, receipt, 1);
            require(state.pool(MagicStates.POOL_WHITE) == 100, "duplicate receipt refund");
            state.setPool(MagicStates.POOL_WHITE, beforePool);
            LOGGER.info("[AkaishiMagicWorld] PASS hurt normal={} SAN={} star={} wardAbsorption={} payment=10 refundOnce", normalDamage, mentalDamage, starDamage, absorption);
        } catch (RuntimeException error) {
            LOGGER.error("[AkaishiMagicWorld] FAIL", error);
        } finally {
            caster.discard(); normal.discard(); mental.discard(); star.discard();
        }
        Vec3 testOrigin = Vec3.atCenterOf(level.getSharedSpawnPos()).add(0, 12, 0);
        Cow dotCaster = cow(level, testOrigin);
        Cow dotTarget = cow(level, testOrigin.add(0, 0, 2));
        dotCaster.setNoGravity(true); dotCaster.setNoAi(true);
        dotTarget.setNoGravity(true); dotTarget.setNoAi(true);
        level.addFreshEntity(dotCaster); level.addFreshEntity(dotTarget);
        var dot = context(level, dotCaster, "chi", "san", "feng_seam");
        dotCaster.setYRot(0);
        java.util.List<Integer> notified = new java.util.ArrayList<>();
        var dotResult = MagicPipeline.castResult(dot, notified::add);
        float first = dotTarget.getHealth();
        MagicScheduler.schedule(level, 25, () -> {
            try {
                require(dotTarget.getHealth() < first, "ATTACH did not pulse damage");
                require(dotResult.accepted() && dotResult.successfulTargets() >= 2
                        && notified.size() == dotResult.successfulTargets()
                        && notified.get(notified.size() - 1) == dotResult.successfulTargets(), "DOT callback/count mismatch");
                LOGGER.info("[AkaishiMagicWorld] PASS ATTACH real hurt firstHealth={} secondHealth={} callbacks={} count={} plannedPulses={}",
                        first, dotTarget.getHealth(), notified.size(), dotResult.successfulTargets(),
                        com.example.akaishi.forbidden.magic.exec.MagicPulseTiming.perSegment(dot.snapshot().plan(), dot.snapshot().plan().operations().get(0)));
            } catch (RuntimeException error) { LOGGER.error("[AkaishiMagicWorld] FAIL ATTACH", error); }
            finally { dotTarget.discard(); dotCaster.discard(); }
        });
    }
}
