package accident.util.render.shader;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import lombok.Getter;
import lombok.Setter;
import accident.util.render.pipeline.GlassCompositePipeline;
import accident.util.render.pipeline.HandFirePipeline;
import accident.util.render.pipeline.HandTrailPipeline;
import accident.util.render.pipeline.KawaseBlurPipeline;
import accident.util.render.pipeline.MaskDiffPipeline;

public class GlassEntityRenderer {

    private static GlassEntityRenderer instance;

    private final MinecraftClient client;
    private KawaseBlurPipeline kawaseBlur;
    private GlassCompositePipeline glassComposite;
    private MaskDiffPipeline maskDiff;
    private HandTrailPipeline fireTrail;
    private HandFirePipeline fireComposite;

    private GpuTexture sceneBeforeTexture;
    private GpuTextureView sceneBeforeTextureView;
    private GpuTexture sceneAfterTexture;
    private GpuTextureView sceneAfterTextureView;
    private GpuTexture depthBeforeTexture;
    private GpuTextureView depthBeforeTextureView;
    private GpuTexture depthAfterTexture;
    private GpuTextureView depthAfterTextureView;
    private GpuTexture maskTexture;
    private GpuTextureView maskTextureView;

    private int lastWidth = 0;
    private int lastHeight = 0;

    @Getter @Setter private boolean needsEffect = false;

    public void markNeedsEffect() { this.needsEffect = true; }
    public void clearNeedsEffect() { this.needsEffect = false; }

    @Getter @Setter private boolean skipEffect = false;
    private boolean hadMapThisFrame = false;
    private boolean capturing = false;
    @Getter private boolean enabled = false;
    private boolean initialized = false;

    @Setter private float blurRadius = 6.0f;
    private int blurIterations = 4;
    @Setter private float saturation = 1.0f;
    @Setter private boolean reflect = true;
    @Setter private int tintColor = 0x00000000;
    @Setter private float tintIntensity = 0.1f;
    @Setter private float edgeGlowIntensity = 0.3f;

    @Setter
    private float waveIntensity = 0.004f;

    @Setter
    private float time = 0.0f;

    // те же стили, что у рук - glass_composite.fsh ветвится по этому же значению
    @Setter
    private int style = 0;

    // тот же огонь, что у рук, но по маске сущности - горит постоянно, без envelope
    @Setter private boolean fireEnabled = false;
    @Setter private float fireIntensity = 0.8f;
    @Setter private float fireSpeed = 1.0f;
    @Setter private float fireLength = 0.55f;
    @Setter private float fireSmoke = 0.55f;
    @Setter private float fireTrailFade = 0.94f;
    @Setter private float fireSoftness = 1.3f;
    @Setter private float fireBlur = 1.4f;
    @Setter private int fireColor = 0xFFFF7320;

    private static final float FIRE_RADIUS = 0.35f;

    public GlassEntityRenderer() {
        this.client = MinecraftClient.getInstance();
        instance = this;
    }

    public static GlassEntityRenderer getInstance() {
        if (instance == null) {
            instance = new GlassEntityRenderer();
        }
        return instance;
    }

    public static void resetInstance() {
        if (instance != null) {
            instance.close();
            instance.initialized = false;
        }
    }

