package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.api.magic.SpellSnapshot;
import com.example.akaishi.forbidden.api.magic.exec.IPersistenceHandler;
import com.example.akaishi.forbidden.api.magic.exec.MagicContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 一次进行中的施法（设计记忆 §21.1 / §21.6）。<b>仅存在于运行期，不落盘。</b>
 *
 * <p><b>为什么不落盘</b>：会话的全部内容都是"这一瞬间的时序进度"（已扣几段、下一段何时到）。
 * 重登后世界时间基准与实体引用都已失效，恢复它只会得到一个指向不存在实体的半截施法。
 * §21.1 的裁定也正是如此 —— 退游戏属<b>被动打断</b>，返还 50% 后结束。
 *
 * <p>可变对象：状态机逐 tick 改写 {@code state} / {@code segmentsPaid} 等字段。
 * 只在服务端主线程被读写，故不加锁（与 {@code MagicState} 同口径）。
 */
public final class MagicCastSession {

    private MagicCastState state;

    private final MagicContext context;
    private final ResourceKey<Level> dimension;

    /** 前摇结束时刻（{@code getGameTime()}） */
    private final long chargeEndTick;

    /** 计价段数（§21.2：单次 = 1 / 引导 = 秒数 / 连发 = 发数） */
    private final int segments;

    /** 每段单价 = 整段总价 ÷ 段数（§21.6 的预扣与逐段结算都按它算） */
    private final float segmentCost;

    /** 逐段结算的节拍（X3 = 每秒 20 刻；X7 = 每发 2 刻；单次型无意义） */
    private final int segmentIntervalTicks;

    /** 下一段的结算时刻（仅持续型有效） */
    private long nextSegmentTick;

    /** 已结算段数（含进入前摇时预扣的首段） */
    private int segmentsPaid;

    /** 首段是否已经真正"生效"（投送受理）—— 打断返还的判据：未生效才退 */
    private boolean firstFired;

    /** 结构段注册的持续处理器（§19.2 阶段④；无注册时为空表） */
    private final List<IPersistenceHandler> persistence;

    /** 持续处理器的已推进刻数（从首段生效起算） */
    private int persistenceTicks;

    /** 上次记录的施法者血量（用于"受击打断"的落差判定，§21.1） */
    private float lastHealth;
    private PaymentReceipt firstPayment;
    private final java.util.ArrayList<PaymentReceipt> successfulPayments = new java.util.ArrayList<>();

    public PaymentReceipt firstPayment() { return firstPayment; }
    public void setFirstPayment(PaymentReceipt payment) { this.firstPayment = payment; }
    public List<PaymentReceipt> successfulPayments() { return List.copyOf(successfulPayments); }
    public void recordSuccessfulPayment(PaymentReceipt payment) {
        if (payment != null) successfulPayments.add(payment);
    }

    public MagicCastSession(MagicContext context, ResourceKey<Level> dimension, long chargeEndTick,
            int segments, float segmentCost, int segmentIntervalTicks,
            List<IPersistenceHandler> persistence, float lastHealth) {
        this.state = MagicCastState.CHARGING;
        this.context = context;
        this.dimension = dimension;
        this.chargeEndTick = chargeEndTick;
        this.segments = Math.max(1, segments);
        this.segmentCost = Math.max(0f, segmentCost);
        this.segmentIntervalTicks = Math.max(1, segmentIntervalTicks);
        this.nextSegmentTick = chargeEndTick;
        this.segmentsPaid = 1; // 首段在进入前摇时已预扣（§21.6）
        this.persistence = persistence == null ? List.of() : List.copyOf(persistence);
        this.lastHealth = lastHealth;
    }

    public MagicCastState state() {
        return state;
    }

    public void setState(MagicCastState value) {
        this.state = value;
    }

    public MagicContext context() {
        return context;
    }

    public SpellSnapshot snapshot() {
        return context.snapshot();
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public long chargeEndTick() {
        return chargeEndTick;
    }

    public int segments() {
        return segments;
    }

    public float segmentCost() {
        return segmentCost;
    }

    public int segmentIntervalTicks() {
        return segmentIntervalTicks;
    }

    public long nextSegmentTick() {
        return nextSegmentTick;
    }

    public void setNextSegmentTick(long value) {
        this.nextSegmentTick = value;
    }

    public int segmentsPaid() {
        return segmentsPaid;
    }

    public void markSegmentPaid() {
        this.segmentsPaid++;
    }

    /** 是否还有未结算的段（持续型的继续条件） */
    public boolean hasMoreSegments() {
        return segmentsPaid < segments;
    }

    public boolean firstFired() {
        return firstFired;
    }

    public void markFirstFired() {
        this.firstFired = true;
    }

    public List<IPersistenceHandler> persistence() {
        return persistence;
    }

    public int persistenceTicks() {
        return persistenceTicks;
    }

    public void advancePersistence() {
        this.persistenceTicks++;
    }

    public float lastHealth() {
        return lastHealth;
    }

    public void setLastHealth(float value) {
        this.lastHealth = value;
    }

    /** 是否属于"持续型"（X3 / X7）—— 决定前摇结束后是回到空闲还是转持续态 */
    public boolean continuous() {
        return segments > 1;
    }
}
