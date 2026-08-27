package accident.mixin;

import accident.Initialization;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import accident.IMinecraft;
import accident.util.camera.AngleConnection;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("unchecked")
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> implements IMinecraft {

    @Shadow
    @Nullable
    protected abstract RenderLayer getRenderLayer(S state, boolean showBody, boolean translucent, boolean showOutline);

    @Shadow
    protected M model;


    @ModifyExpressionValue(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerpAngleDegrees(FFF)F")
    )
    private float lerpAngleDegreesHook(float original,
                                       @Local(ordinal = 0, argsOnly = true) LivingEntity entity,
                                       @Local(ordinal = 0, argsOnly = true) float delta) {
        AngleConnection controller = AngleConnection.INSTANCE;
        if (entity.equals(mc.player) && controller.getCurrentAngle() != null
                && !(mc.currentScreen instanceof HandledScreen)) {
            return MathHelper.lerpAngleDegrees(delta,
                    controller.getPreviousRotation().getYaw(),
                    controller.getRotation().getYaw());
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F")
    )
    private float getLerpedPitchHook(float original,
                                     @Local(ordinal = 0, argsOnly = true) LivingEntity entity,
                                     @Local(ordinal = 0, argsOnly = true) float delta) {
        AngleConnection controller = AngleConnection.INSTANCE;
        if (entity.equals(mc.player) && controller.getCurrentAngle() != null
                && !(mc.currentScreen instanceof HandledScreen)) {
            return MathHelper.lerp(delta,
                    controller.getPreviousRotation().getPitch(),
                    controller.getRotation().getPitch());
        }
        return original;
    }
}