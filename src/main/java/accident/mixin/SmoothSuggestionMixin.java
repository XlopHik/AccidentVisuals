package accident.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.misc.SmoothAnimations;

@Mixin(ChatInputSuggestor.SuggestionWindow.class)
public class SmoothSuggestionMixin {

    @Shadow private int inWindowIndex;

    @Unique private float smoothSug$smoothPos = 0f;
    @Unique private float smoothSug$targetPos = 0f;
    @Unique private boolean smoothSug$initialized = false;

    // After scroll() updates inWindowIndex, capture as target
    @Inject(method = "scroll", at = @At("TAIL"))
    private void onScroll(int offset, CallbackInfo ci) {
        smoothSug$targetPos = this.inWindowIndex;
    }

    // Before rendering, lerp and apply smooth position
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.suggestionSmoothness.getValue() == 0) return;

        if (!smoothSug$initialized) {
            smoothSug$smoothPos = this.inWindowIndex;
            smoothSug$targetPos = this.inWindowIndex;
            smoothSug$initialized = true;
        }

        smoothSug$smoothPos = SmoothAnimations.smooth(
                smoothSug$smoothPos,
                smoothSug$targetPos,
                SmoothAnimations.factor(sa.suggestionSmoothness));

        if (Math.abs(smoothSug$smoothPos - smoothSug$targetPos) < 0.01f) {
            smoothSug$smoothPos = smoothSug$targetPos;
        }

        this.inWindowIndex = Math.round(smoothSug$smoothPos);
    }
}
