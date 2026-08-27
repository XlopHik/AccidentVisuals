package accident.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import accident.events.api.events.Event;

@Getter
@AllArgsConstructor
public class RotationUpdateEvent implements Event {
    byte type;
}
