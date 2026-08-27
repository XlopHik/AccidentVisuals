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
import com.mojang.blaze3d.textures.GpuTextureView;
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

public class CharmsCompositePipeline {

    private static final Identifier PIPELINE_ID     = Identifier.of("accident", "pipeline/charms_composite");
    private static final Identifier VERTEX_SHADER   = Identifier.of("accident", "core/charms_composite");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/charms_composite");

    // Alpha blend: draws outline/fill/glow on top of whatever is already in the framebuffer.
    // Transparent pixels (alpha=0) are a no-op, so non-effect areas are untouched.
    private static final BlendFunction ALPHA_BLEND = new BlendFunction(
            SourceFactor.SRC_ALPHA,         DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE,               DestFactor.ONE_MINUS_SRC_ALPHA
    );

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("CharmsData", UniformType.UNIFORM_BUFFER)
                    .withSampler("MaskSampler")
                    .withSampler("BlurredMaskSampler")
                    .withBlend(ALPHA_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET    = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX  = new Matrix4f();

    // 4 vec4 × 16 bytes = 64 bytes
    private static final int BUFFER_SIZE = 64;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:charms_composite_dummy_vertex",
                GpuBuffer.USAGE_VERTEX, dummyData
        );
        MemoryUtil.memFree(dummyData);

        this.uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:charms_composite_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                BUFFER_SIZE
        );

        initialized = true;
    }

    public void composite(GpuTextureView targetView,
                          GpuTextureView maskView,
                          GpuTextureView blurredMaskView,
                          int width, int height,
                          int outlineColor, float outlineThickness,
                          int fillColor,    float fillAlpha,
                          float glowIntensity) {
        ensureInitialized();

        prepareUniformData(width, height, outlineColor, outlineThickness,
                fillColor, fillAlpha, glowIntensity);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler linearSampler  = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        GpuSampler nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

        // OptionalInt.empty() = no clear, blend ON TOP of existing framebuffer content
        try (RenderPass pass = encoder.createRenderPass(
                () -> "accident:charms_composite_pass",
                targetView,
                OptionalInt.empty())) {

            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("MaskSampler",        maskView,        nearestSampler);
            pass.bindTexture("BlurredMaskSampler", blurredMaskView, linearSampler);

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("CharmsData",        uniformBuffer);

            pass.draw(0, 6);
        }
    }

    private void prepareUniformData(int width, int height,
                                    int outlineColor, float outlineThickness,
                                    int fillColor,    float fillAlpha,
                                    float glowIntensity) {
        dataBuffer.clear();

        // vec4 resolution
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(outlineThickness);
        dataBuffer.putFloat(0);

        // vec4 outlineColor (ARGB → float r,g,b,a)
        dataBuffer.putFloat(((outlineColor >> 16) & 0xFF) / 255f);
        dataBuffer.putFloat(((outlineColor >>  8) & 0xFF) / 255f);
        dataBuffer.putFloat(( outlineColor        & 0xFF) / 255f);
        dataBuffer.putFloat(((outlineColor >> 24) & 0xFF) / 255f);

        // vec4 fillColor (ARGB → float r,g,b,a)
        dataBuffer.putFloat(((fillColor >> 16) & 0xFF) / 255f);
        dataBuffer.putFloat(((fillColor >>  8) & 0xFF) / 255f);
        dataBuffer.putFloat(( fillColor        & 0xFF) / 255f);
        dataBuffer.putFloat(((fillColor >> 24) & 0xFF) / 255f);

        // vec4 settings
        dataBuffer.putFloat(fillAlpha);
        dataBuffer.putFloat(glowIntensity);
        dataBuffer.putFloat(0);
        dataBuffer.putFloat(0);

        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer    != null) { uniformBuffer.close();    uniformBuffer    = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (dataBuffer       != null) { MemoryUtil.memFree(dataBuffer); dataBuffer = null; }
        initialized = false;
    }
}