    private void ensureInitialized() {
        if (initialized) return;

        if (kawaseBlur != null) kawaseBlur.close();
        if (glassComposite != null) glassComposite.close();
        if (maskDiff != null) maskDiff.close();
        if (fireTrail != null) fireTrail.close();
        if (fireComposite != null) fireComposite.close();

        this.kawaseBlur = new KawaseBlurPipeline();
        this.glassComposite = new GlassCompositePipeline();
        this.maskDiff = new MaskDiffPipeline();
        this.fireTrail = new HandTrailPipeline();
        this.fireComposite = new HandFirePipeline();

        lastWidth = 0;
        lastHeight = 0;
        initialized = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) ensureInitialized();
    }

    public void setBlurIterations(int iterations) {
        this.blurIterations = Math.max(1, Math.min(8, iterations));
    }

    private void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneBeforeTexture != null) return;

        cleanupTextures();

        sceneBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_entity_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        sceneBeforeTextureView = RenderSystem.getDevice().createTextureView(sceneBeforeTexture);

        sceneAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_entity_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        sceneAfterTextureView = RenderSystem.getDevice().createTextureView(sceneAfterTexture);

        depthBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_entity_depth_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1
        );
        depthBeforeTextureView = RenderSystem.getDevice().createTextureView(depthBeforeTexture);

        depthAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_entity_depth_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1
        );
        depthAfterTextureView = RenderSystem.getDevice().createTextureView(depthAfterTexture);

        maskTexture = RenderSystem.getDevice().createTexture(
                () -> "minecraft:glass_entity_mask",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        maskTextureView = RenderSystem.getDevice().createTextureView(maskTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupTextures() {
        if (sceneBeforeTextureView != null) { sceneBeforeTextureView.close(); sceneBeforeTextureView = null; }
        if (sceneBeforeTexture != null) { sceneBeforeTexture.close(); sceneBeforeTexture = null; }
        if (sceneAfterTextureView != null) { sceneAfterTextureView.close(); sceneAfterTextureView = null; }
        if (sceneAfterTexture != null) { sceneAfterTexture.close(); sceneAfterTexture = null; }
        if (depthBeforeTextureView != null) { depthBeforeTextureView.close(); depthBeforeTextureView = null; }
        if (depthBeforeTexture != null) { depthBeforeTexture.close(); depthBeforeTexture = null; }
        if (depthAfterTextureView != null) { depthAfterTextureView.close(); depthAfterTextureView = null; }
        if (depthAfterTexture != null) { depthAfterTexture.close(); depthAfterTexture = null; }
        if (maskTextureView != null) { maskTextureView.close(); maskTextureView = null; }
        if (maskTexture != null) { maskTexture.close(); maskTexture = null; }
    }

    public void captureSceneBeforeHands() {
        if (!enabled) return;
        ensureInitialized();

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        int width = fb.textureWidth;
        int height = fb.textureHeight;
        ensureTextures(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getColorAttachment(), sceneBeforeTexture,
                0, 0, 0, 0, 0, width, height
        );
        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(
                    fb.getDepthAttachment(), depthBeforeTexture,
                    0, 0, 0, 0, 0, width, height
            );
        }
        capturing = true;
    }

    public void captureSceneAfterHands() {
        if (!enabled || !capturing || hadMapThisFrame) {
            capturing = false;
            hadMapThisFrame = false;
            return;
        }

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getColorAttachment(), sceneAfterTexture,
                0, 0, 0, 0, 0, lastWidth, lastHeight
        );
        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(
                    fb.getDepthAttachment(), depthAfterTexture,
                    0, 0, 0, 0, 0, lastWidth, lastHeight
            );
        }
    }

    public void renderGlassEffect() {
        if (!enabled || !capturing) return;
        hadMapThisFrame = false;

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) {
            capturing = false;
            return;
        }

        maskDiff.createMask(
                maskTextureView,
                sceneBeforeTextureView,
                sceneAfterTextureView,
                depthBeforeTextureView,
                depthAfterTextureView,
                lastWidth, lastHeight
        );

        GpuTextureView blurredView = kawaseBlur.blur(
                sceneBeforeTexture, sceneBeforeTextureView,
                lastWidth, lastHeight,
                blurIterations, blurRadius
        );

        if (blurredView == null) {
            capturing = false;
            return;
        }

        glassComposite.composite(
                fb.getColorAttachmentView(),
                sceneBeforeTextureView,
                blurredView,
                maskTextureView,
                lastWidth, lastHeight,
                saturation,
                reflect,
                tintColor,
                tintIntensity,
                edgeGlowIntensity,
                time,
                waveIntensity,
                style
        );

        if (fireEnabled) {
            // glass pass только что перезаписал framebuffer, огню нужна свежая
            // копия фона - иначе он сработает поверх старой сцены и сотрёт glass
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    fb.getColorAttachment(), sceneAfterTexture,
                    0, 0, 0, 0, 0, lastWidth, lastHeight
            );
            renderFireEffect(fb.getColorAttachmentView());
        }

        capturing = false;
    }

    private void renderFireEffect(GpuTextureView target) {
        if (fireTrail == null || fireComposite == null) return;

        float a = ((fireColor >> 24) & 0xFF) / 255.0f;
        float r = ((fireColor >> 16) & 0xFF) / 255.0f;
        float g = ((fireColor >> 8) & 0xFF) / 255.0f;
        float b = (fireColor & 0xFF) / 255.0f;
        if (a < 0.01f) a = 1.0f;

        GpuTextureView trailView = fireTrail.update(
                sceneAfterTextureView, maskTextureView, lastWidth, lastHeight, time,
                fireIntensity, r, g, b, a,
                FIRE_RADIUS, fireSpeed, fireLength, fireSoftness,
                fireBlur, fireSmoke, 1.0f,
                0f, 1f, fireTrailFade
        );
        if (trailView == null) return;

        int iterations = fireBlur >= 2.15f ? 3 : (fireBlur >= 1.05f ? 2 : 1);
        GpuTextureView smokeView = kawaseBlur.blur(
                fireTrail.texture(), trailView, lastWidth, lastHeight,
                iterations, Math.max(1.05f, fireBlur * 1.22f)
        );
        if (smokeView == null) smokeView = trailView;

        fireComposite.composite(
                target, sceneAfterTextureView, smokeView, maskTextureView,
                lastWidth, lastHeight, time, fireIntensity, r, g, b, a,
                FIRE_RADIUS, fireSpeed, fireLength, fireSoftness,
                fireBlur, fireSmoke, 1.0f
        );
    }

    public boolean isCapturing() { return capturing; }

    public void invalidate() {
        cleanupTextures();
        if (kawaseBlur != null) kawaseBlur.close();
        if (glassComposite != null) glassComposite.close();
        if (maskDiff != null) maskDiff.close();
        if (fireTrail != null) fireTrail.close();
        if (fireComposite != null) fireComposite.close();
        kawaseBlur = null;
        glassComposite = null;
        maskDiff = null;
        fireTrail = null;
        fireComposite = null;
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
        capturing = false;
    }

    public void markMapRendered() { this.hadMapThisFrame = true; }
    public void resetMapFlag() { this.hadMapThisFrame = false; }
    public void cancelCapture() { capturing = false; }

    public void close() {
        cleanupTextures();
        if (kawaseBlur != null) { kawaseBlur.close(); kawaseBlur = null; }
        if (glassComposite != null) { glassComposite.close(); glassComposite = null; }
        if (maskDiff != null) { maskDiff.close(); maskDiff = null; }
        if (fireTrail != null) { fireTrail.close(); fireTrail = null; }
        if (fireComposite != null) { fireComposite.close(); fireComposite = null; }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
    }
}