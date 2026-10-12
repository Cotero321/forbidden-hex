package com.example.akaishi.forbidden.menu;

import com.example.akaishi.AkaishiMod;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

/**
 * 菜单注册 · 禁忌秘典（P3a 随秘典整体迁入 forbidden_hex）。
 *
 * <p>注册 id 与 screen factory 与迁前逐字一致：{@code forbidden_hex:forbidden_hex_codex}
 * （命名空间仍为三模块共用的 {@code forbidden_hex:}）。
 * 菜单类型字段原在本体门面 {@code ModMenus.CHISHI_CODEX}，因禁忌模块不可反向依赖本体，
 * 故随注册一并迁到本类（对界面/网络无行为影响）。
 */
public final class AkaishiCodexMenuRegs {

    /** 禁忌秘典菜单类型（手持物品界面，无任何槽位；进度与条件由 S2C 快照下发） */
    public static RegistrySupplier<MenuType<AkaishiCodexMenu>> CHISHI_CODEX;

    private AkaishiCodexMenuRegs() {
    }

    /** 注册菜单类型 + 客户端 screen factory（手持物品界面：无槽位、无方块实体） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        MenuType<AkaishiCodexMenu> codexType = MenuRegistry.ofExtended(
                (syncId, inv, buf) -> new AkaishiCodexMenu(syncId, inv));
        CHISHI_CODEX = (RegistrySupplier<MenuType<AkaishiCodexMenu>>) (Object) RegistrarManager
                .get(AkaishiMod.MOD_ID).get(Registries.MENU)
                .register(new ResourceLocation(AkaishiMod.MOD_ID, "forbidden_hex_codex"), () -> codexType);
        EnvExecutor.runInEnv(Env.CLIENT, () -> () ->
                MenuRegistry.registerScreenFactory(codexType, AkaishiCodexScreen::new));
    }
}
