package accident.modules.impl.render;

import lombok.Getter;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;

@Getter
public class ClickGuiModule extends ModuleStructure {

    public final SelectSetting style = new SelectSetting("accident.module.clickgui.setting.style.name", "accident.module.clickgui.setting.style.desc")
            .value("Dropdown", "CS GUI")
            .selected("Dropdown");

    public final SelectSetting girlMode = new SelectSetting("accident.module.clickgui.setting.girlmode.name", "accident.module.clickgui.setting.girlmode.desc")
            .value("None", "catgirl", "furrymaid", "kiskis", "mylove", "nyashka", "pinky")
            .selected("None");

    public final SliderSettings girlAlpha = new SliderSettings("accident.module.clickgui.setting.girlalpha.name", "accident.module.clickgui.setting.girlalpha.desc")
            .setValue(0.7f).range(0.1f, 1.0f);

    public final SliderSettings girlScale = new SliderSettings("accident.module.clickgui.setting.girlscale.name", "accident.module.clickgui.setting.girlscale.desc")
            .setValue(1.0f).range(0.5f, 2.0f);

    public final SliderSettings bgDarkness = new SliderSettings("accident.module.clickgui.setting.bgdarkness.name", "accident.module.clickgui.setting.bgdarkness.desc")
            .setValue(0.6f).range(0.0f, 1.0f);

    public final SliderSettings columnOpacity = new SliderSettings("accident.module.clickgui.setting.columnopacity.name", "accident.module.clickgui.setting.columnopacity.desc")
            .setValue(0.85f).range(0.1f, 1.0f);

    public final SliderSettings columnScale = new SliderSettings("accident.module.clickgui.setting.columnscale.name", "accident.module.clickgui.setting.columnscale.desc")
            .setValue(1.0f).range(0.7f, 1.4f);

    public ClickGuiModule() {
        super("accident.module.clickgui.name", "accident.module.clickgui.desc", ModuleCategory.RENDER);
        settings(style, girlMode, girlAlpha, girlScale, bgDarkness, columnOpacity, columnScale);
        this.state = true;
    }

    public static ClickGuiModule getInstance() {
        return Instance.get(ClickGuiModule.class);
    }
}


