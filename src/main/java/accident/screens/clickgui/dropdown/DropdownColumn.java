package accident.screens.clickgui.dropdown;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.SettingComponentAdder;
import accident.screens.clickgui.impl.settingsrender.ColorComponent;
import accident.screens.clickgui.impl.settingsrender.MultiColorComponent;
import accident.screens.clickgui.impl.settingsrender.SelectComponent;
import accident.util.interfaces.AbstractSettingComponent;
import accident.util.render.Render2D;
import accident.util.render.shader.Scissor;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DropdownColumn {

    public static final float HEADER_H   = 22f;
    public static final float MODULE_H   = 20f;
    public static final float MODULE_GAP = 1.5f;
    public static final float SETTING_H  = 14f;
    public static final float SETTING_SP = 1f;
    public static final float RADIUS     = 6f;
    public static final float PAD_X      = 3f;
    public static final float HEADER_GAP = 1.5f;
    public static final float LIST_TOP_PADDING = 3f;

    public static final float SEARCH_BAR_H = 24f;
    public static final float SEARCH_ICON_SIZE = 18f;
    public static final float UNIFORM_COLUMN_WIDTH = 110f;

    private Float savedScrollOffset = null;
    private String savedExpandedModuleName = null;
    private String savedFilter = "";


    private ModuleStructure hoveredModuleForTooltip = null;
    private float tooltipAlpha = 0f;
    private static final float TOOLTIP_FADE_SPEED = 0.15f;
    private static final float TOOLTIP_PADDING = 6f;
    private static final float TOOLTIP_RADIUS = 4f;
    private static final float TOOLTIP_FIXED_X = 460f;
    private static final float TOOLTIP_FIXED_Y = 90f;

    private static final int BASE_BG_COLOR = 0x000000;
    private static final float ITEM_ALPHA = 0.08f;
    private static final int COLOR_TEXT = 0xD8D8D8;
    private static final int COLOR_ACCENT = 0xC0C0C0;
    private static final int COLOR_SCROLL = 0x636363;

    public static final float FOOTER_HEIGHT = 3f;
    private static final int HEADER_BLUR_COLOR = 0x0A0A12;
    private static final int LIST_BLUR_COLOR   = 0x11111F;

    private static final int DOT_ROWS = 3;
    private static final float DOT_SIZE = 1f;
    private static final float DOT_SPACING = 2.5f;
    private static final float DOT_MARGIN_RIGHT = 7f;
    private static final float DOT_COL_GAP = 4f;
    private static final float DOTS_MARGIN_RIGHT = 6f;
    private static final float DOT_RADIUS = 1f;
    private static final float DOT_GAP = 2.5f;

    public final ModuleCategory        category;
    private final List<ModuleStructure> modules;
    private final List<ModuleStructure> displayModules = new ArrayList<>();
    private String currentFilter = "";
    private float  width;

    private ModuleStructure expandedModule = null;
    private final Map<ModuleStructure, List<AbstractSettingComponent>> settingsCache = new HashMap<>();
    private final Map<ModuleStructure, Float> expandAnim  = new HashMap<>();
    private final Map<ModuleStructure, Float> hoverAnim   = new HashMap<>();
    private final Map<ModuleStructure, Float> toggleAnim  = new HashMap<>();

    private float columnAnim      = 0f;
    private long  columnAnimStart = System.currentTimeMillis();
    private long  lastUpdate      = System.currentTimeMillis();

    private ModuleStructure bindingModule   = null;
    private float           bindCircleAnim  = 0f;
    private float           bindAppearAnim  = 0f;
    private boolean         bindClosing     = false;
    private float           bindCircleX     = 0f;
    private float           bindCircleY     = 0f;
    private float           bindCircleAlpha = 0f;
    private boolean         showKeyLabel    = false;
    private float           keyLabelAnim    = 0f;
    private ModuleStructure lastBoundModule = null;
    private long            lastBindUpdate  = System.currentTimeMillis();
    private long            bindHoldStart   = 0;
    private int             bindHoldKey     = -1;
    private static final float BIND_HOLD_DURATION = 1.2f;

    private float scrollOffset       = 0f;
    private float targetScrollOffset = 0f;
    public static final float MAX_COLUMN_HEIGHT = 220f;

    private static class Spark {
        float x, y, vx, vy, life, maxLife, size;
        Spark(float x, float y) {
            this.x = x; this.y = y;
            float angle = (float)(Math.random() * Math.PI * 2);
            float speed = 1.5f + (float)Math.random() * 3f;
            this.vx = (float)Math.cos(angle) * speed;
            this.vy = (float)Math.sin(angle) * speed - 2f;
            this.life = 1f;
            this.maxLife = 2.5f + (float)Math.random() * 1.5f;
            this.size = 2f + (float)Math.random() * 2.5f;
        }
        boolean update(float dt) {
            vy += 3f * dt;
            x += vx * dt * 60f;
            y += vy * dt * 60f;
            life = Math.max(0f, life - dt / maxLife);
            return life > 0f;
        }
    }
    private final List<Spark> sparks = new ArrayList<>();

    public DropdownColumn(ModuleCategory category, List<ModuleStructure> modules, float width) {
        this.category = category;
        this.modules = modules;
        this.width = width;
        this.displayModules.addAll(modules);
        for (ModuleStructure m : modules) {
            expandAnim.put(m, 0f);
            hoverAnim.put(m, 0f);
            toggleAnim.put(m, m.isState() ? 1f : 0f);
        }
    }

    private static final Map<String, ColumnState> SAVED_STATES = new HashMap<>();

    private static class ColumnState {
        float scrollOffset;
        String expandedModuleName;
        String filter;

        ColumnState(float scroll, String expanded, String filter) {
            this.scrollOffset = scroll;
            this.expandedModuleName = expanded;
            this.filter = filter;
        }
    }

    public void setWidth(float w) { this.width = w; }

    public void setFilter(String query) {
        if (query.equals(currentFilter)) return;
        currentFilter = query;
        scrollOffset = 0;
        targetScrollOffset = 0;
        displayModules.clear();
        if (query == null || query.isEmpty()) {
            displayModules.addAll(modules);
        } else {
            String q = query.toLowerCase(java.util.Locale.ROOT);
            for (ModuleStructure m : modules)
                if (m.getName().toLowerCase(java.util.Locale.ROOT).contains(q))
                    displayModules.add(m);
        }
    }

    public void saveState() {
        if (category == null) return;
        String key = category.name();
        String expandedName = expandedModule != null ? expandedModule.getName() : null;
        SAVED_STATES.put(key, new ColumnState(scrollOffset, expandedName, currentFilter));
    }

    public void loadState() {
        if (category == null) return;
        ColumnState state = SAVED_STATES.get(category.name());
        if (state == null) return;

        this.scrollOffset = state.scrollOffset;
        this.targetScrollOffset = state.scrollOffset;

        if (state.filter != null && !state.filter.isEmpty()) {
            this.currentFilter = state.filter;
            applySearchFilter(state.filter);
        }

        if (state.expandedModuleName != null) {
            for (ModuleStructure m : modules) {
                if (m.getName().equals(state.expandedModuleName)) {
                    expandedModule = m;
                    expandAnim.put(m, 1f);
                    break;
                }
            }
        }
    }

    public static void clearAllStates() {
        SAVED_STATES.clear();
    }

    public void resetState() {
        savedScrollOffset = null;
        savedExpandedModuleName = null;
        savedFilter = "";
    }

    public void render(DrawContext context, float x, float y,
                       int mouseX, int mouseY, float alphaMultiplier) {

        updateAnimations(mouseX, mouseY, x, y);

        float alpha = columnAnim * alphaMultiplier;
        float contentH = computeTotalContentHeight();
        float maxVisibleListH = MAX_COLUMN_HEIGHT - HEADER_H - HEADER_GAP;
        float visContentH = Math.min(contentH, maxVisibleListH);

        float alphaFactor = alphaMultiplier * columnAnim;
        int headerGlassAlpha = (int)(alphaFactor * 60);
        int headerBgColor = (headerGlassAlpha << 24) | BASE_BG_COLOR;

        int headerBlurColor = getThemeBlurColor(alphaFactor * 0.6f, true);
        Render2D.blur(x, y, width, HEADER_H, 10f, 4f, headerBlurColor);

        Render2D.rect(x, y, width, HEADER_H, headerBgColor, RADIUS);
        renderHeader(x, y, alpha, alphaMultiplier);

        float listY = y + HEADER_H + HEADER_GAP;
        float listBgHeight = visContentH + FOOTER_HEIGHT + LIST_TOP_PADDING;

        if (listBgHeight > 1f) {
            int listGlassAlpha = (int)(alphaFactor * 40);
            int listBgColor = (listGlassAlpha << 24) | BASE_BG_COLOR;

            int listBlurColor = getThemeBlurColor(alphaFactor * 0.4f, false);
            Render2D.blur(x, listY, width, listBgHeight, 10f, 4f, listBlurColor);
            Render2D.rect(x, listY, width, listBgHeight, listBgColor, RADIUS);

            Scissor.enable(x, listY, width, visContentH, 2);

            float curY = listY + LIST_TOP_PADDING + scrollOffset;
            for (ModuleStructure module : displayModules) {
                curY = renderModule(context, module, x, curY,
                        mouseX, mouseY, alpha, alphaMultiplier, listY, visContentH);
            }
            Scissor.disable();

            renderScrollbar(x, listY, width, visContentH, alpha, alphaMultiplier);
            renderModuleTooltip(context, alpha);
        }

        if (bindingModule != null && bindAppearAnim > 0.01f)
            renderBindCircle(bindCircleX, bindCircleY, bindCircleAlpha);
    }

    private void renderHeader(float x, float y, float alpha, float alphaMultiplier) {
        String icon = switch (category) {
            case MOVEMENT -> "E";
            case RENDER -> "B";
            case HUD -> "A";
            case LUA -> "B";
            default -> "A";
        };
        accident.util.render.font.Font iconFont = switch (category) {
            case HUD, LUA -> Fonts.UIicons;
            default -> Fonts.CLICKGUIICONS;
        };
        int iconAlpha = clamp((int)(alpha * 0.80f * 255));
        int iconColor = (iconAlpha << 24) | COLOR_ACCENT;
        iconFont.draw(icon, x + 10, y + 6.5f, 9f, iconColor);

        String name = category.getReadableName();
        int nameAlpha = clamp((int)(alpha * 0.88f * 255));
        int nameColor = (nameAlpha << 24) | COLOR_TEXT;
        Fonts.TEST.draw(name, x + 26, y + 7.5f, 7f, nameColor);

        float totalDotsHeight = DOT_ROWS * DOT_SIZE + (DOT_ROWS - 1) * DOT_SPACING;
        float dotsStartY = y + (HEADER_H - totalDotsHeight) / 2f;
        float dotsX = x + width - DOT_MARGIN_RIGHT - DOT_SIZE * 2 - DOT_COL_GAP;
        int dotColor = ((int)(alpha * 0.20f * 255) << 24) | 0xFFFFFF;

        for (int row = 0; row < DOT_ROWS; row++) {
            float dotY = dotsStartY + row * (DOT_SIZE + DOT_SPACING);
            Render2D.rect(dotsX, dotY, DOT_SIZE, DOT_SIZE, dotColor, DOT_SIZE / 2f);
            Render2D.rect(dotsX + DOT_SIZE + DOT_COL_GAP, dotY, DOT_SIZE, DOT_SIZE, dotColor, DOT_SIZE / 2f);
        }
    }

    private float renderModule(DrawContext context, ModuleStructure module,
                               float x, float y, int mouseX, int mouseY,
                               float alpha, float alphaMultiplier,
                               float listY, float listH) {

        float hover = hoverAnim.getOrDefault(module, 0f);
        float toggle = toggleAnim.getOrDefault(module, 0f);
        float expand = expandAnim.getOrDefault(module, 0f);
        boolean hasSettings = hasSettings(module);

        int itemBgColor = ((int)(255 * alphaMultiplier * ITEM_ALPHA) << 24) | BASE_BG_COLOR;
        Render2D.rect(x + PAD_X, y, width - (PAD_X * 2), MODULE_H, itemBgColor, RADIUS - 2);

        boolean locked = module.isLocked();

        if (hover > 0.02f && !locked) {
            int hA = clamp((int)(hover * 20 * alphaMultiplier));
            Render2D.rect(x + PAD_X, y, width - (PAD_X * 2), MODULE_H,
                    (hA << 24) | 0xA0A0CC, RADIUS - 2);
        }

        float ty = y + (MODULE_H / 2f) - 1.5f;
        int textAlpha = clamp((int)(150 + (255 - 150) * toggle));
        // заблокированный модуль - серым, чтобы не выглядел кликабельным
        int nameColor = locked ? (clamp((int)(110 * alphaMultiplier)) << 24) | 0x8A8A94
                : (textAlpha << 24) | 0xFFFFFF;
        String displayName = (module == bindingModule && !bindClosing) ? "Select a Key.." : module.getName();
        Fonts.TEST.draw(displayName, x + PAD_X + 8, ty - 2.5f, 6f, nameColor);


        int moduleKey = module.getKey();
        if (!locked && moduleKey != GLFW.GLFW_KEY_UNKNOWN && moduleKey != -1 && !(module == bindingModule && !bindClosing)) {
            String keyName = accident.util.string.KeyHelper.getKeyName(moduleKey).toUpperCase();

            float keyBgW = Math.max(20f, Fonts.TEST.getWidth(keyName, 5f) + 6f);
            float keyBgX = x + width - PAD_X - keyBgW - 2f;
            float keyBgY = y + 3f;

            int keyAlpha = clamp((int)(alpha * 0.8f * 255));
            Fonts.TEST.draw(keyName, keyBgX + keyBgW - 25f / 2f - Fonts.TEST.getWidth(keyName, 5f) / 2f, keyBgY + 4.5f, 5f,
                    (keyAlpha << 24) | 0xE0E0E0);
        }

        if (hasSettings(module)) {
            int dotsColor = ((int)(alpha * 0.35f * 255) << 24) | 0xFFFFFF;
            float dotsStartX = x + width - DOTS_MARGIN_RIGHT;
            float dotsY = y + (MODULE_H / 2f) - 0.5f;
            for (int i = 0; i < 3; i++) {
                float dotX = dotsStartX - (i * DOT_GAP);
                Render2D.rect(dotX, dotsY, DOT_RADIUS, DOT_RADIUS, dotsColor, DOT_RADIUS);
            }
        }

        float nextY = y + MODULE_H + MODULE_GAP;

        if (expand > 0.01f && hasSettings) {
            List<AbstractSettingComponent> settings = getOrBuildSettings(module);
            float settingsH = computeSettingsHeight(settings);
            float clipH = settingsH * expand;

            if (clipH > 1f) {
                int sBA = clamp((int)(30 * expand * alphaMultiplier));
                Render2D.rect(x + PAD_X, nextY, width - (PAD_X * 2), clipH,
                        (sBA << 24) | 0x050508, 0f);

                float sy = nextY;
                for (AbstractSettingComponent comp : settings) {
                    if (!comp.getSetting().isVisible()) continue;
                    comp.position(x + PAD_X + 1, sy);
                    comp.size(width - (PAD_X * 2) - 2, SETTING_H);
                    comp.setAlphaMultiplier(expand * alphaMultiplier);
                    if (comp instanceof MultiColorComponent mcc)
                        mcc.setColumnBounds(x, width, listY + listH);
                    context.getMatrices().pushMatrix();
                    comp.render(context, mouseX, mouseY, 0f);
                    context.getMatrices().popMatrix();
                    sy += getCompHeight(comp) + SETTING_SP;
                }
                nextY += clipH;
            }
        }

        return nextY;
    }

    public void applySearchFilter(String query) {
        displayModules.clear();
        if (query == null || query.isEmpty()) {
            displayModules.addAll(modules);
        } else {
            String q = query.toLowerCase(java.util.Locale.ROOT);
            for (ModuleStructure m : modules)
                if (m.getName().toLowerCase(java.util.Locale.ROOT).contains(q))
                    displayModules.add(m);
        }
    }

    public static void renderSearchBar(DrawContext context, float x, float y, float width,
                                       String query, boolean focused, String langDisplay, float alpha) {
        final float ICON_PADDING = 26f;
        final float LANG_PADDING = 50f;
        final float TEXT_OFFSET = 15f;

        int glassAlpha = (int)(alpha * 60);
        int bgColor = (glassAlpha << 24) | 0x000000;
        float bgX = x + ICON_PADDING - 3f;
        float bgW = width - ICON_PADDING - LANG_PADDING + 3f;

        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        int themeBlurBase;

        if (theme != null && theme.palette != null && theme.palette.length > 0) {
            if (theme.palette.length == 1) {
                themeBlurBase = theme.palette[0];
            } else {
                long timeMs = System.currentTimeMillis();
                double speed = 6000.0;
                float indexProgress = (float) ((timeMs % (long)speed) / speed * theme.palette.length);
                int idx1 = (int) indexProgress % theme.palette.length;
                int idx2 = (idx1 + 1) % theme.palette.length;
                float frac = indexProgress - (int) indexProgress;
                themeBlurBase = interpolateColor(theme.palette[idx1], theme.palette[idx2], frac);
            }
            themeBlurBase = darkenColor(themeBlurBase, 0.6f);
        } else {
            themeBlurBase = 0x0A0A12;
        }

        int themeBlurColor = (glassAlpha << 24) | (themeBlurBase & 0xFFFFFF);
        Render2D.blur(bgX, y, bgW, SEARCH_BAR_H, 10f, 4f, themeBlurColor);
        Render2D.rect(bgX, y, bgW, SEARCH_BAR_H, bgColor, 6f);

        float iconSquareX = x + 3f;
        float iconSquareY = y + 3f;
        int squareAlpha = (int)(alpha * 0.15f * 255);
        Render2D.rect(iconSquareX + 145f, iconSquareY, SEARCH_ICON_SIZE, SEARCH_ICON_SIZE,
                (squareAlpha << 24) | 0x000000, 4f);

        int iconAlpha = (int)(alpha * 0.8f * 255);
        Fonts.SEARCHICON.draw("A", iconSquareX + 25f, iconSquareY + 4.5f, 9f,
                (iconAlpha << 24) | 0xC0C0C0);

        String displayText;
        int textAlpha;
        if (!query.isEmpty()) {
            displayText = query;
            textAlpha = (int)(alpha * 0.88f * 255);
        } else if (focused) {
            displayText = "";
            textAlpha = 0;
        } else {
            displayText = "Search...";
            textAlpha = (int)(alpha * 0.6f * 255);
        }

        int textColor = (textAlpha << 24) | 0xD8D8D8;
        float textX = x + ICON_PADDING;
        float textY = y + 8f;

        float textMaxWidth = width - ICON_PADDING - LANG_PADDING - 4f;
        Scissor.enable(textX - 25f, y, textMaxWidth, SEARCH_BAR_H, 2);

        Fonts.TEST.draw(displayText, textX + TEXT_OFFSET, textY, 6f, textColor);

        if (focused) {
            float cursorBlink = (float)(Math.sin(System.currentTimeMillis() / 200f) * 0.5 + 0.5);
            if (cursorBlink > 0.4f) {
                float cursorX = displayText.isEmpty()
                        ? textX + TEXT_OFFSET
                        : textX + TEXT_OFFSET + Fonts.TEST.getWidth(displayText, 6f);

                int cursorAlpha = (int)(alpha * cursorBlink * 220);
                Render2D.rect(cursorX + 0.5f, y + 9f, 0.35f, 10f,
                        (cursorAlpha << 24) | 0xFFFFFF, 0f);
            }
        }
        Scissor.disable();

        int langAlpha = (int)(alpha * 0.7f * 255);
        int langColor = (langAlpha << 24) | 0xB0B0B0;
        Fonts.TEST.draw(langDisplay, x + width - LANG_PADDING - 19.2f, y + 8f, 6f, langColor);
    }

    private void renderScrollbar(float x, float y, float w, float h,
                                 float alpha, float alphaMultiplier) {
        float contentH = computeTotalContentHeight();
        if (contentH <= h) return;

        float sbX = x + w - 3f;
        float sbW = 1.5f;
        float thumbH = Math.max(14f, h * (h / contentH));
        float maxScroll = contentH - h;
        float scrollRatio = maxScroll > 0 ? (-scrollOffset) / maxScroll : 0f;
        float thumbY = y + (h - thumbH) * scrollRatio;

        int trackAlpha = clamp((int)(45 * alphaMultiplier));
        Render2D.rect(sbX, y, sbW, h, (trackAlpha << 24) | COLOR_SCROLL, 4f);

        int thumbAlpha = clamp((int)(50 * alphaMultiplier));
        Render2D.rect(sbX, thumbY, sbW, thumbH, (thumbAlpha << 24) | COLOR_SCROLL, 4f);
    }

    private void updateAnimations(int mouseX, int mouseY, float colX, float colY) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastUpdate) / 1000f, 0.05f);
        lastUpdate = now;

        columnAnim = easeOutQuart(Math.min(1f, (now - columnAnimStart) / 300f));

        float diff = targetScrollOffset - scrollOffset;
        if (Math.abs(diff) > 0.1f) {
            scrollOffset += diff * 20f * dt;
        } else {
            scrollOffset = targetScrollOffset;
        }

        long now2 = System.currentTimeMillis();
        float dt2 = Math.min((now2 - lastBindUpdate) / 1000f, 0.05f);
        lastBindUpdate = now2;
        sparks.removeIf(sp -> !sp.update(dt2));

        if (!bindClosing && bindingModule != null) {
            bindAppearAnim += (1f - bindAppearAnim) * 18f * dt2;
            bindAppearAnim = Math.min(1f, bindAppearAnim);
            if (bindHoldStart > 0) {
                bindCircleAnim = Math.min(1f, (now2 - bindHoldStart) / 1000f / BIND_HOLD_DURATION);
                if (bindCircleAnim >= 0.99f && bindHoldKey != -1) {
                    bindingModule.bindKey(bindHoldKey);
                    accident.screens.clickgui.BindHelper.justBoundKey = bindHoldKey;
                    accident.screens.clickgui.BindHelper.justBoundTime = System.currentTimeMillis();
                    bindHoldKey = -1; bindHoldStart = 0; bindCircleAnim = 0f;
                    bindClosing = true; showKeyLabel = true; keyLabelAnim = 0f;
                    lastBoundModule = bindingModule;
                    for (int si = 0; si < 50; si++) sparks.add(new Spark(bindCircleX, bindCircleY));
                }
            } else {
                bindCircleAnim += (0f - bindCircleAnim) * 10f * dt2;
                if (bindCircleAnim < 0.01f) bindCircleAnim = 0f;
            }
        } else if (bindClosing) {
            if (showKeyLabel) keyLabelAnim += (1f - keyLabelAnim) * 12f * dt2;
            bindAppearAnim += (0f - bindAppearAnim) * 14f * dt2;
            if (bindAppearAnim < 0.01f) {
                bindAppearAnim = 0f; bindClosing = false;
                bindCircleAnim = 0f; bindingModule = null;
            }
        }

        float listY = colY + HEADER_H + HEADER_GAP;
        float curY = listY + LIST_TOP_PADDING + scrollOffset;

        for (ModuleStructure module : displayModules) {
            boolean hovered = mouseX >= colX + PAD_X && mouseX <= colX + width - PAD_X
                    && mouseY >= curY && mouseY <= curY + MODULE_H;

            if (hovered && module.getDescription() != null && !module.getDescription().isEmpty()) {
                hoveredModuleForTooltip = module;
            } else if (hoveredModuleForTooltip == module) {
                hoveredModuleForTooltip = null;
            }

            float h = hoverAnim.getOrDefault(module, 0f);
            h += ((hovered ? 1f : 0f) - h) * 15f * dt;
            hoverAnim.put(module, h);

            float tT = module.isState() ? 1f : 0f;
            float t = toggleAnim.getOrDefault(module, tT);
            t += (tT - t) * 15f * dt;
            toggleAnim.put(module, t);

            float eT = (module == expandedModule) ? 1f : 0f;
            float e = expandAnim.getOrDefault(module, 0f);
            e += (eT - e) * 14f * dt;
            if (Math.abs(eT - e) < 0.001f) e = eT;
            expandAnim.put(module, e);

            curY += MODULE_H + MODULE_GAP;
            if (e > 0.01f && hasSettings(module))
                curY += computeSettingsHeight(getOrBuildSettings(module)) * e;

            if (hoveredModuleForTooltip != null) {
                boolean stillInColumn = mouseX >= colX && mouseX <= colX + width;
                if (!stillInColumn) {
                    hoveredModuleForTooltip = null;
                }
            }

            if (hoveredModuleForTooltip == null) {
                tooltipAlpha += (0f - tooltipAlpha) * TOOLTIP_FADE_SPEED;
                if (tooltipAlpha < 0.01f) tooltipAlpha = 0f;
            }
        }
    }


    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                float colX, float colY) {
        if (mouseX < colX || mouseX > colX + width) {
            return false;
        }

        float listY = colY + HEADER_H + HEADER_GAP;
        float visibleListTop = listY + LIST_TOP_PADDING;
        float visibleListBottom = visibleListTop + (MAX_COLUMN_HEIGHT - HEADER_H - HEADER_GAP - FOOTER_HEIGHT - LIST_TOP_PADDING);

        if (mouseY < visibleListTop || mouseY > visibleListBottom) return false;

        float curY = listY + LIST_TOP_PADDING + scrollOffset;
        for (ModuleStructure module : displayModules) {
            float rowTop = curY, rowBot = curY + MODULE_H;
            if (mouseX >= colX + PAD_X && mouseX <= colX + width - PAD_X
                    && mouseY >= rowTop && mouseY <= rowBot) {
                if (module.isLocked()) {
                    // глотаем клик, чтобы не провалился на то, что позади ряда
                    if (button == 0) notifyLocked(module);
                    return true;
                }
                if (button == 0) { module.switchState(); return true; }
                if (button == 1 && hasSettings(module)) {
                    expandedModule = (module == expandedModule) ? null : module;
                    return true;
                }
                if (button == 2) {
                    if (startBind(mouseX, mouseY, colX, colY)) {
                        return true;
                    }
                }
            }
            float e = expandAnim.getOrDefault(module, 0f);
            curY += MODULE_H + MODULE_GAP;
            if (e > 0.01f && hasSettings(module)) {
                float sH = computeSettingsHeight(getOrBuildSettings(module)) * e;
                if (mouseY >= rowBot && mouseY <= rowBot + sH) {
                    float sy = rowBot;
                    for (AbstractSettingComponent comp : getOrBuildSettings(module)) {
                        if (!comp.getSetting().isVisible()) continue;
                        float cH = getCompHeight(comp) + SETTING_SP;
                        if (mouseY >= sy && mouseY <= sy + cH) {
                            comp.mouseClicked((int)mouseX, (int)mouseY, button);
                            return true;
                        }
                        sy += cH;
                    }
                }
                curY += sH;
            }
        }
        return false;
    }

    private void notifyLocked(ModuleStructure module) {
        String message = module.lockedMessage();
        if (message.isEmpty()) return;

        var notifications = accident.modules.impl.hud.Notifications.getInstance();
        if (notifications != null) notifications.addNotification(message, 2000, false);
    }

    public void mouseReleased(double mouseX, double mouseY, int button, float colX, float colY) {
        if (mouseX < colX || mouseX > colX + width) return;

        if (expandedModule == null) return;
        for (AbstractSettingComponent c : getOrBuildSettings(expandedModule))
            if (c.getSetting().isVisible()) c.mouseReleased((int)mouseX, (int)mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY, float colX, float colY) {
        if (mouseX < colX || mouseX > colX + width) return false;

        if (expandedModule == null) return false;
        for (AbstractSettingComponent c : getOrBuildSettings(expandedModule))
            if (c.getSetting().isVisible() && c.mouseDragged(mouseX, mouseY, button, dX, dY)) return true;
        return false;
    }

    public boolean keyPressed(int key, int scanCode, int mods, float colX, float colY) {
        if (expandedModule == null) return false;
        for (AbstractSettingComponent c : getOrBuildSettings(expandedModule))
            if (c.getSetting().isVisible() && c.keyPressed(key, scanCode, mods)) return true;
        return false;
    }

    public boolean charTyped(char chr, int mods, float colX, float colY) {
        if (expandedModule == null) return false;
        for (AbstractSettingComponent c : getOrBuildSettings(expandedModule))
            if (c.getSetting().isVisible() && c.charTyped(chr, mods)) return true;
        return false;
    }

    public boolean hasModuleMatching(String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase(java.util.Locale.ROOT);
        for (ModuleStructure m : modules)
            if (m.getName().toLowerCase(java.util.Locale.ROOT).contains(q)) return true;
        return false;
    }

    public void scroll(double amount) {
        targetScrollOffset += (float)(amount * 12f);
        clampScroll();
    }

    public boolean startBind(double mouseX, double mouseY, float colX, float colY) {
        if (mouseX < colX || mouseX > colX + width) return false;

        float listY = colY + HEADER_H + HEADER_GAP;
        float visibleListTop = listY + LIST_TOP_PADDING;
        float visibleListBottom = visibleListTop + (MAX_COLUMN_HEIGHT - HEADER_H - HEADER_GAP - FOOTER_HEIGHT - LIST_TOP_PADDING);
        if (mouseY < visibleListTop || mouseY > visibleListBottom) return false;

        float curY = listY + LIST_TOP_PADDING + scrollOffset;
        for (ModuleStructure module : displayModules) {
            if (mouseX >= colX + PAD_X && mouseX <= colX + width - PAD_X
                    && mouseY >= curY && mouseY <= curY + MODULE_H) {
                if (bindingModule == module && !bindClosing && !showKeyLabel) {
                    bindClosing = true; return true;
                }
                bindingModule = module; bindCircleAnim = 0f; bindClosing = false;
                bindAppearAnim = 0f; showKeyLabel = false; keyLabelAnim = 0f;
                bindHoldKey = -1; bindHoldStart = 0;
                return true;
            }
            float e = expandAnim.getOrDefault(module, 0f);
            curY += MODULE_H + MODULE_GAP;
            if (e > 0.01f && hasSettings(module))
                curY += computeSettingsHeight(getOrBuildSettings(module)) * e;
        }
        return false;
    }


    public boolean handleBindKey(int key) {
        if (bindingModule == null) return false;

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            bindClosing = true;
            return true;
        }

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
            bindingModule.bindKey(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
            accident.util.config.ConfigSystem.getInstance().saveAsync();
            bindClosing = true;
            return true;
        }

        bindingModule.bindKey(key);
        accident.util.config.ConfigSystem.getInstance().saveAsync();

        bindClosing = true;
        bindHoldKey = -1;
        bindHoldStart = 0;
        bindCircleAnim = 0f;

        return true;
    }

    public boolean handleBindKeyRelease(int key) {
        if (bindingModule == null || bindHoldKey != key) return false;
        if (bindCircleAnim >= 0.99f) {
            bindingModule.bindKey(key);
            accident.screens.clickgui.BindHelper.justBoundKey = key;
            bindClosing = true;
        }
        bindHoldKey = -1; bindHoldStart = 0;
        return true;
    }

    public boolean isBinding() { return bindingModule != null && !bindClosing && !showKeyLabel; }

    private void clampScroll() {
        float maxVisibleListH = MAX_COLUMN_HEIGHT - HEADER_H - HEADER_GAP;
        float contentH = computeTotalContentHeight();

        if (contentH <= maxVisibleListH) {
            targetScrollOffset = 0;
        } else {
            float minScroll = -(contentH - maxVisibleListH);
            float oldTarget = targetScrollOffset;
            targetScrollOffset = Math.max(minScroll, Math.min(0, targetScrollOffset));
        }
    }

    private float computeTotalContentHeight() {
        float h = 0;
        for (ModuleStructure m : modules) {
            h += MODULE_H + MODULE_GAP;
            float e = expandAnim.getOrDefault(m, 0f);
            if (e > 0.01f && hasSettings(m)) {
                h += computeSettingsHeight(getOrBuildSettings(m));
            }
        }
        return h;
    }

    private float computeSettingsHeight(List<AbstractSettingComponent> settings) {
        float h = 0;
        for (AbstractSettingComponent c : settings)
            if (c.getSetting().isVisible()) h += getCompHeight(c) + SETTING_SP;
        return h + 4f;
    }

    private float getCompHeight(AbstractSettingComponent comp) {
        return comp.getTotalHeight();
    }

    private boolean hasSettings(ModuleStructure module) {
        var s = module.settings(); return s != null && !s.isEmpty();
    }

    private List<AbstractSettingComponent> getOrBuildSettings(ModuleStructure module) {
        return settingsCache.computeIfAbsent(module, m -> {
            List<AbstractSettingComponent> list = new ArrayList<>();
            new SettingComponentAdder().addSettingComponent(m.settings(), list);
            return list;
        });
    }

    private void renderBindCircle(float x, float y, float alpha) {
        float a = bindAppearAnim * alpha;
        if (a < 0.01f) return;
        float degrees = bindCircleAnim * 360f;
        if (degrees > 5f)
            Render2D.arc(x - 6f, y - 6f, 12f, 2f, degrees, -90f, getAccentColor(a));
    }

    private int getAccentColor(float alphaFactor) {
        return getThemeAccentColor(alphaFactor);
    }

    private int getThemeBlurColor(float alphaFactor, boolean isHeader) {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;

        int[] palette = theme.palette;

        int baseColor;
        if (palette == null || palette.length == 0) {
            baseColor = isHeader ? 0x0A0A12 : 0x11111F;
        } else if (palette.length == 1) {
            baseColor = palette[0];
        } else {
            long timeMs = System.currentTimeMillis();
            double speed = 6000.0;
            float indexProgress = (float) ((timeMs % (long)speed) / speed * palette.length);
            int index1 = (int) indexProgress % palette.length;
            int index2 = (index1 + 1) % palette.length;
            float fraction = indexProgress - (int) indexProgress;
            baseColor = interpolateColor(palette[index1], palette[index2], fraction);
        }

        int darkened = darkenColor(baseColor, isHeader ? 0.85f : 0.80f);
        int alpha = clamp((int) (alphaFactor * 255));
        return (alpha << 24) | (darkened & 0xFFFFFF);
    }

    private int getThemeAccentColor(float alphaFactor) {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;
        return getMixedColorFromPalette(theme.palette, alphaFactor);
    }

    private int getMixedColorFromPalette(int[] palette, float alphaFactor) {
        if (palette == null || palette.length == 0) {
            int alpha = clamp((int) (alphaFactor * 255));
            return (alpha << 24) | 0xC0C0C0;
        }
        if (palette.length == 1) {
            int alpha = clamp((int) (alphaFactor * 255));
            return (alpha << 24) | (palette[0] & 0xFFFFFF);
        }

        long timeMs = System.currentTimeMillis();
        double speed = 6000.0;
        float indexProgress = (float) ((timeMs % (long)speed) / speed * palette.length);
        int index1 = (int) indexProgress % palette.length;
        int index2 = (index1 + 1) % palette.length;
        float fraction = indexProgress - (int) indexProgress;

        int mixed = interpolateColor(palette[index1], palette[index2], fraction);
        int alpha = clamp((int) (alphaFactor * 255));
        return (alpha << 24) | (mixed & 0xFFFFFF);
    }

    private static int darkenColor(int color, float factor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = (int) (r * (1f - factor));
        g = (int) (g * (1f - factor));
        b = (int) (b * (1f - factor));

        return (r << 16) | (g << 8) | b;
    }

    private static int interpolateColor(int color1, int color2, float factor) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        return ((int) (r1 + factor * (r2 - r1)) << 16)
                | ((int) (g1 + factor * (g2 - g1)) << 8)
                | (int) (b1 + factor * (b2 - b1));
    }

    public boolean isHovered(double mouseX, double mouseY, float colX, float colY) {
        return mouseX >= colX && mouseX <= colX + width
                && mouseY >= colY && mouseY <= colY + MAX_COLUMN_HEIGHT;
    }

    private void renderModuleTooltip(DrawContext context, float alpha) {
        if (hoveredModuleForTooltip == null) return;

        String desc = hoveredModuleForTooltip.getDescription();
        if (desc == null || desc.isEmpty()) return;

        tooltipAlpha += (1f - tooltipAlpha) * TOOLTIP_FADE_SPEED;
        if (tooltipAlpha < 0.01f) return;

        float textWidth = Fonts.TEST.getWidth(desc, 5);
        float tooltipW = textWidth + TOOLTIP_PADDING * 2;
        float tooltipH = 14f;

        float tooltipX = TOOLTIP_FIXED_X - tooltipW / 2f;
        float tooltipY = TOOLTIP_FIXED_Y;

        int bgAlpha = (int)(tooltipAlpha * 0.9f * 255);
        int outlineAlpha = (int)(tooltipAlpha * 0.6f * 255);
        int textAlpha = (int)(tooltipAlpha * 220);
        Fonts.TEST.draw(desc, tooltipX + TOOLTIP_PADDING, tooltipY + tooltipH / 2f - 2.5f, 8,
                (textAlpha << 24) | 0xE0E0E0);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
    private float easeOutQuart(float x) { return 1f - (float)Math.pow(1 - x, 4); }
}