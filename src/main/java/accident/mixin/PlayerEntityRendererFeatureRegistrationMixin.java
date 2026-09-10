package accident.mixin;

import accident.util.render.CosmeticHelmetFeatureRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererFeatureRegistrationMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void accident$registerCosmeticHelmetFeature(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        PlayerEntityRenderer self = (PlayerEntityRenderer) (Object) this;

        @SuppressWarnings("unchecked")
        LivingEntityRendererAccessor<?, PlayerEntityRenderState, PlayerEntityModel> accessor =
                (LivingEntityRendererAccessor<?, PlayerEntityRenderState, PlayerEntityModel>) (Object) self;

        accessor.accident$invokeAddFeature(new CosmeticHelmetFeatureRenderer(self));
    }
}
