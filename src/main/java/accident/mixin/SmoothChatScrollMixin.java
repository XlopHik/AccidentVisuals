package accident.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.misc.SmoothAnimations;

@Mixin(value = ChatHud.class, priority = 1101)
public class SmoothChatScrollMixin {

    @Shadow private int scrolledLines;

    @Unique private float smoothChat$smoothPos = 0f;
    @Unique private float smoothChat$targetPos = 0f;

    // After vanilla scroll() updates scrolledLines, capture as target
    @Inject(method = "scroll", at = @At("TAIL"))
    private void onScroll(int amount, CallbackInfo ci) {
        smoothChat$targetPos = this.scrolledLines;
    }

    // On resetScroll, snap both to 0
    @Inject(method = "resetScroll", at = @At("TAIL"))
    private void onResetScroll(CallbackInfo ci) {
        smoothChat$smoothPos = 0f;
        smoothChat$targetPos = 0f;
    }

    // On each render frame: lerp and apply to scrolledLines before lines are drawn
    @Inject(
            method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V",
            at = @At("HEAD")
    )
    private void onRenderHead(ChatHud.Backend drawer, int windowHeight, int currentTick,
                              boolean expanded, CallbackInfo ci) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.chatSmoothness.getValue() == 0) return;

        smoothChat$smoothPos = SmoothAnimations.smooth(
                smoothChat$smoothPos,
                smoothChat$targetPos,
                SmoothAnimations.factor(sa.chatSmoothness));

        if (Math.abs(smoothChat$smoothPos - smoothChat$targetPos) < 0.01f) {
            smoothChat$smoothPos = smoothChat$targetPos;
        }

        this.scrolledLines = (int) Math.floor(smoothChat$smoothPos);
    }
}
