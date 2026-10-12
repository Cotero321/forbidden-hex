package com.example.akaishi.forbidden.forge.sound;

import com.example.akaishi.forbidden.multiblock.AkaishiGoatAltarTiersStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 祭坛成型后屏蔽四个结构信标的声音。
 * <p>信标的环境音/激活音均由 {@code BeaconBlockEntity} 经 {@code level.playSound} 播放，
 * 服务端与客户端都会派发可取消的 {@link PlayLevelSoundEvent.AtPosition}；取消事件即不播放该声音，
 * 故无需 Mixin。信标是否归属成型结构由方块状态判定（{@link AkaishiGoatAltarTiersStructure#isFormedBeacon}），
 * 无额外状态需持久化，世界重载后依旧生效。
 */
public final class AkaishiAltarSoundMuter {

    public static final AkaishiAltarSoundMuter INSTANCE = new AkaishiAltarSoundMuter();

    private AkaishiAltarSoundMuter() {
    }

    @SubscribeEvent
    public void onPlaySoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        // 过滤前置：非信标音效直接放行，避免对每个位置音都做方块查询
        if (!isBeaconSound(event.getSound())) {
            return;
        }
        Level level = event.getLevel();
        if (AkaishiGoatAltarTiersStructure.isFormedBeacon(level, BlockPos.containing(event.getPosition()))) {
            event.setCanceled(true);
        }
    }

    /** 仅拦截信标自身音效，避免误伤恰好落在信标坐标上的其它声音 */
    private static boolean isBeaconSound(@Nullable Holder<SoundEvent> sound) {
        if (sound == null) {
            return false;
        }
        SoundEvent value = sound.value();
        return value == SoundEvents.BEACON_AMBIENT
                || value == SoundEvents.BEACON_ACTIVATE
                || value == SoundEvents.BEACON_DEACTIVATE
                || value == SoundEvents.BEACON_POWER_SELECT;
    }
}
