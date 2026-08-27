package accident.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import accident.modules.impl.render.SeeInvisible;
import accident.util.render.RevealTintHolder;

// ванильный клиент сам рисует невидимку призраком, если снять invisibleToPlayer - цвет подмены берём константой в render()
@Mixin(LivingEntityRenderer.class)
public abstract class SeeInvisibleMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void accident$revealInvisible(T entity, S state, float tickDelta, CallbackInfo ci) {
        if (state instanceof RevealTintHolder holder) {
            holder.accident$setRevealTint(RevealTintHolder.NO_TINT);
        }

        if (!state.invisible) return;

        SeeInvisible module = SeeInvisible.getInstance();
        if (module == null || !module.shouldReveal()) return;

        state.invisibleToPlayer = false;

        if (module.isSolid()) {
            state.invisible = false;
            return;
        }

        if (state instanceof RevealTintHolder holder) {
            holder.accident$setRevealTint(module.ghostTint());
        }
    }

    @ModifyConstant(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            constant = @Constant(intValue = 0x26FFFFFF))
    private int accident$revealTint(int original, S state, MatrixStack matrices,
                                    OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (state instanceof RevealTintHolder holder) {
            int tint = holder.accident$getRevealTint();
            if (tint != RevealTintHolder.NO_TINT) return tint;
        }
        return original;
    }
}
