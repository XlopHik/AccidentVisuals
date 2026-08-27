package accident.screens.clickgui.dropdown;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import accident.Initialization;
import accident.modules.module.ModuleStructure;
import accident.util.lang.LanguageManager;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchOverlay {

    private static final float PANEL_W    = 240f;
    private static final float ITEM_H     = 22f;
    private static final float BAR_H      = 24f;
    private static final float RADIUS     = 8f;
    private static final int   MAX_SHOWN  = 12;

    private boolean active   = false;
    private String  query    = "";

    private float   alpha    = 0f;
    private long    lastTime = System.currentTimeMillis();

    private float cursorBlink = 0f;

    private double scrollOffset       = 0;
    private double targetScrollOffset = 0;

    private List<ModuleStructure> results = new ArrayList<>();

    public boolean isActive() { return active; }
    public String  getQuery() { return query;  }

    public void setActive(boolean v) {
        active = v;
        if (!v) {
            query = "";
            scrollOffset = targetScrollOffset = 0;
        } else {
            rebuildResults();
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float alphaMultiplier) {
        updateAnimation();
        if (alpha < 0.01f) return;

        float a = alpha * alphaMultiplier;

        int screenW = Render2D.getFixedScaledWidth();
        int screenH = Render2D.getFixedScaledHeight();

        float panelX = (screenW - PANEL_W) / 2f;
        float panelY = screenH * 0.18f;

        Render2D.rect(0, 0, screenW, screenH,
                new Color(0, 0, 0, (int)(80 * a)).getRGB(), 0);

        renderSearchBar(panelX, panelY, a);

        renderResults(context, panelX, panelY + BAR_H + 4f, mouseX, mouseY, a);
    }

    private void renderSearchBar(float x, float y, float a) {
        Render2D.rect(x, y, PANEL_W, BAR_H,
                new Color(18, 15, 25, Math.min(255, (int)(240 * a))).getRGB(), RADIUS);
        Render2D.outline(x, y, PANEL_W, BAR_H, 0.5f,
                new Color(120, 90, 180, (int)(200 * a)).getRGB(), RADIUS);

        Fonts.BOLD.draw("⌕", x + 6f, y + 6f, 6f,
                new Color(140, 110, 200, (int)(180 * a)).getRGB());

        String display = query.isEmpty() ? LanguageManager.get("accident.gui.search.placeholder") : query;
        int    textAlpha = query.isEmpty() ? (int)(100 * a) : Math.min(255, (int)(220 * a));
        Fonts.BOLD.draw(display, x + 16f, y + 6f, 6f,
                new Color(200, 190, 220, textAlpha).getRGB());

        if (!query.isEmpty()) {
            float cursorX = x + 16f + Fonts.BOLD.getWidth(query, 6f);
            float ca = (float)(Math.sin(cursorBlink * Math.PI * 2) * 0.5 + 0.5);
            if (ca > 0.3f) {
                Render2D.rect(cursorX + 1f, y + 4f, 0.5f, BAR_H - 8f,
                        new Color(180, 150, 230, (int)(200 * ca * a)).getRGB(), 0);
            }
        }
    }

    private void renderResults(DrawContext context, float x, float y, int mouseX, int mouseY, float a) {
        if (results.isEmpty()) {
            String msg = query.isEmpty() ? LanguageManager.get("accident.gui.search.starttyping") : LanguageManager.get("accident.gui.search.notfound");
            float tw = Fonts.BOLD.getWidth(msg, 5f);
            Fonts.BOLD.draw(msg, x + (PANEL_W - tw) / 2f, y + 10f, 5f,
                    new Color(120, 110, 140, (int)(150 * a)).getRGB());
            return;
        }

        float listH = Math.min(results.size(), MAX_SHOWN) * ITEM_H;

        Render2D.rect(x, y, PANEL_W, listH,
                new Color(12, 10, 18, Math.min(255, (int)(230 * a))).getRGB(), RADIUS);
        Render2D.outline(x, y, PANEL_W, listH, 0.5f,
                new Color(70, 55, 100, (int)(180 * a)).getRGB(), RADIUS);

        scrollOffset += (targetScrollOffset - scrollOffset) * 0.15;

        accident.util.render.shader.Scissor.enable(x + 2, y + 2, PANEL_W - 4, listH - 4, 2);

        float iy = y + (float) scrollOffset;
        for (ModuleStructure m : results) {
            boolean hovered = mouseX >= x + 2 && mouseX <= x + PANEL_W - 2
                    && mouseY >= iy && mouseY <= iy + ITEM_H;

            int rowBg = hovered ? (int)(40 * a) : (int)(20 * a);
            Render2D.rect(x + 2, iy, PANEL_W - 4, ITEM_H,
                    new Color(50, 40, 70, rowBg).getRGB(), 4f);

            if (m.isState()) {
                Render2D.rect(x + 5, iy + ITEM_H / 2f - 2f, 4f, 4f,
                        new Color(180, 140, 255, Math.min(255, (int)(220 * a))).getRGB(), 2f);
            }

            int brightness = m.isState() ? 230 : 160;
            Fonts.BOLD.draw(m.getName(), x + 13f, iy + (ITEM_H - 6f) / 2f, 6f,
                    new Color(brightness, brightness, Math.min(255, brightness + 10), Math.min(255, Math.min(255, (int)(220 * a)))).getRGB());

            String catName = m.getCategory().getReadableName();
            float  catW    = Fonts.BOLD.getWidth(catName, 4f);
            Fonts.BOLD.draw(catName, x + PANEL_W - catW - 6f, iy + (ITEM_H - 4f) / 2f, 4f,
                    new Color(110, 90, 150, (int)(160 * a)).getRGB());

            iy += ITEM_H;
        }

        accident.util.render.shader.Scissor.disable();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active) return false;
        int screenW = Render2D.getFixedScaledWidth();
        int screenH = Render2D.getFixedScaledHeight();
        float panelX  = (screenW - PANEL_W) / 2f;
        float panelY  = screenH * 0.18f;
        float listY   = panelY + BAR_H + 4f;
        float listH   = Math.min(results.size(), MAX_SHOWN) * ITEM_H;

        if (mouseX >= panelX + 2 && mouseX <= panelX + PANEL_W - 2
                && mouseY >= listY + 2 && mouseY <= listY + listH - 2) {
            int idx = (int)((mouseY - listY - scrollOffset) / ITEM_H);
            if (idx >= 0 && idx < results.size()) {
                results.get(idx).switchState();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double amount) {
        if (results.isEmpty()) return false;
        float maxScroll = -(results.size() - MAX_SHOWN) * ITEM_H;
        targetScrollOffset += amount * 15;
        targetScrollOffset  = Math.max(maxScroll, Math.min(0, targetScrollOffset));
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            setActive(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            rebuildResults();
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr) {
        if (Character.isLetterOrDigit(chr) || chr == ' ') {
            query += chr;
            rebuildResults();
            return true;
        }
        return false;
    }

    private void rebuildResults() {
        results.clear();
        scrollOffset = targetScrollOffset = 0;
        if (query.isEmpty()) {
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        Initialization.getInstance().getManager().getModuleRepository()
                .modules().stream()
                .filter(m -> m.getName().toLowerCase(Locale.ROOT).contains(q))
                .sorted((a, b) -> {
                    boolean aStarts = a.getName().toLowerCase(Locale.ROOT).startsWith(q);
                    boolean bStarts = b.getName().toLowerCase(Locale.ROOT).startsWith(q);
                    if (aStarts != bStarts) return aStarts ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .forEach(results::add);
    }

    private void updateAnimation() {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastTime) / 1000f, 0.1f);
        lastTime = now;

        float target = active ? 1f : 0f;
        alpha += (target - alpha) * 16f * dt;
        alpha  = Math.max(0f, Math.min(1f, alpha));

        cursorBlink += dt * 1.6f;
        if (cursorBlink > 1f) cursorBlink -= 1f;
    }
}