package accident.screens.clickgui.cs;

import net.minecraft.client.gui.DrawContext;
import accident.modules.module.setting.Setting;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.screens.clickgui.impl.settingsrender.ColorComponent;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.util.HashMap;
import java.util.Map;

// рисует сеттинг как в референсном клиенте, а не как дропдаун-меню - те под узкую колонку заточены
// все размеры взяты оттуда же, поделены пополам под наш GUI-скейл и умножены на масштаб вызывающего кода
final class CsSettingRenderer {

    private CsSettingRenderer() {}

    private static final float TITLE_SIZE = 7f;
    private static final float VALUE_SIZE = 6f;
    private static final float BLOCK_SPACING = 6f;
    private static final float ROW_H = 12.5f;
    private static final float ROW_RADIUS = 3.5f;
    private static final float OUTLINE = 1.25f;
    private static final float CHECKBOX = 9f;
    private static final float CHECKBOX_RADIUS = 3f;
    private static final float TRACK_H = 2f;
    private static final float TRACK_RADIUS = 1f;
    private static final float KNOB = 2f;
    private static final float KNOB_RING = 0.9f;

    // цвета взяты напрямую из ThemeData.createPalette() референса, не подобраны на глаз
    private static final int TEXT_TITLE = 0xDADCE2;   // text.tone(200)
    private static final int SURFACE_600 = 0x1A1B1D;  // surfaceBackground.tone(600)
    private static final int SURFACE_700 = 0x18191A;  // surfaceBackground.tone(700)
    private static final int SURFACE_900 = 0x0F1011;  // surfaceBackground.tone(900)
    private static final int OUTLINE_400 = 0x222325;  // surfaceOutline.tone(400)

    // раньше цветовой свотч был просто декорацией без кликов - теперь делегирует в тот же
    // ColorComponent что и дропдаун ClickGUI, кэшируется на сеттинг т.к. хранит своё состояние между кадрами
    private static final Map<ColorSetting, ColorComponent> COLOR_COMPONENTS = new HashMap<>();

    static ColorComponent colorComponentFor(ColorSetting setting) {
        return COLOR_COMPONENTS.computeIfAbsent(setting, ColorComponent::new);
    }

    private static float titleHeight(float s) {
        return Fonts.TEST.getHeight(TITLE_SIZE * s);
    }

    private static float colorRowY(float titleY, float s) {
        return titleY + titleHeight(s) + BLOCK_SPACING * s;
    }

    /** Height this setting will occupy, so the card can be sized before drawing. */
    static float height(Setting setting, float s) {
        float h = titleHeight(s);

        if (setting instanceof SliderSettings) {
            h += (BLOCK_SPACING + TRACK_H) * s;
        } else if (setting instanceof SelectSetting) {
            h += (BLOCK_SPACING + ROW_H) * s;
        } else if (setting instanceof ColorSetting color) {
            ColorComponent comp = colorComponentFor(color);
            comp.size(1f, ROW_H * s);
            h += BLOCK_SPACING * s + comp.getTotalHeight();
        }

        // чекбокс стоит рядом с тайтлом, а не под ним - поэтому только задаёт минимум высоты
        if (setting instanceof BooleanSetting) {
            h = Math.max(h, CHECKBOX * s);
        }

        return h;
    }

    static float draw(DrawContext context, Setting setting,
                      float x, float y, float width, float s,
                      float alpha, int accent, int mouseX, int mouseY) {

        float cursorY = y;

        Fonts.TEST.draw(setting.getName(), x, cursorY, TITLE_SIZE * s,
                (clamp((int) (alpha * 245)) << 24) | TEXT_TITLE);

        if (setting instanceof SliderSettings slider) {
            String value = formatValue(slider);
            float valueW = Fonts.TEST.getWidth(value, VALUE_SIZE * s);
            Fonts.TEST.draw(value, x + width - valueW, cursorY + 0.5f * s, VALUE_SIZE * s,
                    (clamp((int) (alpha * 255)) << 24) | accent);
        }

        cursorY += titleHeight(s);

        if (setting instanceof BooleanSetting bool) {
            float box = CHECKBOX * s;
            drawCheckbox(x + width - box, y + (height(setting, s) - box) / 2f, s,
                    bool.isValue(), alpha, accent);
        } else if (setting instanceof SliderSettings slider) {
            cursorY += BLOCK_SPACING * s;
            drawSlider(x, cursorY, width, s, slider, alpha, accent);
        } else if (setting instanceof SelectSetting select) {
            cursorY += BLOCK_SPACING * s;
            drawRow(x, cursorY, width, s, select.getSelected(), alpha, true);
        } else if (setting instanceof ColorSetting color) {
            cursorY += BLOCK_SPACING * s;
            ColorComponent comp = colorComponentFor(color);
            comp.position(x, cursorY);
            comp.size(width, ROW_H * s);
            comp.setAlphaMultiplier(alpha);
            comp.render(context, mouseX, mouseY, 0f);
        }

        return height(setting, s);
    }

