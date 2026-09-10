package accident.util.render;

import accident.modules.impl.render.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

public class CosmeticHelmetFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public CosmeticHelmetFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {

        MinecraftClient client = MinecraftClient.getInstance();
        Cosmetics cosmetics = Cosmetics.getInstance();
        if (client.player == null || cosmetics == null || !cosmetics.isEnabled() || !isLocalPlayer(state, client)) {
            return;
        }

        if (cosmetics.pieces.isSelected("Hat")) renderHead(matrices, queue, light, cosmetics, "cosmetic_hat");
        if (cosmetics.pieces.isSelected("Glasses")) renderHead(matrices, queue, light, cosmetics, "cosmetic_glasses");
        if (cosmetics.pieces.isSelected("Shield")) renderOffHand(matrices, queue, light, client, cosmetics, "cosmetic_shield");
    }

    private void renderHead(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                            Cosmetics cosmetics, String modelId) {
        matrices.push();
        getContextModel().getHead().applyTransform(matrices);
        // Blockbench экспортирует эти модели под систему координат ItemDisplay;
        // у функции рендера игрока вертикальная ось направлена в другую сторону.
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cosmetics.headRotationX.getValue()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(cosmetics.headRotationY.getValue()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(cosmetics.headRotationZ.getValue()));
        matrices.translate(cosmetics.headPositionX.getValue(), cosmetics.headPositionY.getValue(), cosmetics.headPositionZ.getValue());
        float scale = cosmetics.headScale.getValue();
        matrices.scale(scale, scale, scale);
        renderItem(matrices, queue, light, modelId, ItemDisplayContext.HEAD);
        matrices.pop();
    }

    private void renderOffHand(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                               MinecraftClient client, Cosmetics cosmetics, String modelId) {
        matrices.push();
        if (client.player.getMainArm() == Arm.RIGHT) getContextModel().leftArm.applyTransform(matrices);
        else getContextModel().rightArm.applyTransform(matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cosmetics.shieldRotationX.getValue()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(cosmetics.shieldRotationY.getValue()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(cosmetics.shieldRotationZ.getValue()));
        matrices.translate(cosmetics.shieldPositionX.getValue(), cosmetics.shieldPositionY.getValue(), cosmetics.shieldPositionZ.getValue());
        float scale = cosmetics.shieldScale.getValue();
        matrices.scale(scale, scale, scale);
        renderItem(matrices, queue, light, modelId, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        matrices.pop();
    }

    private void renderItem(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, String modelId, ItemDisplayContext context) {
        ItemStack carrier = new ItemStack(Items.PAPER);
        carrier.set(DataComponentTypes.ITEM_MODEL, Identifier.of("accident", modelId));
        ItemRenderState renderState = new ItemRenderState();
        MinecraftClient client = MinecraftClient.getInstance();
        client.getItemModelManager().update(renderState, carrier, context, client.world, null, 0);
        if (!renderState.isEmpty()) renderState.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, 0);
    }

    /** Идентификатор RenderState различается между маппингами и путями рендера; проверяем оба стабильных признака. */
    private boolean isLocalPlayer(PlayerEntityRenderState state, MinecraftClient client) {
        if (state.id == client.player.getId()) return true;
        return state.playerName != null && client.player.getName() != null
                && state.playerName.getString().equals(client.player.getName().getString());
    }
}
