package accident.util.render.pipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public class HandsWavePipeline {

    public static final RenderPipeline PIPELINE;

    static {
        PIPELINE = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                        .withLocation(Identifier.of("accident", "pipeline/hands_wave"))
                        .withVertexShader(Identifier.of("accident", "core/hands_post_vertex"))
                        .withFragmentShader(Identifier.of("accident", "core/hands_wave_fragment"))

                        // Используем POSITION_TEXTURE как ожидается
                        .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.TRIANGLES)

                        .withUniform("HandsData", UniformType.UNIFORM_BUFFER)
                        .withSampler("ColorTexture")
                        .withSampler("DepthTexture")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build()
        );
    }
}