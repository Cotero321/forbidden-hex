package com.example.akaishi.forbidden.familiar.entity;

import com.example.akaishi.forbidden.familiar.core.FamiliarData;
import com.example.akaishi.forbidden.familiar.core.FamiliarMode;
import com.example.akaishi.forbidden.familiar.core.FamiliarTemplateId;
import com.example.akaishi.forbidden.familiar.core.FamiliarStats;
import com.example.akaishi.forbidden.familiar.core.FamiliarTemplateData;
import com.example.akaishi.forbidden.familiar.core.FamiliarMaterialData;
import com.example.akaishi.forbidden.familiar.FamiliarContent;
import com.example.akaishi.forbidden.familiar.FamiliarBehaviorService;
import com.example.akaishi.forbidden.familiar.FamiliarSoulAbilityService;
import com.example.akaishi.forbidden.familiar.FamiliarRuntimeService;
import com.example.akaishi.forbidden.familiar.item.FamiliarContractItem;
import com.example.akaishi.forbidden.familiar.item.FamiliarBoundGearItem;
import com.example.akaishi.forbidden.familiar.info.FamiliarInfoMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.tags.DamageTypeTags;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Common-side familiar entity shell. Template behavior and Geo rendering are added by the
 * data-driven behavior/rendering layers; this class owns persistent identity and sync state.
 */
