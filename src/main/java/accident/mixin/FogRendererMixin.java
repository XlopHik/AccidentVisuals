package accident.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.modules.impl.render.Ambience;
import accident.modules.impl.render.NoFog;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(
            method = "applyFog",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onApplyFog(
            Camera camera,
            int viewDistance,
            RenderTickCounter renderTickCounter,
            float f,
            ClientWorld clientWorld,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        NoFog noFog = NoFog.getInstance();
        if (noFog != null && noFog.isEnabled()) {
            // Если NoFog включён, отменяем применение тумана
            // cir.cancel(); // Раскомментируй если нужно полностью отключить туман
        }
    }

    // содиум читает дистанции тумана из FogData, а не из аргументов вызова - поэтому патчим сам объект,
    // а не аргументы. хук стоит прямо перед вызовом, который его читает: FogData уже заполнен, но ни ваниль,
    // ни содиум ещё не прочитали из него ни поля - порядок инжектов между модами не важен
    @Inject(
            method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/MappableRingBuffer;getBlocking()Lcom/mojang/blaze3d/buffers/GpuBuffer;"
            )
    )
    private void accident$applyCustomFogStart(
            Camera camera,
            int viewDistance,
            RenderTickCounter renderTickCounter,
            float f,
            ClientWorld clientWorld,
            CallbackInfoReturnable<Vector4f> cir,
            @Local FogData fogData
    ) {
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.isEnabled() || !ambience.customFog.isValue()) return;

        float customStart = ambience.fogStart.getValue() * 16f; // chunks to blocks
        fogData.renderDistanceStart = Math.min(customStart, fogData.renderDistanceEnd);
    }

    @Inject(
            method = "getFogColor",
            at = @At("RETURN"),
            cancellable = true
    )
    private void onGetFogColor(
            Camera camera,
            float tickProgress,
            ClientWorld world,
            int viewDistance,
            float skyDarkness,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isEnabled() && ambience.customFog.isValue()) {
            float r = ambience.getFogRed();
            float g = ambience.getFogGreen();
            float b = ambience.getFogBlue();

            cir.setReturnValue(new Vector4f(r, g, b, 1.0F));
        }
    }
}