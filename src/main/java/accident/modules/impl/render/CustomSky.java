package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import accident.events.api.EventHandler;
import accident.events.impl.WorldChangeEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.render.shader.SkyRenderer;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomSky extends ModuleStructure {

    @Getter
    private static CustomSky instance;

    // Index the shader branches on. Keep in step with the values above.
    public float getStyleIndex() {
        if (style.isSelected("Aurora")) return 1.0f;
        if (style.isSelected("Galaxy")) return 2.0f;
        if (style.isSelected("Borealis")) return 3.0f;
        if (style.isSelected("Solstice")) return 4.0f;
        if (style.isSelected("Tempest")) return 5.0f;
        return 0.0f;
    }

    public float getSkyRed()   { return ((skyColor.getColor() >> 16) & 0xFF) / 255.0f; }
    public float getSkyGreen() { return ((skyColor.getColor() >> 8)  & 0xFF) / 255.0f; }
    public float getSkyBlue()  { return  (skyColor.getColor()        & 0xFF) / 255.0f; }

    SliderSettings opacity = new SliderSettings("accident.module.customsky.setting.opacity.name", "accident.module.customsky.setting.opacity.desc")
            .setValue(0.92f).range(0.0f, 1.0f);

    SliderSettings fogDensity = new SliderSettings("accident.module.customsky.setting.fogdensity.name", "accident.module.customsky.setting.fogdensity.desc")
            .setValue(0.35f).range(0.0f, 1.0f);

    SliderSettings starBrightness = new SliderSettings("accident.module.customsky.setting.starbrightness.name", "accident.module.customsky.setting.starbrightness.desc")
            .setValue(0.8f).range(0.0f, 1.0f);

    @Getter
    SelectSetting style = new SelectSetting("accident.module.customsky.setting.style.name", "accident.module.customsky.setting.style.desc")
            .value("Space", "Aurora", "Galaxy", "Borealis", "Solstice", "Tempest")
            .selected("Space");

    // Drives the whole palette: the gradient, the nebula and the fog tint, so the
    // sky and the haze under it can never disagree.
    @Getter
    ColorSetting skyColor = new ColorSetting("accident.module.customsky.setting.skycolor.name", "accident.module.customsky.setting.skycolor.desc")
            .value(new java.awt.Color(24, 60, 130).getRGB());

    public CustomSky() {
        super("accident.module.customsky.name", "accident.module.customsky.desc", ModuleCategory.RENDER);
        settings(style, opacity, fogDensity, starBrightness, skyColor);
        instance = this;
    }

    @Override
    public boolean activate() {
        SkyRenderer renderer = SkyRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        updateRendererSettings();
        return false;
    }

    @Override
    public boolean deactivate() {
        SkyRenderer.getInstance().setEnabled(false);
        return false;
    }

    @EventHandler
    public void onWorldChange(WorldChangeEvent event) {
        if (!isState()) return;
        SkyRenderer renderer = SkyRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        updateRendererSettings();
    }

    private void updateRendererSettings() {
        SkyRenderer renderer = SkyRenderer.getInstance();
        renderer.setOpacity(opacity.getValue());
        renderer.setFogDensity(fogDensity.getValue());
        renderer.setStarBrightness(starBrightness.getValue());
        renderer.setSkyRed(getSkyRed());
        renderer.setSkyGreen(getSkyGreen());
        renderer.setSkyBlue(getSkyBlue());
    }
}


