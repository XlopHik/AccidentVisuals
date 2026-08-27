package accident.mixin;

import accident.Initialization;
import accident.modules.impl.render.HitModules;
import accident.util.accesors.OverlayTextureAccessor;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public class OverlayTextureMixin implements OverlayTextureAccessor {

    @Shadow
    private NativeImageBackedTexture texture;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
    }

    @Override
    public void accident$recolor() {
        NativeImage image = texture.getImage();
        if (image == null) return;

        HitModules module = null;
        try {
            if (Initialization.getInstance() != null && Initialization.getInstance().getManager() != null) {
                module = HitModules.getInstance();
            }
        } catch (Exception e) {
        }

        int hurtColor;
        if (module != null && module.isOverlayActive()) {
            int argb = module.getDamageVignetteColor();
            int a = 255 - ((argb >> 24) & 0xFF);
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8)  & 0xFF;
            int b =  argb        & 0xFF;
            hurtColor = (a << 24) | (r << 16) | (g << 8) | b;
        } else {
            hurtColor = -1291911168;
        }

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                image.setColorArgb(x, y, hurtColor);
            }
        }

        texture.upload();
    }
}