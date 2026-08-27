package accident.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("renderLayer")
    RenderLayer getRenderLayer();

    @Accessor("quads")
    List<BakedQuad> getQuads();

    @Accessor("tints")
    int[] getTints();

    @Accessor("glint")
    ItemRenderState.Glint getGlint();
}