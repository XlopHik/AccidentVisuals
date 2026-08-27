package accident.events.impl;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import accident.events.api.events.callables.EventCancellable;
import accident.util.camera.Angle;


@Getter
@Setter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CameraEvent extends EventCancellable {
    boolean cameraClip;
    float distance;
    Angle angle;
}
