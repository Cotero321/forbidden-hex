package com.example.akaishi.forbidden.forge.magic.core;

import com.example.akaishi.AkaishiMod;
import com.example.akaishi.forbidden.wand.WandCoreTable;
import com.example.akaishi.forbidden.wand.WandHandleTable;
import com.example.akaishi.forbidden.wand.WandItem;
import com.example.akaishi.forbidden.wand.WandTipTable;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * 法杖<b>三段组装</b>渲染（客户端 · BEWLR）。
 *
 * <h3>为什么是 BEWLR 而不是普通模型</h3>
 * 外观由物品 NBT 的 {@code Tip / Handle / Core} 三段决定，组合数 = 31×20×17；
 * 逐组合烘模型会产出上万份资源。改为运行时读 NBT、各取一个已烘焙的<b>局部模型</b>依次提交，
 * 资源只需 68 份部件模型 + 3 份几何模板。
 *
 * <h3>坐标与变换为什么"什么都不做"</h3>
 * {@code forbidden_hex_wand.json} 以 {@code builtin/entity} 为父，使该物品模型成为
 * {@code BuiltInModel}（{@code isCustomRenderer() == true}）。原版 {@code ItemRenderer#render} 的顺序是：
 * <pre>
 *   ForgeHooksClient.handleCameraTransforms(pose, model, ctx, leftHand);  // ① 应用 JSON 的 display 变换
 *   pose.translate(-0.5F, -0.5F, -0.5F);                                  // ② 把 0..1 模型空间居中
 *   IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(...)  // ③ 交给本类
 * </pre>
 * 也就是说本类收到的 pose 已经是"变换 + 居中完成"的，部件模型又是同一套 0..1 坐标，
 * 因此这里<b>不能再做任何平移或旋转</b> —— 多一次 {@code translate(0.5,0,0.5)} 就会整根杖偏出半格。
 * 三段只在同一空间里先后提交，天然对齐。
 *
 * <h3>为什么不能逐段调 renderStatic</h3>
 * 完整入口会重新应用 display 变换与居中，导致三段各自被变换一次（GUI 错位、手持叠加旋转）。
 * 故这里只取 {@link ItemRenderer#renderModelLists}，按段各自的 RenderType 提交。
 */
public final class WandRenderer extends BlockEntityWithoutLevelRenderer implements IClientItemExtensions {

    public static final WandRenderer INSTANCE = new WandRenderer();

    private WandRenderer() {
        // 不使用方块实体渲染分发器与实体模型集：本渲染器只画 baked model，不涉及它们
        super(null, null);
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return this;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof WandItem)) {
            return;
        }
        // 四层由下到上：杖端 → 杖柄 → 杖核（含宝石外轮廓）→ 咒核内嵌符文层
        // 顺序有意义：后画的层覆盖前者，宝石的开放晶腔才能透出内部的咒核符文
        renderPart(pose, buffers, stack, WandItem.tipId(stack), light, overlay);
        renderPart(pose, buffers, stack, WandItem.handleId(stack), light, overlay);
        renderPart(pose, buffers, stack, WandItem.coreId(stack), light, overlay);
        SpellInsetRenderer.render(pose, buffers, WandItem.spell(stack), light);
    }

    /** 提交一段部件模型：按该段自己的 RenderType 取消费者（否则透明/裁剪段会画不出来）。 */
    private static void renderPart(PoseStack pose, MultiBufferSource buffers, ItemStack wand,
                                   String id, int light, int overlay) {
        if (id == null || id.isEmpty() || !isLegalPart(id)) {
            return;
        }
        BakedModel model = resolve(id);
        if (model == null) {
            return;
        }
        // 部件栈只作为渲染上下文（贴图与 tint 都在模型里，不读栈内容）
        ItemStack context = new ItemStack(wand.getItem());
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        for (BakedModel pass : model.getRenderPasses(context, true)) {
            for (RenderType type : pass.getRenderTypes(context, true)) {
                renderer.renderModelLists(pass, context, light, overlay, pose, buffers.getBuffer(type));
            }
        }
    }

    /**
     * 取该部件 id 的已烘焙局部模型；缺失/未注册返回 {@code null}（宁可不画，也不要画紫黑方块）。
     *
     * <p><b>前提</b>：这些模型由 {@code ModelEvent.RegisterAdditional} 显式注册进烘焙图
     * （它们不被任何物品模型引用，光有 json 文件是不会被烘焙的）。
     */
    private static BakedModel resolve(String id) {
        ResourceLocation modelId = new ResourceLocation(AkaishiMod.MOD_ID, "item/wand_parts/" + id);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelId);
        return model == Minecraft.getInstance().getModelManager().getMissingModel() ? null : model;
    }

    /** 只接受三张表内的合法 id：避免把任意 NBT 字符串拼成资源路径去查模型。 */
    private static boolean isLegalPart(String id) {
        return WandTipTable.get(id) != null || WandHandleTable.get(id) != null || WandCoreTable.get(id) != null;
    }
}
