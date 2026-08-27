package accident.util.render.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.texture.AbstractTexture;

// оборачиваем сырую GPU-текстуру в поля AbstractTexture, чтобы её можно было
// использовать через vanilla Identifier/RenderLayer; close() уже унаследован
public class GpuTextureWrapper extends AbstractTexture {

    public GpuTextureWrapper(GpuTexture texture, GpuTextureView textureView) {
        this.glTexture = texture;
        this.glTextureView = textureView;
        this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
    }
}
