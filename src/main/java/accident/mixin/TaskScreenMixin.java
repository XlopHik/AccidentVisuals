package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TaskScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TaskScreen.class)
public abstract class TaskScreenMixin {

    @Unique
    protected MinecraftClient client;
    @Unique
    protected int width;
    @Unique
    protected int height;

    @Unique
    private static final Identifier CUSTOM_BG = Identifier.of("accident", "textures/menu/back1.png");

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                CUSTOM_BG,
                0, 0, 0f, 0f,
                width, height,
                width, height
        );
    }
}