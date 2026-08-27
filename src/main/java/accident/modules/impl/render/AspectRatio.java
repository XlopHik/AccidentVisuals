package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AspectRatio extends ModuleStructure {

    public static AspectRatio getInstance() {
        return Instance.get(AspectRatio.class);
    }

    SelectSetting mode = new SelectSetting("accident.module.aspectratio.setting.mode.name", "accident.module.aspectratio.setting.mode.desc")
            .value("Custom", "4:3", "16:9", "16:10", "21:9")
            .selected("4:3");

    SliderSettings customRatio = new SliderSettings("accident.module.aspectratio.setting.customratio.name", "accident.module.aspectratio.setting.customratio.desc")
            .setValue(1.33F).range(0.5F, 3.0F)
            .visible(() -> mode.isSelected("Custom"));

    public AspectRatio() {
        super("accident.module.aspectratio.name", "accident.module.aspectratio.desc", ModuleCategory.RENDER);
        settings(mode, customRatio);
    }

    public float getRatio() {
        if (!state) return -1f;

        return switch (mode.getSelected()) {
            case "4:3" -> 1.3333334f;
            case "16:9" -> 1.7777778f;
            case "16:10" -> 1.6f;
            case "21:9" -> 2.3333333f;
            default -> customRatio.getValue();
        };
    }
}


