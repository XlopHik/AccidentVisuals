package accident.screens.clickgui.cs;

import net.minecraft.client.gui.DrawContext;
import accident.Initialization;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.Setting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.render.shader.Scissor;
import accident.util.string.KeyHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// второй лейаут ClickGUI: одно окно по центру, категории табами сверху,
// модули выбранной категории - карточками в сетке.
// все размеры ниже в масштабе рефки (960 = её 1920x1080 пополам под наш Render2D),
// потом домножаются на uiScale() под текущее разрешение - без этого окно
// на маленьком окне игры вылезало шире экрана
public class CsGui {

    private static final float DESIGN_WIDTH = 960f;

    private static final float PANEL_W = 460f;
    private static final float PANEL_H = 275f;
    private static final float HEADER_H = 30f;
    private static final float CARD_W = 126f;
    private static final float CARD_GAP = 4f;
    private static final float CARD_HEADER_H = 24f;
    private static final float CARD_RADIUS = 6f;
    private static final float CARD_OUTLINE = 1.25f;
    private static final float PAD = 7f;
    private static final float SWITCH_W = 12f;
    private static final float SWITCH_H = 8.5f;
    private static final int COLUMNS = 3;

    // цвета из рефки (ThemeData.createPalette/ModuleCard) - "Expensive Dark" это
    // нейтральный почти-чёрный, без синеватого оттенка; хедер - тот же фон, просто с разделителем
    private static final int COLOR_PANEL = 0x0F1011;
    private static final int COLOR_PANEL_OUTLINE = 0x17181A;
    private static final int COLOR_HEADER_DIVIDER = 0x18191A;
    private static final int COLOR_CARD = 0x151617;
    private static final int COLOR_TEXT = 0xE3E4E7;
    private static final int COLOR_TEXT_DIM = 0x868791;
    private static final int COLOR_TRACK = 0x1A1B1D;

    private static final ModuleCategory[] CATEGORIES = {
            ModuleCategory.MOVEMENT, ModuleCategory.RENDER,
            ModuleCategory.MISC, ModuleCategory.HUD, ModuleCategory.LUA
    };

    private ModuleCategory activeCategory = ModuleCategory.MOVEMENT;

    private float scroll = 0f;
    private float smoothScroll = 0f;
    private float contentHeight = 0f;

    private final Map<ModuleStructure, Float> toggleAnim = new HashMap<>();

    private Setting draggingSlider;
    private float[] draggingBounds;

    // The module whose bind chip is currently listening for a key press.
    private ModuleStructure bindingModule;

    // куда встала каждая карточка в этом кадре - клики читают отсюда, а не пересчитывают
    // лейаут заново (высоты зависят от анимации, пересчёт разъедется на кадр)
    private final List<CardBounds> lastLayout = new ArrayList<>();

    private record CardBounds(ModuleStructure module, float x, float y, float width, float headerH) {}

    private static float uiScale() {
        return Render2D.getFixedScaledWidth() / DESIGN_WIDTH;
    }

    // ---- render ---------------------------------------------------------

    public void render(DrawContext context, int mouseX, int mouseY, float alpha) {
        if (alpha <= 0.01f) return;

        float s = uiScale();
        float panelW = PANEL_W * s;
        float panelH = PANEL_H * s;
        float panelX = (Render2D.getFixedScaledWidth() - panelW) / 2f;
        float panelY = (Render2D.getFixedScaledHeight() - panelH) / 2f;

        renderChrome(panelX, panelY, panelW, panelH, s, mouseX, mouseY, alpha);

        float bodyY = panelY + HEADER_H * s;
        float bodyH = panelH - HEADER_H * s;

        updateScroll(bodyH);

        Scissor.enableForClickGuiOverlay(panelX, bodyY, panelW, bodyH);
        renderCards(context, panelX, panelW, bodyY, bodyH, s, mouseX, mouseY, alpha);
        Scissor.disable();

        renderScrollbar(panelX, panelW, bodyY, bodyH, s, alpha);
    }

