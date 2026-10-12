package com.example.akaishi.forbidden.forge;

import com.example.akaishi.forbidden.boss.agaitolos.AgaitolosEntity;
import com.example.akaishi.forbidden.command.AkaishiSanityCommand;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.AkaishiForbiddenMod;
import com.example.akaishi.forbidden.forge.AkaishiAltarDrainHandler;
import com.example.akaishi.forbidden.forge.boss.agaitolos.AgaitolosArenaEvents;
import com.example.akaishi.forbidden.forge.boss.agaitolos.AgaitolosDoomHandler;
import com.example.akaishi.config.ModConfig;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import com.example.akaishi.forbidden.forge.magic.AkaishiMagicRegistryChecker;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanityCombatHandler;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanityDamageHandler;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanityDamageSeenHandler;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanityFoodHandler;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanityKillHandler;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanityPotionBrewing;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanitySleepHandler;
import com.example.akaishi.forbidden.forge.sanity.AkaishiSanityUnnameableHandler;
import com.example.akaishi.forbidden.forge.sound.AkaishiAltarSoundMuter;
import com.example.akaishi.forbidden.sanity.shadow.ShadowEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 赤石禁忌的 Forge 平台入口（mods.toml 中 modId = forbidden_hex 的实现类）。
 *
 * <p>P3a 仅完成构造与通用初始化转发；P3b 起承接理智系统的平台生效层：
 * 8 个 Forge 事件处理器、酿造配方、影怪属性/渲染器、HUD 元素、深海视野锁定、
 * 低理智视野后处理与 {@code /akaishi sanity} 调试指令；P3c 起承接 BOSS 阿盖托洛丝的平台生效层：
 * 凋亡降低治疗、下界牢狱场地事件、BOSS 属性与渲染器、铭牌血条与战斗音乐 —— 均与迁前逐处一致（只换模块位置）。
 */
@Mod(AkaishiForbiddenMod.MOD_ID)
public final class AkaishiForbiddenModForge {

