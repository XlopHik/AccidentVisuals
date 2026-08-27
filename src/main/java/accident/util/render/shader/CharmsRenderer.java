package accident.util.render.shader;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import accident.util.render.pipeline.CharmsCompositePipeline;
import accident.util.render.pipeline.HandFirePipeline;
import accident.util.render.pipeline.HandTrailPipeline;
import accident.util.render.pipeline.KawaseBlurPipeline;
import accident.util.render.pipeline.MaskDiffPipeline;

public class CharmsRenderer {

    private static CharmsRenderer instance;

    private final MinecraftClient client;

    private KawaseBlurPipeline    kawaseBlur;
    private MaskDiffPipeline      maskDiff;
    private CharmsCompositePipeline charmsComposite;
    private HandTrailPipeline     fireTrail;
    private HandFirePipeline      fireComposite;

    // тот же огонь что и на руках, но по силуэту всего тела
    // горит постоянно, без привязки к взмаху - активность всегда 1
    @Setter private boolean fireEnabled = false;
    @Setter private float fireIntensity = 0.8f;
    @Setter private float fireSpeed = 1.0f;
    @Setter private float fireLength = 0.55f;
    @Setter private float fireSmoke = 0.55f;
    @Setter private float fireTrailFade = 0.94f;
    @Setter private float fireSoftness = 1.3f;
    @Setter private float fireBlur = 1.4f;
    @Setter private int fireColor = 0xFFFF7320;
    @Setter private float fireTime = 0f;

    private static final float FIRE_RADIUS = 0.35f;

    // Color snapshots (before/after entity render)
    private GpuTexture     sceneBeforeTexture;
    private GpuTextureView sceneBeforeTextureView;
    private GpuTexture     sceneAfterTexture;
    private GpuTextureView sceneAfterTextureView;

    // Depth snapshots (used by MaskDiffPipeline to detect entity vs wall)
    private GpuTexture     depthBeforeTexture;
    private GpuTextureView depthBeforeTextureView;
    private GpuTexture     depthAfterTexture;
    private GpuTextureView depthAfterTextureView;

    // Entity silhouette mask  (1=visible entity pixel, 0=background)
    private GpuTexture     maskTexture;
    private GpuTextureView maskTextureView;

    private int  lastWidth  = 0;
    private int  lastHeight = 0;

    @Getter @Setter private boolean needsEffect = false;
    @Getter         private boolean enabled     = false;
    private boolean initialized = false;
    private boolean capturing   = false;
    private boolean hadMapThisFrame = false;

    // ── render parameters set by Charms module each frame ─────────────────
    @Setter private int   outlineColor     = 0xFF_FFFFFF;
    @Setter private float outlineThickness = 2f;
    @Setter private int   fillColor        = 0x44_FFFFFF;
    @Setter private float fillAlpha        = 0.12f;
    @Setter private float glowIntensity    = 0.55f;
    @Setter private int   glowIterations   = 3;
    @Setter private float glowRadius       = 3.0f;

    public CharmsRenderer() {
        this.client = MinecraftClient.getInstance();
        instance = this;
    }

    public static CharmsRenderer getInstance() {
        if (instance == null) instance = new CharmsRenderer();
        return instance;
    }

    public static void resetInstance() {
        if (instance != null) { instance.close(); instance.initialized = false; }
    }

    // ── lifecycle ──────────────────────────────────────────────────────────