public class FamiliarEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> TEMPLATE =
            SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> AFFINITY =
            SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LEVEL =
            SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ALIGNMENT =
            SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HUNGER =
            SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOOD =
            SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private UUID familiarId = UUID.randomUUID();
    private FamiliarMode mode = FamiliarMode.FOLLOW;
    private long experience;
    private int sourceEnergy;
    private int sourceEnergyCapacity;
    private float saturation;
    private CompoundTag soulScores = new CompoundTag();
    private List<String> dataMaterials = new ArrayList<>();
    private int dataBiomassCost;
    private ItemStack equipment = ItemStack.EMPTY;
    private ItemStack accessory = ItemStack.EMPTY;
    private FamiliarMaterialData.Modifiers materialModifiers = FamiliarMaterialData.Modifiers.none();
    private boolean deathPending;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public FamiliarEntity(EntityType<? extends FamiliarEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TEMPLATE, FamiliarTemplateId.NONE.id());
        entityData.define(AFFINITY, "");
        entityData.define(LEVEL, 0);
        entityData.define(ALIGNMENT, 0);
        entityData.define(HUNGER, FamiliarData.MAX_HUNGER);
        entityData.define(MOOD, FamiliarData.MAX_MOOD);
    }

    @Override
    protected void registerGoals() {
        // Template-specific goals are installed after the template definition is loaded.
    }

    @Override
    public void tick() {
        super.tick();
        FamiliarBehaviorService.tick(this);
        FamiliarSoulAbilityService.tick(this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && amount > 0.0F) {
            if (dodgeChance() > 0.0D && random.nextDouble() < dodgeChance()) return false;
            if (magicDamageReduction() > 0.0D && source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
                amount *= (float) (1.0D - magicDamageReduction());
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        FamiliarAnimations.registerControllers(this, controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            deathPending = true;
            FamiliarRuntimeService.storeDeathPending(this);
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!isOwnedBy(player)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) FamiliarInfoMenu.open(serverPlayer, this);
            return InteractionResult.CONSUME;
        }
        var stack = FamiliarRuntimeService.findContractForEntity(player, getUUID());
        if (stack != null) {
            FamiliarData data = toFamiliarData();
            data.setActive(false);
            data.setActiveEntity(null);
            FamiliarContractItem.writeData(stack, data);
            discard();
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    public void bindOwner(@Nullable UUID owner) {
        this.ownerId = owner;
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    public UUID familiarId() {
        return familiarId;
    }

    public ItemStack equipment() {
        return equipment.copy();
    }

    public ItemStack accessory() {
        return accessory.copy();
    }

    public void setGear(FamiliarBoundGearItem.SlotType slotType, ItemStack stack) {
        if (slotType == FamiliarBoundGearItem.SlotType.EQUIPMENT) equipment = stack == null ? ItemStack.EMPTY : stack.copy();
        else accessory = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public boolean isOwnedBy(@Nullable Player player) {
        return player != null && ownerId != null && ownerId.equals(player.getUUID());
    }

    public FamiliarTemplateId template() {
        return FamiliarTemplateId.fromId(entityData.get(TEMPLATE));
    }

    public void setTemplate(FamiliarTemplateId template) {
        entityData.set(TEMPLATE, (template == null ? FamiliarTemplateId.NONE : template).id());
        applyTemplateStats();
    }

    public String affinity() {
        return entityData.get(AFFINITY);
    }

    public void setAffinity(String affinity) {
        entityData.set(AFFINITY, affinity == null ? "" : affinity);
    }

    public int levelValue() {
        return entityData.get(LEVEL);
    }

    public void setLevelValue(int level) {
        entityData.set(LEVEL, Math.max(0, level));
        applyTemplateStats();
    }

    public int alignment() {
        return entityData.get(ALIGNMENT);
    }

    public void setAlignment(int alignment) {
        entityData.set(ALIGNMENT, Math.max(-7, Math.min(7, alignment)));
    }

    public int hunger() {
        return entityData.get(HUNGER);
    }

    public void setHunger(int hunger) {
        entityData.set(HUNGER, Math.max(0, Math.min(FamiliarData.MAX_HUNGER, hunger)));
    }

    public int mood() {
        return entityData.get(MOOD);
    }

    public void setMood(int mood) {
        entityData.set(MOOD, Math.max(0, Math.min(FamiliarData.MAX_MOOD, mood)));
    }

    public FamiliarMode mode() {
        return mode;
    }

    public void setMode(FamiliarMode mode) {
        this.mode = mode == null ? FamiliarMode.FOLLOW : mode;
    }

    public long experience() {
        return experience;
    }

    public void setExperience(long experience) {
        this.experience = Math.max(0L, experience);
    }

    public void setSourceEnergy(int current, int capacity) {
        sourceEnergy = Math.max(0, current);
        sourceEnergyCapacity = Math.max(0, capacity);
    }

    public int sourceEnergy() {
        return sourceEnergy;
    }

    public int sourceEnergyCapacity() {
        return sourceEnergyCapacity;
    }

    /** Aggregated non-vanilla material modifiers for skill and combat services. */
    public FamiliarMaterialData.Modifiers materialModifiers() {
        return materialModifiers;
    }

    public double spellPower() {
        FamiliarStats base = FamiliarTemplateData.statsAtLevel(template(), levelValue());
        return Math.max(0.0D, base.spellPower() * (1.0D + materialModifiers.spellPowerMultiplier()));
    }

    public double hungerConsumptionMultiplier() {
        return Math.max(0.0D, 1.0D + materialModifiers.hungerConsumptionMultiplier());
    }

    public double dodgeChance() {
        return Math.max(0.0D, Math.min(1.0D, materialModifiers.dodgeChance()));
    }

    public double knockbackResistanceBonus() {
        return Math.max(0.0D, materialModifiers.knockbackResistance());
    }

    public double criticalChance() {
        return Math.max(0.0D, Math.min(1.0D, materialModifiers.criticalChance()));
    }

    public double lifesteal() {
        return Math.max(0.0D, Math.min(1.0D, materialModifiers.lifesteal()));
    }

    public double regenerationMultiplier() {
        return Math.max(0.0D, 1.0D + materialModifiers.regenerationMultiplier());
    }

    public double spellPenetration() {
        return Math.max(0.0D, materialModifiers.spellPenetration());
    }

    public double magicDamageReduction() {
        return Math.max(0.0D, Math.min(1.0D, materialModifiers.magicDamageReduction()));
    }

    public double soulAttackMultiplier() {
        return FamiliarSoulAbilityService.attackDamageMultiplier(this);
    }

    /** Apply only template and level values; material, soul and equipment modifiers remain separate. */
    private void applyTemplateStats() {
        FamiliarTemplateId template = template();
        if (!template.isConfigured()) return;
        FamiliarStats stats = FamiliarTemplateData.statsAtLevel(template, levelValue());
        materialModifiers = FamiliarMaterialData.combined(dataMaterials);
        applyBaseAttribute(Attributes.ATTACK_DAMAGE,
                stats.attack() * (1.0D + materialModifiers.attackMultiplier()));
        applyBaseAttribute(Attributes.ARMOR,
                Math.max(0.0D, stats.armor() * (1.0D + materialModifiers.armorMultiplier())));
        applyBaseAttribute(Attributes.MOVEMENT_SPEED,
                Math.max(0.0D, stats.moveSpeed() * (1.0D + materialModifiers.moveSpeedMultiplier())));
        applyBaseAttribute(Attributes.ATTACK_SPEED,
                Math.max(0.0D, stats.attackSpeed() * (1.0D + materialModifiers.attackSpeedMultiplier())));
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double oldMax = Math.max(1.0D, maxHealth.getValue());
            float ratio = oldMax > 0.0D ? Math.min(1.0F, getHealth() / (float) oldMax) : 1.0F;
            double effectiveMaxHealth = Math.max(1.0D,
                    stats.maxHealth() * (1.0D + materialModifiers.maxHealthMultiplier()));
            maxHealth.setBaseValue(effectiveMaxHealth);
            setHealth(Math.max(0.0F, Math.min((float) effectiveMaxHealth, (float) effectiveMaxHealth * ratio)));
        }
        sourceEnergyCapacity = Math.max(0,
                (int) Math.floor(stats.sourceEnergyCapacity()
                        * (1.0D + materialModifiers.sourceCapacityMultiplier())));
        sourceEnergy = Math.min(sourceEnergy, sourceEnergyCapacity);
        applyBaseAttribute(Attributes.KNOCKBACK_RESISTANCE,
                Math.max(0.0D, Math.min(1.0D, materialModifiers.knockbackResistance())));
    }

    private void applyBaseAttribute(net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        var instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    public void setDeathPending(boolean pending) {
        deathPending = pending;
    }

    public boolean deathPending() {
        return deathPending;
    }

    public FamiliarData toFamiliarData() {
        FamiliarData data = new FamiliarData();
        data.setOwner(ownerId);
        data.setFamiliarId(familiarId);
        data.setTemplate(template());
        data.setLevel(levelValue());
        data.setExperience(experience);
        data.setAlignment(alignment());
        data.setAffinity(affinity());
        data.setSoulScores(soulScores);
        data.setHunger(hunger());
        data.setSaturation(saturation);
        data.setMood(mood());
        data.setMode(mode);
        data.setSourceEnergy(sourceEnergy);
        data.setSourceEnergyCapacity(sourceEnergyCapacity);
        data.setHealth(getHealth());
        data.setMaterials(dataMaterials);
        data.setMaterialBiomassCost(dataBiomassCost);
        data.setEquipment(equipment);
        data.setAccessory(accessory);
        data.setActive(true);
        data.setActiveEntity(getUUID());
        data.setDeathPending(deathPending);
        return data;
    }

    public void applyFamiliarData(FamiliarData data) {
        if (data == null) return;
        bindOwner(data.owner());
        familiarId = data.familiarId();
        setTemplate(data.template());
        setLevelValue(data.level());
        setExperience(data.experience());
        setAlignment(data.alignment());
        setAffinity(data.affinity());
        soulScores = data.soulScores();
        dataMaterials = new ArrayList<>(data.materials());
        dataBiomassCost = data.materialBiomassCost();
        equipment = data.equipment();
        accessory = data.accessory();
        setHunger(data.hunger());
        saturation = data.saturation();
        setMood(data.mood());
        setMode(data.mode());
        setSourceEnergy(data.sourceEnergy(), data.sourceEnergyCapacity());
        float savedHealth = data.health();
        int persistedCapacity = data.sourceEnergyCapacity();
        applyTemplateStats();
        if (persistedCapacity > 0) {
            sourceEnergyCapacity = persistedCapacity;
            sourceEnergy = Math.min(sourceEnergy, sourceEnergyCapacity);
        }
        if (savedHealth > 0.0F) {
            setHealth(Math.min(savedHealth, (float) getMaxHealth()));
        }
        deathPending = data.deathPending();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        FamiliarData data = toFamiliarData();
        tag.put("FamiliarData", data.save());
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putString("Mode", mode.id());
        tag.putLong("Experience", experience);
        tag.putInt("SourceEnergy", sourceEnergy);
        tag.putInt("SourceEnergyCapacity", sourceEnergyCapacity);
        tag.putFloat("Saturation", saturation);
        tag.putFloat("SavedHealth", getHealth());
        tag.put("SoulScores", soulScores.copy());
        tag.putBoolean("DeathPending", deathPending);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Vanilla Health is the live entity snapshot, including in saves written before
        // FamiliarData stopped retaining the health from the last summon.
        float loadedHealth = getHealth();
        if (tag.contains("FamiliarData", CompoundTag.TAG_COMPOUND)) {
            applyFamiliarData(FamiliarData.load(tag.getCompound("FamiliarData")));
        }
        if (tag.contains("Health", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            setHealth(Math.min(loadedHealth, getMaxHealth()));
        } else if (!tag.contains("FamiliarData", CompoundTag.TAG_COMPOUND)
                && tag.contains("SavedHealth", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            setHealth(Math.max(0.0F, Math.min(tag.getFloat("SavedHealth"), getMaxHealth())));
        }
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        mode = FamiliarMode.fromId(tag.getString("Mode"));
        experience = Math.max(0L, tag.getLong("Experience"));
        sourceEnergy = Math.max(0, tag.getInt("SourceEnergy"));
        sourceEnergyCapacity = Math.max(0, tag.getInt("SourceEnergyCapacity"));
        saturation = Math.max(0.0F, tag.getFloat("Saturation"));
        soulScores = tag.contains("SoulScores", CompoundTag.TAG_COMPOUND)
                ? tag.getCompound("SoulScores").copy() : new CompoundTag();
        deathPending = tag.getBoolean("DeathPending");
    }
}