    public AkaishiForbiddenModForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(AkaishiForbiddenMod.MOD_ID, modBus);
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, ModConfig.SPEC);
        ModConfig.bake(modBus);
        com.example.akaishi.forbidden.brewing.WitchOvenBlockEntity.setFuelProvider(stack ->
                net.minecraftforge.common.ForgeHooks.getBurnTime(stack, net.minecraft.world.item.crafting.RecipeType.SMELTING),
                net.minecraft.world.item.ItemStack::getCraftingRemainingItem);
        com.example.akaishi.forbidden.brewing.BrewingBlocks.register(
                com.example.akaishi.forbidden.forge.brewing.ForgeCrucibleBlockEntity::new);
        com.example.akaishi.forbidden.brewing.BrewingEntities.register(
                com.example.akaishi.forbidden.forge.brewing.ForgeBrewingPotionProjectile::new,
                com.example.akaishi.forbidden.forge.brewing.ForgeBrewingEffectCloud::new);
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> com.example.akaishi.forbidden.brewing.client.BrewingClientSetup::register)));
        AkaishiForbiddenMod.init();

        // 理智回复药水的酿造配方：addMix 是 Forge 补进原版 PotionBrewing 的 API（common 不可见），
        // 且必须等药水注册完成，故放在 common setup（enqueueWork 内执行）
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (FMLCommonSetupEvent event) -> event.enqueueWork(AkaishiSanityPotionBrewing::register));
        // 影怪属性供应商：非 Mob 的 LivingEntity 同样必须在此登记，否则实体构造时读不到 MAX_HEALTH 即崩
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (EntityAttributeCreationEvent event) ->
                        event.put(AkaishiForbiddenEntities.SHADOW.get(), ShadowEntity.createAttributes().build()));
        // 阿盖托洛丝 BOSS 属性：自定义生物必须在此注册属性供应商，否则实体生成即崩（P3c 自本体迁入）
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (EntityAttributeCreationEvent event) ->
                        event.put(AkaishiForbiddenEntities.AGAITOLOS.get(), AgaitolosEntity.createAttributes().build()));

        // 理智系统·精神伤害减免：只处理 forbidden_hex:psychic 伤害（减伤口径在 common 的 SanityDamageGuard 内）
        MinecraftForge.EVENT_BUS.register(AkaishiSanityDamageHandler.INSTANCE);
        // 禁忌秘典·"挨过的伤害类型"记档：LivingHurtEvent 里把玩家被打中的伤害类型记进 SanityState
        MinecraftForge.EVENT_BUS.register(AkaishiSanityDamageSeenHandler.INSTANCE);
        // 理智系统·阈值惩罚（伤害侧）：攻击效能 / 易伤 / 护甲效能 / 0% 档精神化 / 骷髅凋零
        MinecraftForge.EVENT_BUS.register(AkaishiSanityCombatHandler.INSTANCE);
        // 理智系统·食补触发：原版进食 Finish 事件 → common 的食补入口
        MinecraftForge.EVENT_BUS.register(AkaishiSanityFoodHandler.INSTANCE);
        // 理智系统·遭遇不可名状的起手扣减：效果被施加到玩家身上时结算一次（MobEffectEvent.Added）
        MinecraftForge.EVENT_BUS.register(AkaishiSanityUnnameableHandler.INSTANCE);
        // 理智系统·击杀类首见上报：LivingDeathEvent 里按"责任实体是玩家"归因
        MinecraftForge.EVENT_BUS.register(AkaishiSanityKillHandler.INSTANCE);
        // 理智系统·睡眠（P6）：睡醒奖励 + 幻翼附加精神伤害投递
        MinecraftForge.EVENT_BUS.register(AkaishiSanitySleepHandler.INSTANCE);

        // BOSS 阿盖托洛丝（P3c 自本体迁入）：
        // 阶段三「凋亡」的降低治疗层：common 无治疗钩子，故在此消费 Forge 的 LivingHealEvent
        MinecraftForge.EVENT_BUS.register(AgaitolosDoomHandler.INSTANCE);
        // 下界牢狱：召唤仪式右键入口（RightClickBlock）+ 场地方块不可破坏（BreakEvent / ExplosionEvent.Detonate）
        MinecraftForge.EVENT_BUS.register(AgaitolosArenaEvents.INSTANCE);

        // 母神祭坛（P3d 自本体迁入）：
        // 祭坛成型静音：屏蔽四个结构信标的环境音/激活音（取消位置音事件，无需 Mixin）
        MinecraftForge.EVENT_BUS.register(AkaishiAltarSoundMuter.INSTANCE);
        // 仪式吸取掉落豁免：被吸死无掉落/无经验（common 定钩子，此处注入实现并消费事件，D158/D257）
        AkaishiAltarDrainHandler.install();
        MinecraftForge.EVENT_BUS.register(AkaishiAltarDrainHandler.INSTANCE);

        // 理智系统调试指令：/akaishi sanity get|set|env|food；魔法侧调试：/akaishi magic unlockall|maxlevel|resetwords
        MinecraftForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> {
                    AkaishiSanityCommand.register(event.getDispatcher());
                    com.example.akaishi.forbidden.command.AkaishiMagicCommand.register(event.getDispatcher());
                });

        // 魔法侧静态表自检：dev 环境 -Dakaishi.gametest=1 时，服务端启动后同步核对
        // （表规模 / 交叉引用 / 词分组 / 墨色唯一 / 词协同与对立轴互斥 / 加词不得变弱）
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            if (System.getProperty("akaishi.gametest") != null) {
                AkaishiMagicRegistryChecker.run();
                if (Boolean.getBoolean("akaishi.magic.worldcheck"))
                    com.example.akaishi.forbidden.forge.magic.MagicPlanWorldCheck.run(event.getServer().overworld());
            }
        });

        // 客户端专属注册（渲染器 / HUD 元素 / 雾效 / 后处理）：在 FMLClientSetupEvent 中求值 ——
        // 该事件晚于各注册表填充，AkaishiForbiddenEntities.*.get() 才能取到值；服务端不加载客户端类型
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (FMLClientSetupEvent event) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> AkaishiForbiddenClientSetup::register));

        // ⚠ 注册类事件（键位 / HUD 叠加层）必须在**模组构造阶段**就挂上监听。
        // 它们与 FMLClientSetupEvent **同阶段**但不保证在其之后 —— 若在 FMLClientSetupEvent 内部再
        // addListener，事件可能已经派发完毕 ⇒ 键位没注册、overlay 永不绘制（表现：按 R 无反应 / HUD 不显示）。
        // 本体 AkaishiModForge 正是构造阶段挂的，此处与它同口径。
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(
                    (RegisterKeyMappingsEvent event) -> AkaishiForbiddenClientSetup.registerKeyMapping(event));
            FMLJavaModLoadingContext.get().getModEventBus().addListener(
                    (RegisterGuiOverlaysEvent event) -> AkaishiForbiddenClientSetup.registerOverlays(event));
            // 法杖部件模型：同样必须在构造阶段挂监听（ModelEvent 在资源加载时派发，晚挂即漏）
            FMLJavaModLoadingContext.get().getModEventBus().addListener(
                    (ModelEvent.RegisterAdditional event) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> AkaishiForbiddenClientSetup.registerWandPartModels(event)));
        });
    }
}