    private static void drawCheckbox(float x, float y, float s, boolean checked,
                                     float alpha, int accent) {
        float box = CHECKBOX * s;
        int fill = checked ? accent : SURFACE_700;
        int border = checked ? accent : OUTLINE_400;

        Render2D.rect(x, y, box, box,
                (clamp((int) (alpha * (checked ? 235 : 180))) << 24) | fill, CHECKBOX_RADIUS * s);
        Render2D.outline(x, y, box, box, OUTLINE * s,
                (clamp((int) (alpha * 150)) << 24) | border, CHECKBOX_RADIUS * s);

        if (checked) {
            // галочки нет в шрифтах и rect нельзя повернуть, поэтому тик собран из мелких квадратов
            float cx = x + box / 2f;
            float cy = y + box / 2f;
            float p = 0.75f * s;
            int tick = (clamp((int) (alpha * 255)) << 24) | 0xFFFFFF;

            for (int i = 0; i < 2; i++) {
                Render2D.rect(cx - 2.4f * s + i * p, cy - 0.2f * s + i * p, p, p, tick, 0f);
            }
            for (int i = 0; i < 4; i++) {
                Render2D.rect(cx - 1.0f * s + i * p, cy + 1.3f * s - i * p, p, p, tick, 0f);
            }
        }
    }

    private static void drawSlider(float x, float y, float width, float s,
                                   SliderSettings slider, float alpha, int accent) {
        float range = Math.max(1e-4f, slider.getMax() - slider.getMin());
        float progress = clamp01((slider.getValue() - slider.getMin()) / range);

        float trackH = TRACK_H * s;
        Render2D.rect(x, y, width, trackH,
                (clamp((int) (alpha * 220)) << 24) | SURFACE_900, TRACK_RADIUS * s);

        float inset = (KNOB + KNOB_RING) * s;
        float travel = Math.max(0f, width - inset * 2f);
        Render2D.rect(x, y, progress * travel + inset, trackH,
                (clamp((int) (alpha * 255)) << 24) | accent, TRACK_RADIUS * s);

        float knobX = x + inset + progress * travel;
        float knobY = y + trackH / 2f;
        float outer = (KNOB + KNOB_RING) * s;
        float inner = KNOB * s;

        Render2D.rect(knobX - outer, knobY - outer, outer * 2f, outer * 2f,
                (clamp((int) (alpha * 255)) << 24) | accent, outer);
        Render2D.rect(knobX - inner, knobY - inner, inner * 2f, inner * 2f,
                (clamp((int) (alpha * 255)) << 24) | SURFACE_900, inner);
    }

    private static void drawRow(float x, float y, float width, float s,
                                String label, float alpha, boolean chevron) {
        float rowH = ROW_H * s;

        Render2D.rect(x, y, width, rowH,
                (clamp((int) (alpha * 230)) << 24) | SURFACE_600, ROW_RADIUS * s);
        Render2D.outline(x, y, width, rowH, OUTLINE * s,
                (clamp((int) (alpha * 90)) << 24) | OUTLINE_400, ROW_RADIUS * s);

        // цвет как в drawValueText() референса - на тон темнее тайтла
        float textY = y + rowH / 2f - Fonts.TEST.getHeight(TITLE_SIZE * s) / 2f;
        Fonts.TEST.draw(label == null ? "" : label, x + 5f * s, textY, TITLE_SIZE * s,
                (clamp((int) (alpha * 245)) << 24) | 0xC5C6C8);

        if (chevron) {
            // две полоски рядом выглядели бы как тире, а не шеврон - поэтому стрелка
            // собрана из рядов убывающей ширины, получается треугольник вниз
            float cx = x + width - 6f * s;
            float cy = y + rowH / 2f - 1.2f * s;
            int c = (clamp((int) (alpha * 190)) << 24) | TEXT_TITLE;

            int rows = 4;
            float rowH2 = 0.7f * s;
            float startW = 5f * s;
            for (int i = 0; i < rows; i++) {
                float w = startW * (1f - i / (float) rows);
                Render2D.rect(cx - w / 2f, cy + i * rowH2, w, rowH2, c, 0f);
            }
        }
    }