    private void renderChrome(float x, float y, float panelW, float panelH, float s,
                              int mouseX, int mouseY, float alpha) {
        // в рефке (1920) радиус 8 и обводка 2.5, тут пополам - 4 и 1.25
        float radius = 4f * s;

        Render2D.blur(x, y, panelW, panelH, 14f, radius,
                (clamp((int) (alpha * 235)) << 24) | COLOR_PANEL);
        Render2D.outline(x, y, panelW, panelH, 1.25f * s,
                (clamp((int) (alpha * 255)) << 24) | COLOR_PANEL_OUTLINE, radius);

        // хедер - не отдельная панель, тот же фон + тонкая линия-разделитель
        Render2D.rect(x, y + HEADER_H * s - 0.5f * s, panelW, 0.5f * s,
                (clamp((int) (alpha * 255)) << 24) | COLOR_HEADER_DIVIDER, 0f);

        Fonts.LOGO.draw("A", x + 12f * s, y + 9f * s, 11f * s,
                (clamp((int) (alpha * 255)) << 24) | getAccent());

        renderTabs(x, y, panelW, s, mouseX, mouseY, alpha);
    }

    private void renderTabs(float panelX, float panelY, float panelW, float s,
                            int mouseX, int mouseY, float alpha) {
        float tabW = 30f * s;
        float totalW = CATEGORIES.length * tabW;
        float startX = panelX + (panelW - totalW) / 2f;
        float tabY = panelY + 5f * s;
        float tabH = (HEADER_H - 10f) * s;

        for (int i = 0; i < CATEGORIES.length; i++) {
            ModuleCategory category = CATEGORIES[i];
            float tabX = startX + i * tabW;

            boolean active = category == activeCategory;
            boolean hovered = inside(mouseX, mouseY, tabX, tabY, tabW, tabH);

            if (active) {
                Render2D.rect(tabX, tabY, tabW, tabH,
                        (clamp((int) (alpha * 60)) << 24) | 0xFFFFFF, tabH / 2f);
            } else if (hovered) {
                Render2D.rect(tabX, tabY, tabW, tabH,
                        (clamp((int) (alpha * 25)) << 24) | 0xFFFFFF, tabH / 2f);
            }

            float brightness = active ? 1f : (hovered ? 0.8f : 0.45f);
            int iconColor = (clamp((int) (alpha * brightness * 255)) << 24) | COLOR_TEXT;

            categoryIconFont(category).drawCentered(categoryIcon(category),
                    tabX + tabW / 2f, tabY + tabH / 2f - 4.5f * s, 9f * s, iconColor);
        }
    }

    private void renderCards(DrawContext context, float panelX, float panelW,
                             float bodyY, float bodyH, float s,
                             int mouseX, int mouseY, float alpha) {
        lastLayout.clear();

        List<ModuleStructure> modules = modulesOf(activeCategory);
        float cardW = CARD_W * s;
        float gap = CARD_GAP * s;
        float gridW = COLUMNS * cardW + (COLUMNS - 1) * gap;
        float gridX = panelX + (panelW - gridW) / 2f;

        // пакуем по колонкам, а не по рядам - иначе одна высокая карточка
        // сдвигала бы весь свой ряд вниз с рваными промежутками
        float[] columnY = new float[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) {
            columnY[i] = bodyY + PAD * s - smoothScroll;
        }

        for (ModuleStructure module : modules) {
            int column = shortestColumn(columnY);
            float cardX = gridX + column * (cardW + gap);
            float cardY = columnY[column];

            float totalH = renderCard(context, module, cardX, cardY, cardW,
                    bodyY, bodyH, s, mouseX, mouseY, alpha);

            lastLayout.add(new CardBounds(module, cardX, cardY, cardW, CARD_HEADER_H * s));
            columnY[column] = cardY + totalH + gap;
        }

        float tallest = 0f;
        for (float columnBottom : columnY) {
            tallest = Math.max(tallest, columnBottom + smoothScroll - bodyY);
        }
        contentHeight = tallest + PAD * s;
    }

