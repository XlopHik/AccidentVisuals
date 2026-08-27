package accident.mixin;

import accident.Initialization;
import accident.modules.impl.misc.CustomModels;
import accident.util.custommodels.ICustomPlayerModelState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererCustomModelsMixin<AvatarlikeEntity extends PlayerLikeEntity & ClientPlayerLikeEntity> {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void accident$updateCustomModelState(AvatarlikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
        CustomModels customModels = Initialization.getInstance()
                .getManager()
                .getModuleProvider()
                .get(CustomModels.class);

        boolean enabled = customModels != null && customModels.isEnabled() && customModels.shouldApplyTo(player);
        ((ICustomPlayerModelState) state).accident$setCustomModel(enabled, enabled ? customModels.getModelName() : "");
    }
}