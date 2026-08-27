package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.MultiSelectSetting;

import accident.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoRender extends ModuleStructure {

    public static NoRender getInstance() {
        return Instance.get(NoRender.class);
    }

    public MultiSelectSetting modeSetting = new MultiSelectSetting("accident.module.norender.setting.modsetting.name", "accident.module.norender.setting.modsetting.desc")
            .value("Fire", "Bad Effects", "Block Overlay", "Darkness", "Damage", "Nausea", "Scoreboard", "BossBar", "Spawner")
            .selected("Fire", "Bad Effects", "Block Overlay", "Darkness", "Damage", "Nausea");

    public NoRender() {
        super("accident.module.norender.name", "accident.module.norender.desc", ModuleCategory.RENDER);
        settings(modeSetting);
    }

    public boolean shouldHideSpawner() {
        return isEnabled() && modeSetting.isSelected("Spawner");
    }
}


