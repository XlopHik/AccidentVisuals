package accident.modules.impl.render;

import lombok.Getter;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import accident.events.api.EventHandler;
import accident.events.impl.DrawEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.render.Render2D;

import java.awt.*;

public class Crosshair extends ModuleStructure {

    private final SliderSettings thickness = new SliderSettings("accident.module.crosshair.setting.thickness.name", "accident.module.crosshair.setting.thickness.desc")
            .range(0.5f, 3.0f).setValue(1.0f);

    private final SliderSettings length = new SliderSettings("accident.module.crosshair.setting.length.name", "accident.module.crosshair.setting.length.desc")
            .range(1.0f, 8.0f).setValue(3.0f);

    private final SliderSettings gap = new SliderSettings("accident.module.crosshair.setting.gap.name", "accident.module.crosshair.setting.gap.desc")
            .range(0.0f, 5.0f).setValue(2.0f);

    private final BooleanSetting dynamicGap = new BooleanSetting("accident.module.crosshair.setting.dynamicgap.name", "accident.module.crosshair.setting.dynamicgap.desc")
            .setValue(false);

    private final BooleanSetting useEntityColor = new BooleanSetting("accident.module.crosshair.setting.useentitycolor.name", "accident.module.crosshair.setting.useentitycolor.desc")
            .setValue(false);

    private final BooleanSetting showInThirdPerson = new BooleanSetting("accident.module.crosshair.setting.showinthirdperson.name", "accident.module.crosshair.setting.showinthirdperson.desc")
            .setValue(false);

    @Getter
    private static Crosshair instance;

    public Crosshair() {
        super("accident.module.crosshair.name", "accident.module.crosshair.desc", ModuleCategory.RENDER);
        settings(thickness, length, gap, dynamicGap, useEntityColor, showInThirdPerson);
        instance = this;
    }

    // Проверка на невидимость цели
    private boolean isTargetVisible() {
        if (mc.crosshairTarget == null) return false;
        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return false;

        EntityHitResult hitResult = (EntityHitResult) mc.crosshairTarget;
        if (!(hitResult.getEntity() instanceof LivingEntity living)) return false;

        return !living.isInvisible() && !living.isInvisibleTo(mc.player);
    }

    @EventHandler
    public void onDraw(DrawEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        Perspective perspective = mc.options.getPerspective();
        boolean isThirdPerson = perspective == Perspective.THIRD_PERSON_BACK ||
                perspective == Perspective.THIRD_PERSON_FRONT;

        if (perspective != Perspective.FIRST_PERSON &&
                (!showInThirdPerson.isValue() || !isThirdPerson)) {
            return;
        }

        float x = mc.getWindow().getScaledWidth()  / 2f;
        float y = mc.getWindow().getScaledHeight() / 2f;

        float currentGap = gap.getValue();

        if (dynamicGap.isValue()) {
            float cooldown = 1 - mc.player.getAttackCooldownProgress(0);
            currentGap += 8 * cooldown;
        }

        float t = thickness.getValue();
        float l = length.getValue();

        // Проверка: красный цвет ТОЛЬКО если цель видима
        boolean onEntity = useEntityColor.isValue()
                && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.ENTITY
                && isTargetVisible();  // <-- ДОБАВЛЕНА ПРОВЕРКА НА ВИДИМОСТЬ

        int color = onEntity
                ? new Color(255, 80, 80, 255).getRGB()
                : new Color(255, 255, 255, 255).getRGB();

        Render2D.rect(x - t / 2, y - currentGap - l, t, l, color, 0);
        Render2D.rect(x - t / 2, y + currentGap,     t, l, color, 0);
        Render2D.rect(x - currentGap - l, y - t / 2, l, t, color, 0);
        Render2D.rect(x + currentGap,     y - t / 2, l, t, color, 0);
    }
}


