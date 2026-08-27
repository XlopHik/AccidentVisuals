package accident.util.render.shader;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.world.ClientWorld;
import accident.util.render.pipeline.SkySpacePipeline;

public class SkyRenderer {

    private static SkyRenderer instance;

    private final MinecraftClient client;
    private SkySpacePipeline spacePipeline;

    // захватываем в начале кадра - к моменту sky pass эти матрицы уже никто не передаёт
    private final Matrix4f frameView = new Matrix4f();
    private final Matrix4f frameProjection = new Matrix4f();

    @Getter private boolean enabled = false;

    @Setter private float opacity        = 0.92f;
    @Setter private float fogDensity     = 0.35f;
    @Setter private float starBrightness = 0.8f;
    @Setter private float skyRed   = 0.094f;
    @Setter private float skyGreen = 0.235f;
    @Setter private float skyBlue  = 0.510f;
    @Setter private float style    = 0.0f;

    private long startNano = System.nanoTime();
    private long lastDebugMs = 0L;

    public SkyRenderer() {
        this.client = MinecraftClient.getInstance();
        instance = this;
    }

    public static SkyRenderer getInstance() {
        if (instance == null) instance = new SkyRenderer();
        return instance;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void captureFrameMatrices(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (viewMatrix != null) this.frameView.set(viewMatrix);
        if (projectionMatrix != null) this.frameProjection.set(projectionMatrix);
    }

    // захватывается каждый кадр независимо от CustomSky - любой другой эффект,
    // которому нужны реальные камера-матрицы этого кадра, может брать их отсюда,
    // а не городить свой миксин для захвата
    public Matrix4f getFrameView() {
        return new Matrix4f(frameView);
    }

    public Matrix4f getFrameProjection() {
        return new Matrix4f(frameProjection);
    }

    // рисуется прямо внутри vanilla sky pass, поэтому мир, погода и облака сами
    // лягут поверх - не нужно копировать сцену или делать отдельную маску глубины
    public void renderSkyBackground() {
        if (!enabled) return;

        // настройки берём каждый кадр - раньше обновлялись только при тумблере
        // модуля, и слайдер цвета в меню не работал пока модуль не переключишь
        accident.modules.impl.render.CustomSky module = accident.modules.impl.render.CustomSky.getInstance();
        if (module != null) {
            this.opacity = module.getOpacity().getValue();
            this.fogDensity = module.getFogDensity().getValue();
            this.starBrightness = module.getStarBrightness().getValue();
            this.skyRed = module.getSkyRed();
            this.skyGreen = module.getSkyGreen();
            this.skyBlue = module.getSkyBlue();
            this.style = module.getStyleIndex();
        }

        if (spacePipeline == null) spacePipeline = new SkySpacePipeline();

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        float time = (System.nanoTime() - startNano) / 1_000_000_000.0f;

        float sunAngle = 0f;
        ClientWorld world = client.world;
        if (world != null) sunAngle = (world.getTimeOfDay() / 24000.0f) % 1.0f;

        Matrix4f invViewProj = new Matrix4f(frameProjection).mul(frameView).invert();

        spacePipeline.render(
                fb.getColorAttachmentView(),
                fb.textureWidth, fb.textureHeight,
                time, sunAngle,
                fogDensity, starBrightness,
                opacity,
                invViewProj,
                skyRed, skyGreen, skyBlue,
                style
        );
    }

    public void invalidate() {
        if (spacePipeline != null) { spacePipeline.close(); spacePipeline = null; }
        startNano = System.nanoTime();
    }

    public void close() {
        if (spacePipeline != null) { spacePipeline.close(); spacePipeline = null; }
    }
}