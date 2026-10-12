package com.example.akaishi.forbidden;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.effect.ScreenFlashS2C;
import com.example.akaishi.forbidden.api.sanity.SanityServices;
import com.example.akaishi.forbidden.block.AkaishiForbiddenBlocks;
import com.example.akaishi.forbidden.block.AkaishiMotherAltarBlocks;
import com.example.akaishi.forbidden.block.entity.AkaishiForbiddenBlockEntities;
import com.example.akaishi.forbidden.boss.agaitolos.arena.NetherPrisonArena;
import com.example.akaishi.forbidden.effect.AkaishiForbiddenEffects;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.item.AkaishiCodexItems;
import com.example.akaishi.forbidden.item.AkaishiMagicItems;
import com.example.akaishi.forbidden.item.AkaishiSanityItems;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.menu.AkaishiCodexMenuRegs;
import com.example.akaishi.forbidden.menu.AkaishiCodexSync;
import com.example.akaishi.forbidden.menu.AkaishiForbiddenMenuRegs;
import com.example.akaishi.forbidden.sanity.SanityEnvironmentSettlement;
import com.example.akaishi.forbidden.sanity.SanityFirstEncounterSettlement;
import com.example.akaishi.forbidden.sanity.SanityNaturalRegen;
import com.example.akaishi.forbidden.sanity.SanityPenaltySettlement;
import com.example.akaishi.forbidden.sanity.SanityPhantomDive;
import com.example.akaishi.forbidden.sanity.SanityServiceImpl;
import com.example.akaishi.forbidden.sanity.SanitySleepDeprivation;
import com.example.akaishi.forbidden.sanity.SanitySyncS2C;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinFirstEncounters;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinFood;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinRestores;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinRules;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinThresholdCuts;
import com.example.akaishi.forbidden.sanity.content.SanityFoodSettlement;
import com.example.akaishi.forbidden.sanity.content.SanityRestorePotions;
import com.example.akaishi.forbidden.sound.AkaishiForbiddenSounds;
import com.example.akaishi.forbidden.wand.AkaishiWandItems;
import com.example.akaishi.forbidden.wand.AkaishiWandRecipes;
import com.example.akaishi.forbidden.wand.WandMagicHook;
import com.example.akaishi.forbidden.wand.WandSettlement;
import com.example.akaishi.forbidden.item.ForbiddenMaterialItems;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;

public final class AkaishiForbiddenMod {
    public static final String MOD_ID = AkaishiMod.MOD_ID;
    private static boolean initialized;

    private AkaishiForbiddenMod() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ForbiddenMaterialItems.register();
        com.example.akaishi.forbidden.brewing.BrewingItems.register();
        com.example.akaishi.forbidden.brewing.BrewingRecipes.register();
        com.example.akaishi.forbidden.brewing.BrewingMenus.register();
        AkaishiForbiddenBlocks.register();
        AkaishiMotherAltarBlocks.register();
        AkaishiForbiddenBlockEntities.register();
        AkaishiCodexItems.register();
        AkaishiForbiddenEntities.register();
        AkaishiForbiddenEffects.register();
        AkaishiSanityItems.register();
        AkaishiMagicItems.register();
        AkaishiMagicParticles.register();
        AkaishiWandItems.register();
        AkaishiWandRecipes.register();
        WandMagicHook.install();
        WandSettlement.register();
        com.example.akaishi.forbidden.wand.WandSocketC2S.register();
        SanityRestorePotions.register();
        AkaishiForbiddenCreativeTabs.register();
        AkaishiCodexMenuRegs.register();
        AkaishiForbiddenMenuRegs.register();
        AkaishiCodexSync.register();
        SanityServices.register(SanityServiceImpl.instance());
        SanityBuiltinRules.register();
        SanityBuiltinFood.register();
        SanityBuiltinFirstEncounters.register();
        SanityFirstEncounterSettlement.registerNotifier();
        SanityBuiltinRestores.register();
        SanityBuiltinThresholdCuts.register();
        TickEvent.SERVER_LEVEL_POST.register(SanityEnvironmentSettlement::serverTick);
        TickEvent.SERVER_LEVEL_POST.register(SanityPenaltySettlement::serverTick);
        TickEvent.SERVER_LEVEL_POST.register(SanityPhantomDive::serverTick);
        TickEvent.SERVER_LEVEL_POST.register(SanityFoodSettlement::serverTick);
        TickEvent.SERVER_LEVEL_POST.register(SanityNaturalRegen::serverTick);
        TickEvent.SERVER_LEVEL_POST.register(SanitySleepDeprivation::serverTick);
        TickEvent.SERVER_LEVEL_POST.register(SanitySyncS2C::serverTick);
        com.example.akaishi.forbidden.magic.MagicPoolRegen.register();
        com.example.akaishi.forbidden.magic.MagicHeatSettlement.register();
        com.example.akaishi.forbidden.magic.cast.MagicCastManager.register();
        com.example.akaishi.forbidden.magic.exec.MagicExecBuiltins.register();
        com.example.akaishi.forbidden.api.magic.MagicServices.register(
                com.example.akaishi.forbidden.magic.MagicServiceImpl.instance());
        TickEvent.SERVER_LEVEL_POST.register(com.example.akaishi.forbidden.magic.MagicSyncS2C::serverTick);
        TickEvent.SERVER_LEVEL_POST.register(NetherPrisonArena::serverTick);
        AkaishiForbiddenSounds.touch();
        if (Platform.getEnvironment() == Env.CLIENT) {
            AkaishiCodexSync.registerClient();
            SanitySyncS2C.registerClient();
            ScreenFlashS2C.registerClient();
            com.example.akaishi.forbidden.magic.cast.MagicCastStateS2C.registerClient();
            com.example.akaishi.forbidden.magic.MagicSyncS2C.registerClient();
        }
    }
}
