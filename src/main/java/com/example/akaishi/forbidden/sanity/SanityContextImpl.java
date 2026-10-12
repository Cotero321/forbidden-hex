package com.example.akaishi.forbidden.sanity;

import com.example.akaishi.forbidden.api.sanity.SanityContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;

import java.util.Set;

/**
 * {@link com.example.akaishi.forbidden.api.sanity.SanityContext} 的服务端实现：一次结算所依据的环境事实。
 *
 * <p><b>成本控制（本类存在的理由）</b>：规则数量与"每玩家每 tick 查询数"是乘法关系，
 * 故本实现做三件事：
 * <ol>
 *   <li><b>便宜量一次性算</b>：采样点、Y 值、方块光、综合亮度、见天、昼夜、维度，
 *       构造时各算一次（都是原版既有读数的常数级访问，无世界状态改动）；</li>
 *   <li><b>贵量惰性 + 备忘</b>：群系查询、结构查询、幽匿方块扫描只在规则真正问到的那一次执行，
 *       之后本上下文内复用同一结果——N 条规则问同一个问题也只查一次；</li>
 *   <li><b>上下文本身受上层节流</b>（见 {@link SanityEnvironmentSettlement}）：
 *       全局 1s 结算一次，并把上下文按 TTL + 区块段缓存，故最坏情况下
 *       结构查询是"每玩家每 2 秒 1 次"，不是每 tick 1 次。</li>
 * </ol>
 *
 * <p><b>线程语义</b>：只在服务端主线程构造与读取（上层结算保证），故内部备忘字段无需同步；
 * 上下文<b>不得</b>被规则缓存到下一 tick（对象本身仍可用，只是环境事实会过期）。
 */
final class SanityContextImpl implements SanityContext {

    /** 幽匿判定的亮度阈值：亮于此值（有火把/光照）就不算"黑暗幽匿环境"（待调手感值） */
    private static final int SCULK_LIGHT_THRESHOLD = 4;
    /** 幽匿方块搜索半径（格）与步长：半径 4、步长 2 ⇒ 5³ = 125 次方块读取（待调手感值） */
    private static final int SCULK_SCAN_RADIUS = 4;
    private static final int SCULK_SCAN_STEP = 2;

    /** 幽匿族方块：原版没有"sculk 系"方块标签（只有 sculk_replaceable 那类替换用标签），故此处显式列举 */
    private static final Set<Block> SCULK_BLOCKS = Set.of(
            Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST,
            Blocks.SCULK_SHRIEKER, Blocks.SCULK_SENSOR, Blocks.CALIBRATED_SCULK_SENSOR);

    private final ServerLevel level;
    private final Player player;
    private final BlockPos pos;
    private final boolean underY0;
    private final int blockLight;
    private final int effectiveLight;
    private final boolean canSeeSky;
    private final boolean day;
    private final boolean nether;
    private final boolean overworld;

    // 惰性备忘（服务端主线程访问，无需同步）
    private boolean oceanComputed;
    private boolean ocean;
    private boolean ancientCityComputed;
    private boolean ancientCity;
    private boolean deepDarkComputed;
    private boolean deepDark;

