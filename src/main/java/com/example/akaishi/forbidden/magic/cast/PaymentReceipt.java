package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.magic.table.MagicSource;
import net.minecraft.world.item.ItemStack;

/** 单段实付凭据；原载体账户保留施法时的来源与配置。 */
public final class PaymentReceipt {
    public interface CarrierAccount {
        ItemStack carrier();
        float available();
        void setBalance(float amount);
        float refundRatio();
    }

    private final MagicSource source;
    private final CarrierAccount account;
    private final float extraPaid;
    private final float mainPaid;
    private final float sanityPaid;
    private final float healthPaid;
    private boolean refunded;

    PaymentReceipt(MagicSource source, CarrierAccount account, float extraPaid, float mainPaid,
            float sanityPaid, float healthPaid) {
        this.source = source;
        this.account = account;
        this.extraPaid = extraPaid;
        this.mainPaid = mainPaid;
        this.sanityPaid = sanityPaid;
        this.healthPaid = healthPaid;
    }

    public MagicSource source() { return source; }
    public CarrierAccount account() { return account; }
    public ItemStack originalCarrier() { return account == null ? ItemStack.EMPTY : account.carrier(); }
    public float extraPaid() { return extraPaid; }
    public float mainPaid() { return mainPaid; }
    public float sanityPaid() { return sanityPaid; }
    public float healthPaid() { return healthPaid; }
    public float totalPaid() { return extraPaid + mainPaid; }

    synchronized boolean claimRefund() {
        if (refunded) return false;
        refunded = true;
        return true;
    }
}
