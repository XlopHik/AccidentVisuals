package accident.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.util.render.ButtonSkin;
import accident.util.render.SliderSkin;

// тут майнкрафт реально отправляет отложенную очередь DrawContext в GPU
// флашим свои Render2D-скины кнопок после этого, иначе будет гонка - подробнее в ButtonSkin
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void accident$flushButtonSkin(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        accident.util.render.pipeline.RectPipeline.flushPending();
        accident.util.render.Gui2DUniforms.invalidate();
        // List first, keyboard and its tooltip on top of it.
        accident.screens.keybinds.KeybindListRenderer.flush();
        accident.screens.keybinds.KeyboardRenderer.flush();
        ButtonSkin.flush();
        SliderSkin.flush();
    }
}
