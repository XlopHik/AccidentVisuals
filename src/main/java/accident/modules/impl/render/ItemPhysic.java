package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import accident.events.api.EventHandler;
import accident.events.impl.TickEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SelectSetting;

import accident.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ItemPhysic extends ModuleStructure {
    public static ItemPhysic getInstance() {
        return Instance.get(ItemPhysic.class);
    }

    public SelectSetting mode = new SelectSetting("accident.module.itemphysic.setting.mode.name", "").value("Обычная").selected("Обычная");

    public ItemPhysic() {
        super("accident.module.itemphysic.name", "accident.module.itemphysic.desc", ModuleCategory.RENDER);
    }

    @EventHandler
    public void onTick(TickEvent e) {
    }
}


