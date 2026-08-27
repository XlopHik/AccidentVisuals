package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.ColorSetting;

import accident.util.ColorUtil;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChinaHat extends ModuleStructure {

    private static ChinaHat instance;

    public static ChinaHat getInstance() {
        return instance;
    }

    public final ColorSetting color1 = new ColorSetting("accident.module.chinahat.setting.color1.name", "accident.module.chinahat.setting.color1.desc")
            .value(ColorUtil.getColor(255, 50, 100, 255));

    public final ColorSetting color2 = new ColorSetting("accident.module.chinahat.setting.color2.name", "accident.module.chinahat.setting.color2.desc")
            .value(ColorUtil.getColor(100, 50, 255, 255));

    public ChinaHat() {
        super("accident.module.chinahat.name", "accident.module.chinahat.desc", ModuleCategory.RENDER);
        instance = this;
        settings(color1, color2);
    }
}


