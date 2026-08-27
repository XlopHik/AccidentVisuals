package accident.util.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
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

public class RectPipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("accident", "pipeline/rect");
    private static final Identifier VERTEX_SHADER = Identifier.of("accident", "core/rect");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/rect");

    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final float FIXED_GUI_SCALE = 2.0f;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("RectData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    // screen + rects + radii + misc + nine colours each, all vec4 under std140:
    // (1 + 64 + 64 + 64 + 576) * 16 bytes.
    private static final int BUFFER_SIZE = (1 + 64 * 12) * 16;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    public RectPipeline() {
        instance = this;
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    public void drawRect(float x, float y, float width, float height,
                         int[] colors, float[] radii) {
        drawRect(x, y, width, height, colors, radii, 0f);
    }

    public void drawRect(float x, float y, float width, float height,
                         int[] colors, float[] radii, float innerBlur) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        ensureInitialized();

        int framebufferWidth = client.getWindow().getFramebufferWidth();
        int framebufferHeight = client.getWindow().getFramebufferHeight();
        float fixedScreenWidth = framebufferWidth / FIXED_GUI_SCALE;
        float fixedScreenHeight = framebufferHeight / FIXED_GUI_SCALE;

        int[] colors9 = convertTo9Colors(colors);

        // последовательные прямоугольники батчатся вместе; всё остальное (текст, текстура, блюр, scissor) сначала флашит батч
        if (batchCount == MAX_BATCH || screenWidth != fixedScreenWidth || screenHeight != fixedScreenHeight) {
            flush();
        }

        screenWidth = fixedScreenWidth;
        screenHeight = fixedScreenHeight;

        int slot = batchCount++;
        batchRects[slot * 4] = x;
        batchRects[slot * 4 + 1] = y;
        batchRects[slot * 4 + 2] = width;
        batchRects[slot * 4 + 3] = height;

        batchRadii[slot * 4] = radii[0];
        batchRadii[slot * 4 + 1] = radii[1];
        batchRadii[slot * 4 + 2] = radii[2];
        batchRadii[slot * 4 + 3] = radii[3];

        batchBlur[slot] = innerBlur;

        System.arraycopy(colors9, 0, batchColors, slot * 9, 9);
    }

    // остальные 2D-пайплайны флашат накопленный батч отсюда перед своей отрисовкой
    private static RectPipeline instance;

    public static void flushPending() {
        if (instance != null) instance.flush();
    }

    private static final int MAX_BATCH = 64;

    private final float[] batchRects = new float[MAX_BATCH * 4];
    private final float[] batchRadii = new float[MAX_BATCH * 4];
    private final float[] batchBlur = new float[MAX_BATCH];
    private final int[] batchColors = new int[MAX_BATCH * 9];
    private int batchCount = 0;
    private float screenWidth, screenHeight;

    // рисует накопленные прямоугольники; вызывать перед любой другой отрисовкой, иначе они лягут поверх
    public void flush() {
        if (batchCount == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) {
            batchCount = 0;
            return;
        }

        long t0 = accident.util.render.DrawTimes.start();
        prepareUniformData();
        uploadAndDraw(client);
        accident.util.render.DrawTimes.rect(t0);
        batchCount = 0;
    }

    // переиспользуемый scratch-массив вместо аллокации на каждый drawRect
    private final int[] colors9Scratch = new int[9];

    private int[] convertTo9Colors(int[] colors) {
        int[] result = colors9Scratch;

        if (colors.length == 1) {
            for (int i = 0; i < 9; i++) {
                result[i] = colors[0];
            }
        } else if (colors.length == 4) {
            result[0] = colors[0];
            result[1] = blendColors(colors[0], colors[1]);
            result[2] = colors[1];
            result[3] = blendColors(colors[0], colors[3]);
            result[4] = blendColors(colors[0], colors[1], colors[2], colors[3]);
            result[5] = blendColors(colors[1], colors[2]);
            result[6] = colors[3];
            result[7] = blendColors(colors[3], colors[2]);
            result[8] = colors[2];
        } else if (colors.length >= 9) {
            System.arraycopy(colors, 0, result, 0, 9);
        } else {
            for (int i = 0; i < 9; i++) {
                result[i] = colors[i % colors.length];
            }
        }

        return result;
    }

    private int blendColors(int... colors) {
        int r = 0, g = 0, b = 0, a = 0;
        for (int color : colors) {
            a += (color >> 24) & 0xFF;
            r += (color >> 16) & 0xFF;
            g += (color >> 8) & 0xFF;
            b += color & 0xFF;
        }
        int count = colors.length;
        return ((a / count) << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
    }

    // каждый прямоугольник - ENTRY_SIZE vec4 подряд (см. rect.vsh); пишем только реально занятые слоты,
    // а не все 64 - иначе батч из трёх стоил полного 12кб филла каждый кадр
    private static final int ENTRY_SIZE = 12;

    private void prepareUniformData() {
        dataBuffer.clear();

        dataBuffer.putFloat(screenWidth);
        dataBuffer.putFloat(screenHeight);
        dataBuffer.putFloat(FIXED_GUI_SCALE);
        dataBuffer.putFloat(0f);

        for (int i = 0; i < batchCount; i++) {
            dataBuffer.putFloat(batchRects[i * 4]);
            dataBuffer.putFloat(batchRects[i * 4 + 1]);
            dataBuffer.putFloat(batchRects[i * 4 + 2]);
            dataBuffer.putFloat(batchRects[i * 4 + 3]);

            dataBuffer.putFloat(batchRadii[i * 4]);
            dataBuffer.putFloat(batchRadii[i * 4 + 1]);
            dataBuffer.putFloat(batchRadii[i * 4 + 2]);
            dataBuffer.putFloat(batchRadii[i * 4 + 3]);

            dataBuffer.putFloat(batchBlur[i]);
            dataBuffer.putFloat(0f);
            dataBuffer.putFloat(0f);
            dataBuffer.putFloat(0f);

            for (int c = 0; c < 9; c++) {
                int color = batchColors[i * 9 + c];
                dataBuffer.putFloat(((color >> 16) & 0xFF) / 255.0f);
                dataBuffer.putFloat(((color >> 8) & 0xFF) / 255.0f);
                dataBuffer.putFloat((color & 0xFF) / 255.0f);
                dataBuffer.putFloat(((color >> 24) & 0xFF) / 255.0f);
            }
        }

        dataBuffer.flip();
    }

    private void uploadAndDraw(MinecraftClient client) {
        int size = BUFFER_SIZE;
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minecraft:rect_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        long tA = System.nanoTime();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);
        accident.util.render.DrawTimes.stepWrite(tA);

        GpuBufferSlice dynamicTransforms = accident.util.render.Gui2DUniforms.slice();

        long tB = System.nanoTime();
        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "minecraft:rect_pass",
                accident.util.render.GuiCaptureTarget.colorView(client),
                OptionalInt.empty(),
                accident.util.render.GuiCaptureTarget.depthView(client),
                OptionalDouble.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("RectData", uniformBuffer);

            renderPass.draw(0, 6 * batchCount);
        }
        accident.util.render.DrawTimes.stepPass(tB);
    }

    public void close() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}