package accident.screens.loading;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import accident.util.render.font.Fonts;

public class Loading {

    private static Loading instance;
    private static final Identifier LOADING_LOGO = Identifier.of("accident", "textures/gui/load.png");

    private float progress = 0f;
    private long reloadCompleteTime = -1L;
    private boolean initialized = false;

    public Loading() { instance = this; }

    public static Loading getInstance() {
        if (instance == null) instance = new Loading();
        return instance;
    }

    public void render(DrawContext context, int width, int height, float opacity) {
        long currentTime = Util.getMeasuringTimeMs();
        if (!initialized) initialized = true;

        float f = this.reloadCompleteTime > -1L ? (float)(currentTime - this.reloadCompleteTime) / 1000.0F : -1.0F;

        float h;
        if (f >= 1.0F) {
            h = 1.0F - MathHelper.clamp(f - 1.0F, 0.0F, 1.0F);
        } else {
            h = 1.0F;
        }

        // Красный фон с альфой — когда h падает меню просвечивает сзади
        int k = ColorHelper.getArgb((int)(h * 255.0F), 239, 50, 61);
        context.fill(0, 0, width, height, k);

        if (h > 0.001f) {
            renderMojangStyle(context, width, height, h);
        }
    }
    public boolean isFading() {
        if (this.reloadCompleteTime == -1L) return false;
        float f = (float)(Util.getMeasuringTimeMs() - this.reloadCompleteTime) / 1000.0F;
        return f >= 1.0F; // начинаем рендерить меню когда красный начинает фейдиться
    }

    private void renderMojangStyle(DrawContext context, int width, int height, float opacity) {
        int texW = 600; int texH = 350;
        int x = (width - texW) / 2;
        int y = (height - texH) / 2;

        context.drawTexture(RenderPipelines.MOJANG_LOGO, LOADING_LOGO, x, y, 0.0f, 0.0f, texW, texH, texW, texH, texW, texH, ColorHelper.getWhite(opacity * 0.2f));

        int fillWidth = (int)(texW * this.progress);
        if (fillWidth > 0) {
            context.enableScissor(x, y, x + fillWidth, y + texH);
            context.drawTexture(RenderPipelines.MOJANG_LOGO, LOADING_LOGO, x, y, 0.0f, 0.0f, texW, texH, texW, texH, texW, texH, ColorHelper.getWhite(opacity));
            context.disableScissor();
        }
    }

    public void setProgress(float currentProgress) {
        this.progress = MathHelper.clamp(this.progress * 0.95F + currentProgress * 0.05F, 0.0F, 1.0F);
    }

    public void markComplete() {
        if (this.reloadCompleteTime == -1L) {
            this.reloadCompleteTime = Util.getMeasuringTimeMs();
        }
    }

    public boolean isReadyToClose() {
        if (this.reloadCompleteTime == -1L) return false;
        return (Util.getMeasuringTimeMs() - this.reloadCompleteTime) >= 2000L;
    }

    public void reset() {
        this.progress = 0f;
        this.reloadCompleteTime = -1L;
        this.initialized = false;
    }
}