package com.example.akaishi.forbidden.forge;

import com.example.akaishi.forbidden.effect.AkaishiDrainHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 平台「仪式吸取」接线（D158 / D257）：
 * <ul>
 *   <li>承接口：把 common 发出的「死于吸取」标记写入实体持久数据（common 不可见 NBT API）</li>
 *   <li>消费口：被吸死的实体不掉落物品、不掉经验、不算击杀</li>
 * </ul>
 */
public final class AkaishiAltarDrainHandler {

    public static final AkaishiAltarDrainHandler INSTANCE = new AkaishiAltarDrainHandler();

    /** 实体持久数据键：标记该实体死于仪式吸取 */
    private static final String TAG_DRAINED = "AkaishiDrained";

    private AkaishiAltarDrainHandler() {
    }

    /** 注入掉落豁免钩子（由平台初始化调用一次） */
    public static void install() {
        AkaishiDrainHooks.setDrainMarker(AkaishiAltarDrainHandler::setDrained);
    }

    private static void setDrained(LivingEntity entity, boolean drained) {
        CompoundTag data = entity.getPersistentData();
        if (drained) {
            data.putBoolean(TAG_DRAINED, true);
        } else {
            data.remove(TAG_DRAINED);
        }
    }

    /** 被吸死 → 掉落清空：直接清表并取消事件，双保险确保"生命被收回"不留任何产物 */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(TAG_DRAINED)) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    /** 被吸死 → 经验归零（不算击杀，也不留下经验） */
    @SubscribeEvent
    public void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(TAG_DRAINED)) {
            event.setDroppedExperience(0);
        }
    }
}
