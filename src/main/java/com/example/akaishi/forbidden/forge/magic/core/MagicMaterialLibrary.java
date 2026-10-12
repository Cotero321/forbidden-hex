package com.example.akaishi.forbidden.forge.magic.core;

import com.example.akaishi.forbidden.magic.table.MagicInkDef;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;

/** 核心渲染颜色唯一从魔法源表与墨表读取。 */
public final class MagicMaterialLibrary {
    private MagicMaterialLibrary() {}

    public static int source(MagicSource source) {
        return source.rgb();
    }

    public static int ink(String id, int fallback) {
        MagicInkDef ink = MagicInkTable.get(id);
        return ink == null ? fallback : ink.rgb();
    }

    public static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.max(0, (int) (((rgb >> 16) & 255) * factor)));
        int g = Math.min(255, Math.max(0, (int) (((rgb >> 8) & 255) * factor)));
        int b = Math.min(255, Math.max(0, (int) ((rgb & 255) * factor)));
        return r << 16 | g << 8 | b;
    }
}
