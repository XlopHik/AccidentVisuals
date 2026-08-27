package accident.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import accident.events.api.events.Event;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public final class Render2DEvent implements Event {
    @Getter
    private static final Render2DEvent instance = new Render2DEvent();

    private MatrixStack matrixStack;
    private Camera camera;
    private Window window;
    private float tickDelta;
    private float partialTicks;

    public void set(MatrixStack matrixStack, Camera camera, Window window, float tickDelta) {
        this.matrixStack = matrixStack;
        this.camera = camera;
        this.window = window;
        this.tickDelta = tickDelta;
        this.partialTicks = tickDelta;
    }
}