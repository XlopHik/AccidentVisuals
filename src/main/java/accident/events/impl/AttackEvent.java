package accident.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.Entity;
import accident.events.api.events.Event;

@Getter
@AllArgsConstructor
public class AttackEvent implements Event {
    private final Entity target;
}