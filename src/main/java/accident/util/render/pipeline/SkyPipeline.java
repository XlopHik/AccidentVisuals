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

public class SkyPipeline {

    private static final Identifier PIPELINE_ID     = Identifier.of("accident", "pipeline/blood_sky");
    private static final Identifier VERTEX_SHADER   = Identifier.of("accident", "core/blood_sky");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/blood_sky");

    private static final BlendFunction SKY_BLEND = new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE,       DestFactor.ZERO
    );

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withSampler("DepthSampler")
                    .withBlend(SKY_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET    = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX  = new Matrix4f();

    private static final int BUFFER_SIZE = 32;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    public SkyPipeline() {}

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:blood_sky_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    public void render(GpuTextureView targetView,
                       GpuTextureView sceneView,
                       GpuTextureView depthView,
                       int width, int height,
                       float time, float sunAngle,
                       float fogDensity, float starBrightness,
                       float opacity) {

        ensureInitialized();

        prepareUniformData(time, sunAngle, fogDensity, starBrightness, width, height, opacity);

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "accident:blood_sky_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler linearSampler  = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        GpuSampler nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "accident:blood_sky_pass",
                targetView,
                OptionalInt.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("SceneSampler", sceneView, linearSampler);
            renderPass.bindTexture("DepthSampler", depthView, nearestSampler);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("SkyData", uniformBuffer);

            renderPass.draw(0, 6);
        }
    }

    private void prepareUniformData(float time, float sunAngle, float fogDensity,
                                    float starBrightness, int width, int height, float opacity) {
        dataBuffer.clear();
        dataBuffer.putFloat(time);
        dataBuffer.putFloat(sunAngle);
        dataBuffer.putFloat(fogDensity);
        dataBuffer.putFloat(starBrightness);
        dataBuffer.putFloat((float) width);
        dataBuffer.putFloat((float) height);
        dataBuffer.putFloat(opacity);
        dataBuffer.putFloat(0.0f);
        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer != null)     { uniformBuffer.close();          uniformBuffer     = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close();      dummyVertexBuffer = null; }
        if (dataBuffer != null)        { MemoryUtil.memFree(dataBuffer); dataBuffer        = null; }
        initialized = false;
    }
}