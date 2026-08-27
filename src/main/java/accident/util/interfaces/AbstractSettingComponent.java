package accident.util.interfaces;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import accident.modules.module.setting.Setting;
import accident.util.render.font.Font;
import accident.util.render.font.Fonts;

import java.awt.*;

@Getter
@Setter
@RequiredArgsConstructor
public abstract class AbstractSettingComponent extends AbstractComponent {
    private final Setting setting;
    private boolean wexsideStyle = false;
    private boolean bindPopupCompact;
    private String popupLabelOverride;
    @Setter
    protected float alphaMultiplier = 1f;

    protected int applyAlpha(int color, float extraAlpha) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int newAlpha = Math.max(0, Math.min(255, (int)(a * alphaMultiplier * extraAlpha)));
        return (newAlpha << 24) | (r << 16) | (g << 8) | b;
    }

    protected int applyAlpha(int color) { return applyAlpha(color, 1f); }

    protected Color applyAlpha(Color color) {
        int newAlpha = Math.max(0, Math.min(255, (int)(color.getAlpha() * alphaMultiplier)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), newAlpha);
    }

    public float getTotalHeight() {
        return height;
    }

    /** Wex-меню: Montserrat; обычная панель ClickGui: Inter Semibold (стабильная отрисовка). */
    protected accident.util.render.font.Font fontSemi() {
        return wexsideStyle ? Fonts.TEST : Fonts.TEST;
    }

    /** Wex: Montserrat; обычная GUI: Inter Medium. */
    protected accident.util.render.font.Font fontMed() {
        return wexsideStyle ? Fonts.TEST : Fonts.TEST;
    }

    /** Поля ввода / плотные подписи: Wex — mntsb, иначе прежний BOLD. */
    protected accident.util.render.font.Font fontBold() {
        return wexsideStyle ? Fonts.TEST : Fonts.BOLD;
    }

    /** Значение слайдера: как в WexSide — semibold; в обычной GUI — medium. */
    protected Font fontSliderValue() {
        return wexsideStyle ? Fonts.TEST : Fonts.TEST;
    }

    protected String labelForPopupRow() {
        if (popupLabelOverride != null && !popupLabelOverride.isEmpty()) return popupLabelOverride;
        return getSetting().getName();
    }

    protected Color applyAlpha(Color color, float extraAlpha) {
        int newAlpha = Math.max(0, Math.min(255, (int)(color.getAlpha() * alphaMultiplier * extraAlpha)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), newAlpha);
    }
}