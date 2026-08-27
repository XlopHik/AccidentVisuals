package accident.modules.impl.misc;

import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SelectSetting;

import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;

public class CustomModels extends ModuleStructure {

    public static final String RABBIT = "Crazy Rabbit";
    public static final String FREDDY = "Freddy Bear";
    public static final String TUNG = "Tung Sahur";
    public static final String SHINO = "Asada Shino";

    private final SelectSetting models = new SelectSetting("accident.module.custommodels.setting.models.name", "accident.module.custommodels.setting.models.desc")
            .value(RABBIT, FREDDY, TUNG, SHINO)
            .selected(RABBIT);

    public CustomModels() {
        super("accident.module.custommodels.name", "accident.module.custommodels.desc", ModuleCategory.MISC);
        settings(models);
    }

    public String getModelName() {
        return models.getSelected();
    }

    public boolean shouldApplyTo(PlayerLikeEntity player) {
        if (!isEnabled() || player == null || player.isSpectator()) {
            return false;
        }

        return mc.player != null && player == mc.player;
    }
}


