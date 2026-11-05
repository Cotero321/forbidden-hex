package com.example.akaishi.forbidden.forge;

import com.example.akaishi.forbidden.block.entity.AkaishiForbiddenBlockEntities;
import com.example.akaishi.forbidden.entity.AkaishiForbiddenEntities;
import com.example.akaishi.forbidden.forge.boss.agaitolos.AgaitolosBossBarOverlay;
import com.example.akaishi.forbidden.forge.boss.agaitolos.AgaitolosMusicHandler;
import com.example.akaishi.forbidden.forge.boss.agaitolos.AgaitolosRenderer;
import com.example.akaishi.forbidden.forge.boss.chimera.ChimeraRenderer;
import com.example.akaishi.forbidden.forge.boss.chimera.ChimeraHeadRenderer;
import com.example.akaishi.forbidden.forge.client.AkaishiDeepSeaFogHandler;
import com.example.akaishi.forbidden.forge.client.visual.ClientPostEffects;
import com.example.akaishi.forbidden.forge.client.AkaishiUnnameableHandler;
import com.example.akaishi.forbidden.forge.client.AkaishiUnnameableOverlay;
import com.example.akaishi.forbidden.forge.client.MotherAltarRenderer;
import com.example.akaishi.forbidden.forge.client.hud.AkaishiSanityHudElement;
import com.example.akaishi.forbidden.forge.client.hud.AkaishiWandFocusOverlay;
import com.example.akaishi.forbidden.forge.magic.SpellCircleRenderer;
import com.example.akaishi.forbidden.forge.magic.SpellProjectileRenderer;
import com.example.akaishi.forbidden.forge.sanity.shadow.ShadowRenderer;
import com.example.akaishi.forbidden.forge.familiar.FamiliarRenderer;
import com.example.akaishi.forbidden.forge.familiar.FamiliarWorkbenchScreen;
import com.example.akaishi.forbidden.forge.familiar.FamiliarInfoScreen;
import com.example.akaishi.forbidden.forge.transmutation.SourceTransmutationFurnaceScreen;
import com.example.akaishi.forbidden.familiar.workbench.FamiliarWorkbenchContent;
import com.example.akaishi.forbidden.familiar.info.FamiliarInfoMenu;
import com.example.akaishi.forbidden.transmutation.source_furnace.SourceTransmutationFurnaceContent;
import dev.architectury.registry.menu.MenuRegistry;
import com.example.akaishi.forbidden.wand.AkaishiWandItems;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;

/**
 * 禁忌模块的客户端专属注册（P3b 随理智系统、P3c 随 BOSS 从本体 {@code AkaishiModForge} 迁入）。
 *
 * <p>单独成类是为了不在服务端加载任何客户端类型；由平台入口用
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} 延时求值。
 */
public final class AkaishiForbiddenClientSetup {

    private AkaishiForbiddenClientSetup() {
    }

