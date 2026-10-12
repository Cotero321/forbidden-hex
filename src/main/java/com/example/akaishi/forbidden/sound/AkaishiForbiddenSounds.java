package com.example.akaishi.forbidden.sound;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 禁忌模块·音效注册（P3c 随 BOSS 阿盖托洛丝、P3d 随生命融合 / 母神祭坛 / 不可名状从本体 {@code ModSounds} 迁入）。
 *
 * <p><b>只搬注册代码、不搬 {@code sounds.json}</b>：音效事件的键是相对所在命名空间解析的
 * —— 本体 {@code assets/forbidden_hex/sounds.json} 里的 {@code "agaitolos_theme"} ⇒ {@code forbidden_hex:agaitolos_theme}。
 * 若把该文件换到 {@code assets/forbidden_hex/sounds.json}，键会变成
 * {@code forbidden_hex:agaitolos_theme}，与注册 id 不符（音效直接失效）。
 * 故 {@code assets/forbidden_hex/sounds.json} 与其中的条目<b>留在本体不动</b>，仅把 SoundEvent 注册搬来本模块。
 * <p>注册 id 与迁前逐字一致：{@code forbidden_hex:agaitolos_theme}（命名空间仍为三模块共用的 {@code forbidden_hex:}）。
 */
public final class AkaishiForbiddenSounds {

    /**
     * 阿盖托洛丝战斗音乐（53s 无缝循环素材）。
     * <p>与机器 {@code *_hum} 不同，本条不由服务端播放：位置音效且需"BOSS 一死立刻停"，
     * 只有客户端循环 SoundInstance 能做到，实际消费方是 forge 客户端
     * {@code AgaitolosMusicHandler} / {@code AgaitolosThemeSound}。
     * <p>音效资源（{@code sounds/agaitolos_theme.ogg} 与 {@code sounds.json} 条目）随本类迁入本模块资源目录，
     * 但命名空间目录仍为 {@code assets/forbidden_hex/}（见类注释）。
     */
    public static final RegistrySupplier<SoundEvent> AGAITOLOS_THEME = reg("agaitolos_theme");

    /**
     * 生命融合砧运转音（P3d 随生命融合自本体 {@code ModSounds} 迁入）。
     * <p>与 BOSS 音乐同类：只搬注册代码，{@code sounds.json} 条目与 .ogg 仍留在本体
     * {@code assets/forbidden_hex/}（键按所在命名空间解析，见类注释）。
     */
    public static final RegistrySupplier<SoundEvent> LIFE_FUSION_ANVIL_HUM = reg("life_fusion_anvil_hum");

    /**
     * 母神祭坛成型后未工作的氛围音：虚空的心跳声（5s 无缝循环，P3d 随祭坛自本体 {@code ModSounds} 迁入）。
     * <p>由整段无缝循环素材经 {@code MachineHum} 播放（重播间隔取音效时长），见 {@code AkaishiMotherAltarBlockEntity}。
     */
    public static final RegistrySupplier<SoundEvent> VOID_HEARTBEAT = reg("void_heartbeat");

    /** 母神祭坛仪式进行中的氛围音：虚空呓语声（8s 无缝循环） */
    public static final RegistrySupplier<SoundEvent> VOID_WHISPER = reg("void_whisper");

    /** 「不可名状」减益的耳中呓语：以玩家自身为音源播放的一次性低语（8s，P3d 迁入） */
    public static final RegistrySupplier<SoundEvent> UNNAMEABLE_WHISPER = reg("unnameable_whisper");

    private AkaishiForbiddenSounds() {
    }

    /** 强制类加载：确保 SoundEvent 在注册事件前完成注册（由 {@code AkaishiForbiddenMod.init} 调用） */
    public static void touch() {
    }

    private static RegistrySupplier<SoundEvent> reg(String name) {
        Registrar<SoundEvent> registrar = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.SOUND_EVENT);
        ResourceLocation id = new ResourceLocation(AkaishiMod.MOD_ID, name);
        return registrar.register(id, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
