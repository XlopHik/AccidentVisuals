package accident.screens.clickgui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import accident.IMinecraft;
import accident.modules.impl.render.ClickGuiModule;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.screens.clickgui.dropdown.DropdownColumn;
import accident.screens.clickgui.dropdown.ClientColumn;
import accident.util.lang.LanguageManager;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.screens.clickgui.notepad.NotepadPanel;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ClickGui extends Screen implements IMinecraft {

    public static ClickGui INSTANCE = new ClickGui();

    private static final ModuleCategory[] CATEGORIES = {
            ModuleCategory.MOVEMENT, ModuleCategory.RENDER,
            ModuleCategory.MISC, ModuleCategory.HUD, ModuleCategory.LUA
    };

    private static final float COLUMN_SPACING = 6f;
    private static final float COLUMN_MARGIN  = 8f;
    private static final float COLUMN_TOP     = 120f;
    private int activeTab = 0;
    private float tabAnim         = 0f;
    private float tabAnimTarget   = 0f;
    private static final float PILL_W       = 160f;
    private static final float PILL_H       = 22f;
    private static final float PILL_RADIUS  = 11f;
    private static final float TAB_W        = PILL_W / 2f;

    private final NotepadPanel notepad = new NotepadPanel();
    private final accident.screens.clickgui.cs.CsGui csGui = new accident.screens.clickgui.cs.CsGui();

    // какой из двух layout'ов сейчас активен, читаем прямо из настройки, без кэша
    private boolean isCsStyle() {
        ClickGuiModule module = ClickGuiModule.getInstance();
        return module != null && module.getStyle().isSelected("CS GUI");
    }


    private final List<DropdownColumn> columns = new ArrayList<>();
    private ClientColumn clientColumn = null;
    private ThemesColumn themesColumn = new ThemesColumn();

    private ClientColumn getClientColumn() {
        if (clientColumn == null) clientColumn = new ClientColumn();
        return clientColumn;
    }

    private float openAnimation  = 0f;
    private long  openStartTime  = 0;
    private static final float OPEN_DURATION_MS = 180f;

    private float   scrollOffsetX       = 0f;
    private float   targetScrollOffsetX = 0f;
    private boolean dragging            = false;
    private double  dragStartX          = 0;
    private float   dragStartOffset     = 0;

    private boolean closing = false;

    public ClickGui() { super(Text.literal("ClickGUI")); }

    public boolean isClosing() { return closing; }

    public void openGui() {
        if (mc.currentScreen == null) { closing = false; mc.setScreen(this); }
    }

    @Override
    protected void init() {
        rebuildColumns();
        for (DropdownColumn column : columns) column.loadState();
        openStartTime = System.currentTimeMillis();
        openAnimation = 0f;
        closing = false;
    }

    private void rebuildColumns() {
        columns.clear();
        var repo = accident.Initialization.getInstance().getManager().getModuleRepository();
        for (ModuleCategory cat : CATEGORIES) {
            List<ModuleStructure> mods = repo.modules().stream()
                    .filter(m -> m.getCategory() == cat)
                    .sorted(Comparator.comparing(ModuleStructure::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
            columns.add(new DropdownColumn(cat, mods, 110f));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateOpenAnimation();
        updateScrollAnimation();
        updateTabAnimation();
        renderGui(context, mouseX, mouseY);
    }

    public void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        int mouseX = (int) mc.mouse.getScaledX(mc.getWindow());
        int mouseY = (int) mc.mouse.getScaledY(mc.getWindow());
        updateOpenAnimation();
        updateScrollAnimation();
        updateTabAnimation();
        renderGui(context, mouseX, mouseY);
    }

    private void renderGui(DrawContext context, int mouseX, int mouseY) {
        float a = openAnimation;

        ClickGuiModule guiMod = ClickGuiModule.getInstance();
        float darkness = guiMod != null ? guiMod.getBgDarkness().getValue() : 0.6f;
        float opacity  = guiMod != null ? guiMod.getColumnOpacity().getValue() : 0.85f;

        accident.modules.impl.render.GuiEcho echo = accident.modules.impl.render.GuiEcho.getInstance();

        int dimAlpha = clamp((int)(0.25f * 255 * a));
        Render2D.rect(0, 0, Render2D.getFixedScaledWidth(), Render2D.getFixedScaledHeight(),
                new Color(0, 0, 0, dimAlpha).getRGB(), 0);

        // дальше сам GUI - затемнение фона сюда не включаем, иначе echo-скриншот выйдет тёмным
        if (echo != null) echo.beginGuiCapture();

        renderTabPill(context, mouseX, mouseY, a);

        if (tabAnim < 0.99f) {
            float cgAlpha = a * (1f - tabAnim);
            renderClickGuiContent(context, mouseX, mouseY, cgAlpha);
        }

        if (tabAnim > 0.01f) {
            float npAlpha = a * tabAnim;
            notepad.render(context, mouseX, mouseY, npAlpha);
        }

        renderLogin(a);
        if (activeTab == 0) renderGirl(context, a);

        if (echo != null) echo.endGuiCapture();
    }


    private void renderTabPill(DrawContext context, int mouseX, int mouseY, float alpha) {
        int screenW = Render2D.getFixedScaledWidth();

        float pillX = (screenW - PILL_W) / 2f;
        float pillY = 12f;

        int glassA = (int)(alpha * 65);
        int blurColor = getThemeBlurColor(alpha * 0.60f);
        Render2D.blur(pillX, pillY, PILL_W, PILL_H, 12f, 4f, blurColor);
        Render2D.rect(pillX, pillY, PILL_W, PILL_H,
                (glassA << 24) | 0x000000, PILL_RADIUS);

        float sliderX = pillX + tabAnim * TAB_W;
        int sliderA = (int)(alpha * 0.35f * 255);
        Render2D.rect(sliderX, pillY, TAB_W, PILL_H,
                (sliderA << 24) | getThemeRawColor(), PILL_RADIUS);

        int outlineA = (int)(alpha * 0.18f * 255);
        Render2D.outline(pillX, pillY, PILL_W, PILL_H, 0.5f,
                (outlineA << 24) | 0xFFFFFF, PILL_RADIUS);

        String[] labels = { "ClickGui", "NotePad" };
        for (int i = 0; i < 2; i++) {
            float tabX    = pillX + i * TAB_W;
            float labelW  = Fonts.TEST.getWidth(labels[i], 6f);
            float labelX  = tabX + TAB_W / 2f - labelW / 2f;
            float labelY  = pillY + PILL_H / 2f - 3.5f;

            boolean isActive = (i == activeTab);
            boolean hov = mouseX >= tabX && mouseX <= tabX + TAB_W
                    && mouseY >= pillY && mouseY <= pillY + PILL_H;

            float brightness = isActive ? 1.0f : (hov ? 0.75f : 0.50f);
            int txtA = (int)(alpha * brightness * 255);
            int txtColor = isActive
                    ? ((txtA << 24) | 0xFFFFFF)
                    : ((txtA << 24) | 0xC0C0C0);

            Fonts.TEST.draw(labels[i], labelX, labelY, 6f, txtColor);
        }
    }


    private void renderClickGuiContent(DrawContext context, int mouseX, int mouseY, float a) {
        if (isCsStyle()) {
            csGui.render(context, mouseX, mouseY, a);
            return;
        }

        themesColumn.render(context, 4f, 120f, mouseX, mouseY, a);

        float colW   = getColumnWidth();
        float startX = getColumnsStartX();
        String searchQ = getClientColumn().getQuery();

        for (int i = 0; i < columns.size(); i++) {
            float colX = startX + i * (colW + COLUMN_SPACING);
            columns.get(i).setWidth(colW);
            columns.get(i).setFilter(searchQ);
            columns.get(i).render(context, colX, COLUMN_TOP, mouseX, mouseY, a);
        }

        renderSharedSearch(context, mouseX, mouseY, a);
    }

    private void updateOpenAnimation() {
        float t = Math.min(1f, (System.currentTimeMillis() - openStartTime) / OPEN_DURATION_MS);
        openAnimation = 1f - (float) Math.pow(1 - t, 4);
    }

    private void updateScrollAnimation() {
        float diff = targetScrollOffsetX - scrollOffsetX;
        if (Math.abs(diff) > 0.1f) scrollOffsetX += diff * 0.15f;
        else scrollOffsetX = targetScrollOffsetX;
    }

    private void updateTabAnimation() {
        float diff = tabAnimTarget - tabAnim;
        if (Math.abs(diff) > 0.001f) tabAnim += diff * 0.18f;
        else tabAnim = tabAnimTarget;
    }

    private void renderSharedSearch(DrawContext context, int mouseX, int mouseY, float alpha) {
        float colW = getColumnWidth();
        float startX = getColumnsStartX();
        int totalCols = columns.size() + 1;
        float totalColsWidth = totalCols * colW + (totalCols - 1) * COLUMN_SPACING;
        float searchBarW = 220f;
        float searchBarX = startX + totalColsWidth / 2f - searchBarW / 2f;
        float searchBarY = COLUMN_TOP + DropdownColumn.MAX_COLUMN_HEIGHT + 12f;

        DropdownColumn.renderSearchBar(
                context,
                searchBarX, searchBarY, searchBarW,
                getClientColumn().getQuery(),
                getClientColumn().isFocused(),
                accident.util.KeyboardLayoutDetector.getCurrentInputLanguage(),
                alpha
        );
    }

    private void renderLogin(float a) {
        String login = client.getSession().getUsername();
        int screenH = Render2D.getFixedScaledHeight();
        Fonts.TEST.draw(login, 6f, screenH - 20f, 7f, 0xFFFFFFFF);
    }

    private void renderGirl(DrawContext context, float a) {
        ClickGuiModule guiMod = ClickGuiModule.getInstance();
        if (guiMod == null) return;
        String selected = guiMod.getGirlMode().getSelected();
        if (selected.equals("None")) return;
        net.minecraft.util.Identifier girlTex = net.minecraft.util.Identifier.of("accident",
                "textures/gui/girls/" + selected.toLowerCase() + ".png");
        float scale = guiMod.getGirlScale().getValue();
        float girlAlpha = guiMod.getGirlAlpha().getValue();
        float finalW = 200f * scale, finalH = 200f * scale;
        int screenW = Render2D.getFixedScaledWidth(), screenH = Render2D.getFixedScaledHeight();
        int alphaInt = clamp((int)(a * girlAlpha * 255));
        Render2D.texture(girlTex, screenW - finalW - 10, screenH - finalH - 10, finalW, finalH,
                0, 0, 1, 1, (alphaInt << 24) | 0xFFFFFF, 1f, 0f);
    }

    private float getColumnWidth() {
        int screenW = Render2D.getFixedScaledWidth();
        int totalCols = columns.size() + 1;
        float available = screenW - COLUMN_MARGIN * 2 - (totalCols - 1) * COLUMN_SPACING;
        return Math.min(120f, available / totalCols);
    }

    private float getColumnsStartX() {
        int screenW = Render2D.getFixedScaledWidth();
        float colW  = getColumnWidth();
        int totalCols = columns.size() + 1;
        float totalW = totalCols * colW + (totalCols - 1) * COLUMN_SPACING;
        return (screenW - totalW) / 2f + scrollOffsetX;
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();

        int screenW = Render2D.getFixedScaledWidth();
        float pillX = (screenW - PILL_W) / 2f;
        float pillY = 12f;

        if (btn == 0 && mx >= pillX && mx <= pillX + PILL_W
                && my >= pillY && my <= pillY + PILL_H) {
            int clickedTab = (mx < pillX + TAB_W) ? 0 : 1;
            if (clickedTab != activeTab) {
                activeTab      = clickedTab;
                tabAnimTarget  = (float) clickedTab;
                getClientColumn().setFocused(false);
            }
            return true;
        }

        if (activeTab == 1) {
            int sW = Render2D.getFixedScaledWidth();
            int sH = Render2D.getFixedScaledHeight();
            float totalW = notepad.getTotalW(sW);
            float totalH = notepad.getTotalH(sH);
            float panelX = notepad.getPanelX(sW, totalW);
            float panelY = notepad.getPanelY(sH, totalH);
            return notepad.mouseClicked(mx, my, btn, panelX, panelY, totalW, totalH);
        }

        if (isCsStyle()) {
            return csGui.mouseClicked(mx, my, btn);
        }

        float colW = getColumnWidth();
        float startX = getColumnsStartX();
        int totalCols = columns.size() + 1;
        float totalColsWidth = totalCols * colW + (totalCols - 1) * COLUMN_SPACING;

        final float SEARCH_BAR_W = 220f;
        final float SEARCH_BAR_H = 24f;
        float searchBarX = startX + totalColsWidth / 2f - SEARCH_BAR_W / 2f;
        float searchBarY = COLUMN_TOP + 220f + 12f;

        if (btn == 0 && mx >= searchBarX && mx <= searchBarX + SEARCH_BAR_W &&
                my >= searchBarY && my <= searchBarY + SEARCH_BAR_H) {
            boolean newFocus = !getClientColumn().isFocused();
            getClientColumn().setFocused(newFocus);
            if (!newFocus) getClientColumn().clear();
            return true;
        }

        if (themesColumn.mouseClicked(mx, my, btn, 4f, 120f)) return true;

        for (int i = 0; i < columns.size(); i++) {
            float colX = startX + i * (colW + COLUMN_SPACING);
            if (columns.get(i).mouseClicked(mx, my, btn, colX, COLUMN_TOP)) return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (activeTab == 1) return true;

        if (isCsStyle()) {
            csGui.mouseReleased(click.x(), click.y(), click.button());
            return super.mouseReleased(click);
        }

        if (click.button() == 2) dragging = false;
        float startX = getColumnsStartX(), cw = getColumnWidth();
        for (int i = 0; i < columns.size(); i++) {
            float colX = startX + i * (cw + COLUMN_SPACING);
            columns.get(i).mouseReleased(click.x(), click.y(), click.button(), colX, COLUMN_TOP);
        }
        float clientColX = startX + (columns.size() + 1) * (cw + COLUMN_SPACING);
        getClientColumn().mouseReleased(click.x(), click.y(), click.button(), clientColX, COLUMN_TOP);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double dX, double dY) {
        if (activeTab == 1) return true;

        if (isCsStyle()) {
            return csGui.mouseDragged(click.x(), click.y(), click.button(), dX, dY);
        }

        if (dragging && click.button() == 2) {
            targetScrollOffsetX = dragStartOffset + (float)(click.x() - dragStartX);
            clampScroll();
        }
        float startX = getColumnsStartX(), cw = getColumnWidth();
        for (int i = 0; i < columns.size(); i++) {
            float colX = startX + i * (cw + COLUMN_SPACING);
            columns.get(i).mouseDragged(click.x(), click.y(), click.button(), dX, dY, colX, COLUMN_TOP);
        }
        float clientColX = startX + (columns.size() + 1) * (cw + COLUMN_SPACING);
        getClientColumn().mouseDragged(click.x(), click.y(), click.button(), dX, dY, clientColX, COLUMN_TOP);
        return super.mouseDragged(click, dX, dY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hScroll, double vScroll) {
        if (activeTab == 1) {
            int sW = Render2D.getFixedScaledWidth();
            int sH = Render2D.getFixedScaledHeight();
            float totalW = notepad.getTotalW(sW);
            float totalH = notepad.getTotalH(sH);
            float panelX = notepad.getPanelX(sW, totalW);
            float panelY = notepad.getPanelY(sH, totalH);
            notepad.mouseScrolled(mouseX, mouseY, panelX, panelY, totalW, totalH, vScroll);
            return true;
        }

        if (isCsStyle()) {
            return csGui.mouseScrolled(mouseX, mouseY, vScroll);
        }

        if (themesColumn.isHovered(mouseX, mouseY, 4f, 120f)) {
            themesColumn.mouseScrolled(mouseX, mouseY, vScroll);
            return true;
        }
        float startX = getColumnsStartX(), cw = getColumnWidth();
        float hudColX = startX + columns.size() * (cw + COLUMN_SPACING);
        for (int i = 0; i < columns.size(); i++) {
            float colX = startX + i * (cw + COLUMN_SPACING);
            if (columns.get(i).isHovered(mouseX, mouseY, colX, COLUMN_TOP)) {
                columns.get(i).scroll(vScroll);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (activeTab == 1) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }
            return notepad.keyPressed(key);
        }

        if (isCsStyle()) {
            if (csGui.keyPressed(key, input.scancode(), input.modifiers())) return true;
            return super.keyPressed(input);
        }

        if (getClientColumn().onKeyPressed(key)) return true;

        for (DropdownColumn column : columns) {
            if (column.isBinding()) { column.handleBindKey(key); return true; }
        }

        float startX = getColumnsStartX(), cw = getColumnWidth();
        for (int i = 0; i < columns.size(); i++) {
            float colX = startX + i * (cw + COLUMN_SPACING);
            if (columns.get(i).keyPressed(key, input.scancode(), input.modifiers(), colX, COLUMN_TOP))
                return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (activeTab == 1) return false;
        int key = input.key();
        for (int i = 0; i < columns.size(); i++)
            if (columns.get(i).isBinding()) { columns.get(i).handleBindKeyRelease(key); return true; }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();

        if (activeTab == 1) {
            return notepad.charTyped(chr);
        }

        if (isCsStyle()) {
            if (csGui.charTyped(chr, input.modifiers())) return true;
            return super.charTyped(input);
        }

        if (getClientColumn().onCharTyped(chr)) return true;

        float startX = getColumnsStartX(), cw = getColumnWidth();
        for (int i = 0; i < columns.size(); i++) {
            float colX = startX + i * (cw + COLUMN_SPACING);
            if (columns.get(i).charTyped(chr, input.modifiers(), colX, COLUMN_TOP)) return true;
        }
        float hudColX = startX + columns.size() * (cw + COLUMN_SPACING);
        return super.charTyped(input);
    }

    @Override
    public void close() {
        for (DropdownColumn column : columns) column.saveState();
        closing = false;
        super.close();
    }

    private void clampScroll() {}


    private int getThemeBlurColor(float alphaFactor) {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;
        int[] pal = theme.palette;
        int base;
        if (pal == null || pal.length == 0) {
            base = 0x0A0A12;
        } else if (pal.length == 1) {
            base = darkenColor(pal[0], 0.82f);
        } else {
            long t = System.currentTimeMillis();
            float idx = (float)((t % 6000L) / 6000.0 * pal.length);
            int i1 = (int) idx % pal.length, i2 = (i1 + 1) % pal.length;
            float frac = idx - (int) idx;
            base = darkenColor(interpolateColor(pal[i1], pal[i2], frac), 0.82f);
        }
        int a = clamp((int)(alphaFactor * 255));
        return (a << 24) | (base & 0xFFFFFF);
    }

    private int getThemeRawColor() {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;
        int[] pal = theme.palette;
        if (pal == null || pal.length == 0) return 0x8080DD;
        if (pal.length == 1) return pal[0] & 0xFFFFFF;
        long t = System.currentTimeMillis();
        float idx = (float)((t % 6000L) / 6000.0 * pal.length);
        int i1 = (int) idx % pal.length, i2 = (i1 + 1) % pal.length;
        return interpolateColor(pal[i1], pal[i2], idx - (int) idx) & 0xFFFFFF;
    }

    private static int darkenColor(int color, float factor) {
        int r = (int)(((color >> 16) & 0xFF) * (1f - factor));
        int g = (int)(((color >>  8) & 0xFF) * (1f - factor));
        int b = (int)(( color        & 0xFF) * (1f - factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int interpolateColor(int c1, int c2, float f) {
        int r1=(c1>>16)&0xFF, g1=(c1>>8)&0xFF, b1=c1&0xFF;
        int r2=(c2>>16)&0xFF, g2=(c2>>8)&0xFF, b2=c2&0xFF;
        return ((int)(r1+f*(r2-r1))<<16)|((int)(g1+f*(g2-g1))<<8)|(int)(b1+f*(b2-b1));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
}