    /** 客户端注册（<b>运行期</b>部分）：渲染器 / HUD 元素 / 雾效 / 后处理 / 按键消费 */
    public static void register() {
        com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles.setLocalParticleGate(
                com.example.akaishi.forbidden.forge.client.visual.ClientVisualBudget::reserveLocalSpell);
        MinecraftForge.EVENT_BUS.register(com.example.akaishi.forbidden.forge.magic.visual.ProjectileTrailEffects.INSTANCE);
        com.example.akaishi.forbidden.boss.agaitolos.entity.AgaitolosHexOrb.setClientImpactHandler(
                com.example.akaishi.forbidden.forge.magic.visual.ProjectileTrailEffects.INSTANCE::hexOrbBurst);
        // 咒核镶嵌按键的运行期部分：forge 总线消费点击 + 把真实键名注入 tooltip 提示。
        // 键位本身在 {@link #registerKeyMapping} 里注册（由模组构造阶段挂上的监听调用）
        com.example.akaishi.forbidden.forge.client.WandSocketKeybind.registerRuntime(MinecraftForge.EVENT_BUS);
        // 影怪：GeckoLib 几何动画渲染 + 眼睛自发光图层（图层在渲染器构造器内注册）
        EntityRenderers.register(AkaishiForbiddenEntities.SHADOW.get(), ShadowRenderer::new);
        // 影怪的精神弹：复用原版凋零头渲染器（零新增贴图，见 ShadowBolt 类注释）
        EntityRenderers.register(AkaishiForbiddenEntities.SHADOW_BOLT.get(), WitherSkullRenderer::new);
        // 阿盖托洛丝：GeckoLib 几何动画渲染（阶段模型/动画 + 自发光图层）
        EntityRenderers.register(AkaishiForbiddenEntities.AGAITOLOS.get(), AgaitolosRenderer::new);
        EntityRenderers.register(AkaishiForbiddenEntities.CHIMERA.get(), ChimeraRenderer::new);
        EntityRenderers.register(AkaishiForbiddenEntities.CHIMERA_HEAD.get(), ChimeraHeadRenderer::new);
        EntityRenderers.register(AkaishiForbiddenEntities.CHIMERA_ORB.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        // 阿盖托洛丝的远程弹体：复用原版凋零头渲染器（子类可被 EntityRenderer<WitherSkull> 直接渲染）
        EntityRenderers.register(AkaishiForbiddenEntities.AGAITOLOS_WITHER_SKULL.get(), WitherSkullRenderer::new);
        EntityRenderers.register(AkaishiForbiddenEntities.AGAITOLOS_HEX_ORB.get(),
                com.example.akaishi.forbidden.forge.boss.agaitolos.AgaitolosHexOrbRenderer::new);
        // 法术弹体（X2 投射）：程序化绘制 —— 形态由核心 E 决定、配色由源与墨决定（见 SpellProjectileRenderer）。
        // 不再复用原版凋零头模型：固定模型无法表达"这一发是火墨的尖刺还是渊墨的涡旋"
        EntityRenderers.register(AkaishiForbiddenEntities.SPELL_BOLT.get(), SpellProjectileRenderer::new);
        // 法阵（X5 布场）：程序化绘制主体 + 覆盖圈 + 词符环（零新增贴图，见 SpellCircleRenderer）
        EntityRenderers.register(AkaishiForbiddenEntities.SPELL_CIRCLE.get(), SpellCircleRenderer::new);
        EntityRenderers.register(AkaishiForbiddenEntities.FAMILIAR.get(), FamiliarRenderer::new);
        MenuRegistry.registerScreenFactory(FamiliarWorkbenchContent.MENU.get(), FamiliarWorkbenchScreen::new);
        MenuRegistry.registerScreenFactory(FamiliarInfoMenu.MENU.get(), FamiliarInfoScreen::new);
        MenuRegistry.registerScreenFactory(SourceTransmutationFurnaceContent.MENU.get(), SourceTransmutationFurnaceScreen::new);
        com.example.akaishi.forbidden.forge.magic.visual.SpellVisualEventHandler.INSTANCE.register();
        MinecraftForge.EVENT_BUS.register(com.example.akaishi.forbidden.forge.magic.visual.SpellVisualEventHandler.INSTANCE);
        com.example.akaishi.forbidden.forge.magic.sound.SpellSoundHandler.INSTANCE.register();
        MinecraftForge.EVENT_BUS.register(com.example.akaishi.forbidden.forge.magic.sound.SpellSoundHandler.INSTANCE);
        // 母神祭坛：方块实体渲染器（供奉物悬浮展示，P3d 随祭坛自本体迁入）
        BlockEntityRenderers.register(
                AkaishiForbiddenBlockEntities.CHISHI_MOTHER_ALTAR.get(), MotherAltarRenderer::new);
        BlockEntityRenderers.register(AkaishiForbiddenBlockEntities.SOURCE_ALTAR.get(),
                com.example.akaishi.forbidden.forge.client.SourceAltarRenderer::new);
        BlockEntityRenderers.register(AkaishiForbiddenBlockEntities.OFFERING_PEDESTAL.get(),
                com.example.akaishi.forbidden.forge.client.OfferingPedestalRenderer::new);
        // 深海视野锁定：主世界海洋群系 Y<0 时大幅收拢雾距并染深水色
        MinecraftForge.EVENT_BUS.register(AkaishiDeepSeaFogHandler.INSTANCE);
        // 低理智和不可名状后处理由同一调度器管理所有权与优先级。
        MinecraftForge.EVENT_BUS.register(ClientPostEffects.INSTANCE);
        // 相机滚转/抖动与 FOV 脉动继续复用同一个只读强度源。
        MinecraftForge.EVENT_BUS.register(AkaishiUnnameableHandler.INSTANCE);
        // 阿盖托洛丝战斗音乐：附近有存活 BOSS 时挂一条跟随它的循环位置音效（客户端 TickableSoundInstance）
        MinecraftForge.EVENT_BUS.register(AgaitolosMusicHandler.INSTANCE);
        // 注：客户端 HUD 叠加层不在此处注册 —— 见 registerOverlays(RegisterGuiOverlaysEvent)：
        // 那属 mod 总线的注册类事件，必须在模组构造阶段挂监听，晚挂就等于没挂。
        // 断线清空施法状态镜像：会话在服务端，不清则重连后"释放中"光环会一直转
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                        com.example.akaishi.forbidden.magic.ClientCastState.clear());
    }

