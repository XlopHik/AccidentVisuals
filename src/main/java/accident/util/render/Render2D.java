package accident.util.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import accident.Initialization;
import accident.util.ColorUtil;
import accident.util.render.pipeline.Arc2D;
import accident.util.render.pipeline.ArcOutline2D;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public class Render2D {

    @Getter
    private static boolean inOverlayMode = false;
    private static boolean savedDepthTest = false;
    private static boolean savedDepthMask = false;
    private static boolean savedBlend = false;

    private static final List<Runnable> OVERRIDE_TASKS = new ArrayList<>();
    private static final float Z_OVERRIDE = 0.0f;
    private static final float FIXED_GUI_SCALE = 2.0f;

    public static int getFixedScaledWidth() {
        var window = MinecraftClient.getInstance().getWindow();
        return (int) Math.ceil((double) window.getFramebufferWidth() / FIXED_GUI_SCALE);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        rect(x, y, width, height, color, radius);
    }

    public static int getFixedScaledHeight() {
        var window = MinecraftClient.getInstance().getWindow();
        return (int) Math.ceil((double) window.getFramebufferHeight() / FIXED_GUI_SCALE);
    }


    public static void drawBlurredRoundedRectangle(float x, float y, float width, float height,
                                                   float radius, int color, float alpha) {
        int baseAlpha = (color >> 24) & 0xFF;
        int combinedAlpha = clamp((int)(baseAlpha * alpha));
        int tintColor = (combinedAlpha << 24) | (color & 0x00FFFFFF);

        blur(x, y, width, height, 8f, radius, tintColor);
    }

    public static void drawBlurredRoundedRectangle(float x, float y, float width, float height,
                                                   org.joml.Vector4f radius, int color, float alpha) {
        int baseAlpha = (color >> 24) & 0xFF;
        int combinedAlpha = clamp((int)(baseAlpha * alpha));
        int tintColor = (combinedAlpha << 24) | (color & 0x00FFFFFF);

        blur(x, y, width, height, 8f,
                radius.x, radius.y, radius.z, radius.w,
                tintColor);
    }

    public static void drawBlurredRoundedRectangle(float x, float y, float width, float height,
                                                   float[] radii, int color, float alpha) {
        int baseAlpha = (color >> 24) & 0xFF;
        int combinedAlpha = clamp((int)(baseAlpha * alpha));
        int tintColor = (combinedAlpha << 24) | (color & 0x00FFFFFF);

        blur(x, y, width, height, 8f,
                radii[0], radii[1], radii[2], radii[3],
                tintColor);
    }

    public static void drawBlurredRoundedRectangle(float x, float y, float width, float height,
                                                   float radius, int color) {
        blur(x, y, width, height, 8f, radius, color);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static float getFixedGuiScale() {
        return FIXED_GUI_SCALE;
    }

    public static float getScaleMultiplier() {
        MinecraftClient client = MinecraftClient.getInstance();
        float currentScale = (float) client.getWindow().getScaleFactor();
        return FIXED_GUI_SCALE / currentScale;
    }


    public static void beginOverlay() {
        // One allocation per frame is shared by every 2D pass below.
        accident.util.render.Gui2DUniforms.invalidate();

        inOverlayMode = true;

        savedDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void endOverlay() {
        // в очереди ничего не должно оставаться после конца оверлея, иначе отрисуется не туда или пропадёт
        accident.util.render.pipeline.RectPipeline.flushPending();

        if (savedDepthMask) {
            GL11.glDepthMask(true);
        }
        if (savedDepthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        if (!savedBlend) {
            GL11.glDisable(GL11.GL_BLEND);
        }

        inOverlayMode = false;
    }

    public static int interpolateColor(int color1, int color2, float fraction) {
        float invFraction = 1.0f - fraction;
        int a = (int) (((color1 >> 24) & 0xFF) * invFraction + ((color2 >> 24) & 0xFF) * fraction);
        int r = (int) (((color1 >> 16) & 0xFF) * invFraction + ((color2 >> 16) & 0xFF) * fraction);
        int g = (int) (((color1 >> 8) & 0xFF) * invFraction + ((color2 >> 8) & 0xFF) * fraction);
        int b = (int) ((color1 & 0xFF) * invFraction + (color2 & 0xFF) * fraction);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }


    public static void clearDepth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() != null) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
    }

    public static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public static void disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    public static void depthMask(boolean mask) {
        GL11.glDepthMask(mask);
    }


    private static ShaderProgram blurShader = null;
    private static boolean shaderLoaded = false;

    public static void drawKawaseBlur(float x, float y, float width, float height, int iterations, float offset) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null || client.getFramebuffer().getColorAttachment() == null) return;

        try {
            GpuTextureView blurred = Initialization.getInstance().getManager().getRenderCore().getKawaseBlurPipeline()
                    .blur(
                            client.getFramebuffer().getColorAttachment(),
                            client.getFramebuffer().getColorAttachmentView(),
                            (int) width,
                            (int) height,
                            iterations,
                            offset
                    );

            if (blurred == null) {
                return;
            }

            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().translate(x, y, 0);

            Matrix4f modelMatrix = RenderSystem.getModelViewMatrix();

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            try (RenderPass renderPass = encoder.createRenderPass(
                    () -> "kawase_blur_render_pass",
                    client.getFramebuffer().getColorAttachmentView(),
                    OptionalInt.empty())) {

                RenderSystem.ShapeIndexBuffer shapeBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
                GpuBuffer indexBuffer = shapeBuffer.getIndexBuffer(6);

                renderPass.setVertexBuffer(0, indexBuffer);

            }

            RenderSystem.getModelViewStack().popMatrix();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void rect(float x, float y, float width, float height, int color) {
        int[] colors = ColorUtil.solid(color);
        float[] radii = {0, 0, 0, 0};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors, radii);
    }

    public static void rect(float x, float y, float width, float height, int color,
                            float radius) {
        int[] colors = ColorUtil.solid(color);
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors, radii);
    }

    public static void rect(float x, float y, float width, float height, int color,
                            float topLeft, float topRight, float bottomRight, float bottomLeft) {
        int[] colors = ColorUtil.solid(color);
        float[] radii = {topLeft, topRight, bottomRight, bottomLeft};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors, radii);
    }

    public static void gradientRect(float x, float y, float width, float height,
                                    int[] colors, float radius) {
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors, radii);
    }

    public static void gradientRect(float x, float y, float width, float height,
                                    int[] colors, float topLeft, float topRight,
                                    float bottomRight, float bottomLeft) {
        float[] radii = {topLeft, topRight, bottomRight, bottomLeft};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors, radii);
    }

    public static void gradientRect9(float x, float y, float width, float height,
                                     int topLeft, int topCenter, int topRight,
                                     int leftCenter, int center, int rightCenter,
                                     int bottomLeft, int bottomCenter, int bottomRight,
                                     float radius) {
        int[] colors = {topLeft, topCenter, topRight, leftCenter, center, rightCenter, bottomLeft, bottomCenter, bottomRight};
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors, radii);
    }

    public static void gradientRect9(float x, float y, float width, float height,
                                     int[] colors9, float radius) {
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors9, radii);
    }

    public static void gradientRect9(float x, float y, float width, float height,
                                     int[] colors9, float topLeft, float topRight,
                                     float bottomRight, float bottomLeft) {
        float[] radii = {topLeft, topRight, bottomRight, bottomLeft};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors9, radii);
    }

    public static void gradientRect9(float x, float y, float width, float height,
                                     int topLeft, int topCenter, int topRight,
                                     int leftCenter, int center, int rightCenter,
                                     int bottomLeft, int bottomCenter, int bottomRight,
                                     float radius, float innerBlur) {
        int[] colors = {topLeft, topCenter, topRight, leftCenter, center, rightCenter, bottomLeft, bottomCenter, bottomRight};
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors, radii, innerBlur);
    }

    public static void gradientRect9(float x, float y, float width, float height,
                                     int[] colors9, float radius, float innerBlur) {
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getRectPipeline()
                .drawRect(x, y, width, height, colors9, radii, innerBlur);
    }

    public static void outline(float x, float y, float width, float height, float thickness, int color) {
        int[] colors = ColorUtil.solid8(color);
        float[] thicknesses = {thickness, thickness, thickness, thickness, thickness, thickness, thickness, thickness};
        float[] radii = {0, 0, 0, 0};
        Initialization.getInstance().getManager().getRenderCore().getOutlinePipeline()
                .drawOutline(x, y, width, height, colors, thicknesses, radii, 1.0f);
    }

    public static void outline(float x, float y, float width, float height, float thickness, int color, float radius) {
        int[] colors = ColorUtil.solid8(color);
        float[] thicknesses = {thickness, thickness, thickness, thickness, thickness, thickness, thickness, thickness};
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getOutlinePipeline()
                .drawOutline(x, y, width, height, colors, thicknesses, radii, 1.0f);
    }

    public static void outline(float x, float y, float width, float height, float thickness, int color,
                               float topLeft, float topRight, float bottomRight, float bottomLeft) {
        int[] colors = ColorUtil.solid8(color);
        float[] thicknesses = {thickness, thickness, thickness, thickness, thickness, thickness, thickness, thickness};
        float[] radii = {topLeft, topRight, bottomRight, bottomLeft};
        Initialization.getInstance().getManager().getRenderCore().getOutlinePipeline()
                .drawOutline(x, y, width, height, colors, thicknesses, radii, 1.0f);
    }

    public static void gradientOutline(float x, float y, float width, float height, float thickness,
                                       int[] colors, float radius) {
        float[] thicknesses = {thickness, thickness, thickness, thickness, thickness, thickness, thickness, thickness};
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getOutlinePipeline()
                .drawOutline(x, y, width, height, colors, thicknesses, radii, 1.0f);
    }

    public static void blur(float x, float y, float width, float height, float blurRadius, int tintColor) {
        float[] radii = {0, 0, 0, 0};
        Initialization.getInstance().getManager().getRenderCore().getBlurPipeline()
                .drawBlur(x, y, width, height, blurRadius, radii, tintColor);
    }

    public static void blur(float x, float y, float width, float height, float blurRadius, float cornerRadius, int tintColor) {
        float[] radii = {cornerRadius, cornerRadius, cornerRadius, cornerRadius};
        Initialization.getInstance().getManager().getRenderCore().getBlurPipeline()
                .drawBlur(x, y, width, height, blurRadius, radii, tintColor);
    }

    public static void blur(float x, float y, float width, float height, float blurRadius,
                            float topLeft, float topRight, float bottomRight, float bottomLeft, int tintColor) {
        float[] radii = {topLeft, topRight, bottomRight, bottomLeft};
        Initialization.getInstance().getManager().getRenderCore().getBlurPipeline()
                .drawBlur(x, y, width, height, blurRadius, radii, tintColor);
    }

    public static void texture(Identifier id, float x, float y, float width, float height, int color) {
        texture(id, x, y, width, height, 0, 0, 1, 1, color, 1f, 0f);
    }

    public static void texture(Identifier id, float x, float y, float width, float height, float smoothness, int color) {
        texture(id, x, y, width, height, 0, 0, 1, 1, color, smoothness, 0f);
    }

    public static void texture(Identifier id, float x, float y, float width, float height, float smoothness, float radius, int color) {
        texture(id, x, y, width, height, 0, 0, 1, 1, color, smoothness, radius);
    }

    public static void texture(Identifier id, float x, float y, float width, float height,
                               float u0, float v0, float u1, float v1, int color) {
        texture(id, x, y, width, height, u0, v0, u1, v1, color, 1f, 0f);
    }

    public static void texture(Identifier id, float x, float y, float width, float height,
                               float u0, float v0, float u1, float v1, int color, float radius) {
        texture(id, x, y, width, height, u0, v0, u1, v1, color, 1f, radius);
    }

    public static void texture(Identifier id, float x, float y, float width, float height,
                               float u0, float v0, float u1, float v1, int color, float smoothness, float radius) {
        int[] colors = {color, color, color, color};
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getTexturePipeline()
                .drawTexture(id, x, y, width, height, u0, v0, u1, v1, colors, radii, smoothness);
    }

    public static void drawTexture(DrawContext context, Identifier id,
                                   float x, float y, float width, float height,
                                   float u, float v, float regionWidth, float regionHeight,
                                   float textureWidth, float textureHeight,
                                   int color) {
        float u0 = u / textureWidth;
        float v0 = v / textureHeight;
        float u1 = (u + regionWidth) / textureWidth;
        float v1 = (v + regionHeight) / textureHeight;

        texture(id, x, y, width, height, u0, v0, u1, v1, color, 1f, 0f);
    }

    public static void drawTexture(DrawContext context, Identifier id,
                                   float x, float y, float width, float height,
                                   float u, float v, float regionWidth, float regionHeight,
                                   float textureWidth, float textureHeight,
                                   int color, float radius) {
        float u0 = u / textureWidth;
        float v0 = v / textureHeight;
        float u1 = (u + regionWidth) / textureWidth;
        float v1 = (v + regionHeight) / textureHeight;

        texture(id, x, y, width, height, u0, v0, u1, v1, color, 1f, radius);
    }

    public static void drawSprite(Sprite sprite, float x, float y, float width, float height, int color) {
        drawSprite(sprite, x, y, width, height, color, true);
    }

    public static void drawSprite(Sprite sprite, float x, float y, float width, float height, int color, boolean pixelPerfect) {
        if (sprite == null || width == 0 || height == 0) return;

        float smoothness = pixelPerfect ? 1f : 0f;
        texture(sprite.getAtlasId(), x, y, width, height,
                sprite.getMinU(), sprite.getMinV(),
                sprite.getMaxU(), sprite.getMaxV(),
                color, smoothness, 0f);
    }

    public static void drawSpriteSmooth(Sprite sprite, float x, float y, float width, float height, int color) {
        drawSprite(sprite, x, y, width, height, color, false);
    }

    public static void drawFramebufferTexture(int textureId, float x, float y, float width, float height,
                                              float r, float g, float b, float a) {
        int color = ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        int[] colors = {color, color, color, color};
        float[] radii = {0, 0, 0, 0};

        Initialization.getInstance().getManager().getRenderCore().getTexturePipeline()
                .drawFramebufferTexture(textureId, x, y, width, height, colors, radii, a);
    }

    public static void glowOutline(float x, float y, float width, float height, float thickness,
                                   int color, float radius, float progress, float baseAlpha) {
        float[] radii = {radius, radius, radius, radius};
        Initialization.getInstance().getManager().getRenderCore().getGlowOutlinePipeline()
                .drawGlowOutline(x, y, width, height, color, thickness, radii, progress, baseAlpha);
    }

    public static void glowOutline(float x, float y, float width, float height, float thickness,
                                   int color, float topLeft, float topRight, float bottomRight, float bottomLeft,
                                   float progress, float baseAlpha) {
        float[] radii = {topLeft, topRight, bottomRight, bottomLeft};
        Initialization.getInstance().getManager().getRenderCore().getGlowOutlinePipeline()
                .drawGlowOutline(x, y, width, height, color, thickness, radii, progress, baseAlpha);
    }

    public static Matrix4f createProjection() {
        int width = getFixedScaledWidth();
        int height = getFixedScaledHeight();
        return new Matrix4f().ortho(0, width, height, 0, -1000, 1000);
    }

    public static void arc(DrawContext context, float x, float y, float size, float thickness, float degree,
                           float rotation, int color, boolean overrideContext) {
        arc(createProjection(), x, y, size, thickness, degree, rotation, color, overrideContext);
    }

    public static void arc(DrawContext context, float x, float y, float size, float thickness, float degree,
                           float rotation, boolean overrideContext, int... colors) {
        arc(createProjection(), x, y, size, thickness, degree, rotation, overrideContext, colors);
    }

    public static void arc(Matrix4f matrix, float x, float y, float size, float thickness, float degree, float rotation,
                           int color, boolean overrideContext) {
        if (overrideContext) {
            OVERRIDE_TASKS.add(() -> Arc2D.draw(matrix, x, y, size, thickness, degree, rotation, Z_OVERRIDE, color));
            return;
        }
        Arc2D.draw(matrix, x, y, size, thickness, degree, rotation, Z_OVERRIDE, color);
    }

    public static void arc(Matrix4f matrix, float x, float y, float size, float thickness, float degree, float rotation,
                           boolean overrideContext, int... colors) {
        if (overrideContext) {
            OVERRIDE_TASKS.add(() -> Arc2D.draw(matrix, x, y, size, thickness, degree, rotation, Z_OVERRIDE, colors));
            return;
        }
        Arc2D.draw(matrix, x, y, size, thickness, degree, rotation, Z_OVERRIDE, colors);
    }

    public static void arc(float x, float y, float size, float thickness, float degree, float rotation, int color) {
        Arc2D.draw(createProjection(), x, y, size, thickness, degree, rotation, Z_OVERRIDE, color);
    }

    public static void arc(float x, float y, float size, float thickness, float degree, float rotation, int... colors) {
        Arc2D.draw(createProjection(), x, y, size, thickness, degree, rotation, Z_OVERRIDE, colors);
    }

    public static void arcOutline(float x, float y, float size, float arcThickness, float degree,
                                  float rotation, float outlineThickness, int fillColor, int outlineColor) {
        ArcOutline2D.draw(createProjection(), x, y, size, arcThickness, degree, rotation, outlineThickness, fillColor, outlineColor, Z_OVERRIDE);
    }


    public static void arcOutline(DrawContext context, float x, float y, float size, float arcThickness, float degree,
                                  float rotation, float outlineThickness, int fillColor, int outlineColor, boolean overrideContext) {
        Matrix4f matrix = createProjection();
        if (overrideContext) {
            OVERRIDE_TASKS.add(() -> ArcOutline2D.draw(matrix, x, y, size, arcThickness, degree, rotation, outlineThickness, fillColor, outlineColor, Z_OVERRIDE));
            return;
        }
        ArcOutline2D.draw(matrix, x, y, size, arcThickness, degree, rotation, outlineThickness, fillColor, outlineColor, Z_OVERRIDE);
    }

    public static void arcOutline(Matrix4f matrix, float x, float y, float size, float arcThickness, float degree,
                                  float rotation, float outlineThickness, int fillColor, int outlineColor) {
        ArcOutline2D.draw(matrix, x, y, size, arcThickness, degree, rotation, outlineThickness, fillColor, outlineColor, Z_OVERRIDE);
    }

    public static void flushOverrideTasks() {
        for (Runnable task : OVERRIDE_TASKS) {
            task.run();
        }
        OVERRIDE_TASKS.clear();
    }

    public static void cleanup() {
        OVERRIDE_TASKS.clear();
        Arc2D.shutdown();
        ArcOutline2D.shutdown();
    }
}