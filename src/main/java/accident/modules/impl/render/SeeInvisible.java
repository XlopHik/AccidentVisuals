package accident.modules.impl.render;

import accident.manager.ServerManager;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.util.Instance;
import accident.util.lang.LanguageManager;

import static accident.IMinecraft.mc;

public class SeeInvisible extends ModuleStructure {

    private final BooleanSetting solid = new BooleanSetting(
            "accident.module.seeinvisible.setting.solid.name",
            "accident.module.seeinvisible.setting.solid.desc").setValue(false);

    private final SliderSettings opacity = new SliderSettings(
            "accident.module.seeinvisible.setting.opacity.name",
            "accident.module.seeinvisible.setting.opacity.desc")
            .setValue(0.15f).range(0.05f, 0.6f)
            .visible(() -> !solid.isValue());

    public SeeInvisible() {
        super("accident.module.seeinvisible.name", "accident.module.seeinvisible.desc",
                ModuleCategory.RENDER);
        settings(solid, opacity);
    }

    public static SeeInvisible getInstance() {
        return Instance.get(SeeInvisible.class);
    }

    @Override
    public boolean isLocked() {
        return !isAvailable();
    }

    @Override
    public String lockedMessage() {
        return LanguageManager.get("accident.module.seeinvisible.locked");
    }

    public boolean shouldReveal() {
        return isState() && isAvailable();
    }

    private boolean isAvailable() {
        return ServerManager.isHolyWorld() || mc.isInSingleplayer() || mc.isIntegratedServerRunning();
    }

    public boolean isSolid() {
        return solid.isValue();
    }

    public int ghostTint() {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity.getValue() * 255f)));
        return (alpha << 24) | 0xFFFFFF;
    }
}
