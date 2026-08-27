package accident.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import accident.events.api.events.Event;

@Getter
@Setter
@AllArgsConstructor
public class EventCameraUpdate implements Event {
    private double x;
    private double y;
    private double z;
}