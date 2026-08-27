package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.DisplayEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import accident.events.api.EventHandler;
import accident.events.impl.WorldChangeEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.util.render.shader.CharmsRenderer;
import accident.util.render.shader.GlassEntityRenderer;

import java.awt.Color;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Charms extends ModuleStructure {

    @Getter
    private static Charms instance;

    // ── Общие ─────────────────────────────────────────────────────────────────

    SelectSetting target = new SelectSetting("Цель", "К кому применять эффект")
            .value("Игроки", "Все энтити")
            .selected("Игроки");

    SelectSetting style = new SelectSetting("Стиль", "Визуальный стиль эффекта")
            .value("Обводка", "Стекло", "Вода", "Каустика", "Туманность", "Плазма", "Блум")
            .selected("Обводка");

    BooleanSetting renderSelf = new BooleanSetting("Отображать на себе", "Применять эффект к собственному игроку (актуально в виде от третьего лица)")
            .setValue(true);

    // ── Стиль «Обводка» ───────────────────────────────────────────────────────

    ColorSetting outlineColor = new ColorSetting("Цвет обводки", "Цвет обводки и glow-свечения")
            .value(new Color(100, 180, 255, 230).getRGB())
            .visible(() -> isOutlineStyle());

    SliderSettings outlineThickness = new SliderSettings("Толщина обводки", "Толщина линии обводки (пиксели, 1–4)")
            .range(1, 4)
            .setValue(2)
            .visible(() -> isOutlineStyle());

    BooleanSetting enableFill = new BooleanSetting("Заливка", "Лёгкая полупрозрачная заливка силуэта")
            .setValue(true)
            .visible(() -> isOutlineStyle());

    ColorSetting fillColor = new ColorSetting("Цвет заливки", "Цвет заливки внутри силуэта")
            .value(new Color(100, 180, 255, 80).getRGB())
            .visible(() -> isOutlineStyle() && enableFill.isValue());

    SliderSettings fillAlpha = new SliderSettings("Прозрачность заливки", "Интенсивность заливки (0 = невидимо)")
            .range(0.0f, 0.35f)
            .setValue(0.12f)
            .visible(() -> isOutlineStyle() && enableFill.isValue());

    BooleanSetting enableGlow = new BooleanSetting("Glow", "Мягкое свечение вокруг силуэта")
            .setValue(true)
            .visible(() -> isOutlineStyle());

    SliderSettings glowIntensity = new SliderSettings("Сила glow", "Яркость свечения")
            .range(0.0f, 1.0f)
            .setValue(0.55f)
            .visible(() -> isOutlineStyle() && enableGlow.isValue());

    SliderSettings glowRadius = new SliderSettings("Радиус glow", "Радиус Kawase-размытия свечения")
            .range(1.0f, 6.0f)
            .setValue(3.0f)
            .visible(() -> isOutlineStyle() && enableGlow.isValue());

    SliderSettings glowIterations = new SliderSettings("Качество glow", "Итерации размытия (больше = плавнее)")
            .range(1, 4)
            .setValue(2)
            .visible(() -> isOutlineStyle() && enableGlow.isValue());

    // ── Стиль «Стекло» (бывший GlassChams) ────────────────────────────────────

    SliderSettings glassBlurRadius = new SliderSettings("Сила размытия", "Радиус размытия стекла")
            .setValue(2.5f).range(1.0f, 5.0f)
            .visible(() -> isGlassStyle());

    SliderSettings glassBlurIterations = new SliderSettings("Качество", "Итерации размытия стекла")
            .setValue(3).range(1, 5)
            .visible(() -> isGlassStyle());

    SliderSettings glassSaturation = new SliderSettings("Насыщенность", "Насыщенность цвета стекла")
            .setValue(0).range(0.0f, 2.0f)
            .visible(() -> isGlassStyle());

    BooleanSetting glassEnableTint = new BooleanSetting("Оттенок", "Цветной оттенок эффекта")
            .setValue(false)
            .visible(() -> isShaderStyle());

    SliderSettings glassTintIntensity = new SliderSettings("Сила оттенка", "Интенсивность цветного оттенка")
            .setValue(0.2f).range(0.0f, 0.5f)
            .visible(() -> isGlassStyle() && glassEnableTint.isValue());

    ColorSetting glassTintColor = new ColorSetting("Цвет оттенка", "Цвет оттенка эффекта")
            .value(0xFF00FFFF)
            .visible(() -> isShaderStyle() && glassEnableTint.isValue());

    BooleanSetting glassEnableEdgeGlow = new BooleanSetting("Свечение краёв", "Свечение по краям силуэта")
            .setValue(true)
            .visible(() -> isShaderStyle());

    SliderSettings glassEdgeGlowIntensity = new SliderSettings("Сила свечения краёв", "Интенсивность свечения краёв силуэта")
            .setValue(0.2f).range(0.0f, 1.0f)
            .visible(() -> isShaderStyle() && glassEnableEdgeGlow.isValue());

    // ── Огонь (независимо от стиля) ───────────────────────────────────────────

    // огонь накладывается поверх любого стиля, а не является отдельным стилем -
    // оба рендерера строят одинаковую маску силуэта, поэтому огонь работает с любым из них
    BooleanSetting enableFire = new BooleanSetting("Огонь", "Пламя по силуэту цели")
            .setValue(false);

    SliderSettings fireIntensity = new SliderSettings("Сила огня", "Яркость пламени")
            .setValue(0.8f).range(0.1f, 1.5f)
            .visible(() -> enableFire.isValue());

    SliderSettings fireSpeed = new SliderSettings("Скорость огня", "Скорость движения пламени")
            .setValue(1.0f).range(0.2f, 3.0f)
            .visible(() -> enableFire.isValue());

    SliderSettings fireLength = new SliderSettings("Длина огня", "Высота языков пламени")
            .setValue(0.55f).range(0.1f, 1.0f)
            .visible(() -> enableFire.isValue());

    SliderSettings fireSmoke = new SliderSettings("Дым", "Количество дыма")
            .setValue(0.55f).range(0.0f, 0.8f)
            .visible(() -> enableFire.isValue());

    SliderSettings fireTrailFade = new SliderSettings("Затухание следа", "Насколько долго тянется шлейф")
            .setValue(0.94f).range(0.55f, 0.99f)
            .visible(() -> enableFire.isValue());

    SliderSettings fireSoftness = new SliderSettings("Мягкость огня", "Размытость краёв пламени")
            .setValue(1.3f).range(0.45f, 2.5f)
            .visible(() -> enableFire.isValue());

    SliderSettings fireBlur = new SliderSettings("Размытие огня", "Сила размытия свечения")
            .setValue(1.4f).range(0.2f, 3.0f)
            .visible(() -> enableFire.isValue());

    ColorSetting fireColor = new ColorSetting("Цвет огня", "Цвет пламени")
            .value(0xFFFF7320)
            .visible(() -> enableFire.isValue());

    public Charms() {
        super("Chams", "Обводка / стеклянный эффект на энтити (без проникания сквозь стены)", ModuleCategory.RENDER);
        settings(
                target, style, renderSelf,
                // Outline style
                outlineColor, outlineThickness,
                enableFill, fillColor, fillAlpha,
                enableGlow, glowIntensity, glowRadius, glowIterations,
                // Glass style
                glassBlurRadius, glassBlurIterations, glassSaturation,
                glassEnableTint, glassTintIntensity, glassTintColor,
                glassEnableEdgeGlow, glassEdgeGlowIntensity,
                // Fire, on top of either style
                enableFire, fireIntensity, fireSpeed, fireLength, fireSmoke,
                fireTrailFade, fireSoftness, fireBlur, fireColor
        );
        instance = this;
    }

    private float fireTime() {
        // миллисекунды с начала эпохи не помещаются в float без потери точности, берём остаток
        return (float) ((System.currentTimeMillis() % 1_000_000L) / 1000.0);
    }

    // ── Вспомогательные методы ───────────────────────────────────────────────

    // любой шейдерный стиль, кроме обводки
    public boolean isShaderStyle()  { return !style.getSelected().equals("Обводка"); }
    // именно стиль "Стекло" - часть настроек применяется только к нему
    public boolean isGlassStyle()   { return style.getSelected().equals("Стекло"); }
    public boolean isOutlineStyle() { return !isShaderStyle(); }

    // индекс ветки в glass_composite.fsh, общий с шейдерами рук - 1 пропущен (раньше там был огонь)
    public int styleIndex() {
        return switch (style.getSelected()) {
            case "Вода"       -> 2;
            case "Каустика"   -> 3;
            case "Туманность" -> 4;
            case "Плазма"     -> 5;
            case "Блум"       -> 6;
            default            -> 0;
        };
    }

    // true, если к этому энтити нужно применить эффект Charms
    public boolean shouldApplyTo(EntityRenderState state) {
        if (state instanceof DisplayEntityRenderState) return false;
        if (!renderSelf.isValue() && isSelf(state)) return false;
        return switch (target.getSelected()) {
            case "Игроки"     -> state instanceof PlayerEntityRenderState;
            case "Все энтити" -> true;
            default           -> state instanceof PlayerEntityRenderState;
        };
    }

    // true, если это сам игрок (владелец камеры)
    private static boolean isSelf(EntityRenderState state) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && playerState.id == client.player.getId();
    }

    // ── Активация / деактивация ──────────────────────────────────────────────

    @Override
    public boolean activate() {
        if (isShaderStyle()) {
            GlassEntityRenderer renderer = GlassEntityRenderer.getInstance();
            renderer.invalidate();
            renderer.setEnabled(true);
            pushGlassSettings(renderer);
        } else {
            CharmsRenderer renderer = CharmsRenderer.getInstance();
            renderer.invalidate();
            renderer.setEnabled(true);
            pushSettings(renderer);
        }
        return false;
    }

    @Override
    public boolean deactivate() {
        GlassEntityRenderer.getInstance().setEnabled(false);
        CharmsRenderer.getInstance().setEnabled(false);
        return false;
    }

    @EventHandler
    public void onWorldChange(WorldChangeEvent event) {
        if (!isState()) return;
        if (isShaderStyle()) {
            GlassEntityRenderer renderer = GlassEntityRenderer.getInstance();
            renderer.invalidate();
            renderer.setEnabled(true);
            pushGlassSettings(renderer);
        } else {
            CharmsRenderer renderer = CharmsRenderer.getInstance();
            renderer.invalidate();
            renderer.setEnabled(true);
            pushSettings(renderer);
        }
    }

    // передаёт настройки стиля "Обводка" в CharmsRenderer, вызывается из mixin для каждого энтити
    public void pushSettings(CharmsRenderer renderer) {
        renderer.setOutlineColor(outlineColor.getColor());
        renderer.setOutlineThickness(outlineThickness.getValue());
        renderer.setFillColor(enableFill.isValue() ? fillColor.getColor() : 0x00_000000);
        renderer.setFillAlpha(enableFill.isValue()  ? fillAlpha.getValue()     : 0f);
        renderer.setGlowIntensity(enableGlow.isValue() ? glowIntensity.getValue()  : 0f);
        renderer.setGlowRadius(enableGlow.isValue()    ? glowRadius.getValue()     : 1f);
        renderer.setGlowIterations(enableGlow.isValue() ? glowIterations.getInt()  : 1);

        renderer.setFireEnabled(enableFire.isValue());
        if (enableFire.isValue()) {
            renderer.setFireIntensity(fireIntensity.getValue());
            renderer.setFireSpeed(fireSpeed.getValue());
            renderer.setFireLength(fireLength.getValue());
            renderer.setFireSmoke(fireSmoke.getValue());
            renderer.setFireTrailFade(fireTrailFade.getValue());
            renderer.setFireSoftness(fireSoftness.getValue());
            renderer.setFireBlur(fireBlur.getValue());
            renderer.setFireColor(fireColor.getColor());
            renderer.setFireTime(fireTime());
        }
    }

    // передаёт настройки стиля "Стекло" в GlassEntityRenderer, вызывается из mixin для каждого энтити
    public void pushGlassSettings(GlassEntityRenderer renderer) {
        renderer.setBlurRadius(glassBlurRadius.getValue());
        renderer.setBlurIterations(glassBlurIterations.getInt());
        renderer.setSaturation(glassSaturation.getValue());
        renderer.setReflect(true);
        if (glassEnableTint.isValue()) {
            renderer.setTintColor(glassTintColor.getColor());
            renderer.setTintIntensity(glassTintIntensity.getValue());
        } else {
            renderer.setTintColor(0x00000000);
            renderer.setTintIntensity(0f);
        }
        renderer.setEdgeGlowIntensity(
                glassEnableEdgeGlow.isValue() ? glassEdgeGlowIntensity.getValue() : 0f);
        renderer.setStyle(styleIndex());
        renderer.setTime(fireTime());

        renderer.setFireEnabled(enableFire.isValue());
        if (enableFire.isValue()) {
            renderer.setFireIntensity(fireIntensity.getValue());
            renderer.setFireSpeed(fireSpeed.getValue());
            renderer.setFireLength(fireLength.getValue());
            renderer.setFireSmoke(fireSmoke.getValue());
            renderer.setFireTrailFade(fireTrailFade.getValue());
            renderer.setFireSoftness(fireSoftness.getValue());
            renderer.setFireBlur(fireBlur.getValue());
            renderer.setFireColor(fireColor.getColor());
        }
    }
}
