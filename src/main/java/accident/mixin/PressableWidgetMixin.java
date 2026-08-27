package accident.mixin;

import accident.util.render.ButtonSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ресскин ванильных кнопок под стиль MainMenuScreen. renderWidget - единая точка,
// через которую проходят все ButtonWidget/CyclingButtonWidget, поэтому хватает одного миксина.
// сам рендер не тут - просто кладём entry в очередь ButtonSkin, рисует GuiRendererMixin
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin extends ClickableWidget.InactivityIndicatingWidget {

    private PressableWidgetMixin() {
        super(0, 0, 0, 0, Text.empty());
    }

    @Unique
    private float accident$hoverProgress = 0f;
    @Unique
    private long accident$lastRenderNanos = 0L;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void accident$queueCustomButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ci.cancel();

        boolean active = this.active;
        boolean highlighted = active && (this.isHovered() || this.isFocused());

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
        int baseR = active ? 30 : 22, baseG = active ? 35 : 25, baseB = active ? 45 : 32;
        int bodyAlpha = active ? 150 : 110;
        int bodyColor = accident$argb(bodyAlpha,
                Math.min(255, baseR + brightness), Math.min(255, baseG + brightness), Math.min(255, baseB + brightness));

        int borderAlpha = (int) (80 + accident$hoverProgress * 20);
        int borderColor = highlighted ? accident$argb(borderAlpha, 100, 110, 130) : accident$argb(borderAlpha, 50, 55, 65);

        String label = this.getMessage().getString();
        int textGray = active ? 160 + (int) (60 * accident$hoverProgress) : 90;
        int textColor = accident$argb(255, textGray, textGray, textGray);
        float textSize = Math.min(9f, h * 0.42f);

        ButtonSkin.add(new ButtonSkin.Entry(x, y, w, h, radius, bodyColor, borderColor, label, textColor, textSize));
    }

    @Unique
    private static int accident$argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
