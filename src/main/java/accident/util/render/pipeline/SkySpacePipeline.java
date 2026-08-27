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
import java.util.OptionalInt;

public class SkySpacePipeline {

    private static final Identifier LOWRES_PIPELINE_ID = Identifier.of("accident", "pipeline/space_sky_lowres");
    private static final Identifier VERTEX_SHADER       = Identifier.of("accident", "core/space_sky");
    private static final Identifier FRAGMENT_SHADER     = Identifier.of("accident", "core/space_sky");

    private static final Identifier UPSCALE_PIPELINE_ID = Identifier.of("accident", "pipeline/space_sky_upscale");
    private static final Identifier UPSCALE_VERTEX      = Identifier.of("accident", "core/sky_upscale");
    private static final Identifier UPSCALE_FRAGMENT    = Identifier.of("accident", "core/sky_upscale");

    private static final BlendFunction SKY_BLEND = new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE,       DestFactor.ZERO
    );

    // low-res текстура каждый кадр новая (не персистентная), поэтому тут нужна
    // полная перезапись, а не блендинг с мусором/прошлым кадром - реальный
    // блендинг с миром происходит один раз, в upscale-проходе
    private static final BlendFunction REPLACE_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ZERO,
            SourceFactor.ONE, DestFactor.ZERO
    );

    // тормозит именно шум/звёзды в space_sky.fsh, не сам проход - поэтому рендерим
    // в четверть пикселей и апскейлим, небо низкочастотное и разницы не видно
    private static final int DOWNSCALE = 2;

    private static final RenderPipeline LOWRES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(LOWRES_PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline UPSCALE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(UPSCALE_PIPELINE_ID)
                    .withVertexShader(UPSCALE_VERTEX)
                    .withFragmentShader(UPSCALE_FRAGMENT)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("UpscaleData", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler0")
                    .withBlend(SKY_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET    = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX  = new Matrix4f();

    // 8 floats of settings (32 bytes), the inverse view-projection matrix (64),
    // then the sky colour as a vec4 (16).
    private static final int BUFFER_SIZE = 112;

    private GpuBuffer uniformBuffer;
    private GpuBuffer upscaleUniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private ByteBuffer upscaleDataBuffer;
    private boolean initialized = false;

    private GpuTexture lowResTexture;
    private GpuTextureView lowResTextureView;
    private int lowResWidth = 0;
    private int lowResHeight = 0;
    private int lastWidth = 0;
    private int lastHeight = 0;

    public SkySpacePipeline() {}

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);
        this.upscaleDataBuffer = MemoryUtil.memAlloc(16);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:space_sky_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    private void ensureLowResTarget(int width, int height) {
        if (width == lastWidth && height == lastHeight && lowResTextureView != null) return;

        if (lowResTextureView != null) { lowResTextureView.close(); lowResTextureView = null; }
        if (lowResTexture != null)     { lowResTexture.close();     lowResTexture     = null; }

        lowResWidth  = Math.max(1, width  / DOWNSCALE);
        lowResHeight = Math.max(1, height / DOWNSCALE);

        lowResTexture = RenderSystem.getDevice().createTexture(
                () -> "accident:space_sky_lowres",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                lowResWidth, lowResHeight, 1, 1
        );
        lowResTextureView = RenderSystem.getDevice().createTextureView(lowResTexture);

        lastWidth  = width;
        lastHeight = height;
    }

    public void render(GpuTextureView targetView,
                       int width, int height,
                       float time, float sunAngle,
                       float fogDensity, float starBrightness,
                       float opacity,
                       Matrix4f invViewProj,
                       float skyRed, float skyGreen, float skyBlue,
                       float style) {

        ensureInitialized();
        ensureLowResTarget(width, height);

        prepareUniformData(time, sunAngle, fogDensity, starBrightness, lowResWidth, lowResHeight, opacity, invViewProj,
                skyRed, skyGreen, skyBlue, style);

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "accident:space_sky_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        // Pass 1: the actual sky math, at a quarter of the screen's pixel count.
        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "accident:space_sky_lowres_pass",
                lowResTextureView,
                OptionalInt.empty())) {

            renderPass.setPipeline(LOWRES_PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("SkyData", uniformBuffer);

            renderPass.draw(0, 6);
        }

        // Pass 2: a single texture sample per screen pixel, blended over the
        // world exactly like the old single-pass version did.
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        if (upscaleUniformBuffer == null) {
            upscaleUniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "accident:space_sky_upscale_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    16
            );
        }
        upscaleDataBuffer.clear();
        upscaleDataBuffer.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
        upscaleDataBuffer.flip();
        encoder.writeToBuffer(upscaleUniformBuffer.slice(), upscaleDataBuffer);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "accident:space_sky_upscale_pass",
                targetView,
                OptionalInt.empty())) {

            renderPass.setPipeline(UPSCALE_PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("Sampler0", lowResTextureView, sampler);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("UpscaleData", upscaleUniformBuffer);

            renderPass.draw(0, 6);
        }
    }

    private void prepareUniformData(float time, float sunAngle, float fogDensity,
                                    float starBrightness, int width, int height, float opacity,
                                    Matrix4f invViewProj,
                                    float skyRed, float skyGreen, float skyBlue,
                                    float style) {
        dataBuffer.clear();
        dataBuffer.putFloat(time);
        dataBuffer.putFloat(sunAngle);
        dataBuffer.putFloat(fogDensity);
        dataBuffer.putFloat(starBrightness);
        dataBuffer.putFloat((float) width);
        dataBuffer.putFloat((float) height);
        dataBuffer.putFloat(opacity);
        dataBuffer.putFloat(style);

        // Column-major, which is what std140 expects and what JOML stores.
        invViewProj.get(dataBuffer);
        dataBuffer.position(dataBuffer.position() + 64);

        dataBuffer.putFloat(skyRed);
        dataBuffer.putFloat(skyGreen);
        dataBuffer.putFloat(skyBlue);
        dataBuffer.putFloat(1.0f);
        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer != null)        { uniformBuffer.close();             uniformBuffer        = null; }
        if (upscaleUniformBuffer != null) { upscaleUniformBuffer.close();      upscaleUniformBuffer = null; }
        if (dummyVertexBuffer != null)    { dummyVertexBuffer.close();         dummyVertexBuffer    = null; }
        if (dataBuffer != null)           { MemoryUtil.memFree(dataBuffer);    dataBuffer           = null; }
        if (upscaleDataBuffer != null)    { MemoryUtil.memFree(upscaleDataBuffer); upscaleDataBuffer = null; }
        if (lowResTextureView != null)    { lowResTextureView.close();         lowResTextureView    = null; }
        if (lowResTexture != null)        { lowResTexture.close();             lowResTexture        = null; }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
    }
}