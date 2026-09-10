package accident.modules.impl.render;

import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.MultiSelectSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.item.ItemStack;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Cosmetics extends ModuleStructure {

    private static Cosmetics instance;

    public static Cosmetics getInstance() {
        return instance;
    }

    public final SelectSetting model = new SelectSetting(
            "accident.module.cosmetics.setting.model.name",
            "accident.module.cosmetics.setting.model.desc")
            .value("cosmetic_helmet")
            .selected("cosmetic_helmet");

    /** Независимые слоты: очки и шапку можно надеть одновременно. */
    public final MultiSelectSetting pieces = new MultiSelectSetting(
            "accident.module.cosmetics.setting.pieces.name",
            "accident.module.cosmetics.setting.pieces.desc")
            .value("Hat", "Glasses", "Shield")
            .selected("Hat", "Glasses", "Shield");

    public final SliderSettings shieldPositionX = shieldSlider("Щит: позиция X", "Смещение щита влево/вправо", -1.0f, 1.0f, 0.0f);
    public final SliderSettings shieldPositionY = shieldSlider("Щит: позиция Y", "Смещение щита вверх/вниз", -1.0f, 1.0f, 0.0f);
    public final SliderSettings shieldPositionZ = shieldSlider("Щит: позиция Z", "Смещение щита вперёд/назад", -1.0f, 1.0f, 0.0f);
    public final SliderSettings shieldRotationX = shieldSlider("Щит: поворот X", "Поворот щита вокруг X", -180.0f, 180.0f, 180.0f);
    public final SliderSettings shieldRotationY = shieldSlider("Щит: поворот Y", "Поворот щита вокруг Y", -180.0f, 180.0f, 0.0f);
    public final SliderSettings shieldRotationZ = shieldSlider("Щит: поворот Z", "Поворот щита вокруг Z", -180.0f, 180.0f, 90.0f);
    public final SliderSettings shieldScale = shieldSlider("Щит: масштаб", "Размер щита", 0.1f, 2.0f, 1.0f);

    public final SliderSettings headPositionX = headSlider("Шапка/очки: позиция X", "Смещение на голове по X", -1.0f, 1.0f, 0.0f);
    public final SliderSettings headPositionY = headSlider("Шапка/очки: позиция Y", "Смещение на голове по Y", -1.0f, 1.0f, 0.25f);
    public final SliderSettings headPositionZ = headSlider("Шапка/очки: позиция Z", "Смещение на голове по Z", -1.0f, 1.0f, 0.0f);
    public final SliderSettings headRotationX = headSlider("Шапка/очки: поворот X", "Поворот на голове вокруг X", -180.0f, 180.0f, 180.0f);
    public final SliderSettings headRotationY = headSlider("Шапка/очки: поворот Y", "Поворот на голове вокруг Y", -180.0f, 180.0f, 180.0f);
    public final SliderSettings headRotationZ = headSlider("Шапка/очки: поворот Z", "Поворот на голове вокруг Z", -180.0f, 180.0f, 0.0f);
    public final SliderSettings headScale = headSlider("Шапка/очки: масштаб", "Размер шапки и очков", 0.1f, 2.0f, 0.60f);

    public Cosmetics() {
        super("accident.module.cosmetics.name", "accident.module.cosmetics.desc", ModuleCategory.RENDER);
        instance = this;
        settings(model, pieces, shieldPositionX, shieldPositionY, shieldPositionZ,
                shieldRotationX, shieldRotationY, shieldRotationZ, shieldScale,
                headPositionX, headPositionY, headPositionZ,
                headRotationX, headRotationY, headRotationZ, headScale);
    }

    private SliderSettings shieldSlider(String name, String description, float min, float max, float value) {
        return new SliderSettings(name, description)
                .range(min, max)
                .setValue(value)
                .visible(() -> pieces.isSelected("Shield"));
    }

    private SliderSettings headSlider(String name, String description, float min, float max, float value) {
        return new SliderSettings(name, description)
                .range(min, max)
                .setValue(value)
                .visible(() -> pieces.isSelected("Hat") || pieces.isSelected("Glasses"));
    }

    /** Используется миксинами FeatureRenderer/RenderState (косметика на голове игрока) */
    public boolean shouldApplyTo(PlayerLikeEntity player) {
        return player == MinecraftClient.getInstance().player;
    }

    /** Используется MixinItemRenderer (внешний вид предмета в руке/инвентаре/на земле) */
    public ItemStack getRenderStack(ItemStack stack) {
        if (!isEnabled() || stack.isEmpty()) {
            return stack;
        }
        // здесь остаётся ваша исходная логика подмены для отображения в руке,
        // если хотите, чтобы шлем и в инвентаре/руке выглядел иначе
        return stack;
    }
}
