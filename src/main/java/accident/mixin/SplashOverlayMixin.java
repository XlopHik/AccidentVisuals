package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.screens.loading.Loading;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private boolean reloading;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.reloading) return;

        ci.cancel();

        Loading loader = Loading.getInstance();
        loader.setProgress(this.reload.getProgress());

        if (this.reload.isComplete()) {
            loader.markComplete();
        }

        // Рендерим меню под загрузчиком во время фейда
        if (loader.isFading()) {
            if (this.client.currentScreen == null) {
                this.client.setScreen(new accident.screens.menu.MainMenuScreen());
            }
            if (this.client.currentScreen != null) {
                this.client.currentScreen.render(context, mouseX, mouseY, delta);
            }
        }

        loader.render(context, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 1.0f);

        if (loader.isReadyToClose()) {
            this.client.setOverlay(null);
            loader.reset();
        }
    }
}