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

/**
 * Composites the (already Kawase-blurred) hand trail onto the scene with
 * bloom along the hand's silhouette edge.
 */
public class HandFirePipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("accident", "pipeline/hand_fire");
    private static final Identifier VERTEX_SHADER = Identifier.of("accident", "core/hand_fire");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/hand_fire");

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
                    .withUniform("HandFireData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withSampler("BlurSampler")
                    .withSampler("MaskSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int BUFFER_SIZE = 64;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized;

    public void composite(GpuTextureView target, GpuTextureView scene, GpuTextureView smokeView, GpuTextureView mask,
                          int w, int h, float time, float intensity, float r, float g, float b, float a,
                          float radius, float speed, float flameHeight, float softness,
                          float blurRadius, float smoke, float activity) {
        if (target == null || scene == null || smokeView == null || mask == null) return;
        if (w <= 0 || h <= 0) return;

        ensureInitialized();
        prepareUniformData(w, h, time, intensity, r, g, b, a, radius, speed, flameHeight, softness, blurRadius, smoke, activity);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "accident:hand_fire_pass",
                target,
                OptionalInt.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("SceneSampler", scene, linearSampler);
            renderPass.bindTexture("BlurSampler", smokeView, linearSampler);
            renderPass.bindTexture("MaskSampler", mask, linearSampler);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("HandFireData", uniformBuffer);
            renderPass.draw(0, 6);
        }
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:hand_fire_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        this.uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:hand_fire_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                BUFFER_SIZE
        );

        initialized = true;
    }

    private void prepareUniformData(int w, int h, float time, float intensity, float r, float g, float b, float a,
                                    float radius, float speed, float flameHeight, float softness,
                                    float blurRadius, float smoke, float activity) {
        dataBuffer.clear();
        dataBuffer.putFloat(w);
        dataBuffer.putFloat(h);
        dataBuffer.putFloat(time);
        dataBuffer.putFloat(intensity);
        dataBuffer.putFloat(r);
        dataBuffer.putFloat(g);
        dataBuffer.putFloat(b);
        dataBuffer.putFloat(a);
        dataBuffer.putFloat(radius);
        dataBuffer.putFloat(speed);
        dataBuffer.putFloat(flameHeight);
        dataBuffer.putFloat(softness);
        dataBuffer.putFloat(blurRadius);
        dataBuffer.putFloat(smoke);
        dataBuffer.putFloat(activity);
        dataBuffer.putFloat(0.0f);
        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (dataBuffer != null) { MemoryUtil.memFree(dataBuffer); dataBuffer = null; }
        initialized = false;
    }
}
