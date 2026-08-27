package accident.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import accident.util.interfaces.AbstractSettingComponent;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.awt.*;

public class CheckboxComponent extends AbstractSettingComponent {
    private final BooleanSetting booleanSetting;
    private float checkAnimation = 0f;
    private float hoverAnimation = 0f;

    public CheckboxComponent(BooleanSetting setting) {
        super(setting);
        this.booleanSetting = setting;
        this.checkAnimation = setting.isValue() ? 1f : 0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHover(mouseX, mouseY);

        float hoverTarget = hovered ? 1f : 0f;
        hoverAnimation += (hoverTarget - hoverAnimation) * 0.2f;
        hoverAnimation = Math.max(0f, Math.min(1f, hoverAnimation));

        float target = booleanSetting.isValue() ? 1f : 0f;
        if (booleanSetting.isValue()) {
            checkAnimation += (target - checkAnimation) * 0.4f;
        } else {
            checkAnimation = 0f;
        }
        checkAnimation = Math.max(0f, Math.min(1f, checkAnimation));

        Fonts.TEST.draw(booleanSetting.getName(), x + 3.7f, y + height / 2 - 5f, 6,
                applyAlpha(new Color(210, 210, 220, 200)).getRGB());


        float checkboxSize = 10f;
        float checkboxX = x + width - checkboxSize - 2;
        float checkboxY = y + height / 2 - checkboxSize / 2;

        int bgAlpha = 20 + (int)(hoverAnimation * 20);
        Color bgColor = new Color(55, 55, 60, bgAlpha);
        Render2D.rect(checkboxX, checkboxY, checkboxSize, checkboxSize,
                applyAlpha(bgColor).getRGB(), 3f);

        int outlineAlpha = 60 + (int)(hoverAnimation * 40);
        Color outlineColor = new Color(100, 100, 110, outlineAlpha);
        Render2D.outline(checkboxX, checkboxY, checkboxSize, checkboxSize, 0.5f,
                applyAlpha(outlineColor).getRGB(), 3f);

        if (checkAnimation > 0.01f) {
            int checkAlpha = (int)((140 + checkAnimation * 115) * alphaMultiplier);

            float iconSize = 7f;
            float iconX = checkboxX + (checkboxSize - iconSize) / 2f - 0.5f;
            float iconY = checkboxY + (checkboxSize - iconSize) / 2f + 0.5f;

            Fonts.SETTINGSICONS.draw("\uE006", iconX, iconY, iconSize,
                    new Color(220, 220, 225, checkAlpha).getRGB());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHover(mouseX, mouseY) && button == 0) {
            booleanSetting.setValue(!booleanSetting.isValue());
            return true;
        }
        return false;
    }

    @Override public void tick() {}

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}