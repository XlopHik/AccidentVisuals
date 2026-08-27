package accident.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import accident.events.api.events.callables.EventCancellable;

@Getter
@Setter
@AllArgsConstructor
public class UsingItemEvent extends EventCancellable {
    byte type;
}
