package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import accident.events.api.EventHandler;
import accident.events.impl.GlassHandsRenderEvent;
import accident.events.impl.WorldChangeEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.render.shader.GlassHandsRenderer;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GlassHands extends ModuleStructure {
    @Getter
    private static GlassHands instance;

    SelectSetting style = new SelectSetting("accident.module.glasshands.setting.style.name", "accident.module.glasshands.setting.style.desc")
            .value("Нет", "Стекло", "Вода", "Каустика", "Туманность", "Плазма", "Блум")
            .selected("Стекло");

    private boolean isStyleEnabled() { return !style.isSelected("Нет"); }

    // индекс 1 зарезервирован (раньше был Огонь, теперь это отдельный тоггл,
    // а не стиль) - остальные индексы не трогаем, чтобы не перенумеровывать шейдер
    private int styleIndex() {
        if (style.isSelected("Вода"))        return 2;
        if (style.isSelected("Каустика"))    return 3;
        if (style.isSelected("Туманность"))  return 4;
        if (style.isSelected("Плазма"))      return 5;
        if (style.isSelected("Блум"))        return 6;
        return 0;
    }

    private boolean isGlassStyle() { return style.isSelected("Стекло"); }

    // ── Огонь (независимый эффект поверх любого стиля) ───────────────────────

    BooleanSetting enableFire = new BooleanSetting("accident.module.glasshands.setting.enablefire.name", "accident.module.glasshands.setting.enablefire.desc")
            .setValue(false);

    SliderSettings fireIntensity = new SliderSettings("accident.module.glasshands.setting.fireintensity.name", "accident.module.glasshands.setting.fireintensity.desc")
            .setValue(0.8f).range(0.1f, 1.5f)
            .visible(enableFire::isValue);

    SliderSettings fireSpeed = new SliderSettings("accident.module.glasshands.setting.firespeed.name", "accident.module.glasshands.setting.firespeed.desc")
            .setValue(1.0f).range(0.2f, 3.0f)
            .visible(enableFire::isValue);

    SliderSettings fireLength = new SliderSettings("accident.module.glasshands.setting.firelength.name", "accident.module.glasshands.setting.firelength.desc")
            .setValue(0.55f).range(0.1f, 1.0f)
            .visible(enableFire::isValue);

    SliderSettings fireSmoke = new SliderSettings("accident.module.glasshands.setting.firesmoke.name", "accident.module.glasshands.setting.firesmoke.desc")
            .setValue(0.55f).range(0.0f, 0.8f)
            .visible(enableFire::isValue);

    ColorSetting fireColor = new ColorSetting("accident.module.glasshands.setting.firecolor.name", "accident.module.glasshands.setting.firecolor.desc")
            .value(0xFFFF7320)
            .visible(enableFire::isValue);

    // затухание накапливается покадрово: 0.84 - хвост исчезает почти сразу
    // (просто свечение на руке), 0.94 - около 0.2с хвоста, максимум - длинный след кометы
    SliderSettings fireTrailFade = new SliderSettings("accident.module.glasshands.setting.firetrailfade.name", "accident.module.glasshands.setting.firetrailfade.desc")
            .setValue(0.94f).range(0.55f, 0.99f)
            .visible(enableFire::isValue);

    SliderSettings fireTrailSoftness = new SliderSettings("accident.module.glasshands.setting.firetrailsoftness.name", "accident.module.glasshands.setting.firetrailsoftness.desc")
            .setValue(1.35f).range(0.45f, 2.5f)
            .visible(enableFire::isValue);

    SliderSettings fireTrailBlur = new SliderSettings("accident.module.glasshands.setting.firetrailblur.name", "accident.module.glasshands.setting.firetrailblur.desc")
            .setValue(1.55f).range(0.2f, 3.0f)
            .visible(enableFire::isValue);

    SliderSettings fireHandFade = new SliderSettings("accident.module.glasshands.setting.firehandfade.name", "accident.module.glasshands.setting.firehandfade.desc")
            .setValue(0.68f).range(0.45f, 0.9f)
            .visible(enableFire::isValue);

    SliderSettings fireHandSoftness = new SliderSettings("accident.module.glasshands.setting.firehandsoftness.name", "accident.module.glasshands.setting.firehandsoftness.desc")
            .setValue(1.3f).range(0.45f, 2.5f)
            .visible(enableFire::isValue);

    SliderSettings fireHandBlur = new SliderSettings("accident.module.glasshands.setting.firehandblur.name", "accident.module.glasshands.setting.firehandblur.desc")
            .setValue(1.4f).range(0.2f, 3.0f)
            .visible(enableFire::isValue);

    SliderSettings blurRadius = new SliderSettings("accident.module.glasshands.setting.blurradius.name", "accident.module.glasshands.setting.blurradius.desc")
            .setValue(2.5f).range(1.0f, 5.0f)
            .visible(this::isGlassStyle);

    SliderSettings blurIterations = new SliderSettings("accident.module.glasshands.setting.bluriterations.name", "accident.module.glasshands.setting.bluriterations.desc")
            .setValue(3).range(1, 5)
            .visible(this::isGlassStyle);

    SliderSettings saturation = new SliderSettings("accident.module.glasshands.setting.saturation.name", "accident.module.glasshands.setting.saturation.desc")
            .setValue(0).range(0.0f, 2.0f)
            .visible(this::isGlassStyle);

    // акцентный цвет общий для всех стилей, но не для огня - у него свой цвет
    BooleanSetting enableTint = new BooleanSetting("accident.module.glasshands.setting.enabletint.name", "accident.module.glasshands.setting.enabletint.desc")
            .setValue(false)
            .visible(this::isStyleEnabled);

    SliderSettings tintIntensity = new SliderSettings("accident.module.glasshands.setting.tintintensity.name", "accident.module.glasshands.setting.tintintensity.desc")
            .setValue(0.2f).range(0.0f, 0.5f)
            .visible(() -> isGlassStyle() && enableTint.isValue());

    ColorSetting tintColor = new ColorSetting("accident.module.glasshands.setting.tintcolor.name", "accident.module.glasshands.setting.tintcolor.desc")
            .value(0xFF00FFFF)
            .visible(() -> isStyleEnabled() && enableTint.isValue());

    BooleanSetting enableEdgeGlow = new BooleanSetting("accident.module.glasshands.setting.enableedgeglow.name", "accident.module.glasshands.setting.enableedgeglow.desc")
            .setValue(true)
            .visible(this::isStyleEnabled);

    SliderSettings edgeGlowIntensity = new SliderSettings("accident.module.glasshands.setting.edgeglowintensity.name", "accident.module.glasshands.setting.edgeglowintensity.desc")
            .setValue(0.2f).range(0.0f, 1.0f)
            .visible(() -> isStyleEnabled() && enableEdgeGlow.isValue());

    BooleanSetting enableWaves = new BooleanSetting("accident.module.glasshands.setting.enablewaves.name", "accident.module.glasshands.setting.enablewaves.desc")
            .setValue(true)
            .visible(this::isGlassStyle);

    SliderSettings waveIntensity = new SliderSettings("accident.module.glasshands.setting.waveintensity.name", "accident.module.glasshands.setting.waveintensity.desc")
            .setValue(0.004f).range(0.001f, 0.01f)
            .visible(() -> isGlassStyle() && enableWaves.isValue());

    public GlassHands() {
        super("accident.module.glasshands.name", "accident.module.glasshands.desc", ModuleCategory.RENDER);
        settings(style, blurRadius, blurIterations, saturation, enableTint, tintIntensity, tintColor,
                enableEdgeGlow, edgeGlowIntensity, enableWaves, waveIntensity,
                enableFire, fireIntensity, fireSpeed, fireLength, fireSmoke, fireColor,
                fireTrailFade, fireTrailSoftness, fireTrailBlur,
                fireHandFade, fireHandSoftness, fireHandBlur);
        instance = this;
    }

    @Override
    public boolean activate() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer != null) {
            renderer.invalidate();
            renderer.setEnabled(true);
            updateRendererSettings();
        }
        return false;
    }

    @Override
    public boolean deactivate() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer != null) {
            renderer.setEnabled(false);
        }
        return false;
    }

    @EventHandler
    public void onWorldChange(WorldChangeEvent event) {
        if (!isState()) return;

        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer != null) {
            renderer.invalidate();
            renderer.setEnabled(true);
            updateRendererSettings();
        }
    }

    @EventHandler
    public void onGlassHandsRender(GlassHandsRenderEvent event) {
        if (!isState()) return;

        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer == null) return;

        updateRendererSettings();

        if (event.getPhase() == GlassHandsRenderEvent.Phase.PRE) {
            renderer.captureSceneBeforeHands();
        } else if (event.getPhase() == GlassHandsRenderEvent.Phase.POST) {
            if (isHoldingMap()) {
                renderer.cancelCapture();
                return;
            }
            renderer.captureSceneAfterHands();
            renderer.renderGlassEffect();
        }
    }

    private boolean isHoldingMap() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().isOf(net.minecraft.item.Items.FILLED_MAP)
                || mc.player.getOffHandStack().isOf(net.minecraft.item.Items.FILLED_MAP);
    }

    private void updateRendererSettings() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        if (renderer == null) return;

        renderer.setBlurRadius(blurRadius.getValue());
        renderer.setBlurIterations(blurIterations.getInt());
        renderer.setSaturation(saturation.getValue());
        renderer.setReflect(true);

        if (enableTint.isValue()) {
            renderer.setTintColor(tintColor.getColor());
            renderer.setTintIntensity(tintIntensity.getValue());
        } else {
            renderer.setTintColor(0x00000000);
            renderer.setTintIntensity(0.0f);
        }

        if (enableEdgeGlow.isValue()) {
            renderer.setEdgeGlowIntensity(edgeGlowIntensity.getValue());
        } else {
            renderer.setEdgeGlowIntensity(0.0f);
        }

        if (enableWaves.isValue()) {
            renderer.setWaveIntensity(waveIntensity.getValue());
        } else {
            renderer.setWaveIntensity(0.0f);
        }

        // сырые эпоховые секунды не влезают в точность float - всё замирает,
        // поэтому берём остаток по модулю
        renderer.setTime((float) ((System.currentTimeMillis() % 1_000_000L) / 1000.0));
        renderer.setStyle(styleIndex());
        renderer.setStyleEnabled(isStyleEnabled());

        renderer.setFireEnabled(enableFire.isValue());
        if (enableFire.isValue()) {
            renderer.setFireIntensity(fireIntensity.getValue());
            renderer.setFireSpeed(fireSpeed.getValue());
            renderer.setFireLength(fireLength.getValue());
            renderer.setFireSmoke(fireSmoke.getValue());
            renderer.setFireColor(fireColor.getColor());
            renderer.setFireTrailFade(fireTrailFade.getValue());
            renderer.setFireTrailSoftness(fireTrailSoftness.getValue());
            renderer.setFireTrailBlur(fireTrailBlur.getValue());
            renderer.setFireHandFade(fireHandFade.getValue());
            renderer.setFireHandSoftness(fireHandSoftness.getValue());
            renderer.setFireHandBlur(fireHandBlur.getValue());
        }
    }
}


