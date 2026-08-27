package accident.util.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;

public class ChamsVertexConsumerProvider implements VertexConsumerProvider {
    private final VertexConsumerProvider parent;
    private final int color;

    public ChamsVertexConsumerProvider(VertexConsumerProvider parent, int color) {
        this.parent = parent;
        this.color = color;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        VertexConsumer buffer = parent.getBuffer(layer);
        return new ChamsVertexConsumer(buffer, color);
    }
}