package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.screens.keybinds.CustomKeybindsScreen;

@Mixin(GameOptionsScreen.class)
public class KeybindsScreenMixin {

    @Final @Shadow protected Screen parent;
    @Final @Shadow protected GameOptions gameOptions;

    /**
     * Intercept GameOptionsScreen.init() — this is where KeybindsScreen actually
     * initializes. We check if the current instance is KeybindsScreen and swap it.
     */
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if ((Object) this instanceof KeybindsScreen) {
            MinecraftClient.getInstance().setScreen(
                    new CustomKeybindsScreen(parent, gameOptions)
            );
            ci.cancel();
        }
    }
}