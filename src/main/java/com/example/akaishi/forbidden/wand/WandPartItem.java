package com.example.akaishi.forbidden.wand;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 法杖模块物品（杖端 / 杖柄 / 杖核三段共用一个类）。
 *
 * <p><b>为什么三段共用</b>：段的差异已由 {@link WandPartDef} 的数据承载
 * （表 + 词条 + 作用域），物品本体只是"拿一段数据"的壳 ——
 * 三个类会复制三份一模一样的 tooltip 逻辑（违 DRY）。
 * 段位存在 {@link WandPartDef.Kind}，配方分类与合成引擎按它区分。
 *
 * <p>可堆叠 16：模块是<b>零件</b>，"囤零件再拼杖"是正常玩法。
 */
public class WandPartItem extends Item {

    private final WandPartDef def;

    public WandPartItem(WandPartDef def, Properties properties) {
        super(properties);
        this.def = def;
    }

    public WandPartDef def() {
        return def;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        WandTooltips.appendPart(tooltip, def);
    }
}
