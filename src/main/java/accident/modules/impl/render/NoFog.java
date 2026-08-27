package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;

import accident.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NoFog extends ModuleStructure {

    public static NoFog getInstance() {
        return Instance.get(NoFog.class);
    }

    public NoFog() {
        super("accident.module.nofog.name", "accident.module.nofog.desc", ModuleCategory.RENDER);
    }

    @Override
    public boolean activate() {
        return true;
    }

    @Override
    public boolean deactivate() {
        return true;
    }
}