    private float renderCard(DrawContext context, ModuleStructure module,
                             float x, float y, float width,
                             float bodyY, float bodyH, float s,
                             int mouseX, int mouseY, float alpha) {

        float toggle = toggleAnim.getOrDefault(module, 0f);
        float toggleTarget = module.isState() ? 1f : 0f;
        toggle += (toggleTarget - toggle) * 0.2f;
        toggleAnim.put(module, toggle);

        float headerH = CARD_HEADER_H * s;
        float pad = PAD * s;

        // настройки всегда показаны, как в рефке - свёрнутого состояния карточки нет
        List<Setting> settings = visibleSettings(module);
        float settingsH = settingsHeight(settings, s);
        float totalH = headerH + settingsH;

        // карточку за пределами body не рисуем, но высоту всё равно возвращаем -
        // иначе карточки после неё сдвинутся не туда
        if (y + totalH < bodyY || y > bodyY + bodyH) {
            return totalH;
        }

        boolean locked = module.isLocked();
        boolean hovered = !locked && inside(mouseX, mouseY, x, y, width, headerH);

        Render2D.rect(x, y, width, totalH,
                (clamp((int) (alpha * 255)) << 24) | COLOR_CARD, CARD_RADIUS * s);
        // в рефке обводка карточки - плоский white@3%, без окраски в акцент
        Render2D.outline(x, y, width, totalH, CARD_OUTLINE * s,
                (clamp((int) (alpha * 8)) << 24) | 0xFFFFFF, CARD_RADIUS * s);

        if (hovered) {
            Render2D.rect(x, y, width, headerH,
                    (clamp((int) (alpha * 14)) << 24) | 0xFFFFFF, CARD_RADIUS * s);
        }

        // название не окрашивается в акцент при тогле (в рефке это делает только иконка,
        // а иконок тут нет); заблокированный модуль - серый, чтобы не выглядел кликабельным
        int nameColor = locked ? (clamp((int) (alpha * 150)) << 24) | 0x8A8A94
                : (clamp((int) (alpha * 255)) << 24) | COLOR_TEXT;
        Fonts.BOLD.draw(module.getName(), x + pad, y + headerH / 2f - 3.2f * s, 7.5f * s, nameColor);

        float switchW = SWITCH_W * s;
        float switchX = x + width - pad - switchW;
        renderSwitch(switchX, y + headerH / 2f - SWITCH_H * s / 2f, s, toggle, locked ? alpha * 0.35f : alpha);
        if (!locked) {
            renderBindChip(module, switchX - 4f * s, y + headerH / 2f, s, alpha);
        }

        float settingY = y + headerH;
        for (Setting setting : settings) {
            settingY += CsSettingRenderer.draw(context, setting,
                    x + pad, settingY, width - pad * 2f, s, alpha, getAccent(), mouseX, mouseY) + pad;
        }

        return totalH;
    }

    // пилюля "None"/клавиша слева от свитча, растёт влево, чтоб не выталкивала свитч
    private void renderBindChip(ModuleStructure module, float rightEdge, float centerY,
                                float s, float alpha) {
        boolean listening = module == bindingModule;
        String label = listening ? "..." : keyLabel(module);

        float[] bounds = chipBounds(label, rightEdge, centerY, s);
        int color = listening
                ? (clamp((int) (alpha * 255)) << 24) | getAccent()
                : (clamp((int) (alpha * 165)) << 24) | COLOR_TEXT_DIM;

        Fonts.TEST.drawCentered(label, bounds[0] + bounds[2] / 2f, bounds[1] + 2.2f * s, 6f * s, color);
    }

