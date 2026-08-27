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
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

// копирует захваченный GUI-слой в текстуру эха с осветлением на лету -
// зачем эху это нужно, а живому меню нет, см. gui_mask.fsh
public class GuiMaskPipeline {

    private static final Identifier PIPELINE_ID     = Identifier.of("accident", "pipeline/gui_mask");
    private static final Identifier VERTEX_SHADER   = Identifier.of("accident", "core/gui_mask");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/gui_mask");

    // тут каждый раз пишем во свежую scratch-текстуру, а не блендим поверх
    // существующего - поэтому полная перезапись, а не смешивание
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
                    .withUniform("GuiMaskData", net.minecraft.client.gl.UniformType.UNIFORM_BUFFER)
                    .withSampler("SourceSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET    = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX  = new Matrix4f();

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    public GuiMaskPipeline() {}

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(16);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "accident:gui_mask_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    public void render(GpuTextureView targetView, GpuTextureView sourceView, float brightness) {
        ensureInitialized();

        dataBuffer.clear();
        dataBuffer.putFloat(brightness).putFloat(0f).putFloat(0f).putFloat(0f);
        dataBuffer.flip();

        if (uniformBuffer == null) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "accident:gui_mask_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    16
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "accident:gui_mask_pass",
                targetView,
                OptionalInt.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("SourceSampler", sourceView, linear);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("GuiMaskData", uniformBuffer);

            renderPass.draw(0, 6);
        }
    }

    public void close() {
        if (uniformBuffer != null)     { uniformBuffer.close();          uniformBuffer     = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close();      dummyVertexBuffer = null; }
        if (dataBuffer != null)        { MemoryUtil.memFree(dataBuffer); dataBuffer        = null; }
        initialized = false;
    }
}