    /**
     * 键位注册（mod 总线 · {@code RegisterKeyMappingsEvent}）。
     *
     * <p>由 {@code AkaishiForbiddenModForge} 在<b>模组构造阶段</b>挂上的监听调用 ——
     * 该事件与 {@code FMLClientSetupEvent} 同阶段但不保证在其之后，晚挂就等于没挂。
     */
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        com.example.akaishi.forbidden.forge.client.WandSocketKeybind.registerKeyMapping(event);
    }

    /**
     * HUD 叠加层注册（mod 总线 · {@code RegisterGuiOverlaysEvent}）。
     *
     * <p>同上：必须由构造阶段挂上的监听调用，否则叠加层永不绘制。
     */
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(),
                "sanity", new AkaishiSanityHudElement());
        // 「不可名状」边缘粗线 + 噪点 + 低语叠加层（P3d 自本体迁入）
        event.registerAboveAll("unnameable_overlay", new AkaishiUnnameableOverlay());
        // 阿盖托洛丝铭牌血条：锚在原版 boss 血条层。
        // 挂上去即占据"原版血条的位置"（该层已空 —— 本 BOSS 不用 ServerBossEvent）
        event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(),
                "agaitolos_boss_bar", new AgaitolosBossBarOverlay());
        // 法杖 HUD·准星区（缩小后的冷却环 + 魔力条；释放中改画光环与微光）：
        // 挂在原版准星之上 —— 准星本体仍由原版画，我们只在它周围加东西
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(),
                "forbidden_hex_wand_focus", new AkaishiWandFocusOverlay());
    }

    /**
     * 法杖<b>部件模型</b>注册（mod 总线 · {@code ModelEvent.RegisterAdditional}）。
     *
     * <p><b>为什么必须显式注册</b>：{@code assets/.../models/item/wand_parts/<id>.json} 不被任何
     * 物品模型引用 —— 光有文件，模型烘焙器不会去烘它，运行时
     * {@code ModelManager#getModel} 只会拿到 missing model（表现：整根法杖一段都画不出来）。
     * 注册进附加模型后才会随资源重载一起烘焙。
     *
     * <p>id 表沿用 {@link AkaishiWandItems#parts()}（唯一真源），与渲染端的查询路径逐字一致：
     * {@code forbidden_hex:item/wand_parts/<部件id>}。
     */
    public static void registerWandPartModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        for (String id : AkaishiWandItems.parts().keySet()) {
            event.register(new ResourceLocation(com.example.akaishi.AkaishiMod.MOD_ID,
                    "item/wand_parts/" + id));
        }
    }
}
