package accident.modules.impl.render;

import net.minecraft.client.MinecraftClient;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SliderSettings;


public class SmallModel extends ModuleStructure {

    public final SliderSettings scale = new SliderSettings("accident.module.smallmodel.setting.scale.name", "accident.module.smallmodel.setting.scale.desc")
            .range(0.1f, 1.0f)
            .setValue(0.5f);

    public SmallModel() {
        super("accident.module.smallmodel.name", "accident.module.smallmodel.desc", ModuleCategory.RENDER);
        settings(scale);
    }

    public float getScaleValue() {
        return this.isState() ? scale.getValue() : 1.0f;
    }
}


