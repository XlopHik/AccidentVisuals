package accident.util.render;

import net.minecraft.client.render.VertexConsumer;

public class ChamsVertexConsumer implements VertexConsumer {
    private final VertexConsumer parent;
    private final int color;

    public ChamsVertexConsumer(VertexConsumer parent, int color) {
        this.parent = parent;
        this.color = color;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        parent.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        parent.color(
                (color >> 16) & 0xFF,
                (color >> 8) & 0xFF,
                color & 0xFF,
                (color >> 24) & 0xFF
        );
        return this;
    }

    @Override
    public VertexConsumer color(int argb) {
        return this.color(
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                (argb >> 24) & 0xFF
        );
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        parent.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        parent.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        parent.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        parent.normal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        parent.lineWidth(width);
        return this;
    }
}