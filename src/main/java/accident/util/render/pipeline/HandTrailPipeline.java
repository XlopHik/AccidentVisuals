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

// пинг-понг буфер следа для огня на руках: каждый кадр гасим и сносим предыдущий
// кадр по curl-noise, добавляем новые цветные точки вокруг силуэта руки - за счёт
// накопления пламя выглядит живым, а не статичным рисунком
public class HandTrailPipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("accident", "pipeline/hand_trail");
    private static final Identifier VERTEX_SHADER = Identifier.of("accident", "core/hand_trail");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/hand_trail");

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
                    .withUniform("HandTrailData", UniformType.UNIFORM_BUFFER)
                    .withSampler("PrevTrailSampler")
                    .withSampler("SceneSampler")
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
    private static final int BUFFER_SIZE = 80;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private GpuTexture[] textures;
    private GpuTextureView[] views;
    private int readIndex;
    private int width;
    private int height;
    private boolean initialized;

    public GpuTextureView update(GpuTextureView scene, GpuTextureView mask, int w, int h, float time,
                                 float intensity, float r, float g, float b, float a,
                                 float radius, float speed, float flameHeight, float softness,
                                 float blurRadius, float smoke, float activity,
                                 float slash, float slashDirection, float trailFade) {
        if (scene == null || mask == null || w <= 0 || h <= 0) return null;

        ensureInitialized();
        ensureFramebuffers(w, h);

        int writeIndex = 1 - readIndex;
        prepareUniformData(w, h, time, intensity, r, g, b, a, radius, speed, flameHeight, softness,
                blurRadius, smoke, activity, slash, slashDirection, trailFade);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "accident:hand_trail_pass",
                views[writeIndex],
                OptionalInt.of(0x00000000))) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("PrevTrailSampler", views[readIndex], linearSampler);
            renderPass.bindTexture("SceneSampler", scene, linearSampler);
            renderPass.bindTexture("MaskSampler", mask, linearSampler);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("HandTrailData", uniformBuffer);
            renderPass.draw(0, 6);
        }

        readIndex = writeIndex;
        return views[readIndex];
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:hand_trail_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        this.uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:hand_trail_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                BUFFER_SIZE
        );

        textures = new GpuTexture[2];
        views = new GpuTextureView[2];
        initialized = true;
    }

    private void ensureFramebuffers(int w, int h) {
        if (width == w && height == h && textures[0] != null && textures[1] != null) return;

        cleanupFramebuffers();
        for (int i = 0; i < 2; i++) {
            final int index = i;
            textures[i] = RenderSystem.getDevice().createTexture(
                    () -> "accident:hand_trail_" + index,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8, w, h, 1, 1);
            views[i] = RenderSystem.getDevice().createTextureView(textures[i]);
        }
        width = w;
        height = h;
        readIndex = 0;
        clearFramebuffers();
    }

    public void clear() {
        if (views == null || views[0] == null || views[1] == null) return;
        clearFramebuffers();
    }

    private void clearFramebuffers() {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int i = 0; i < 2; i++) {
            try (RenderPass ignored = encoder.createRenderPass(
                    () -> "accident:hand_trail_clear", views[i], OptionalInt.of(0x00000000))) {
            }
        }
    }

    private void prepareUniformData(int w, int h, float time, float intensity, float r, float g, float b, float a,
                                    float radius, float speed, float flameHeight, float softness,
                                    float blurRadius, float smoke, float activity,
                                    float slash, float slashDirection, float trailFade) {
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
        dataBuffer.putFloat(trailFade);
        dataBuffer.putFloat(slash);
        dataBuffer.putFloat(slashDirection);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.flip();
    }

    private void cleanupFramebuffers() {
        if (views != null) {
            for (int i = 0; i < views.length; i++) {
                if (views[i] != null) { views[i].close(); views[i] = null; }
                if (textures[i] != null) { textures[i].close(); textures[i] = null; }
            }
        }
        width = 0;
        height = 0;
    }

    public GpuTexture texture() {
        return textures == null ? null : textures[readIndex];
    }

    public void close() {
        cleanupFramebuffers();
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (dataBuffer != null) { MemoryUtil.memFree(dataBuffer); dataBuffer = null; }
        initialized = false;
    }
}
