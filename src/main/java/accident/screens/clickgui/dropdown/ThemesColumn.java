package accident.screens.clickgui.dropdown;

import net.minecraft.client.gui.DrawContext;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.render.shader.Scissor;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThemesColumn {

    public static final float HEADER_H = 22f;
    public static final float THEME_H = 20f;
    public static final float THEME_GAP = 1.5f;
    public static final float RADIUS = 6f;
    public static final float PAD_X = 3f;
    public static final float HEADER_GAP = 1.5f;
    public static final float LIST_TOP_PADDING = 3f;
    public static final float MAX_COLUMN_HEIGHT = 220f;

    private static final int BASE_BG_COLOR = 0x000000;
    private static final float ITEM_ALPHA = 0.08f;
    private static final int COLOR_TEXT = 0xD8D8D8;
    private static final int COLOR_ACCENT = 0xC0C0C0;
    private static final int COLOR_SCROLL = 0x636363;
    public static final float FOOTER_HEIGHT = 3f;
    private static final int HEADER_BLUR_COLOR = 0x0A0A12;
    private static final int LIST_BLUR_COLOR = 0x11111F;

    private static final int DOT_ROWS = 3;
    private static final float DOT_SIZE = 1f;
    private static final float DOT_SPACING = 2.5f;
    private static final float DOT_MARGIN_RIGHT = 7f;
    private static final float DOT_COL_GAP = 4f;
    private static final float DOTS_MARGIN_RIGHT = 6f;
    private static final float DOT_RADIUS = 1f;
    private static final float DOT_GAP = 2.5f;

    public enum Theme {
        DEFAULT("Default", new int[]{0xFFFFFF}),
        GREEN("Green", new int[]{0x50C878, 0x60D888, 0x70E898, 0x80F8A8, 0x90FFB8}),
        RED("Red", new int[]{0xFF5050, 0xFF6060, 0xFF7070, 0xFF8080, 0xFF9090}),
        PURPLE("Purple", new int[]{0x808ED7, 0x8189D5, 0x8184D3, 0x827FD1, 0x827ACE, 0x8375CC, 0x8370CA}),
        BLUE("Blue", new int[]{0x5090FF, 0x60A0FF, 0x70B0FF, 0x80C0FF, 0x90D0FF}),
        ORANGE("Orange", new int[]{0xFF9050, 0xFFA060, 0xFFB070, 0xFFC080, 0xFFD090}),
        CYAN("Cyan", new int[]{0x50FFFF, 0x60FFFF, 0x70FFFF, 0x80FFFF, 0x90FFFF});

        public final String name;
        public final int[] palette;

        Theme(String name, int[] palette) {
            this.name = name;
            this.palette = palette;
        }
    }

    private final List<Theme> themes = new ArrayList<>();
    private Theme selectedTheme = Theme.DEFAULT;
    private final Map<Theme, Float> hoverAnim = new HashMap<>();
    private final Map<Theme, Float> selectAnim = new HashMap<>();

    private float columnAnim = 0f;
    private long columnAnimStart = System.currentTimeMillis();
    private long lastUpdate = System.currentTimeMillis();

    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;

    private Runnable onThemeChange = null;

    public ThemesColumn() {
        for (Theme t : Theme.values()) {
            themes.add(t);
            hoverAnim.put(t, 0f);
            selectAnim.put(t, t == selectedTheme ? 1f : 0f);
        }
    }

    public void setOnThemeChange(Runnable callback) {
        this.onThemeChange = callback;
    }

    public Theme getSelectedTheme() {
        return selectedTheme;
    }

    private int interpolateColor(int color1, int color2, float factor) {
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

    private int getMixedColor(int[] palette, float alphaFactor) {
        if (palette.length == 1) {
            int alpha = Math.max(0, Math.min(255, (int) (alphaFactor * 255)));
            return (alpha << 24) | (palette[0] & 0xFFFFFF);
        }

        long timeMs = System.currentTimeMillis();
        double speed = 6000.0;
        float indexProgress = (float) ((timeMs % (long) speed) / speed * palette.length);
        int index1 = (int) indexProgress % palette.length;
        int index2 = (index1 + 1) % palette.length;
        float fraction = indexProgress - (int) indexProgress;

        int mixed = interpolateColor(palette[index1], palette[index2], fraction);
        int alpha = Math.max(0, Math.min(255, (int) (alphaFactor * 255)));
        return (alpha << 24) | (mixed & 0xFFFFFF);
    }

    public int getThemeColor(Theme theme, float alphaFactor) {
        return getMixedColor(theme.palette, alphaFactor);
    }

    public void render(DrawContext context, float x, float y, int mouseX, int mouseY, float alphaMultiplier) {
        updateAnimations(mouseX, mouseY, x, y);

        float alpha = columnAnim * alphaMultiplier;
        float contentH = computeTotalContentHeight();
        float maxVisibleListH = MAX_COLUMN_HEIGHT - HEADER_H - HEADER_GAP;
        float visContentH = Math.min(contentH, maxVisibleListH);

        float alphaFactor = alphaMultiplier * columnAnim;
        int headerGlassAlpha = (int) (alphaFactor * 60);
        int headerBgColor = (headerGlassAlpha << 24) | BASE_BG_COLOR;

        Render2D.blur(x, y, 80f, HEADER_H, 10f, 4f, HEADER_BLUR_COLOR);
        Render2D.rect(x, y, 80f, HEADER_H, headerBgColor, RADIUS);
        renderHeader(x, y, alpha, alphaMultiplier);

        float listY = y + HEADER_H + HEADER_GAP;
        float listBgHeight = visContentH + FOOTER_HEIGHT + LIST_TOP_PADDING;

        if (listBgHeight > 1f) {
            int listGlassAlpha = (int) (alphaFactor * 40);
            int listBgColor = (listGlassAlpha << 24) | BASE_BG_COLOR;

            Render2D.blur(x, listY, 80f, listBgHeight, 10f, 4f, LIST_BLUR_COLOR);
            Render2D.rect(x, listY, 80f, listBgHeight, listBgColor, RADIUS);

            Scissor.enable(x, listY, 80f, visContentH, 2);

            float curY = listY + LIST_TOP_PADDING + scrollOffset;
            for (Theme theme : themes) {
                curY = renderTheme(context, theme, x, curY, mouseX, mouseY, alpha, alphaMultiplier, listY, visContentH);
            }
            Scissor.disable();

            renderScrollbar(x, listY, 80f, visContentH, alpha, alphaMultiplier);
        }
    }

    private void renderHeader(float x, float y, float alpha, float alphaMultiplier) {
        int iconAlpha = clamp((int) (alpha * 0.80f * 255));
        int iconColor = (iconAlpha << 24) | COLOR_ACCENT;
        Fonts.COLORIC.draw("A", x + 10, y + 6.5f, 9f, iconColor);

        String name = "Themes";
        int nameAlpha = clamp((int) (alpha * 0.88f * 255));
        int nameColor = (nameAlpha << 24) | COLOR_TEXT;
        Fonts.TEST.draw(name, x + 26, y + 7.5f, 7f, nameColor);

        float totalDotsHeight = DOT_ROWS * DOT_SIZE + (DOT_ROWS - 1) * DOT_SPACING;
        float dotsStartY = y + (HEADER_H - totalDotsHeight) / 2f;
        float dotsX = x + 80f - DOT_MARGIN_RIGHT - DOT_SIZE * 2 - DOT_COL_GAP;
        int dotColor = ((int) (alpha * 0.20f * 255) << 24) | 0xFFFFFF;

        for (int row = 0; row < DOT_ROWS; row++) {
            float dotY = dotsStartY + row * (DOT_SIZE + DOT_SPACING);
            Render2D.rect(dotsX, dotY, DOT_SIZE, DOT_SIZE, dotColor, DOT_SIZE / 2f);
            Render2D.rect(dotsX + DOT_SIZE + DOT_COL_GAP, dotY, DOT_SIZE, DOT_SIZE, dotColor, DOT_SIZE / 2f);
        }
    }

    private float renderTheme(DrawContext context, Theme theme, float x, float y,
                              int mouseX, int mouseY, float alpha, float alphaMultiplier,
                              float listY, float listH) {

        float hover = hoverAnim.getOrDefault(theme, 0f);
        float select = selectAnim.getOrDefault(theme, 0f);
        boolean isSelected = theme == selectedTheme;

        int itemBgColor;
        if (isSelected) {
            int bgAlpha = (int) (255 * alphaMultiplier * 0.7f);
            itemBgColor = (bgAlpha << 24) | 0x202535;
        } else {
            int bgAlpha = (int) (255 * alphaMultiplier * ITEM_ALPHA);
            itemBgColor = (bgAlpha << 24) | BASE_BG_COLOR;
        }
        Render2D.rect(x + PAD_X, y, 80f - (PAD_X * 2), THEME_H, itemBgColor, RADIUS - 2);

        if (hover > 0.02f) {
            int hA = clamp((int) (hover * (isSelected ? 40 : 20) * alphaMultiplier));
            Render2D.rect(x + PAD_X, y, 80f - (PAD_X * 2), THEME_H,
                    (hA << 24) | (isSelected ? 0xC0D0FF : 0xA0A0CC), RADIUS - 2);
        }

        float ty = y + (THEME_H / 2f) - 1.5f;
        int textAlpha = clamp((int) (150 + (255 - 150) * select));
        int nameColor = (textAlpha << 24) | 0xFFFFFF;
        Fonts.TEST.draw(theme.name, x + PAD_X + 8, ty - 2.5f, 6f, nameColor);

        float circleSize = 12f;
        float circleX = x + 80f - PAD_X - circleSize - 4;
        float circleY = y + THEME_H / 2f - circleSize / 2f;

        int themeColor = getThemeColor(theme, alphaMultiplier);
        Render2D.rect(circleX, circleY, circleSize, circleSize, themeColor, circleSize / 2f);

        int outlineAlpha = isSelected ? 220 : 80;
        float outlineWidth = isSelected ? 1f : 0.5f;
        Render2D.outline(circleX, circleY, circleSize, circleSize, outlineWidth,
                (outlineAlpha << 24) | 0xFFFFFF, circleSize / 2f);

        if (isSelected && select > 0.5f) {
            int checkAlpha = (int) ((140 + select * 115) * alphaMultiplier);
            Fonts.SETTINGSICONS.draw("\uE006", circleX + 2f, circleY + 2f, 8f,
                    (checkAlpha << 24) | 0xFFFFFF);
        }

        return y + THEME_H + THEME_GAP;
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

        int trackAlpha = clamp((int) (45 * alphaMultiplier));
        Render2D.rect(sbX, y, sbW, h, (trackAlpha << 24) | COLOR_SCROLL, 4f);

        int thumbAlpha = clamp((int) (50 * alphaMultiplier));
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

        float listY = colY + HEADER_H + HEADER_GAP;
        float curY = listY + LIST_TOP_PADDING + scrollOffset;

        for (Theme theme : themes) {
            boolean hovered = mouseX >= colX + PAD_X && mouseX <= colX + 80f - PAD_X
                    && mouseY >= curY && mouseY <= curY + THEME_H;

            float h = hoverAnim.getOrDefault(theme, 0f);
            h += ((hovered ? 1f : 0f) - h) * 15f * dt;
            hoverAnim.put(theme, h);

            float sT = (theme == selectedTheme) ? 1f : 0f;
            float s = selectAnim.getOrDefault(theme, sT);
            s += (sT - s) * 15f * dt;
            selectAnim.put(theme, s);

            curY += THEME_H + THEME_GAP;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                float colX, float colY) {
        if (button != 0) return false;

        float listY = colY + HEADER_H + HEADER_GAP;
        float visibleListTop = listY + LIST_TOP_PADDING;
        float visibleListBottom = visibleListTop + (MAX_COLUMN_HEIGHT - HEADER_H - HEADER_GAP - FOOTER_HEIGHT - LIST_TOP_PADDING);

        if (mouseY < visibleListTop || mouseY > visibleListBottom) return false;

        float curY = listY + LIST_TOP_PADDING + scrollOffset;
        for (Theme theme : themes) {
            if (mouseX >= colX + PAD_X && mouseX <= colX + 80f - PAD_X
                    && mouseY >= curY && mouseY <= curY + THEME_H) {
                selectedTheme = theme;
                updateGlobalTheme(theme);
                for (Theme t : themes) {
                    selectAnim.put(t, t == theme ? 1f : 0f);
                }
                if (onThemeChange != null) onThemeChange.run();
                return true;
            }
            curY += THEME_H + THEME_GAP;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        targetScrollOffset -= (float) (amount * 12f);
        clampScroll();
        return true;
    }

    private float computeTotalContentHeight() {
        return themes.size() * (THEME_H + THEME_GAP);
    }

    private void clampScroll() {
        float maxVisibleListH = MAX_COLUMN_HEIGHT - HEADER_H - HEADER_GAP;
        float contentH = computeTotalContentHeight();
        if (contentH <= maxVisibleListH) {
            targetScrollOffset = 0;
        } else {
            float minScroll = -(contentH - maxVisibleListH);
            targetScrollOffset = Math.max(minScroll, Math.min(0, targetScrollOffset));
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private float easeOutQuart(float x) {
        return 1f - (float) Math.pow(1 - x, 4);
    }

    public void setWidth(float w) {
    }

    public boolean isHovered(double mouseX, double mouseY, float colX, float colY) {
        return mouseX >= colX && mouseX <= colX + 80f
                && mouseY >= colY && mouseY <= colY + MAX_COLUMN_HEIGHT;
    }

    private static Theme currentGlobalTheme = Theme.valueOf("DEFAULT");

    public static Theme getCurrentGlobalTheme() {
        return currentGlobalTheme;
    }

    public static void updateGlobalTheme(Theme theme) {
        currentGlobalTheme = theme;
    }
}