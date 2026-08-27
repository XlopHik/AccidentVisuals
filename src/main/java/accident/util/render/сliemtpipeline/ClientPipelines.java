package accident.util.render.сliemtpipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import java.util.function.Function;
import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

public class ClientPipelines {

    public static final RenderPipeline ROMB_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtex")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderPipeline ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/esp_blocks")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderPipeline CHAMS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation("pipeline/chams")
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    public static RenderLayer getChamsLayer(Identifier skinTexture) {
        return RenderLayer.of(
                "chams",
                RenderSetup.builder(CHAMS_PIPELINE)
                        .texture("Sampler0", skinTexture)
                        .useLightmap()
                        .useOverlay()
                        .translucent()
                        .expectedBufferSize(65536)
                        .build()
        );
    }

    public static final RenderPipeline TRAIL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/trail")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderLayer TRAIL_LAYER = RenderLayer.of(
            "trail",
            RenderSetup.builder(TRAIL_PIPELINE)
                    .translucent()
                    .expectedBufferSize(65536)
                    .build()
    );

    public static final RenderLayer ESP_BLOCK_LAYER = RenderLayer.of(
            "esp_blocks",
            RenderSetup.builder(ESP_PIPELINE)
                    .expectedBufferSize(65536)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(ROMB_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    public static final RenderPipeline TORNADO_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/tornado_esp")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> TORNADO_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(TORNADO_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    public static final RenderPipeline INWARD_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/inward_esp")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> INWARD_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(INWARD_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    public static final RenderPipeline GHOSTS_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtex")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> GHOSTS_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(GHOSTS_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    public static final RenderPipeline CHAIN_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtex")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> CHAIN_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(CHAIN_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    public static final RenderPipeline CRYSTAL_FILLED_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/crystal_filled")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderLayer CRYSTAL_FILLED = RenderLayer.of(
            "crystal_filled",
            RenderSetup.builder(CRYSTAL_FILLED_PIPELINE)
                    .translucent()
                    .expectedBufferSize(8192)
                    .build()
    );

    public static final RenderPipeline CRYSTAL_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/crystal_glow")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderLayer CRYSTAL_GLOW = RenderLayer.of(
            "crystal_glow",
            RenderSetup.builder(CRYSTAL_GLOW_PIPELINE)
                    .translucent()
                    .expectedBufferSize(4096)
                    .build()
    );

    public static final RenderPipeline BLOOM_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/bloom_esp")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> BLOOM_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(BLOOM_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(2048)
                        .build();
                return RenderLayer.of("bloom_esp", setup);
            });

    public static final RenderPipeline CHINA_HAT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/china_hat")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_FAN)
                    .build()
    );

    public static final RenderLayer CHINA_HAT = RenderLayer.of(
            "china_hat",
            RenderSetup.builder(CHINA_HAT_PIPELINE)
                    .translucent()
                    .expectedBufferSize(8192)
                    .build()
    );

    public static final RenderPipeline CHINA_HAT_OUTLINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/china_hat_outline")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINE_STRIP)
                    .build()
    );

    public static final RenderLayer CHINA_HAT_OUTLINE = RenderLayer.of(
            "china_hat_outline",
            RenderSetup.builder(CHINA_HAT_OUTLINE_PIPELINE)
                    .translucent()
                    .expectedBufferSize(4096)
                    .build()
    );

    public static final RenderPipeline WORLD_PARTICLES_COLOR_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("accident", "world_particles_color"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    public static final RenderLayer WORLD_PARTICLES_QUADS = RenderLayer.of(
            "world_particles_cube",
            RenderSetup.builder(WORLD_PARTICLES_COLOR_PIPELINE)
                    .translucent()
                    .expectedBufferSize(2048)
                    .build()
    );

    public static final RenderPipeline WORLD_PARTICLES_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("accident", "world_particles_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    public static final RenderLayer WORLD_PARTICLES_LINES = RenderLayer.of(
            "world_particles_lines",
            RenderSetup.builder(WORLD_PARTICLES_LINES_PIPELINE)
                    .translucent()
                    .expectedBufferSize(2048)
                    .build()
    );

    public static final RenderPipeline WORLD_PARTICLES_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("accident", "world_particles_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .withSampler("Sampler0")
                    .build()
    );

    public static final Function<Identifier, RenderLayer> WORLD_PARTICLES_GLOW =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(WORLD_PARTICLES_GLOW_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(2048)
                        .build();
                return RenderLayer.of("world_particles_glow", setup);
            });

    public static final RenderPipeline GUI_ARROW_BLEND_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/gui_arrow_blend")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> GUI_ARROW_BLEND =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(GUI_ARROW_BLEND_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(256)
                        .build();
                return RenderLayer.of("gui_arrow_blend", setup);
            });

    public static final RenderPipeline HIT_BUBBLE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/hit_bubble")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)      // additive — чёрный исчезает
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST) // не насквозь
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> HIT_BUBBLE_LAYER =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(HIT_BUBBLE_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(256)
                        .build();
                return RenderLayer.of("hit_bubble", setup);
            });

    // обычный альфа-блендинг, а не аддитивный LIGHTNING как у glow-слоёв выше -
    // иначе цвета скриншота просто выбьет в белый. depth-test включен, чтобы
    // стена корректно перекрывала "фото"
    public static final RenderPipeline GUI_ECHO_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/gui_echo")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> GUI_ECHO_LAYER =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(GUI_ECHO_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(256)
                        .build();
                return RenderLayer.of("gui_echo", setup);
            });

}