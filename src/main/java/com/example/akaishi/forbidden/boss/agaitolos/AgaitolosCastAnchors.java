package com.example.akaishi.forbidden.boss.agaitolos;

import net.minecraft.world.phys.Vec3;

/** 最终运行动画发射帧的 LeftHandLocator 锚点，含渲染抬升 0.01 和掌上球心 0.08。 */
public final class AgaitolosCastAnchors {
    private static final Vec3 THROW = new Vec3(-0.470207756, 1.404946723, -0.735616026);
    private static final Vec3[] TRIPLE = {
            new Vec3(-0.154935700, 1.251028283, -0.486887081),
            new Vec3(0.302831003, 1.251028283, -0.411530660),
            new Vec3(0.510934895, 1.251028283, 0.003103775)
    };
    private static final Vec3 BARRAGE = new Vec3(-0.226576918, 1.613177774, -0.717281002);
    private static final Vec3 REST = new Vec3(-0.484965604, 1.251600488, -0.160845899);

    private AgaitolosCastAnchors() { }

    public static Vec3 origin(AgaitolosEntity boss) {
        Vec3 local = switch (boss.getVisualAction()) {
            case "boss_summon_throw" -> THROW;
            case "boss_triple_throw" -> {
                double age = boss.getVisualActionAge(0f);
                yield TRIPLE[age < 9 ? 0 : age < 15 ? 1 : 2];
            }
            case "boss_saturation_barrage" -> BARRAGE;
            default -> REST;
        };
        return boss.position().add(local.yRot((float) Math.toRadians(180f - boss.yBodyRot)));
    }
}