    private static String keyLabel(ModuleStructure module) {
        if (module.getKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return "None";
        String label = KeyHelper.getKeyName(module.getKey());
        return label == null || label.isEmpty() ? "None" : label;
    }

    // общий метод для рендера и кликов, чтобы кликабельная зона не расходилась с отрисовкой
    private static float[] chipBounds(String label, float rightEdge, float centerY, float s) {
        float size = 6f * s;
        float textW = Fonts.TEST.getWidth(label, size);
        float chipW = Math.max(textW + 6f * s, 14f * s);
        float chipH = 10f * s;
        return new float[]{rightEdge - chipW, centerY - chipH / 2f, chipW, chipH};
    }

    private void renderSwitch(float x, float y, float s, float toggle, float alpha) {
        float w = SWITCH_W * s;
        float h = SWITCH_H * s;

        int trackColor = lerpColor(COLOR_TRACK, getAccent(), toggle);
        Render2D.rect(x, y, w, h,
                (clamp((int) (alpha * (140 + 100 * toggle))) << 24) | trackColor, h / 2f);

        // геометрия ползунка из рефки: inset 3 из 17, радиус (17-6)/2, ход (24-6)-2*радиус
        float inset = 3f * s * (h / (17f * s));
        float knobR = (h - inset * 2f) / 2f;
        float travel = (w - inset * 2f) - knobR * 2f;
        float knobX = x + inset + knobR + travel * toggle;

        Render2D.rect(knobX - knobR, y + inset, knobR * 2f, knobR * 2f,
                (clamp((int) (alpha * 255)) << 24) | 0xFFFFFF, knobR);
    }

    private void renderScrollbar(float panelX, float panelW, float bodyY, float bodyH,
                                 float s, float alpha) {
        if (contentHeight <= bodyH) return;

        float trackX = panelX + panelW - 3f * s;
        float trackH = bodyH - PAD * s * 2f;
        float trackY = bodyY + PAD * s;

        float thumbH = Math.max(16f * s, trackH * (bodyH / contentHeight));
        float maxScroll = Math.max(1f, contentHeight - bodyH);
        float thumbY = trackY + (trackH - thumbH) * (smoothScroll / maxScroll);

        Render2D.rect(trackX, trackY, 1.5f * s, trackH,
                (clamp((int) (alpha * 30)) << 24) | 0xFFFFFF, 0.75f * s);
        Render2D.rect(trackX, thumbY, 1.5f * s, thumbH,
                (clamp((int) (alpha * 130)) << 24) | 0xFFFFFF, 0.75f * s);
    }

    private void updateScroll(float bodyH) {
        float maxScroll = Math.max(0f, contentHeight - bodyH);
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        smoothScroll += (scroll - smoothScroll) * 0.25f;
        if (Math.abs(scroll - smoothScroll) < 0.1f) smoothScroll = scroll;
    }

    // ---- input ----------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float s = uiScale();
        float panelW = PANEL_W * s;
        float panelH = PANEL_H * s;
        float panelX = (Render2D.getFixedScaledWidth() - panelW) / 2f;
        float panelY = (Render2D.getFixedScaledHeight() - panelH) / 2f;

        // пока ждём нажатия клавиши для бинда - глотаем любой другой клик,
        // иначе случайный клик во время бинда переключит модуль или настройку
        if (bindingModule != null) {
            bindingModule = null;
            return true;
        }

        if (handleTabClick(panelX, panelY, panelW, s, mouseX, mouseY, button)) return true;

        float pad = PAD * s;
        for (CardBounds card : lastLayout) {
            if (handleBindChipClick(card, s, mouseX, mouseY, button)) return true;

            // настройки внутри карточки - клик по ним не должен проваливаться на саму карточку
            float settingY = card.y + card.headerH;
            for (Setting setting : visibleSettings(card.module)) {
                float w = card.width - pad * 2f;
                float sx = card.x + pad;

                if (CsSettingRenderer.drag(setting, sx, settingY, w, s, mouseX, mouseY)) {
                    draggingSlider = setting;
                    draggingBounds = new float[]{sx, settingY, w, s};
                    return true;
                }
                if (CsSettingRenderer.click(setting, sx, settingY, w, s, mouseX, mouseY, button)) {
                    return true;
                }
                settingY += CsSettingRenderer.height(setting, s) + pad;
            }

            if (!inside(mouseX, mouseY, card.x, card.y, card.width, card.headerH)) continue;

            if (card.module.isLocked()) {
                // глотаем клик, чтобы не провалился на то, что позади карточки
                if (button == 0) notifyLocked(card.module);
                return true;
            }

            if (button == 0) card.module.switchState();
            return true;
        }

        return inside(mouseX, mouseY, panelX, panelY, panelW, panelH);
    }

    private void notifyLocked(accident.modules.module.ModuleStructure module) {
        String message = module.lockedMessage();
        if (message.isEmpty()) return;

        var notifications = accident.modules.impl.hud.Notifications.getInstance();
        if (notifications != null) notifications.addNotification(message, 2000, false);
    }

    private boolean handleBindChipClick(CardBounds card, float s, double mouseX, double mouseY, int button) {
        if (card.module.isLocked()) return false;

        float pad = PAD * s;
        float switchW = SWITCH_W * s;
        float switchX = card.x + card.width - pad - switchW;
        float rightEdge = switchX - 4f * s;
        float centerY = card.y + card.headerH / 2f;

        String label = keyLabel(card.module);
        float[] bounds = chipBounds(label, rightEdge, centerY, s);
        if (!inside(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3])) return false;

        if (button == 1) {
            card.module.bindKey(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
        } else if (button == 0) {
            bindingModule = card.module;
        }
        return true;
    }

