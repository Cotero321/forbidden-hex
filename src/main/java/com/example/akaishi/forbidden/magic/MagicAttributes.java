package com.example.akaishi.forbidden.magic;

import com.example.akaishi.AkaishiMod;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/** 法术专属防御属性；装备和效果均通过属性修饰符提供魔法抗性。 */
public final class MagicAttributes {
    public static final double MAX_RESISTANCE = 0.8d;
    public static RegistrySupplier<Attribute> MAGIC_RESISTANCE;

    private MagicAttributes() {
    }

    public static void register() {
        Registrar<Attribute> registrar = RegistrarManager.get(AkaishiMod.MOD_ID).get(Registries.ATTRIBUTE);
        MAGIC_RESISTANCE = registrar.register(new ResourceLocation(AkaishiMod.MOD_ID, "magic_resistance"),
                () -> new RangedAttribute("attribute.name.forbidden_hex.magic_resistance",
                        0d, 0d, MAX_RESISTANCE).setSyncable(true));
    }

    public static float resistance(LivingEntity entity) {
        var attribute = entity.getAttribute(MAGIC_RESISTANCE.get());
        double value = attribute == null ? 0d : attribute.getValue();
        return Double.isFinite(value) ? (float) Math.max(0d, Math.min(MAX_RESISTANCE, value)) : 0f;
    }
}
