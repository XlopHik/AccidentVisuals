package accident.util.render.shader;

import accident.util.render.pipeline.HandsWavePipeline;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.OptionalInt;

public class WaveHandsRenderer {

    @Getter
    private static final WaveHandsRenderer instance = new WaveHandsRenderer();

    private final MinecraftClient client = MinecraftClient.getInstance();

    private GpuBuffer uniformBuffer;
    private GpuBuffer vertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    @Getter @Setter private boolean enabled = false;
    @Getter @Setter private float speed = 1.0f;
    @Getter @Setter private float strength = 0.5f;
    @Getter @Setter private boolean useOriginalBase = true;
    @Getter @Setter private boolean multiplyByOriginal = false;
    @Getter @Setter private int color = 0xFF00FFFF;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private void ensureInitialized() {
        if (initialized) return;

        // 8 vec4 * 16 bytes = 128 bytes для uniform
        this.dataBuffer = MemoryUtil.memAlloc(128);

        // Создаем вершинный буфер: 6 вершин * (3 float pos + 2 float uv) * 4 bytes
        // Формат: x, y, z, u, v для каждой вершины
        ByteBuffer vertexData = MemoryUtil.memAlloc(120); // 6 * 5 * 4 = 120

        // Квад на весь экран (2 треугольника)
        // Треугольник 1
        vertexData.putFloat(-1.0f).putFloat(-1.0f).putFloat(0.0f); // pos 0
        vertexData.putFloat(0.0f).putFloat(0.0f);                   // uv 0

        vertexData.putFloat(3.0f).putFloat(-1.0f).putFloat(0.0f);  // pos 1
        vertexData.putFloat(2.0f).putFloat(0.0f);                   // uv 1

        vertexData.putFloat(-1.0f).putFloat(3.0f).putFloat(0.0f);  // pos 2
        vertexData.putFloat(0.0f).putFloat(2.0f);                   // uv 2

        // Треугольник 2
        vertexData.putFloat(3.0f).putFloat(-1.0f).putFloat(0.0f);  // pos 3
        vertexData.putFloat(2.0f).putFloat(0.0f);                   // uv 3

        vertexData.putFloat(3.0f).putFloat(3.0f).putFloat(0.0f);   // pos 4
        vertexData.putFloat(2.0f).putFloat(2.0f);                   // uv 4

        vertexData.putFloat(-1.0f).putFloat(3.0f).putFloat(0.0f);  // pos 5
        vertexData.putFloat(0.0f).putFloat(2.0f);                   // uv 5

        vertexData.flip();

        this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:wave_hands_vertex",
                GpuBuffer.USAGE_VERTEX,
                vertexData
        );
        MemoryUtil.memFree(vertexData);

        initialized = true;
    }

    public void render(GpuTextureView colorTexture, GpuTextureView depthTexture) {
        if (!enabled) {
            return;
        }
        if (colorTexture == null || depthTexture == null) {
            return;
        }

        ensureInitialized();

        Framebuffer fb = client.getFramebuffer();
        if (fb == null) return;

        prepareUniformData(fb.textureWidth, fb.textureHeight);

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "accident:wave_hands_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "accident:wave_hands_pass",
                fb.getColorAttachmentView(),
                OptionalInt.empty())) {

            renderPass.setPipeline(HandsWavePipeline.PIPELINE);
            renderPass.setVertexBuffer(0, vertexBuffer);

            renderPass.bindTexture("ColorTexture", colorTexture, linearSampler);
            renderPass.bindTexture("DepthTexture", depthTexture, linearSampler);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("HandsData", uniformBuffer);

            renderPass.draw(0, 6);
        }
    }

    private void prepareUniformData(int width, int height) {
        dataBuffer.clear();

        // 1. vec4 resolutionTimeAlpha
        float time = (float) (System.currentTimeMillis() / 1000.0) * speed;
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(time);
        dataBuffer.putFloat(strength);

        // 2. vec4 customColorData
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = multiplyByOriginal ? 1.0f : 0.0f;
        dataBuffer.putFloat(r);
        dataBuffer.putFloat(g);
        dataBuffer.putFloat(b);
        dataBuffer.putFloat(a);

        // 3-6. Градиенты (пустые)
        for (int i = 0; i < 16; i++) dataBuffer.putFloat(0.0f);

        // 7. vec4 params
        float baseFlag = useOriginalBase ? 1.0f : 0.0f;
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(baseFlag);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);

        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}