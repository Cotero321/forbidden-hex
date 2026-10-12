package com.example.akaishi.forbidden.forge.sanity;

import com.example.akaishi.forbidden.api.sanity.SanityServices;
import com.example.akaishi.forbidden.sanity.SanityKillReward;
import com.example.akaishi.forbidden.sanity.content.SanityBuiltinFirstEncounters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 击杀侧的理智处理（Forge 服务端）：① 击杀类首见上报；② 击杀恢复（SAN + 临时上限减免）。
 *
 * <p><b>为什么必须程序化</b>：击杀是"行为"，环境轮询只能看到"附近有监守者"，
 * 表达不了"我把它杀了"；而"首次击杀"与"首次遭遇"在数值上是两条不同档位（击杀不削上限、只给认知）。
 *
 * <p>归因取 {@code getSource().getEntity()}（伤害的<b>责任实体</b>）：弓箭 / 三叉戟等投射物击杀
 * 也算到射手头上；环境伤害（摔死、岩浆）则不会命中本分支。
 *
 * <p><b>两条消费的职责边界</b>：本类只做"事件 → 责任实体"的搬运与分支，
 * 数值口径分居 {@code SanityBuiltinFirstEncounters}（首见表）与 {@link SanityKillReward}（击杀恢复判据），
 * 两者共用同一次事件、同一次归因，不重复扫实体、不重复判死因。
 */
public final class AkaishiSanityKillHandler {

    public static final AkaishiSanityKillHandler INSTANCE = new AkaishiSanityKillHandler();

    private AkaishiSanityKillHandler() {
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return; // 双端都会收到该事件，只在服务端结算
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // ① 击杀恢复：任何生物击杀都给（类别判据与常量见 SanityKillReward，内部自判总开关）
        SanityKillReward.apply(player, event.getEntity());
        // ② 击杀类首见：只有表内三个 BOSS/精英入口才是首见（是否首次由核心按玩家存档判定）
        ResourceLocation encounterId = encounterFor(event);
        if (encounterId != null) {
            SanityServices.get().reportFirstEncounter(player, encounterId);
        }
    }

    /** 死亡生物 → 首见 id；不在表内返回 null（只有三个敌对 BOSS/精英入口） */
    private static ResourceLocation encounterFor(LivingDeathEvent event) {
        if (event.getEntity() instanceof Warden) {
            return SanityBuiltinFirstEncounters.KILL_WARDEN;
        }
        if (event.getEntity() instanceof WitherBoss) {
            return SanityBuiltinFirstEncounters.KILL_WITHER;
        }
        if (event.getEntity() instanceof EnderDragon) {
            return SanityBuiltinFirstEncounters.KILL_ENDER_DRAGON;
        }
        return null;
    }
}
