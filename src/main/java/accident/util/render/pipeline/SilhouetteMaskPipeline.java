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
import net.minecraft.client.model.Model;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import accident.util.render.shader.MaskCommandCollector;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalInt;

public class SilhouetteMaskPipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("accident", "pipeline/silhouette_mask");
    private static final Identifier VERTEX_SHADER = Identifier.of("accident", "core/silhouette_mask");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("accident", "core/silhouette_mask");

    private static final BlendFunction REPLACE_BLEND = new BlendFunction(
            SourceFactor.ONE, DestFactor.ZERO,
            SourceFactor.ONE, DestFactor.ZERO
    );


    private final MatrixStack matrices = new MatrixStack();

    @SuppressWarnings("unchecked")
    public void renderSilhouettes(
            GpuTextureView maskTargetView,
            List<MaskCommandCollector.MaskEntry> entries,
            int width, int height
    ) {
        if (entries.isEmpty()) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        try (RenderPass pass = encoder.createRenderPass(
                () -> "accident:silhouette_clear",
                maskTargetView,
                OptionalInt.of(0xFF000000)
        )) {
        }

        for (MaskCommandCollector.MaskEntry entry : entries) {
            matrices.push();
            matrices.peek().copy(entry.matricesEntry());

            @SuppressWarnings("rawtypes")
            Model model = entry.model();
            model.setAngles(entry.state());


            matrices.pop();
        }
    }
}