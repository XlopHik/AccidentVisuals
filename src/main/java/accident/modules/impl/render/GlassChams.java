package accident.modules.impl.render;

import net.minecraft.client.render.entity.state.DisplayEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import accident.events.api.EventHandler;
import accident.events.api.EventManager;
import accident.events.impl.GlassEntityRenderEvent;
import accident.events.impl.WorldChangeEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.render.pipeline.MaskDiffPipeline;
import accident.util.render.shader.GlassEntityRenderer;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GlassChams extends ModuleStructure {

    @Getter
    private static GlassChams instance;

    SelectSetting target = new SelectSetting("accident.module.glasschams.setting.target.name", "accident.module.glasschams.setting.target.desc")
            .value("Игроки", "Все энтити", "Враги")
            .selected("Игроки");

    SliderSettings blurRadius = new SliderSettings("accident.module.glasschams.setting.blurradius.name", "accident.module.glasschams.setting.blurradius.desc")
            .setValue(2.5f).range(1.0f, 5.0f);

    SliderSettings blurIterations = new SliderSettings("accident.module.glasschams.setting.bluriterations.name", "accident.module.glasschams.setting.bluriterations.desc")
            .setValue(3).range(1, 5);

    SliderSettings saturation = new SliderSettings("accident.module.glasschams.setting.saturation.name", "accident.module.glasschams.setting.saturation.desc")
            .setValue(0).range(0.0f, 2.0f);

    BooleanSetting enableTint = new BooleanSetting("accident.module.glasschams.setting.enabletint.name", "accident.module.glasschams.setting.enabletint.desc")
            .setValue(false);

    SliderSettings tintIntensity = new SliderSettings("accident.module.glasschams.setting.tintintensity.name", "accident.module.glasschams.setting.tintintensity.desc")
            .setValue(0.2f).range(0.0f, 0.5f)
            .visible(enableTint::isValue);

    ColorSetting tintColor = new ColorSetting("accident.module.glasschams.setting.tintcolor.name", "accident.module.glasschams.setting.tintcolor.desc")
            .value(0xFF00FFFF)
            .visible(enableTint::isValue);

    BooleanSetting enableEdgeGlow = new BooleanSetting("accident.module.glasschams.setting.enableedgeglow.name", "accident.module.glasschams.setting.enableedgeglow.desc")
            .setValue(true);

    SliderSettings edgeGlowIntensity = new SliderSettings("accident.module.glasschams.setting.edgeglowintensity.name", "accident.module.glasschams.setting.edgeglowintensity.desc")
            .setValue(0.2f).range(0.0f, 1.0f)
            .visible(enableEdgeGlow::isValue);

    // тот же огонь, что и на руках, но по силуэту цели
    BooleanSetting enableFire = new BooleanSetting("accident.module.glasschams.setting.enablefire.name", "accident.module.glasschams.setting.enablefire.desc")
            .setValue(false);

    SliderSettings fireIntensity = new SliderSettings("accident.module.glasschams.setting.fireintensity.name", "accident.module.glasschams.setting.fireintensity.desc")
            .setValue(0.8f).range(0.1f, 1.5f)
            .visible(enableFire::isValue);

    SliderSettings fireSpeed = new SliderSettings("accident.module.glasschams.setting.firespeed.name", "accident.module.glasschams.setting.firespeed.desc")
            .setValue(1.0f).range(0.2f, 3.0f)
            .visible(enableFire::isValue);

    SliderSettings fireLength = new SliderSettings("accident.module.glasschams.setting.firelength.name", "accident.module.glasschams.setting.firelength.desc")
            .setValue(0.55f).range(0.1f, 1.0f)
            .visible(enableFire::isValue);

    SliderSettings fireSmoke = new SliderSettings("accident.module.glasschams.setting.firesmoke.name", "accident.module.glasschams.setting.firesmoke.desc")
            .setValue(0.55f).range(0.0f, 0.8f)
            .visible(enableFire::isValue);

    SliderSettings fireTrailFade = new SliderSettings("accident.module.glasschams.setting.firetrailfade.name", "accident.module.glasschams.setting.firetrailfade.desc")
            .setValue(0.94f).range(0.55f, 0.99f)
            .visible(enableFire::isValue);

    SliderSettings fireSoftness = new SliderSettings("accident.module.glasschams.setting.firesoftness.name", "accident.module.glasschams.setting.firesoftness.desc")
            .setValue(1.3f).range(0.45f, 2.5f)
            .visible(enableFire::isValue);

    SliderSettings fireBlur = new SliderSettings("accident.module.glasschams.setting.fireblur.name", "accident.module.glasschams.setting.fireblur.desc")
            .setValue(1.4f).range(0.2f, 3.0f)
            .visible(enableFire::isValue);

    ColorSetting fireColor = new ColorSetting("accident.module.glasschams.setting.firecolor.name", "accident.module.glasschams.setting.firecolor.desc")
            .value(0xFFFF7320)
            .visible(enableFire::isValue);

    public GlassChams() {
        super("accident.module.glasschams.name", "accident.module.glasschams.desc", ModuleCategory.RENDER);
        settings(target, blurRadius, blurIterations, saturation,
                enableTint, tintIntensity, tintColor,
                enableEdgeGlow, edgeGlowIntensity,
                enableFire, fireIntensity, fireSpeed, fireLength, fireSmoke,
                fireTrailFade, fireSoftness, fireBlur, fireColor);
        instance = this;
    }

    public boolean shouldApplyTo(EntityRenderState state) {
        if (state instanceof DisplayEntityRenderState) return false;

        return switch (target.getSelected()) {
            case "Игроки" -> state instanceof PlayerEntityRenderState;
            case "Все энтити" -> true;
            default -> state instanceof PlayerEntityRenderState;
        };
    }

    @Override
    public boolean activate() {
        EventManager.register(this);
        GlassEntityRenderer renderer = GlassEntityRenderer.getInstance();
        if (renderer != null) {
            renderer.invalidate();
            renderer.setEnabled(true);
            updateRendererSettings();
        }
        return false;
    }

    @Override
    public boolean deactivate() {
        EventManager.unregister(this);
        GlassEntityRenderer renderer = GlassEntityRenderer.getInstance();
        if (renderer != null) {
            renderer.setEnabled(false);
        }
        return false;
    }

    @EventHandler
    public void onWorldChange(WorldChangeEvent event) {
        if (!isState()) return;
        GlassEntityRenderer renderer = GlassEntityRenderer.getInstance();
        if (renderer != null) {
            renderer.invalidate();
            renderer.setEnabled(true);
            updateRendererSettings();
        }
    }

    @EventHandler
    public void onGlassEntityRender(GlassEntityRenderEvent event) {
        if (!isState()) return;

        GlassEntityRenderer renderer = GlassEntityRenderer.getInstance();
        if (renderer == null) return;


        updateRendererSettings();

        if (event.getPhase() == GlassEntityRenderEvent.Phase.PRE) {
            renderer.captureSceneBeforeHands();
        } else if (event.getPhase() == GlassEntityRenderEvent.Phase.POST) {
            renderer.captureSceneAfterHands();
            renderer.renderGlassEffect();
        }
    }

    private void updateRendererSettings() {
        GlassEntityRenderer renderer = GlassEntityRenderer.getInstance();
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

        renderer.setEdgeGlowIntensity(
                enableEdgeGlow.isValue() ? edgeGlowIntensity.getValue() : 0.0f
        );

        // огонь завязан на время, а epoch millis float переполняет и стопорит анимацию -
        // берём тот же wrap, что и у рендерера рук, чтобы держать значение маленьким
        renderer.setTime((float) ((System.currentTimeMillis() % 1_000_000L) / 1000.0));

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


