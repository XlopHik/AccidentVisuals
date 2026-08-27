package accident.mixin;

import accident.modules.impl.render.NameTags;
import accident.util.render.EntityRenderInterceptor;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.Initialization;
import accident.modules.impl.render.SmallModel;
import accident.modules.impl.render.chinahat.ChinaHatFeatureRenderer;

@Mixin(PlayerEntityRenderer.class)
public class MixinPlayerEntityRenderer {

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
        renderer.addFeature(new ChinaHatFeatureRenderer(renderer));
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderLabelIfPresent(
            PlayerEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (EntityRenderInterceptor.suppressLabels) {
            ci.cancel();
        }
    }

    @Inject(method = "scale", at = @At("HEAD"))
    private void onScale(PlayerEntityRenderState entityRenderState, MatrixStack matrixStack, CallbackInfo ci) {
        var manager = Initialization.getInstance().getManager();
        if (manager == null) return;

        SmallModel smallModel = manager.getModuleProvider().get(SmallModel.class);

        if (smallModel != null && smallModel.isState()) {
            float s = smallModel.scale.getValue();
            matrixStack.scale(s, s, s);
        }
    }
}