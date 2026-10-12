package com.example.akaishi.forbidden.magic.cast;

import com.example.akaishi.forbidden.magic.table.MagicSource;
import java.util.ArrayList;
import java.util.List;

/** 使用生产拆算和凭据验证跨源支付、持续段和单次退款。 */
public final class MagicPaymentSelfCheck {
    private MagicPaymentSelfCheck() {}
    public static List<String> inspect() {
        List<String> out = new ArrayList<>();
        for (MagicSource source : MagicSource.values()) {
            for (float available : new float[]{0f, 147f, 400f, 900f}) {
                PaymentPlan plan = PaymentPlan.calculate(source, 391f, available);
                if (Math.abs(plan.extra() + plan.main() - 391f) > 0.001f
                        || plan.extra() > available || plan.main() < 0f) out.add("支付拆分不守恒 " + source);
                if ((source == MagicSource.SAN ? plan.main() / 100f : 0f) != plan.sanity()
                        || (source == MagicSource.LIFE ? plan.main() / 200f : 0f) != plan.health())
                    out.add("源附加支付错配 " + source);
                PaymentReceipt receipt = new PaymentReceipt(source, null, plan.extra(), plan.main(), plan.sanity(), plan.health());
                if (!receipt.claimRefund() || receipt.claimRefund()) out.add("同一凭据可重复退款");
            }
        }
        float extra = 147f, pool = 1000f, paid = 0f;
        for (int segment = 0; segment < 6; segment++) {
            PaymentPlan plan = PaymentPlan.calculate(MagicSource.LIFE, 80f, extra);
            extra -= plan.extra();
            pool -= plan.main();
            paid += plan.extra() + plan.main();
        }
        if (extra != 0f || pool != 667f || paid != 480f) out.add("持续六段扣款不守恒");
        if (PaymentPlan.refundableHealth(0f, 20f, 6f, 1f, false, false) != 0f
                || PaymentPlan.refundableHealth(4f, 20f, 6f, 1f, true, true) != 4f
                || PaymentPlan.refundableHealth(19f, 20f, 6f, 1f, true, false) != 20f)
            out.add("死亡/移除退款复活或超出生命上限");
        try {
            PaymentPlan.calculate(MagicSource.LIFE, Float.NaN, 0f);
            out.add("接受非有限支付");
        } catch (IllegalArgumentException expected) { }
        return out;
    }
    public static void main(String[] args) {
        List<String> failures = inspect();
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
        System.out.println("MagicPaymentSelfCheck: PASS");
    }
}
