package accident.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.misc.SmoothAnimations;

@Mixin(Mouse.class)
public class SmoothHotbarMouseMixin {

    @Unique
    private int smoothHotbar$oldSlot;

    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    private void onScrollHead(long window, double horizontal, double vertical, CallbackInfo ci) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            smoothHotbar$oldSlot = player.getInventory().getSelectedSlot();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("TAIL"))
    private void onScrollTail(long window, double horizontal, double vertical, CallbackInfo ci) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || !sa.hotbarRollover.isValue()) return;
        if (sa.hotbarSmoothness.getValue() == 0) return;

        var player = MinecraftClient.getInstance().player;
        if (player == null) return;

        int newSlot = player.getInventory().getSelectedSlot();
        if (newSlot == smoothHotbar$oldSlot) return;

        if (vertical > 0 && newSlot > smoothHotbar$oldSlot) {
            SmoothAnimations.hotbarRolloverCount++;
        } else if (vertical < 0 && newSlot < smoothHotbar$oldSlot) {
            SmoothAnimations.hotbarRolloverCount--;
        }
    }
}