    private boolean handleTabClick(float panelX, float panelY, float panelW, float s,
                                   double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float tabW = 30f * s;
        float startX = panelX + (panelW - CATEGORIES.length * tabW) / 2f;
        float tabY = panelY + 5f * s;
        float tabH = (HEADER_H - 10f) * s;

        for (int i = 0; i < CATEGORIES.length; i++) {
            if (inside(mouseX, mouseY, startX + i * tabW, tabY, tabW, tabH)) {
                if (activeCategory != CATEGORIES[i]) {
                    activeCategory = CATEGORIES[i];
                    scroll = 0f;
                    smoothScroll = 0f;
                }
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        draggingBounds = null;
        CsSettingRenderer.forwardColorRelease(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        // держимся за слайдер, который схватили, а не за то, что под курсором сейчас -
        // иначе драг за край карточки перескочит на соседний слайдер
        if (draggingSlider != null && draggingBounds != null) {
            return CsSettingRenderer.drag(draggingSlider, draggingBounds[0], draggingBounds[1],
                    draggingBounds[2], draggingBounds[3], mouseX, mouseY);
        }
        // ColorComponent сам следит за своим драгом, тут просто форвардим событие
        return CsSettingRenderer.forwardColorDrag(mouseX, mouseY, button, dX, dY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        float s = uiScale();
        float panelW = PANEL_W * s;
        float panelH = PANEL_H * s;
        float panelX = (Render2D.getFixedScaledWidth() - panelW) / 2f;
        float panelY = (Render2D.getFixedScaledHeight() - panelH) / 2f;
        if (!inside(mouseX, mouseY, panelX, panelY, panelW, panelH)) return false;

        scroll -= (float) amount * 20f * s;
        return true;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // хекс-поле колорпикера в приоритете - вернёт true только если реально в нём печатают
        if (CsSettingRenderer.forwardColorKey(key, scanCode, modifiers)) return true;

        if (bindingModule == null) return false;

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            bindingModule = null;
            return true;
        }

        bindingModule.bindKey(key);
        accident.screens.clickgui.BindHelper.justBoundKey = key;
        accident.screens.clickgui.BindHelper.justBoundTime = System.currentTimeMillis();
        bindingModule = null;
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        return CsSettingRenderer.forwardColorChar(chr, modifiers);
    }

    // ---- helpers --------------------------------------------------------

    private List<ModuleStructure> modulesOf(ModuleCategory category) {
        return Initialization.getInstance().getManager().getModuleRepository().modules().stream()
                .filter(m -> m.getCategory() == category)
                .sorted(Comparator.comparing(ModuleStructure::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private List<Setting> visibleSettings(ModuleStructure module) {
        List<Setting> visible = new ArrayList<>();
        List<Setting> all = module.settings();
        if (all == null) return visible;
        for (Setting setting : all) {
            if (setting.isVisible()) visible.add(setting);
        }
        return visible;
    }

    private float settingsHeight(List<Setting> settings, float s) {
        if (settings.isEmpty()) return 0f;
        float h = PAD * s;
        for (Setting setting : settings) {
            h += CsSettingRenderer.height(setting, s) + PAD * s;
        }
        return h;
    }

    private int shortestColumn(float[] columnY) {
        int best = 0;
        for (int i = 1; i < columnY.length; i++) {
            if (columnY[i] < columnY[best]) best = i;
        }
        return best;
    }

    private static String categoryIcon(ModuleCategory category) {
        return switch (category) {
            case MOVEMENT -> "E";
            case RENDER -> "B";
            case HUD -> "A";
            case LUA -> "B";
            default -> "A";
        };
    }

    private static accident.util.render.font.Font categoryIconFont(ModuleCategory category) {
        return switch (category) {
            case HUD, LUA -> Fonts.UIicons;
            default -> Fonts.CLICKGUIICONS;
        };
    }

    // палитра темы "Default" - это чистый 0xFFFFFF, а с ним весь менюшка становится
    // монохромной (все свитчи/слайдеры/значения белые). этот лейаут рассчитан на цветной
    // акцент, поэтому белый акцент подменяем дефолтным цветом из рефки
    private static final int FALLBACK_ACCENT = 0x6E74E3;

    static int getAccent() {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;

        int[] palette = theme.palette;
        if (palette == null || palette.length == 0) return FALLBACK_ACCENT;

        int accent = palette[0] & 0xFFFFFF;
        return accent == 0xFFFFFF ? FALLBACK_ACCENT : accent;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}
