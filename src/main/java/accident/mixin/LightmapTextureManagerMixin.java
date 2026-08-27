package accident.mixin;

import accident.modules.impl.render.Ambience;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.ColorHelper;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.Initialization;
import accident.modules.impl.render.FullBright;
import accident.modules.impl.render.NoRender;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Shadow
    private boolean dirty;


    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    private float leet$getValue(Double instance) {
        if (Initialization.getInstance().getManager().getModuleProvider().get(FullBright.class).isState()) {
            return 200F;
        }
        return instance.floatValue();
    }

    @Inject(method = "getDarkness", at = @At("HEAD"), cancellable = true)
    private void removeDarknessEffect(CallbackInfoReturnable<Float> cir) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isState() && noRender.modeSetting.isSelected("Darkness")) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void forceRedirty(float tickProgress, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isEnabled()) {
            this.dirty = true;
        }
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/ColorHelper;toRgbVector(I)Lorg/joml/Vector3f;"
            )
    )
    private Vector3f redirectSkyLightColor(int color) {
        Vector3f original = ColorHelper.toRgbVector(color);
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.isEnabled()) return original;

        float r = ambience.getWorldRed();
        float g = ambience.getWorldGreen();
        float b = ambience.getWorldBlue();
        float strength = 0.6f;

        return new Vector3f(
                original.x * (1f - strength) + r * strength,
                original.y * (1f - strength) + g * strength,
                original.z * (1f - strength) + b * strength
        );
    }
}
