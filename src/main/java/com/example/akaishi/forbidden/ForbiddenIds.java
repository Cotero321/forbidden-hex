package com.example.akaishi.forbidden;

import com.example.akaishi.AkaishiMod;
import net.minecraft.resources.ResourceLocation;

public final class ForbiddenIds {
    private ForbiddenIds() {
    }

    public static String inkName(String inkId) {
        return switch (inkId) {
            case "chi" -> "ember";
            case "rang" -> "earth";
            case "ju" -> "gale";
            case "man" -> "vine";
            case "shi" -> "blight";
            case "xu" -> "order";
            case "hun" -> "chaos";
            case "yuan" -> "abyss";
            case "yao" -> "radiance";
            case "an" -> "gloom";
            case "xing" -> "astral";
            default -> inkId;
        };
    }

    public static String itemPath(String path) {
        if (path.startsWith("akaishi_")) {
            String legacySuffix = path.substring("akaishi_".length());
            boolean knownLegacyId = legacySuffix.startsWith("altar_")
                    || legacySuffix.startsWith("crying_")
                    || legacySuffix.startsWith("essence_")
                    || legacySuffix.startsWith("ink_")
                    || legacySuffix.startsWith("inscription_")
                    || legacySuffix.startsWith("magic_")
                    || legacySuffix.startsWith("mother_")
                    || legacySuffix.startsWith("wand_")
                    || switch (legacySuffix) {
                        case "cub_heart", "fertility_ring", "life_touch", "golden_melon_slice",
                                "sculk_lifeform", "wither_charm", "sanity_tonic", "greater_sanity_tonic",
                                "sanity_garland", "forbidden_codex" -> true;
                        default -> false;
                    };
            if (knownLegacyId) {
                path = "forbidden_hex_" + legacySuffix;
            }
        }
        path = switch (path) {
            case "forbidden_codex", "golden_melon_slice", "sculk_lifeform", "sanity_tonic",
                    "greater_sanity_tonic", "sanity_garland" -> "forbidden_hex_" + path;
            default -> path;
        };
        for (String prefix : new String[] {"forbidden_hex_ink_", "forbidden_hex_wand_tip_ink_"}) {
            if (path.startsWith(prefix)) {
                return prefix + inkName(path.substring(prefix.length()));
            }
        }
        return path;
    }

    public static String savedKey(String key) {
        if (key.startsWith("akaishi_")) {
            return itemPath(key);
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) {
            return key;
        }
        ResourceLocation newId = current(id);
        return newId.equals(id) ? key : newId.toString();
    }

    public static ResourceLocation current(ResourceLocation id) {
        String namespace = id.getNamespace();
        if (!namespace.equals(AkaishiMod.MOD_ID) && !namespace.equals("wuzhou_forbidden")
                && !namespace.equals("akaishi") && !namespace.equals("akaishi_forbidden")) {
            return id;
        }
        String path = id.getPath();
        if (path.equals("akaishi_forbidden")) {
            path = "forbidden_hex";
        } else if (path.startsWith("akaishi_forbidden_")) {
            path = "forbidden_hex_" + path.substring("akaishi_forbidden_".length());
        } else {
            path = itemPath(path);
        }
        return new ResourceLocation(AkaishiMod.MOD_ID, path);
    }
}
