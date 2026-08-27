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
import java.util.OptionalDouble;
import java.util.OptionalInt;

// кольцо не текстурный меш на земле, а читает глубину сцены, чтобы найти свой
// силуэт в мировых координатах - подробности в liquid_ring.fsh
public class LiquidRingPipeline {

    private static final Identifier PIPELINE_ID     = Identifier.of("accident", "pipeline/liquid_ring");
    private static final Identifier BLAST_PIPELINE_ID = Identifier.of("accident", "pipeline/blast_wave");
    private static final Identifier BLAST_SHADER      = Identifier.of("accident", "core/blast_wave");
    private static final Identifier VERTEX_SHADER   = Identifier.of("accident", "core/liquid_ring");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/liquid_ring");

    private static final BlendFunction ALPHA_BLEND = new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE,       DestFactor.ONE_MINUS_SRC_ALPHA
    );

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("LiquidRingData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withSampler("DepthSampler")
                    .withBlend(ALPHA_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    // те же юниформы и проход, но луч пересекается со сферой, а не с плоскостью
    // земли - у взрывной волны нет пола, на который она ложится
    private static final RenderPipeline BLAST_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(BLAST_PIPELINE_ID)
                    .withVertexShader(BLAST_SHADER)
                    .withFragmentShader(BLAST_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("LiquidRingData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withSampler("DepthSampler")
                    .withBlend(ALPHA_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET    = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX  = new Matrix4f();

    public static final int MAX_RINGS = 16;

    // camPosNear(16) + params(16) + camRight(16) + camUp(16) + tintDistort(16)
    // + warpParams(16) + columnParams(16) + invViewProj(64)
    // + ringPosRadius[16](256) + ringMeta[16](256)
    private static final int BUFFER_SIZE = 16 * 7 + 64 + 16 * MAX_RINGS + 16 * MAX_RINGS;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    private GpuTexture sceneTexture;
    private GpuTextureView sceneTextureView;
    private GpuTexture depthTexture;
    private GpuTextureView depthTextureView;
    private int lastWidth = 0;
    private int lastHeight = 0;

    public LiquidRingPipeline() {}

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:liquid_ring_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    private void ensureCaptureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneTextureView != null) return;

        if (sceneTextureView != null) { sceneTextureView.close(); sceneTextureView = null; }
        if (sceneTexture != null)     { sceneTexture.close();     sceneTexture     = null; }
        if (depthTextureView != null) { depthTextureView.close(); depthTextureView = null; }
        if (depthTexture != null)     { depthTexture.close();     depthTexture     = null; }

        sceneTexture = RenderSystem.getDevice().createTexture(
                () -> "accident:liquid_ring_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        sceneTextureView = RenderSystem.getDevice().createTextureView(sceneTexture);

        depthTexture = RenderSystem.getDevice().createTexture(
                () -> "accident:liquid_ring_depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, width, height, 1, 1
        );
        depthTextureView = RenderSystem.getDevice().createTextureView(depthTexture);

        lastWidth = width;
        lastHeight = height;
    }

    public void render(GpuTextureView targetView,
                       GpuTexture liveColor, GpuTexture liveDepth,
                       int width, int height,
                       Vector3f camPos, float near, float far,
                       Vector3f camRight, Vector3f camUp,
                       Matrix4f invViewProj,
                       float tintR, float tintG, float tintB, float tintAmount,
                       boolean columnEnabled, float columnHeight, float columnWidthFraction, float columnAlpha,
                       float[] ringX, float[] ringY, float[] ringZ, float[] ringRadius,
                       float[] ringRimWidth, float[] ringEnvelope, float[] ringDomeAmp,
                       int ringCount,
                       boolean spherical) {

        if (ringCount <= 0) return;

        ensureInitialized();
        ensureCaptureTextures(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(liveColor, sceneTexture, 0, 0, 0, 0, 0, width, height);
        if (liveDepth != null) {
            encoder.copyTextureToTexture(liveDepth, depthTexture, 0, 0, 0, 0, 0, width, height);
        }

        prepareUniformData(width, height, camPos, near, far, camRight, camUp, invViewProj,
                tintR, tintG, tintB, tintAmount,
                columnEnabled, columnHeight, columnWidthFraction, columnAlpha,
                ringX, ringY, ringZ, ringRadius, ringRimWidth, ringEnvelope, ringDomeAmp, ringCount);

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "accident:liquid_ring_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "accident:liquid_ring_pass",
                targetView,
                OptionalInt.empty())) {

            renderPass.setPipeline(spherical ? BLAST_PIPELINE : PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("SceneSampler", sceneTextureView, linear);
            renderPass.bindTexture("DepthSampler", depthTextureView, nearest);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("LiquidRingData", uniformBuffer);

            renderPass.draw(0, 6);
        }
    }

    private void prepareUniformData(int width, int height,
                                    Vector3f camPos, float near, float far,
                                    Vector3f camRight, Vector3f camUp,
                                    Matrix4f invViewProj,
                                    float tintR, float tintG, float tintB, float tintAmount,
                                    boolean columnEnabled, float columnHeight, float columnWidthFraction, float columnAlpha,
                                    float[] ringX, float[] ringY, float[] ringZ, float[] ringRadius,
                                    float[] ringRimWidth, float[] ringEnvelope, float[] ringDomeAmp,
                                    int ringCount) {
        dataBuffer.clear();

        dataBuffer.putFloat(camPos.x).putFloat(camPos.y).putFloat(camPos.z).putFloat(near);
        dataBuffer.putFloat((float) width).putFloat((float) height).putFloat(far).putFloat((float) ringCount);
        dataBuffer.putFloat(camRight.x).putFloat(camRight.y).putFloat(camRight.z).putFloat(0f);
        dataBuffer.putFloat(camUp.x).putFloat(camUp.y).putFloat(camUp.z).putFloat(0f);
        dataBuffer.putFloat(tintR).putFloat(tintG).putFloat(tintB).putFloat(tintAmount);
        dataBuffer.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f); // reserved
        dataBuffer.putFloat(columnEnabled ? 1f : 0f).putFloat(columnHeight).putFloat(columnWidthFraction).putFloat(columnAlpha);

        invViewProj.get(dataBuffer);
        dataBuffer.position(dataBuffer.position() + 64);

        for (int i = 0; i < MAX_RINGS; i++) {
            if (i < ringCount) {
                dataBuffer.putFloat(ringX[i]).putFloat(ringY[i]).putFloat(ringZ[i]).putFloat(ringRadius[i]);
            } else {
                dataBuffer.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
            }
        }
        for (int i = 0; i < MAX_RINGS; i++) {
            if (i < ringCount) {
                dataBuffer.putFloat(ringRimWidth[i]).putFloat(ringEnvelope[i])
                        .putFloat(ringDomeAmp[i]).putFloat(0f);
            } else {
                dataBuffer.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
            }
        }

        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer != null)     { uniformBuffer.close();          uniformBuffer     = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close();      dummyVertexBuffer = null; }
        if (dataBuffer != null)        { MemoryUtil.memFree(dataBuffer); dataBuffer        = null; }
        if (sceneTextureView != null)  { sceneTextureView.close();       sceneTextureView  = null; }
        if (sceneTexture != null)      { sceneTexture.close();           sceneTexture      = null; }
        if (depthTextureView != null)  { depthTextureView.close();       depthTextureView  = null; }
        if (depthTexture != null)      { depthTexture.close();           depthTexture      = null; }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
    }
}
