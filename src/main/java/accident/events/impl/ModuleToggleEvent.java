package accident.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import accident.events.api.events.Event;
import accident.modules.module.ModuleStructure;

@Getter
@AllArgsConstructor
public class ModuleToggleEvent implements Event {
    private final ModuleStructure module;
    private final boolean enabled;
}