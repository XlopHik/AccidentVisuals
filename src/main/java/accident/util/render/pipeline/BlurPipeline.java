package accident.util.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class BlurPipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("accident", "pipeline/blur");
    private static final Identifier VERTEX_SHADER = Identifier.of("accident", "core/blur");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/blur");

    private static final Identifier PIPELINE_H_ID = Identifier.of("accident", "pipeline/blur_h");
    private static final Identifier FRAGMENT_SHADER_H = Identifier.of("accident", "core/blur_h");

    private static final float FIXED_GUI_SCALE = 2.0f;

    // горизонтальный проход пишет цвет в scratch-текстуру напрямую (replace, не blend) - под ним ничего нет
    private static final BlendFunction REPLACE_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ZERO,
            SourceFactor.ONE, DestFactor.ZERO
    );

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline PIPELINE_H = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_H_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER_H)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int BUFFER_SIZE = 128;

    private GpuBuffer uniformBuffer;
    private GpuBuffer uniformBufferH;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private ByteBuffer dataBufferH;

    // Scratch target for the horizontal half of the blur.
    private GpuTexture stageTexture;
    private GpuTextureView stageTextureView;

    private int lastWidth = 0;
    private int lastHeight = 0;

    /** No-op: every blur copies the framebuffer, as it always has. */
    public void invalidateSnapshot() {
    }

    private boolean initialized = false;

    private int getFixedScaledWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return 960;
        return (int) Math.ceil((double) client.getWindow().getFramebufferWidth() / FIXED_GUI_SCALE);
    }

    private int getFixedScaledHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return 540;
        return (int) Math.ceil((double) client.getWindow().getFramebufferHeight() / FIXED_GUI_SCALE);
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);
        this.dataBufferH = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:blur_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    private void ensureCopyTexture(int width, int height) {
        if (stageTexture == null || lastWidth != width || lastHeight != height) {
            if (stageTextureView != null) {
                stageTextureView.close();
                stageTextureView = null;
            }
            if (stageTexture != null) {
                stageTexture.close();
                stageTexture = null;
            }

            stageTexture = RenderSystem.getDevice().createTexture(
                    () -> "accident:blur_stage",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8,
                    width, height, 1, 1
            );
            stageTextureView = RenderSystem.getDevice().createTextureView(stageTexture);

            lastWidth = width;
            lastHeight = height;
        }
    }

    public void drawBlur(float x, float y, float width, float height,
                         float radius, float[] radii, int color) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;
        if (client.getFramebuffer().getColorAttachment() == null) return;

        ensureInitialized();

        int fbWidth = client.getFramebuffer().textureWidth;
        int fbHeight = client.getFramebuffer().textureHeight;

        ensureCopyTexture(fbWidth, fbHeight);

        int fixedScreenWidth = getFixedScaledWidth();
        int fixedScreenHeight = getFixedScaledHeight();

        prepareUniformData(dataBuffer, x, y, width, height,
                fixedScreenWidth,
                fixedScreenHeight,
                fbWidth, fbHeight,
                FIXED_GUI_SCALE, radius, radii, color);

        // вертикальный проход читает `samples` текселей выше/ниже панели - горизонтальный должен покрыть этот запас
        int samples = Math.max(1, Math.min(10, (int) Math.ceil(radius)));
        float marginY = (samples + 1) / FIXED_GUI_SCALE;

        prepareUniformData(dataBufferH, x, y - marginY, width, height + marginY * 2f,
                fixedScreenWidth,
                fixedScreenHeight,
                fbWidth, fbHeight,
                FIXED_GUI_SCALE, radius, radii, color);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // копии фреймбуфера больше нет - раньше она нужна была потому что старый однопроходный блюр писал
        // в фреймбуфер и одновременно сэмплил его, чего гпу не разрешает
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);
        encoder.writeToBuffer(uniformBufferH.slice(), dataBufferH);

        GpuBufferSlice dynamicTransforms = accident.util.render.Gui2DUniforms.slice();

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        RectPipeline.flushPending();
        long accidentT0 = accident.util.render.DrawTimes.start();

        // Pass one: blur along X out of the framebuffer copy into the scratch.
        try (RenderPass horizontal = encoder.createRenderPass(
                () -> "accident:blur_pass_h",
                stageTextureView,
                OptionalInt.empty())) {

            horizontal.setPipeline(PIPELINE_H);
            horizontal.setVertexBuffer(0, dummyVertexBuffer);
            horizontal.bindTexture("Sampler0",
                    client.getFramebuffer().getColorAttachmentView(), sampler);

            RenderSystem.bindDefaultUniforms(horizontal);
            horizontal.setUniform("DynamicTransforms", dynamicTransforms);
            horizontal.setUniform("BlurData", uniformBufferH);

            horizontal.draw(0, 6);
        }

        // Pass two: blur along Y out of the scratch, then mask and tint it.
        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "minecraft:blur_pass",
                // только назначение - реальный фреймбуфер как источник и нужен, его же и блюрим
                accident.util.render.GuiCaptureTarget.colorView(client),
                OptionalInt.empty(),
                accident.util.render.GuiCaptureTarget.depthView(client),
                OptionalDouble.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("Sampler0", stageTextureView, sampler);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("BlurData", uniformBuffer);

            renderPass.draw(0, 6);
        }
        accident.util.render.DrawTimes.blur(accidentT0);
    }

    private void prepareUniformData(ByteBuffer target,
                                    float x, float y, float width, float height,
                                    float screenWidth, float screenHeight,
                                    int fbWidth, int fbHeight,
                                    float guiScale, float blurRadius,
                                    float[] radii, int color) {
        target.clear();

        target.putFloat(x);
        target.putFloat(y);
        target.putFloat(width);
        target.putFloat(height);

        target.putFloat(screenWidth);
        target.putFloat(screenHeight);
        target.putFloat(guiScale);
        target.putFloat(blurRadius);

        // гауссовы веса зависят только от радиуса - считаем один раз тут, а не на каждый пиксель в шейдере
        int samples = Math.max(1, Math.min(10, (int) Math.ceil(blurRadius)));
        float sigma = Math.max(blurRadius * 0.5f, 0.1f);
        float twoSigma2 = 2f * sigma * sigma;

        float[] offsets = new float[6];
        float[] weights = new float[6];
        int pairs = 0;
        float total = 1f;

        for (int k = 1; k <= samples; k += 2) {
            float o1 = k;
            float o2 = k + 1;
            float w1 = (float) Math.exp(-(o1 * o1) / twoSigma2);
            float w2 = (k + 1 <= samples) ? (float) Math.exp(-(o2 * o2) / twoSigma2) : 0f;

            float pairWeight = w1 + w2;
            offsets[pairs] = (o1 * w1 + o2 * w2) / pairWeight;
            weights[pairs] = pairWeight;
            total += 2f * pairWeight;
            pairs++;
        }

        target.putFloat(fbWidth);
        target.putFloat(fbHeight);
        target.putFloat(pairs);
        target.putFloat(1f / total);

        target.putFloat(radii[0]);
        target.putFloat(radii[1]);
        target.putFloat(radii[2]);
        target.putFloat(radii[3]);

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        target.putFloat(r);
        target.putFloat(g);
        target.putFloat(b);
        target.putFloat(a);

        // Three vec4s carrying up to six (offset, weight) pairs.
        for (int i = 0; i < 6; i++) {
            target.putFloat(offsets[i]);
            target.putFloat(weights[i]);
        }

        target.flip();

        // два прохода - два отдельных буфера, оба пишутся до отрисовки, общий буфер перезаписал бы первый проход
        int size = target.remaining();
        if (target == dataBuffer) {
            if (uniformBuffer == null || uniformBuffer.size() < size) {
                if (uniformBuffer != null) uniformBuffer.close();
                uniformBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "minecraft:blur_uniform",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        size
                );
            }
        } else {
            if (uniformBufferH == null || uniformBufferH.size() < size) {
                if (uniformBufferH != null) uniformBufferH.close();
                uniformBufferH = RenderSystem.getDevice().createBuffer(
                        () -> "accident:blur_uniform_h",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        size
                );
            }
        }
    }

    public void close() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (uniformBufferH != null) {
            uniformBufferH.close();
            uniformBufferH = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        if (dataBufferH != null) {
            MemoryUtil.memFree(dataBufferH);
            dataBufferH = null;
        }
        if (stageTextureView != null) {
            stageTextureView.close();
            stageTextureView = null;
        }
        if (stageTexture != null) {
            stageTexture.close();
            stageTexture = null;
        }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
    }
}