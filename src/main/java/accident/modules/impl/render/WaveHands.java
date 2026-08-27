package accident.modules.impl.render;

import accident.events.api.EventHandler;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;
import accident.util.render.shader.WaveHandsRenderer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WaveHands extends ModuleStructure {

    @Getter
    private static WaveHands instance;

    SliderSettings waveSpeed = new SliderSettings("accident.module.wavehands.setting.wavespeed.name", "accident.module.wavehands.setting.wavespeed.desc")
            .range(0.1f, 3.0f).setValue(1.0f);

    SliderSettings waveStrength = new SliderSettings("accident.module.wavehands.setting.wavestrength.name", "accident.module.wavehands.setting.wavestrength.desc")
            .range(0.0f, 1.0f).setValue(0.5f);

    BooleanSetting useOriginalBase = new BooleanSetting("accident.module.wavehands.setting.useoriginalbase.name", "accident.module.wavehands.setting.useoriginalbase.desc")
            .setValue(true);

    BooleanSetting multiplyByOriginal = new BooleanSetting("accident.module.wavehands.setting.multiplybyoriginal.name", "accident.module.wavehands.setting.multiplybyoriginal.desc")
            .setValue(true);

    ColorSetting effectColor = new ColorSetting("accident.module.wavehands.setting.effectcolor.name", "accident.module.wavehands.setting.effectcolor.desc")
            .value(0xFF00FFFF); // Циан по умолчанию

    public WaveHands() {
        super("accident.module.wavehands.name", "accident.module.wavehands.desc", ModuleCategory.RENDER);
        settings(waveSpeed, waveStrength, useOriginalBase, multiplyByOriginal, effectColor);
        instance = this;
    }

    @Override
    public boolean activate() {
        WaveHandsRenderer.getInstance().setEnabled(true);
        return false;
    }

    @Override
    public boolean deactivate() {
        WaveHandsRenderer.getInstance().setEnabled(false);
        return false;
    }

    public void updateRenderer() {
        WaveHandsRenderer renderer = WaveHandsRenderer.getInstance();
        renderer.setSpeed(waveSpeed.getValue());
        renderer.setStrength(waveStrength.getValue());
        renderer.setUseOriginalBase(useOriginalBase.isValue());
        renderer.setMultiplyByOriginal(multiplyByOriginal.isValue());
        renderer.setColor(effectColor.getColor());
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (!isState() || mc.world == null) return;

        // Обновляем параметры рендерера из настроек
        WaveHandsRenderer renderer = WaveHandsRenderer.getInstance();
        renderer.setSpeed(waveSpeed.getValue());
        renderer.setStrength(waveStrength.getValue());
        renderer.setUseOriginalBase(useOriginalBase.isValue());
        renderer.setMultiplyByOriginal(multiplyByOriginal.isValue());
        renderer.setColor(effectColor.getColor());

        // Получаем текстуры из основного фреймбуфера
        var fb = mc.getFramebuffer();
        if (fb != null && fb.getColorAttachment() != null && fb.getDepthAttachment() != null) {
            renderer.render(fb.getColorAttachmentView(), fb.getDepthAttachmentView());
        }
    }
}


