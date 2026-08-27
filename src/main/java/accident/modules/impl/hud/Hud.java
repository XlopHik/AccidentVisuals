package accident.modules.impl.hud;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.ModuleStructure;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Hud extends ModuleStructure {
    public static Hud getInstance() {
        return Instance.get(Hud.class);
    }

    public BooleanSetting showGrid = new BooleanSetting("accident.module.hud.setting.showgrid.name", "accident.module.hud.setting.showgrid.desc")
            .setValue(false);

    public Hud() {
        super("accident.module.hud.name", "accident.module.hud.desc", ModuleCategory.HUD);
        settings(showGrid);
    }
}
