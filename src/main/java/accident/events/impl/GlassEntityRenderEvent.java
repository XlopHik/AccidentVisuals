package accident.events.impl;

import lombok.*;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import accident.events.api.events.callables.EventCancellable;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@Getter
@Setter
public class GlassEntityRenderEvent extends EventCancellable {
    public enum Phase { PRE, POST }
    Phase phase;
    Entity entity;
    float tickDelta;
}