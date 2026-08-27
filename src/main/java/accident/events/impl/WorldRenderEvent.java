package accident.events.impl;

import lombok.*;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import accident.events.api.events.Event;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@Getter
public class WorldRenderEvent implements Event {
    MatrixStack stack;
    float partialTicks;
    Camera camera;
    VertexConsumerProvider vertexConsumers;
}
