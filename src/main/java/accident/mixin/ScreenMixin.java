package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.command.CommandManager;

@Mixin(Screen.class)
public class ScreenMixin {
    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true)
    private void disableBlur(DrawContext context, CallbackInfo ci) {
        ci.cancel();
    }

    // ClickGui рисуется через ClickGui.renderOverlay() из GameRendererMixin.afterGuiRender(),
    // а не через Screen.render() - хуки GuiEcho тут молча не срабатывали, поэтому захват
    // GuiEcho теперь стоит вокруг renderOverlay() в GameRendererMixin. оставил как заметку

    @Inject(method = "handleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void onHandleClickEvent(ClickEvent clickEvent, MinecraftClient client, Screen screenAfterRun, CallbackInfo ci) {
        if (clickEvent instanceof ClickEvent.RunCommand(String command)) {
            CommandManager manager = CommandManager.getInstance();

            if (manager != null && command != null && command.startsWith(manager.getPrefix())) {
                manager.execute(command.substring(manager.getPrefix().length()));
                ci.cancel();
            }
        }
    }
}