package accident.modules.module.category;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ModuleCategory {
    MOVEMENT("Movement"),
    RENDER("Render"),
    MISC("Misc"),
    HUD("Hud"),
    LUA("Lua"),
    CONFIGS("Configs");

    final String readableName;
}