package com.example.akaishi.forbidden.forge.magic;

import com.example.akaishi.forbidden.brewing.BrewingStatusEffects;
import com.example.akaishi.forbidden.magic.MagicAttributes;
import com.example.akaishi.forbidden.magic.MagicDamageTypes;
import com.example.akaishi.forbidden.boss.chimera.ChimeraEntity;
import com.example.akaishi.forbidden.sanity.SanityDamageGuard;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanityCombatHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 原版护甲等减伤由法术标签跳过；此处只处理法术的专属防御分量。 */
public final class SpellDamageEvents {
    public static final SpellDamageEvents INSTANCE = new SpellDamageEvents();

    private SpellDamageEvents() {
    }

    public static void attributes(EntityAttributeModificationEvent event) {
        var attribute = MagicAttributes.MAGIC_RESISTANCE.get();
        for (var type : event.getTypes()) {
            if (!event.has(type, attribute)) event.add(type, attribute);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void hurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0f
                || !event.getSource().is(MagicDamageTypes.MAGIC_RESISTIBLE)) return;
        if (event.getEntity() instanceof Player player
                && AkaishiSanityCombatHandler.convertsToPsychic(player, event.getSource())) return;
        float psychic = event.getEntity() instanceof ChimeraEntity chimera ? chimera.spellPsychicFraction(event.getSource())
                : event.getSource() instanceof MagicDamageTypes.SpellSource source ? source.psychicFraction() : 0f;
        float mentalMultiplier = event.getEntity() instanceof Player player
                ? SanityDamageGuard.applyPsychicReduction(player, 1f) : 1f;
        mentalMultiplier *= 1f - BrewingStatusEffects.damageReduction(event.getEntity(), event.getSource());
        float multiplier = (1f - psychic) * (1f - MagicAttributes.resistance(event.getEntity()))
                + psychic * mentalMultiplier;
        event.setAmount(event.getAmount() * multiplier);
    }
}
