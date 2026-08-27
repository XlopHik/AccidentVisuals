package accident.modules.impl.render;

import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;

import accident.util.Instance;

public class NameTags extends ModuleStructure {

    public static NameTags getInstance() {
        return Instance.get(NameTags.class);
    }

    public final BooleanSetting noBackground = new BooleanSetting("accident.module.nametags.setting.nobackground.name", "accident.module.nametags.setting.nobackground.desc")
            .setValue(true);

    public final BooleanSetting shadow = new BooleanSetting("accident.module.nametags.setting.shadow.name", "accident.module.nametags.setting.shadow.desc")
            .setValue(true);

    public NameTags() {
        super("accident.module.nametags.name", "accident.module.nametags.desc", ModuleCategory.RENDER);
        settings(noBackground, shadow);
    }
}


