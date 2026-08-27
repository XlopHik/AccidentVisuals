package accident.modules.impl.misc;

import net.minecraft.client.MinecraftClient;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.util.Instance;

public class SmoothAnimations extends ModuleStructure {
    public static SmoothAnimations getInstance() { return Instance.get(SmoothAnimations.class); }

    // Shared rollover counter for hotbar wrap-around animation
    public static int hotbarRolloverCount = 0;

    public final SliderSettings hotbarSmoothness = new SliderSettings("Hotbar плавность", "Плавность выбора слота хотбара (0 = выкл)")
            .range(0, 95)
            .setValue(75);

    public final BooleanSetting hotbarRollover = new BooleanSetting("Hotbar перемотка", "Анимация перехода через края хотбара")
            .setValue(true);

    public final SliderSettings entryListSmoothness = new SliderSettings("Список плавность", "Плавность скролла списков (настройки, сервера)")
            .range(0, 95)
            .setValue(70);

    public final SliderSettings chatSmoothness = new SliderSettings("Чат плавность", "Плавность скролла чата")
            .range(0, 95)
            .setValue(70);

    public final SliderSettings suggestionSmoothness = new SliderSettings("Подсказки плавность", "Плавность скролла окна подсказок")
            .range(0, 95)
            .setValue(70);

    public final SliderSettings inventorySmoothness = new SliderSettings("Инвентарь плавность", "Плавность скролла в творческом инвентаре (0 = выкл)")
            .range(0, 95)
            .setValue(75);

    public final SliderSettings tabListSmoothness = new SliderSettings("Таб-лист плавность", "Плавность выезда/заезда таб-листа (0 = выкл)")
            .range(0, 95)
            .setValue(70);

    public SmoothAnimations() {
        super("SmoothAnimations", "Плавные анимации скролла во всём интерфейсе", ModuleCategory.MISC);
        settings(hotbarSmoothness, hotbarRollover, entryListSmoothness, chatSmoothness, suggestionSmoothness, inventorySmoothness, tabListSmoothness);
        setState(true);
    }

    @Override
    public boolean activate() { return false; }

    @Override
    public boolean deactivate() {
        hotbarRolloverCount = 0;
        return false;
    }

    /** Returns getDynamicDeltaTicks() — frame delta in ticks (≈0.33 at 60 fps). */
    public static float getDt() {
        return MinecraftClient.getInstance().getRenderTickCounter().getDynamicDeltaTicks();
    }

    /**
     * Core exponential smoothing: position approaches target frame-rate independently.
     * smoothness 0 = instant, 0.95 = very slow.
     */
    public static float smooth(float current, float target, float smoothness) {
        if (smoothness <= 0f) return target;
        float dt = getDt();
        return (float) ((current - target) * Math.pow(smoothness, dt) + target);
    }

    public static double smooth(double current, double target, float smoothness) {
        if (smoothness <= 0f) return target;
        float dt = getDt();
        return (current - target) * Math.pow(smoothness, dt) + target;
    }

    /** Returns smoothness as 0..1 fraction from a 0..95 slider. */
    public static float factor(SliderSettings setting) {
        return setting.getValue() / 100.0f;
    }
}
