package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import accident.client.draggables.AbstractHudElement;
import accident.manager.ServerManager;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

// показывает известный сервер, к которому подключен клиент
public class ServerLabel extends AbstractHudElement {

    private static final float FONT_SIZE = 7f;
    private static final float PADDING_X = 9f;
    private static final float HEIGHT = 14.5f;
    private static final float RADIUS = 10f;
    private static final long FADE_MS = 450L;

    private boolean wasOnServer = false;
    private long joinedAt = 0L;

    public ServerLabel() {
        super("ServerLabel", 10, 30, 60, (int) HEIGHT, true);
        startAnimation();
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean visible() {
        // Still shown while arranging the HUD, otherwise it could never be placed.
        return !label().isEmpty() || isChat(mc.currentScreen);
    }

    private String label() {
        return ServerManager.isHolyWorld() ? "ХОЛИВОРЛД" : "";
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;

        String text = label();
        boolean onServer = !text.isEmpty();

        if (onServer != wasOnServer) {
            wasOnServer = onServer;
            joinedAt = System.currentTimeMillis();
        }

        // In the HUD editor there is a placeholder to grab hold of.
        if (!onServer) text = "ХОЛИВОРЛД";

        float appear = onServer
                ? Math.min(1f, (System.currentTimeMillis() - joinedAt) / (float) FADE_MS)
                : 1f;
        appear = 1f - (float) Math.pow(1f - appear, 3);

        float alphaFactor = (alpha / 255.0f) * appear;
        if (alphaFactor <= 0.01f) return;

        float textWidth = Fonts.TEST.getWidth(text, FONT_SIZE);
        float boxWidth = textWidth + PADDING_X * 2f;

        setWidth((int) boxWidth);
        setHeight((int) HEIGHT);

        float x = getRenderX();
        // Slides down into place as it fades in.
        float y = getRenderY() - (1f - appear) * 6f;

        int backgroundAlpha = (int) (alphaFactor * 80);
        Render2D.blur(x, y, boxWidth, HEIGHT, RADIUS, 4f, (backgroundAlpha << 24) | 0x0F121F);

        int textAlpha = Math.max(0, Math.min(255, (int) (alphaFactor * 255)));
        Fonts.TEST.draw(text, x + PADDING_X, y + (HEIGHT - FONT_SIZE) / 2f + 1f,
                FONT_SIZE, (textAlpha << 24) | (accentColor() & 0xFFFFFF));
    }

    /** The theme colour, cycled the same way the rest of the HUD cycles it. */
    private int accentColor() {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;

        int[] palette = theme.palette;
        if (palette == null || palette.length == 0) return 0xFFFFFF;
        if (palette.length == 1) return palette[0];

        double speed = 6000.0;
        float progress = (float) ((System.currentTimeMillis() % (long) speed) / speed * palette.length);
        int first = (int) progress % palette.length;
        int second = (first + 1) % palette.length;

        return interpolate(palette[first], palette[second], progress - (int) progress);
    }

    private int interpolate(int from, int to, float factor) {
        int r = (int) (((from >> 16) & 0xFF) + factor * (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)));
        int g = (int) (((from >> 8) & 0xFF) + factor * (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)));
        int b = (int) ((from & 0xFF) + factor * ((to & 0xFF) - (from & 0xFF)));
        return (r << 16) | (g << 8) | b;
    }
}
