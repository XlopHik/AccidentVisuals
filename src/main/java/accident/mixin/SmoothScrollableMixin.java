package accident.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ScrollableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.misc.SmoothAnimations;

@Mixin(ScrollableWidget.class)
public abstract class SmoothScrollableMixin {

    @Shadow private double scrollY;
    @Shadow public abstract void setScrollY(double scrollY);

    @Unique private double smoothList$smoothPos = 0.0;
    @Unique private double smoothList$targetPos = 0.0;
    @Unique private boolean smoothList$noSync = false;
    @Unique private boolean smoothList$initialized = false;

    // Track every external setScrollY call as a new target
    @Inject(method = "setScrollY", at = @At("TAIL"))
    private void onSetScrollY(double y, CallbackInfo ci) {
        if (smoothList$noSync) return;
        if (!smoothList$initialized) {
            smoothList$smoothPos = this.scrollY;
            smoothList$initialized = true;
        }
        smoothList$targetPos = this.scrollY;
    }

    // Each frame: lerp smooth position and apply it to the widget
    @Inject(method = "drawScrollbar", at = @At("HEAD"), require = 0)
    private void onDrawScrollbar(DrawContext context, int mx, int my, CallbackInfo ci) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.entryListSmoothness.getValue() == 0) return;

        smoothList$smoothPos = SmoothAnimations.smooth(
                smoothList$smoothPos,
                smoothList$targetPos,
                SmoothAnimations.factor(sa.entryListSmoothness));

        if (Math.abs(smoothList$smoothPos - smoothList$targetPos) < 0.5) {
            smoothList$smoothPos = smoothList$targetPos;
        }

        smoothList$noSync = true;
        setScrollY(Math.round(smoothList$smoothPos));
        smoothList$noSync = false;
    }
}
