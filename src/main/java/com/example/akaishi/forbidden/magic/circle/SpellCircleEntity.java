package com.example.akaishi.forbidden.magic.circle;

import com.example.akaishi.forbidden.api.magic.exec.MagicDeliveryAction;
import com.example.akaishi.forbidden.magic.particle.AkaishiMagicParticles;
import com.example.akaishi.forbidden.magic.table.MagicIds;
import com.example.akaishi.forbidden.magic.table.MagicSource;
import com.example.akaishi.forbidden.magic.cast.MagicCastState;
import com.example.akaishi.forbidden.magic.visual.SpellVisual;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.example.akaishi.forbidden.magic.visual.SpellCircleLayers;
import com.example.akaishi.forbidden.magic.cast.MagicCastManager;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 法阵实体（设计记忆 §36.2：「<b>法阵的生命周期 = X 的时序</b>」）。
 *
 * <p>本类承载 <b>X5 布场</b>：「留在落点，随场地效果存在」—— 它站在落点上，
 * <b>每秒把本段效果在原地重放一次</b>，直到生命耗尽。
 *
 * <p><b>为什么是实体而不是一张服务端表</b>：法阵要在世界里"看得见、有位置、
 * 随区块加载与卸载"，而且 §25.3 的<b>自主法阵</b>（施法后留存自跑、上限 2）
 * 需要它脱离施法者独立存在。这些性质只有世界实体具备。
 *
 * <h3>它替执行器做了什么</h3>
 * 它<b>不</b>知道这次法术是什么效果 —— 只持有一个 {@link MagicDeliveryAction}（闭包），
 * 每秒调一次 {@code fire(自己的位置)}。于是"区域 + 持续"由它提供、
 * "打谁、打多狠"仍由管线与执行器决定（§19.2 的分工没有被它破坏）。
 *
 * <h3>视觉</h3>
 * 外观由 {@link SpellVisual} 描述、由客户端的 {@code SpellVisualPainter} 组装绘制 ——
 * 本类只负责把那份描述<b>同步给客户端</b>（see {@link #visual()}）。
 * 另有一层<b>半径圈</b>由渲染器按 {@link #effectRadius()} 画，
 * 让玩家看得见"这一块到底覆盖到哪"（§36.1 的区域上限对它才有意义）。
 */
public class SpellCircleEntity extends Entity {

    /** 重放间隔：每秒一次 */
    public static final int PULSE_INTERVAL_TICKS = 20;

    /** 生命上限（30 秒）——防御性上限，防坏公式产出"几十分钟的场地" */
    public static final int MAX_LIFE_TICKS = 600;

    /** 作用半径（同步给客户端：渲染器据此画覆盖圈） */
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.FLOAT);

    /** 剩余刻数（同步出去只是为了让客户端也知道"它快没了"） */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.INT);

    // —— 视觉描述：拆成若干基本类型同步（SynchedEntityData 没有复合类型）——

    /** 核心 E 的 id → 客户端据此查形态规格 */
    private static final EntityDataAccessor<String> DATA_EFFECT =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);
    /** 主墨识别色（法术的身份色） */
    private static final EntityDataAccessor<Integer> DATA_INK_RGB =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.INT);
    /** 主墨 id（粒子按墨选形态用；未知时回退源形态） */
    private static final EntityDataAccessor<String> DATA_INK_ID =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);
    /** 源基调色（能量的体质色） */
    private static final EntityDataAccessor<Integer> DATA_SOURCE_RGB =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SOURCE_ID =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_DELIVERY_ID =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_WORD_IDS =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);
    /** 威力轴 → 体积与亮度 */
    private static final EntityDataAccessor<Float> DATA_POWER =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.FLOAT);
    /** 本句词的符号（逐词不同 —— 每一句法术独一份的来源） */
    private static final EntityDataAccessor<String> DATA_GLYPHS =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);

    /** 每次重放要执行的闭包；<b>仅服务端持有</b>（不落盘、不同步） */
    private MagicDeliveryAction action;

    private int pulseTicks = 1;
    private static final EntityDataAccessor<Long> DATA_EXPIRES =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VISUAL_STATE =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SLOT_WORDS =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SLOT_INKS =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> DATA_STARTED =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_CHARGE_END =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_FADE_END =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.LONG);

    private static final EntityDataAccessor<Long> DATA_PHASE_STARTED =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_PHASE_OFFSET =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> DATA_DURATION =
            SynchedEntityData.defineId(SpellCircleEntity.class, EntityDataSerializers.FLOAT);

    private SpellVisual cachedVisual;
    private SpellCircleLayers cachedLayers;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYaw;
    private float lerpPitch;

    private static final int MODE_PLACED = 0;
    private static final int MODE_FOLLOWING = 1;
    private static final int FADE_TICKS = 10;
    private static final String SLOT_SEPARATOR = String.valueOf((char) 1);

    public enum VisualState {
        PLACED, CHARGING, CHANNELING, VOLLEYING, FADING
    }

    public SpellCircleEntity(EntityType<? extends SpellCircleEntity> type, Level level) {
        super(type, level);
        // 法阵是一块"贴地的印记"：不受重力、不参与碰撞
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * 布置法阵（服务端调用一次）。
     *
     * @param visual        视觉描述（同时决定作用半径的显示与外观）
     * @param action        每次重放要执行的闭包
     * @param durationTicks 存活刻数（夹到 [1秒, {@link #MAX_LIFE_TICKS}]）
     */
    public void arm(SpellVisual visual, MagicDeliveryAction action, int durationTicks) {
        this.action = action;
        long now = this.level().getGameTime();
        int life = Math.max(PULSE_INTERVAL_TICKS, Math.min(MAX_LIFE_TICKS, durationTicks));
        this.entityData.set(DATA_MODE, MODE_PLACED);
        this.entityData.set(DATA_VISUAL_STATE, VisualState.PLACED.ordinal());
        this.entityData.set(DATA_OWNER, "");
        this.entityData.set(DATA_STARTED, now);
        this.entityData.set(DATA_PHASE_STARTED, now);
        this.entityData.set(DATA_PHASE_OFFSET, 0f);
        this.entityData.set(DATA_CHARGE_END, now);
        this.entityData.set(DATA_FADE_END, 0L);
        this.entityData.set(DATA_LIFE, life);
        this.entityData.set(DATA_EXPIRES, now + life);
        setVisual(visual);
    }

    public void armFollowing(SpellVisual visual, ServerPlayer owner, long chargeEndTick) {
        if (owner == null || owner.level() != this.level()) return;
        this.action = null;
        long now = this.level().getGameTime();
        this.entityData.set(DATA_MODE, MODE_FOLLOWING);
        this.entityData.set(DATA_VISUAL_STATE, VisualState.CHARGING.ordinal());
        this.entityData.set(DATA_OWNER, owner.getUUID().toString());
        this.entityData.set(DATA_STARTED, now);
        this.entityData.set(DATA_PHASE_STARTED, now);
        this.entityData.set(DATA_PHASE_OFFSET, 0f);
        this.entityData.set(DATA_CHARGE_END, Math.max(now + 1L, chargeEndTick));
        this.entityData.set(DATA_FADE_END, 0L);
        this.entityData.set(DATA_EXPIRES, Long.MAX_VALUE);
        setVisual(visual);
        follow(owner);
    }

    private void setVisual(SpellVisual visual) {
        if (visual == null) return;
        this.entityData.set(DATA_RADIUS, Math.max(0.5f, visual.radius()));
        this.entityData.set(DATA_EFFECT, visual.effectId());
        this.entityData.set(DATA_DELIVERY_ID, visual.deliveryId());
        this.entityData.set(DATA_INK_RGB, visual.inkRgb());
        this.entityData.set(DATA_INK_ID, visual.inkId());
        this.entityData.set(DATA_SOURCE_RGB, visual.sourceRgb());
        this.entityData.set(DATA_SOURCE_ID, visual.sourceId());
        this.entityData.set(DATA_WORD_IDS, String.join(SLOT_SEPARATOR, visual.wordIds()));
        this.entityData.set(DATA_SLOT_WORDS, String.join(SLOT_SEPARATOR, visual.slotWords()));
        this.entityData.set(DATA_SLOT_INKS, String.join(SLOT_SEPARATOR, visual.slotInks()));
        this.entityData.set(DATA_POWER, visual.power());
        this.entityData.set(DATA_DURATION, visual.duration());
        this.entityData.set(DATA_GLYPHS, visual.glyphs());
    }

    public void setVisualState(MagicCastState state) {
        if (this.entityData.get(DATA_MODE) != MODE_FOLLOWING
                || this.visualState() == VisualState.FADING) return;
        VisualState next = switch (state) {
            case CHANNELING -> VisualState.CHANNELING;
            case VOLLEYING -> VisualState.VOLLEYING;
            case IDLE -> VisualState.FADING;
            case CHARGING -> VisualState.CHARGING;
        };
        if (next == VisualState.FADING) beginFade();
        else changeVisualState(next);
    }

    public void fadeOut() {
        if (this.entityData.get(DATA_MODE) == MODE_FOLLOWING) beginFade();
    }

    private void beginFade() {
        if (visualState() == VisualState.FADING) return;
        long now = this.level().getGameTime();
        changeVisualState(VisualState.FADING);
        this.entityData.set(DATA_FADE_END, now + FADE_TICKS);
        this.entityData.set(DATA_EXPIRES, now + FADE_TICKS);
        this.action = null;
    }

    private void follow(ServerPlayer owner) {
        float yaw = owner.getYRot();
        double radians = Math.toRadians(yaw);
        double backX = Math.sin(radians);
        double backZ = -Math.cos(radians);
        this.setPos(owner.getX() + backX * 0.72d,
                owner.getY() + owner.getBbHeight() * 0.62d,
                owner.getZ() + backZ * 0.72d);
        this.setYRot(yaw);
        this.setXRot(0f);
    }

    public boolean followsCaster() {
        return this.entityData.get(DATA_MODE) == MODE_FOLLOWING;
    }

    public VisualState visualState() {
        int state = this.entityData.get(DATA_VISUAL_STATE);
        VisualState[] values = VisualState.values();
        return state >= 0 && state < values.length ? values[state] : VisualState.PLACED;
    }

    public float yaw(float partialTick) {
        return Mth.rotLerp(partialTick, this.yRotO, this.getYRot());
    }

    private void changeVisualState(VisualState next) {
        if (visualState() == next) return;
        float phase = animationTime(0f);
        this.entityData.set(DATA_PHASE_OFFSET, phase);
        this.entityData.set(DATA_PHASE_STARTED, this.level().getGameTime());
        this.entityData.set(DATA_VISUAL_STATE, next.ordinal());
    }

    public float animationTime(float partialTick) {
        long elapsed = this.level().getGameTime() - this.entityData.get(DATA_PHASE_STARTED);
        float speed = switch (visualState()) {
            case CHANNELING -> 1.35f;
            case VOLLEYING -> 1.8f;
            case FADING -> 0.7f;
            default -> 1f;
        };
        return this.entityData.get(DATA_PHASE_OFFSET) + Math.max(0f, elapsed + partialTick) * speed / 20f;
    }

    private double growthTime(float partialTick) {
        if (visualState() == VisualState.FADING) return this.entityData.get(DATA_FADE_END) - FADE_TICKS;
        return this.level().getGameTime() + partialTick;
    }

    public float chargeProgress(float partialTick) {
        long start = this.entityData.get(DATA_STARTED);
        long end = this.entityData.get(DATA_CHARGE_END);
        if (end <= start) return 1f;
        return Mth.clamp((float) ((growthTime(partialTick) - start) / (end - start)), 0f, 1f);
    }

    private static List<String> slots(String encoded) {
        if (encoded == null || encoded.isEmpty()) return java.util.Collections.nCopies(8, "");
        List<String> values = new java.util.ArrayList<>(java.util.Arrays.asList(encoded.split(SLOT_SEPARATOR, -1)));
        while (values.size() < 8) values.add("");
        if (values.size() > 8) values.subList(8, values.size()).clear();
        return List.copyOf(values);
    }

    /** 客户端的视觉描述（服务端也能读；字段不全时用默认值兜底，绝不返回 null） */
    public SpellVisual visual() {
        if (cachedVisual != null) return cachedVisual;
        String words = this.entityData.get(DATA_WORD_IDS);
        cachedVisual = new SpellVisual(
                this.entityData.get(DATA_EFFECT), this.entityData.get(DATA_DELIVERY_ID),
                this.entityData.get(DATA_SOURCE_ID), this.entityData.get(DATA_INK_ID),
                this.entityData.get(DATA_INK_RGB),
                this.entityData.get(DATA_SOURCE_RGB), this.entityData.get(DATA_POWER),
                this.entityData.get(DATA_RADIUS), this.entityData.get(DATA_DURATION), 0,
                words.isEmpty() ? java.util.List.of() : java.util.Arrays.asList(
                        words.split(java.util.regex.Pattern.quote(SLOT_SEPARATOR), -1)),
                this.entityData.get(DATA_GLYPHS), slots(this.entityData.get(DATA_SLOT_WORDS)),
                slots(this.entityData.get(DATA_SLOT_INKS)));
        return cachedVisual;
    }

    public SpellCircleLayers layers() {
        if (cachedLayers == null) cachedLayers = visual().circleLayers();
        return cachedLayers;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_RADIUS) || key.equals(DATA_EFFECT) || key.equals(DATA_DELIVERY_ID)
                || key.equals(DATA_INK_RGB) || key.equals(DATA_INK_ID) || key.equals(DATA_SOURCE_RGB)
                || key.equals(DATA_SOURCE_ID) || key.equals(DATA_WORD_IDS) || key.equals(DATA_SLOT_WORDS)
                || key.equals(DATA_SLOT_INKS) || key.equals(DATA_POWER) || key.equals(DATA_GLYPHS)
                || key.equals(DATA_DURATION)) {
            cachedVisual = null;
            cachedLayers = null;
        }
    }

    /** 作用半径（渲染器据此画覆盖圈） */
    public float effectRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    /**
     * 消散淡出系数（1 = 还早，0 = 即将消失）。
     *
     * <p><b>只按"最后 1 秒"淡出</b>，而不是按 {@link #MAX_LIFE_TICKS} 归一化 ——
     * 后者会让一个 5 秒的法阵亮度只有 0.008（几乎看不见）。
     * 它要表达的是"快没了"这个<b>临近</b>事件，不是"还剩百分之几"。
     */
    public float fadeAlpha() {
        return fadeAlpha(0f);
    }

    public float fadeAlpha(float partialTick) {
        long now = this.level().getGameTime();
        if (visualState() == VisualState.FADING) {
            long end = this.entityData.get(DATA_FADE_END);
            return Mth.clamp((end - now - partialTick) / FADE_TICKS, 0f, 1f);
        }
        long expires = this.entityData.get(DATA_EXPIRES);
        if (expires <= 0L || expires == Long.MAX_VALUE) return 1f;
        return Mth.clamp((expires - now - partialTick) / PULSE_INTERVAL_TICKS, 0f, 1f);
    }

    public float introProgress(float partialTick) {
        return Mth.clamp((float) ((growthTime(partialTick) - this.entityData.get(DATA_STARTED)) / 12d), 0f, 1f);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        if (!this.level().isClientSide() || teleport || !followsCaster()) {
            this.lerpSteps = 0;
            super.lerpTo(x, y, z, yaw, pitch, steps, teleport);
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
        this.lerpSteps = Math.max(1, steps);
    }

    private void tickLerp() {
        if (lerpSteps <= 0) return;
        double weight = 1d / lerpSteps;
        this.setPos(Mth.lerp(weight, this.getX(), lerpX), Mth.lerp(weight, this.getY(), lerpY),
                Mth.lerp(weight, this.getZ(), lerpZ));
        this.setYRot(this.getYRot() + Mth.wrapDegrees(lerpYaw - this.getYRot()) / lerpSteps);
        this.setXRot(this.getXRot() + (lerpPitch - this.getXRot()) / lerpSteps);
        lerpSteps--;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            tickLerp();
            // 法阵脉动：每 5 tick 沿半径撒一圈（客户端本地），让作用范围一眼可见 ——
            // 否则"区域上限"对玩家就是不可见的天书（§42.5 裁定 5）
            if (this.tickCount % 5 == 0 && !followsCaster()) {
                AkaishiMagicParticles.ring(this.level(),
                        MagicSource.byIdOrDefault(this.entityData.get(DATA_SOURCE_ID)),
                        this.entityData.get(DATA_INK_ID),
                        com.example.akaishi.forbidden.magic.visual.SpellCarrierForm.FIELD,
                        this.entityData.get(DATA_INK_RGB),
                        this.getX(), this.getY() + 0.1d, this.getZ(),
                        this.entityData.get(DATA_RADIUS), 6);
            }
            return;
        }
        // 闭包不落盘 ⇒ 重载后 action 必为 null：那种法阵"再也打不出东西"，
        // 留着只会是一个占着位置、看得见粒子却毫无作用的幽灵。直接消散。
        long now = this.level().getGameTime();
        if (this.entityData.get(DATA_MODE) == MODE_FOLLOWING) {
            if (visualState() == VisualState.FADING) {
                if (now >= this.entityData.get(DATA_FADE_END)) this.discard();
                return;
            }
            UUID ownerId;
            try {
                ownerId = UUID.fromString(this.entityData.get(DATA_OWNER));
            } catch (IllegalArgumentException exception) {
                beginFade();
                return;
            }
            if (!(this.level() instanceof ServerLevel serverLevel)) return;
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null || owner.level() != this.level() || owner.isDeadOrDying()
                    || !MagicCastManager.ownsCircle(owner, this)) {
                beginFade();
                return;
            }
            follow(owner);
            return;
        }
        if (this.action == null || now >= this.entityData.get(DATA_EXPIRES)) {
            this.discard();
            return;
        }

        // 首跳下一刻触发，避免最短20刻法阵到期前零次生效。
        if (--this.pulseTicks <= 0) {
            this.pulseTicks = PULSE_INTERVAL_TICKS;
            this.action.fire(this.position());
        }
    }

    // ==================== 存档（只存剩余寿命与视觉；闭包无法序列化，重载后法阵自然失效） ====================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_LIFE, tag.getInt("Life"));
        this.entityData.set(DATA_EXPIRES, tag.getLong("Expires"));
        this.entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        this.entityData.set(DATA_EFFECT, tag.getString("Effect"));
        this.entityData.set(DATA_DELIVERY_ID, tag.getString("Delivery"));
        this.entityData.set(DATA_INK_RGB, tag.getInt("InkRgb"));
        this.entityData.set(DATA_INK_ID, tag.getString("InkId"));
        this.entityData.set(DATA_SOURCE_RGB, tag.getInt("SourceRgb"));
        this.entityData.set(DATA_SOURCE_ID, tag.getString("SourceId"));
        this.entityData.set(DATA_WORD_IDS, tag.getString("WordIds"));
        this.entityData.set(DATA_SLOT_WORDS, tag.getString("SlotWords"));
        this.entityData.set(DATA_SLOT_INKS, tag.getString("SlotInks"));
        this.entityData.set(DATA_POWER, tag.getFloat("Power"));
        this.entityData.set(DATA_DURATION, tag.getFloat("Duration"));
        this.entityData.set(DATA_GLYPHS, tag.getString("Glyphs"));
        this.entityData.set(DATA_MODE, tag.getInt("Mode"));
        this.entityData.set(DATA_VISUAL_STATE, tag.getInt("VisualState"));
        this.entityData.set(DATA_OWNER, tag.getString("Owner"));
        this.entityData.set(DATA_STARTED, tag.getLong("Started"));
        this.entityData.set(DATA_CHARGE_END, tag.getLong("ChargeEnd"));
        this.entityData.set(DATA_FADE_END, tag.getLong("FadeEnd"));
        this.entityData.set(DATA_PHASE_STARTED, tag.contains("PhaseStarted")
                ? tag.getLong("PhaseStarted") : this.entityData.get(DATA_STARTED));
        this.entityData.set(DATA_PHASE_OFFSET, tag.getFloat("PhaseOffset"));
        this.action = null;
        if (followsCaster()) {
            this.entityData.set(DATA_VISUAL_STATE, VisualState.FADING.ordinal());
            this.entityData.set(DATA_FADE_END, this.level().getGameTime());
            this.entityData.set(DATA_EXPIRES, this.level().getGameTime());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", this.entityData.get(DATA_LIFE));
        tag.putLong("Expires", this.entityData.get(DATA_EXPIRES));
        tag.putFloat("Radius", this.entityData.get(DATA_RADIUS));
        tag.putString("Effect", this.entityData.get(DATA_EFFECT));
        tag.putString("Delivery", this.entityData.get(DATA_DELIVERY_ID));
        tag.putInt("InkRgb", this.entityData.get(DATA_INK_RGB));
        tag.putString("InkId", this.entityData.get(DATA_INK_ID));
        tag.putInt("SourceRgb", this.entityData.get(DATA_SOURCE_RGB));
        tag.putString("SourceId", this.entityData.get(DATA_SOURCE_ID));
        tag.putString("WordIds", this.entityData.get(DATA_WORD_IDS));
        tag.putString("SlotWords", this.entityData.get(DATA_SLOT_WORDS));
        tag.putString("SlotInks", this.entityData.get(DATA_SLOT_INKS));
        tag.putFloat("Power", this.entityData.get(DATA_POWER));
        tag.putFloat("Duration", this.entityData.get(DATA_DURATION));
        tag.putString("Glyphs", this.entityData.get(DATA_GLYPHS));
        tag.putInt("Mode", this.entityData.get(DATA_MODE));
        tag.putInt("VisualState", this.entityData.get(DATA_VISUAL_STATE));
        tag.putString("Owner", this.entityData.get(DATA_OWNER));
        tag.putLong("Started", this.entityData.get(DATA_STARTED));
        tag.putLong("ChargeEnd", this.entityData.get(DATA_CHARGE_END));
        tag.putLong("FadeEnd", this.entityData.get(DATA_FADE_END));
        tag.putLong("PhaseStarted", this.entityData.get(DATA_PHASE_STARTED));
        tag.putFloat("PhaseOffset", this.entityData.get(DATA_PHASE_OFFSET));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 2f);
        this.entityData.define(DATA_LIFE, 0);
        this.entityData.define(DATA_EXPIRES, 0L);
        this.entityData.define(DATA_EFFECT, "");
        this.entityData.define(DATA_DELIVERY_ID, MagicIds.Delivery.PLACE);
        this.entityData.define(DATA_INK_RGB, 0xFFFFFF);
        this.entityData.define(DATA_INK_ID, "");
        this.entityData.define(DATA_SOURCE_RGB, 0xFFFFFF);
        this.entityData.define(DATA_SOURCE_ID, "san");
        this.entityData.define(DATA_WORD_IDS, "");
        this.entityData.define(DATA_SLOT_WORDS, "");
        this.entityData.define(DATA_SLOT_INKS, "");
        this.entityData.define(DATA_POWER, 0f);
        this.entityData.define(DATA_GLYPHS, "");
        this.entityData.define(DATA_MODE, MODE_PLACED);
        this.entityData.define(DATA_VISUAL_STATE, VisualState.PLACED.ordinal());
        this.entityData.define(DATA_OWNER, "");
        this.entityData.define(DATA_STARTED, 0L);
        this.entityData.define(DATA_CHARGE_END, 0L);
        this.entityData.define(DATA_FADE_END, 0L);
        this.entityData.define(DATA_PHASE_STARTED, 0L);
        this.entityData.define(DATA_PHASE_OFFSET, 0f);
        this.entityData.define(DATA_DURATION, 0f);
    }

    /** 不可被选取 / 攻击：它是地面上的一个印记，不是靶子 */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** 不被推动：水流 / 活塞 / 挤压都不该把一个"印记"推走 */
    @Override
    public boolean isPushable() {
        return false;
    }
}
