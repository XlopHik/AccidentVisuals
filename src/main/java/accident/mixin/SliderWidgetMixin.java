package accident.mixin;

import accident.util.render.SliderSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// тот же ресскин, что в PressableWidgetMixin, но для слайдеров (FOV, громкость и тд) -
// SliderWidget не наследует PressableWidget, поэтому отдельный хук
@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin extends ClickableWidget.InactivityIndicatingWidget {

    private SliderWidgetMixin() {
        super(0, 0, 0, 0, Text.empty());
    }

    @Shadow
    protected double value;

    @Unique
    private float accident$hoverProgress = 0f;
    @Unique
    private long accident$lastRenderNanos = 0L;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void accident$queueCustomSlider(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ci.cancel();

        boolean highlighted = this.isHovered() || this.isFocused();

        long now = System.nanoTime();
        float dt = accident$lastRenderNanos == 0L ? 0.016f : Math.min(0.1f, (now - accident$lastRenderNanos) / 1_000_000_000f);
        accident$lastRenderNanos = now;
        float lerpSpeed = 1f - (float) Math.pow(0.001, dt);
        accident$hoverProgress = MathHelper.lerp(lerpSpeed, accident$hoverProgress, highlighted ? 1f : 0f);

        float scaleMul = (float) (MinecraftClient.getInstance().getWindow().getScaleFactor() / 2.0);
        float x = this.getX() * scaleMul;
        float y = this.getY() * scaleMul;
        float w = this.getWidth() * scaleMul;
        float h = this.getHeight() * scaleMul;
        float radius = Math.min(8f, h / 2.5f);

        int brightness = (int) (accident$hoverProgress * 15);
        int bodyColor = accident$argb(150,
                Math.min(255, 30 + brightness), Math.min(255, 35 + brightness), Math.min(255, 45 + brightness));

        int fillAlpha = (int) (90 + accident$hoverProgress * 40);
        int fillColor = accident$argb(fillAlpha, 100, 110, 130);

        int handleAlpha = (int) (180 + accident$hoverProgress * 60);
        int handleColor = accident$argb(Math.min(255, handleAlpha), 190, 200, 220);

        int borderAlpha = (int) (80 + accident$hoverProgress * 20);
        int borderColor = highlighted ? accident$argb(borderAlpha, 100, 110, 130) : accident$argb(borderAlpha, 50, 55, 65);

        String label = this.getMessage().getString();
        int textGray = 160 + (int) (60 * accident$hoverProgress);
        int textColor = accident$argb(255, textGray, textGray, textGray);
        float textSize = Math.min(9f, h * 0.42f);

        SliderSkin.add(new SliderSkin.Entry(x, y, w, h, radius, this.value,
                bodyColor, fillColor, handleColor, borderColor, label, textColor, textSize));
    }

    @Unique
    private static int accident$argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