    SanityContextImpl(ServerLevel level, Player player) {
        this.level = level;
        this.player = player;
        // 采样点固定为玩家脚部方块（附属按"这一点"理解全部环境 getter）
        this.pos = player.blockPosition();
        this.blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        this.effectiveLight = level.getMaxLocalRawBrightness(pos);
        this.canSeeSky = level.canSeeSky(pos);
        this.underY0 = pos.getY() < 0;
        this.nether = level.dimension() == Level.NETHER;
        this.overworld = level.dimension() == Level.OVERWORLD;
        // 昼夜只在有昼夜循环的维度有意义，非主世界恒 false（避免下界/末地被算成"昼间"）
        this.day = overworld && level.isDay();
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean isUnderY0() {
        return underY0;
    }

    @Override
    public int blockLight() {
        return blockLight;
    }

    @Override
    public int effectiveLight() {
        return effectiveLight;
    }

    @Override
    public boolean canSeeSky() {
        return canSeeSky;
    }

    @Override
    public boolean isDay() {
        return day;
    }

    @Override
    public boolean isOcean() {
        if (!oceanComputed) {
            // 群系查询：生物群系源的采样 + 标签判定，属"每段一次"级开销，故备忘后复用
            ocean = level.getBiome(pos).is(BiomeTags.IS_OCEAN);
            oceanComputed = true;
        }
        return ocean;
    }

    /**
     * 远古城市判定：结构 piece 命中即算在城内。
     *
     * <p><b>代价与缓存</b>：走 {@code StructureManager#getStructureWithPieceAt(BlockPos, ResourceKey)}——
     * 只读<b>本区块已加载</b>的结构引用表并按 section 做哈希查表，<b>不做</b>
     * {@code findNearestMapStructure} 那种跨区块搜索（那才是真正的贵操作）；
     * 单次约几十微秒级（少量装箱与遍历）。配合上层"上下文 TTL 缓存"（2s），
     * 最坏频率 = 每玩家每 2 秒 1 次。
     *
     * <p>用 piece 而不是整个结构包围盒：包围盒会覆盖城市外围的天然岩层，
     * 让"在城旁边挖矿"也被算进城市，体感不符。
     */
    @Override
    public boolean inAncientCity() {
        if (!ancientCityComputed) {
            ancientCity = level.structureManager()
                    .getStructureWithPieceAt(pos, BuiltinStructures.ANCIENT_CITY)
                    .isValid();
            ancientCityComputed = true;
        }
        return ancientCity;
    }

    /**
     * 幽匿环境（深层黑暗）判定：群系为 {@code deep_dark}，或满足"Y &lt; 0 + 亮度低于阈值 + 附近有幽匿族方块"。
     *
     * <p><b>为什么不能只认群系</b>：玩家可以在普通洞穴里自己铺一圈幽匿造出等价环境，
     * 只认 {@code deep_dark} 会漏判，而感官体验并无差别。
     *
     * <p><b>为什么"便宜判据优先"</b>：三个条件按代价升序短路——
     * 群系 tag（1 次采样）→ Y 与亮度（构造时已算好的字段，零成本）→
     * 幽匿方块扫描（最贵，125 次方块读取）。因此绝大多数洞穴在第二个条件就被否掉，
     * 只有真正"深层 + 黑暗"的点才会触发扫描，且扫描结果在本上下文内复用。
     */
    @Override
    public boolean inDeepDark() {
        if (!deepDarkComputed) {
            deepDark = level.getBiome(pos).is(Biomes.DEEP_DARK) || isSculkDarkHere();
            deepDarkComputed = true;
        }
        return deepDark;
    }

    @Override
    public boolean isNether() {
        return nether;
    }

    @Override
    public boolean isOverworld() {
        return overworld;
    }

    /** "深层 + 黑暗 + 附近有幽匿"三重判定（最贵的分支，只在前面便宜条件都通过后走到） */
    private boolean isSculkDarkHere() {
        if (!underY0 || effectiveLight >= SCULK_LIGHT_THRESHOLD) {
            return false;
        }
        for (int dx = -SCULK_SCAN_RADIUS; dx <= SCULK_SCAN_RADIUS; dx += SCULK_SCAN_STEP) {
            for (int dy = -SCULK_SCAN_RADIUS; dy <= SCULK_SCAN_RADIUS; dy += SCULK_SCAN_STEP) {
                for (int dz = -SCULK_SCAN_RADIUS; dz <= SCULK_SCAN_RADIUS; dz += SCULK_SCAN_STEP) {
                    if (SCULK_BLOCKS.contains(level.getBlockState(pos.offset(dx, dy, dz)).getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