    private void ensureInitialized() {
        if (initialized) return;
        if (kawaseBlur      != null) kawaseBlur.close();
        if (maskDiff        != null) maskDiff.close();
        if (charmsComposite != null) charmsComposite.close();
        if (fireTrail       != null) fireTrail.close();
        if (fireComposite   != null) fireComposite.close();

        this.kawaseBlur      = new KawaseBlurPipeline();
        this.maskDiff        = new MaskDiffPipeline();
        this.charmsComposite = new CharmsCompositePipeline();
        this.fireTrail       = new HandTrailPipeline();
        this.fireComposite   = new HandFirePipeline();

        lastWidth = 0;
        lastHeight = 0;
        initialized = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) ensureInitialized();
    }

    // ── texture management ─────────────────────────────────────────────────

    private void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneBeforeTexture != null) return;
        cleanupTextures();

        sceneBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "accident:charms_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1);
        sceneBeforeTextureView = RenderSystem.getDevice().createTextureView(sceneBeforeTexture);

        sceneAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "accident:charms_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1);
        sceneAfterTextureView = RenderSystem.getDevice().createTextureView(sceneAfterTexture);

        depthBeforeTexture = RenderSystem.getDevice().createTexture(
                () -> "accident:charms_depth_before",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1);
        depthBeforeTextureView = RenderSystem.getDevice().createTextureView(depthBeforeTexture);

        depthAfterTexture = RenderSystem.getDevice().createTexture(
                () -> "accident:charms_depth_after",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1);
        depthAfterTextureView = RenderSystem.getDevice().createTextureView(depthAfterTexture);

        maskTexture = RenderSystem.getDevice().createTexture(
                () -> "accident:charms_mask",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1);
        maskTextureView = RenderSystem.getDevice().createTextureView(maskTexture);

        lastWidth  = width;
        lastHeight = height;
    }

    private void cleanupTextures() {
        if (sceneBeforeTextureView  != null) { sceneBeforeTextureView.close();  sceneBeforeTextureView  = null; }
        if (sceneBeforeTexture      != null) { sceneBeforeTexture.close();      sceneBeforeTexture      = null; }
        if (sceneAfterTextureView   != null) { sceneAfterTextureView.close();   sceneAfterTextureView   = null; }
        if (sceneAfterTexture       != null) { sceneAfterTexture.close();       sceneAfterTexture       = null; }
        if (depthBeforeTextureView  != null) { depthBeforeTextureView.close();  depthBeforeTextureView  = null; }
        if (depthBeforeTexture      != null) { depthBeforeTexture.close();      depthBeforeTexture      = null; }
        if (depthAfterTextureView   != null) { depthAfterTextureView.close();   depthAfterTextureView   = null; }
        if (depthAfterTexture       != null) { depthAfterTexture.close();       depthAfterTexture       = null; }
        if (maskTextureView         != null) { maskTextureView.close();         maskTextureView         = null; }
        if (maskTexture             != null) { maskTexture.close();             maskTexture             = null; }
    }

    // ── capture ────────────────────────────────────────────────────────────

    /** Call before the entity is rendered into the framebuffer. */
    public void captureSceneBefore() {
        if (!enabled) return;
        ensureInitialized();

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        ensureTextures(fb.textureWidth, fb.textureHeight);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorAttachment(), sceneBeforeTexture,
                0, 0, 0, 0, 0, lastWidth, lastHeight);
        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(fb.getDepthAttachment(), depthBeforeTexture,
                    0, 0, 0, 0, 0, lastWidth, lastHeight);
        }
        capturing = true;
    }

    /** Call after the entity (and its equipment) finished rendering. */
    public void captureSceneAfter() {
        if (!enabled || !capturing || hadMapThisFrame) {
            capturing = false;
            hadMapThisFrame = false;
            return;
        }

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorAttachment(), sceneAfterTexture,
                0, 0, 0, 0, 0, lastWidth, lastHeight);
        if (fb.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(fb.getDepthAttachment(), depthAfterTexture,
                    0, 0, 0, 0, 0, lastWidth, lastHeight);
        }
    }

    // ── composite ──────────────────────────────────────────────────────────

    /** Build mask, blur it for glow, then draw outline/fill/glow onto the framebuffer. */
    public void renderCharmsEffect() {
        if (!enabled || !capturing) return;

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) {
            capturing = false;
            return;
        }

        // 1. Depth diff → entity visibility mask
        maskDiff.createMask(
                maskTextureView,
                sceneBeforeTextureView, sceneAfterTextureView,
                depthBeforeTextureView, depthAfterTextureView,
                lastWidth, lastHeight
        );

        // 2. Blur the mask → glow falloff
        GpuTextureView blurredMask = kawaseBlur.blur(
                maskTexture, maskTextureView,
                lastWidth, lastHeight,
                glowIterations, glowRadius
        );
        if (blurredMask == null) { capturing = false; return; }

        // 3. Alpha-blend outline + fill + glow onto the live framebuffer
        charmsComposite.composite(
                fb.getColorAttachmentView(),
                maskTextureView,
                blurredMask,
                lastWidth, lastHeight,
                outlineColor, outlineThickness,
                fillColor,    fillAlpha,
                glowIntensity
        );

        if (fireEnabled) {
            // нужна свежая копия фреймбуфера после отрисовки контура, иначе огонь его затрёт
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
        float g = ((fireColor >>  8) & 0xFF) / 255.0f;
        float b = ( fireColor        & 0xFF) / 255.0f;
        if (a < 0.01f) a = 1.0f;

        GpuTextureView trailView = fireTrail.update(
                sceneAfterTextureView, maskTextureView, lastWidth, lastHeight, fireTime,
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
                lastWidth, lastHeight, fireTime, fireIntensity, r, g, b, a,
                FIRE_RADIUS, fireSpeed, fireLength, fireSoftness,
                fireBlur, fireSmoke, 1.0f
        );
    }

    // ── helpers ────────────────────────────────────────────────────────────

    public boolean isCapturing()    { return capturing; }
    public void markMapRendered()   { hadMapThisFrame = true; }
    public void resetMapFlag()      { hadMapThisFrame = false; }
    public void cancelCapture()     { capturing = false; }
    public void clearNeedsEffect()  { needsEffect = false; }
    public void markNeedsEffect()   { needsEffect = true; }

    public void invalidate() {
        cleanupTextures();
        if (kawaseBlur      != null) { kawaseBlur.close();      kawaseBlur      = null; }
        if (maskDiff        != null) { maskDiff.close();        maskDiff        = null; }
        if (charmsComposite != null) { charmsComposite.close(); charmsComposite = null; }
        if (fireTrail       != null) { fireTrail.close();       fireTrail       = null; }
        if (fireComposite   != null) { fireComposite.close();   fireComposite   = null; }
        lastWidth = 0; lastHeight = 0;
        initialized = false; capturing = false;
    }

    public void close() {
        cleanupTextures();
        if (kawaseBlur      != null) { kawaseBlur.close();      kawaseBlur      = null; }
        if (maskDiff        != null) { maskDiff.close();        maskDiff        = null; }
        if (charmsComposite != null) { charmsComposite.close(); charmsComposite = null; }
        if (fireTrail       != null) { fireTrail.close();       fireTrail       = null; }
        if (fireComposite   != null) { fireComposite.close();   fireComposite   = null; }
        lastWidth = 0; lastHeight = 0;
        initialized = false;
    }
}