    // ---- interaction ----------------------------------------------------

    /** Returns true when the click belonged to this setting. */
    static boolean click(Setting setting, float x, float y, float width, float s,
                         double mouseX, double mouseY, int button) {

        float h = height(setting, s);

        if (setting instanceof BooleanSetting bool) {
            if (inside(mouseX, mouseY, x, y, width, h)) {
                bool.setValue(!bool.isValue());
                return true;
            }
            return false;
        }

        if (setting instanceof SelectSetting select) {
            if (!inside(mouseX, mouseY, x, y, width, h)) return false;
            // просто перебирает значения по кругу, а не открывает список - сетка пока
            // не умеет рисовать попап поверх остальных карточек и вне скролл-клипа
            var options = select.getList();
            if (options == null || options.isEmpty()) return true;
            int index = options.indexOf(select.getSelected());
            int next = button == 1
                    ? (index - 1 + options.size()) % options.size()
                    : (index + 1) % options.size();
            select.setSelected(options.get(next));
            return true;
        }

        if (setting instanceof ColorSetting color) {
            ColorComponent comp = colorComponentFor(color);
            comp.position(x, colorRowY(y, s));
            comp.size(width, ROW_H * s);
            return comp.mouseClicked(mouseX, mouseY, button);
        }

        return false;
    }

    /** Sliders keep responding while the button is held, so dragging works. */
    static boolean drag(Setting setting, float x, float y, float width, float s,
                        double mouseX, double mouseY) {
        if (!(setting instanceof SliderSettings slider)) return false;

        float h = height(setting, s);
        if (!inside(mouseX, mouseY, x, y, width, h)) return false;

        float inset = (KNOB + KNOB_RING) * s;
        float travel = Math.max(1e-4f, width - inset * 2f);
        float progress = clamp01((float) ((mouseX - (x + inset)) / travel));

        float value = slider.getMin() + progress * (slider.getMax() - slider.getMin());
        if (slider.isInteger()) value = Math.round(value);
        slider.setValue(value);
        return true;
    }

    // ColorComponent сам следит за своим состоянием драга/ввода, поэтому просто
    // рассылаем событие всем закэшированным - активным сработает, остальные проигнорируют
    static boolean forwardColorDrag(double mouseX, double mouseY, int button, double dX, double dY) {
        boolean handled = false;
        for (ColorComponent comp : COLOR_COMPONENTS.values()) {
            if (comp.mouseDragged(mouseX, mouseY, button, dX, dY)) handled = true;
        }
        return handled;
    }

    static boolean forwardColorRelease(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (ColorComponent comp : COLOR_COMPONENTS.values()) {
            if (comp.mouseReleased(mouseX, mouseY, button)) handled = true;
        }
        return handled;
    }

    static boolean forwardColorKey(int key, int scanCode, int modifiers) {
        for (ColorComponent comp : COLOR_COMPONENTS.values()) {
            if (comp.keyPressed(key, scanCode, modifiers)) return true;
        }
        return false;
    }

    static boolean forwardColorChar(char chr, int modifiers) {
        for (ColorComponent comp : COLOR_COMPONENTS.values()) {
            if (comp.charTyped(chr, modifiers)) return true;
        }
        return false;
    }

    // ---- helpers --------------------------------------------------------

    private static String formatValue(SliderSettings slider) {
        float v = slider.getValue();
        return v % 1f == 0f ? String.valueOf((int) v) : String.format("%.2f", v);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
