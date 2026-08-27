package accident.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.misc.SmoothAnimations;

@Mixin(value = InGameHud.class, priority = 900)
public class SmoothHotbarMixin {

    private static final int SLOT_WIDTH  = 20;
    private static final int SLOT_COUNT  = 9;
    private static final int ROLLOVER_GAP = 4;

    @Unique private float smoothSelectorPos = 0f;
    @Unique private boolean smoothHotbar$initialized = false;

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void updateSmoothPos(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.hotbarSmoothness.getValue() == 0) return;

        var player = MinecraftClient.getInstance().player;
        if (player == null) return;

        int rollover = SmoothAnimations.hotbarRolloverCount;
        int selectedSlot = player.getInventory().getSelectedSlot();
        int rolloverGap = ROLLOVER_GAP;

        float target = ((selectedSlot - (rollover * SLOT_COUNT)) * SLOT_WIDTH)
                - (rollover * rolloverGap);

        if (!smoothHotbar$initialized) {
            smoothSelectorPos = target;
            smoothHotbar$initialized = true;
        }

        smoothSelectorPos = SmoothAnimations.smooth(smoothSelectorPos, target, SmoothAnimations.factor(sa.hotbarSmoothness));

        // Wrap around if selector went past edges
        if (Math.round(smoothSelectorPos) < rolloverGap - (SLOT_WIDTH / 2)) {
            smoothSelectorPos += SLOT_COUNT * SLOT_WIDTH + rolloverGap;
            SmoothAnimations.hotbarRolloverCount--;
        } else if (Math.round(smoothSelectorPos) > (rolloverGap - SLOT_WIDTH / 2) + SLOT_WIDTH * SLOT_COUNT) {
            smoothSelectorPos -= SLOT_COUNT * SLOT_WIDTH + rolloverGap;
            SmoothAnimations.hotbarRolloverCount++;
        }
    }

    // Intercept the selector texture draw (ordinal 1 in renderHotbar = the selection box)
    @WrapOperation(
            method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1)
    )
    private void moveSelectorTexture(DrawContext context, RenderPipeline pipeline, Identifier texture,
                                     int x, int y, int width, int height, Operation<Void> op) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.hotbarSmoothness.getValue() == 0) {
            op.call(context, pipeline, texture, x, y, width, height);
            return;
        }

        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            op.call(context, pipeline, texture, x, y, width, height);
            return;
        }

        int hotbarStart = x - (player.getInventory().getSelectedSlot() * SLOT_WIDTH);
        int translatedX = Math.round(hotbarStart + smoothSelectorPos);

        op.call(context, pipeline, texture, translatedX, y, width, height);
    }
}
