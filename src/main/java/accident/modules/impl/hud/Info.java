package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.*;
import accident.client.draggables.AbstractHudElement;
import accident.manager.ServerManager;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import accident.modules.module.setting.implement.BooleanSetting;

import java.awt.*;
import java.time.LocalTime;

public class Info extends AbstractHudElement {

    public static final BooleanSetting showBps = new BooleanSetting("accident.module.hud.setting.showbps.name", "accident.module.hud.setting.showbps.desc")
            .setValue(false);

    private double lastX = 0;
    private double lastZ = 0;
    private double currentBps = 0;
    private double displayBps = 0;
    private double targetBps = 0;
    private long lastUpdateTime = 0;

    private String lastPing = "";
    private String oldPing = "";
    private long pingAnimationStart = 0;

    private static final double BPS_SMOOTHING = 0.05;
    private static final double DISPLAY_SMOOTHING = 0.03;
    private static final long ANIMATION_DURATION = 200;
    private static final float ANIMATION_OFFSET = 8.0f;
    private static final float HEADER_HEIGHT = 15f;
    private static final float MARGIN = 5f;

    private long lastTimeNs = 0L;

    public Info() {
        super("Info", 10, 0, 200, 18, false);
        settings(showBps);
        startAnimation();
    }

    @Override
    public void tick() {
    }

    private float computeDtSeconds() {
        long now = System.nanoTime();
        if (lastTimeNs == 0L) {
            lastTimeNs = now;
            return 1f / 60f;
        }
        long d = now - lastTimeNs;
        lastTimeNs = now;
        double dt = Math.min(Math.max(d / 1_000_000_000.0, 0.0), 0.1);
        return (float) dt;
    }

    private int clampAlpha(float alpha) {
        return Math.max(0, Math.min(255, (int) (alpha * 255)));
    }

    private int interpolateColor(int color1, int color2, float factor) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + factor * (r2 - r1));
        int g = (int) (g1 + factor * (g2 - g1));
        int b = (int) (b1 + factor * (b2 - b1));

        return (r << 16) | (g << 8) | b;
    }

    private int lastSavedPing = 0;

    private int getPing() {
        if (mc.player == null || mc.world == null) return lastSavedPing;

        try {
            net.minecraft.scoreboard.Scoreboard scoreboard = mc.world.getScoreboard();

            for (net.minecraft.scoreboard.Team team : scoreboard.getTeams()) {
                String fullText = team.getPrefix().getString() + team.getSuffix().getString();
                String stripped = net.minecraft.util.Formatting.strip(fullText).toLowerCase();

                if (stripped.contains("пинг") || stripped.contains("ping")) {
                    String digits = stripped.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        lastSavedPing = Integer.parseInt(digits);
                        return lastSavedPing;
                    }
                }
            }
        } catch (Exception ignored) {}

        if (mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null && entry.getLatency() > 0) {
                lastSavedPing = entry.getLatency();
                return lastSavedPing;
            }
        }

        return lastSavedPing;
    }

    private double roundToStep(double value, double step) {
        return Math.round(value / step) * step;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        if (mc.player == null) return;

        float dt = computeDtSeconds();
        float alphaFactor = alpha / 255.0f;

        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;

        int[] palette = theme.palette;

        if (palette == null || palette.length == 0) {
            palette = new int[]{0xFFFFFF};
        }

        int mixedColor;
        if (palette.length == 1) {
            mixedColor = palette[0];
        } else {
            long timeMs = System.currentTimeMillis();
            double speed = 6000.0;
            float indexProgress = (float) ((timeMs % (long)speed) / speed * palette.length);
            int index1 = (int) indexProgress % palette.length;
            int index2 = (index1 + 1) % palette.length;
            float fraction = indexProgress - (int) indexProgress;

            mixedColor = interpolateColor(palette[index1], palette[index2], fraction);
        }

        int finalCustomColor = (clampAlpha(alphaFactor) << 24) | (mixedColor & 0xFFFFFF);

        boolean showBps = Info.showBps.isValue();
        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastUpdateTime) / 1000.0;

        if (lastUpdateTime > 0 && deltaTime > 0) {
            double dx = mc.player.getX() - lastX;
            double dz = mc.player.getZ() - lastZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double instantBps = distance / deltaTime;

            currentBps = currentBps + (instantBps - currentBps) * BPS_SMOOTHING;
            targetBps = roundToStep(currentBps, 0.50);
        }

        displayBps = displayBps + (targetBps - displayBps) * DISPLAY_SMOOTHING;

        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        lastUpdateTime = currentTime;

        float x = -5;
        float y = 22;

        int playerX = (int) mc.player.getX();
        int playerY = (int) mc.player.getY();
        int playerZ = (int) mc.player.getZ();

        String xText = "x";
        String yText = "y";
        String zText = "z";

        String xValue = String.valueOf(playerX);
        String yValue = String.valueOf(playerY);
        String zValue = String.valueOf(playerZ);

        double roundedDisplayBps = roundToStep(displayBps, 0.50);
        String bpsValue = String.format("%.1f", roundedDisplayBps);
        String bpsText = "b/s";

        String pingNumber = String.valueOf(getPing());
        String pingText = "Ping";

        if (!pingNumber.equals(lastPing)) {
            oldPing = lastPing;
            lastPing = pingNumber;
            pingAnimationStart = currentTime;
        }
        float pingAnimation = Math.min(1.0f, (currentTime - pingAnimationStart) / (float) ANIMATION_DURATION);

        float xTextWidth = Fonts.TEST.getWidth(xText, 5.5f);
        float yTextWidth = Fonts.TEST.getWidth(yText, 5.5f);
        float zTextWidth = Fonts.TEST.getWidth(zText, 5.5f);
        float xValueWidth = Fonts.TEST.getWidth(xValue, 5.5f);
        float yValueWidth = Fonts.TEST.getWidth(yValue, 5.5f);
        float zValueWidth = Fonts.TEST.getWidth(zValue, 5.5f);
        float bpsValueWidth = Fonts.TEST.getWidth(bpsValue, 5.5f);
        float bpsTextWidth = Fonts.TEST.getWidth(bpsText, 5.5f);
        float pingNumberWidth = Fonts.TEST.getWidth(pingNumber, 5.5f);
        float pingTextWidth = Fonts.TEST.getWidth(pingText, 5.5f);

        float coordsWidth = 8 + 12 + xTextWidth + 2 + xValueWidth + 6 + yTextWidth + 2 + yValueWidth + 6 + zTextWidth + 2 + zValueWidth - 4;
        float pingWidth = 8 + 12 + pingNumberWidth + 2 + pingTextWidth - 2;
        float bpsWidth = 8 + 12 + bpsValueWidth + 2 + bpsTextWidth - 2;

        setX((int) x);
        setY((int) y);
        float totalWidth = coordsWidth + 6 + pingWidth;
        if (showBps) totalWidth += 6 + bpsWidth;
        setWidth((int) totalWidth);
        setHeight((int) HEADER_HEIGHT);

        int bgColor = ((int)(alphaFactor * 80) << 24) | 0x0F121F;
        float textY = y + 4;
        float startX = x + 12;

        Render2D.blur(startX, y + 1, coordsWidth, HEADER_HEIGHT, 10f, 4f, bgColor);
        float offsetX = startX + 3;
        Fonts.FORHUD.draw("B", offsetX - 16.5f, textY + 0.5f, 8, finalCustomColor);
        offsetX += 10;
        Fonts.TEST.draw(xText, offsetX, textY + 1.5f, 5.5f, (155 << 24) | 0x9B9B9B);
        offsetX += xTextWidth + 2;
        Fonts.TEST.draw(xValue, offsetX, textY + 1.5f, 5.5f, -1);
        offsetX += xValueWidth + 4;
        Fonts.TEST.draw(yText, offsetX, textY + 1.5f, 5.5f, (155 << 24) | 0x9B9B9B);
        offsetX += yTextWidth + 2;
        Fonts.TEST.draw(yValue, offsetX, textY + 1.5f, 5.5f, -1);
        offsetX += yValueWidth + 4;
        Fonts.TEST.draw(zText, offsetX, textY + 1.5f, 5.5f, (155 << 24) | 0x9B9B9B);
        offsetX += zTextWidth + 2;
        Fonts.TEST.draw(zValue, offsetX, textY + 1.5f, 5.5f, -1);

        float pingBoxX = startX + coordsWidth + 2;
        Render2D.blur(pingBoxX, y + 1, pingWidth, HEADER_HEIGHT, 10f, 4f, bgColor);
        float pingOffsetX = pingBoxX + 3;
        Fonts.ICONKIHUD.draw("T", pingOffsetX, textY + 1f, 7.5f, finalCustomColor);
        pingOffsetX += 10;
        drawAnimatedTextPerChar(pingNumber, oldPing, pingOffsetX, textY + 1.5f, 5.5f, pingAnimation);
        pingOffsetX += pingNumberWidth + 2;
        Fonts.TEST.draw(pingText, pingOffsetX, textY + 1.5f, 5.5f, (155 << 24) | 0x9B9B9B);

        if (showBps) {
            float bpsBoxX = pingBoxX + pingWidth + 2;
            Render2D.blur(bpsBoxX, y + 1, bpsWidth, HEADER_HEIGHT, 10f, 4f, bgColor);
            float bpsOffsetX = bpsBoxX + 3;
            Fonts.FORHUD.draw("C", bpsOffsetX- 9, textY + 0.5f, 8, finalCustomColor);
            bpsOffsetX += 10;
            Fonts.TEST.draw(bpsValue, bpsOffsetX, textY + 1.5f, 5.5f, -1);
            bpsOffsetX += bpsValueWidth + 2;
            Fonts.TEST.draw(bpsText, bpsOffsetX, textY + 1.5f, 5.5f, (155 << 24) | 0x9B9B9B);
        }
    }

    private void drawAnimatedTextPerChar(String newText, String oldText, float x, float y, float size, float progress) {
        if (oldText.isEmpty() || progress >= 1.0f) {
            Fonts.TEST.draw(newText, x, y, size, (255 << 24) | 0xFFFFFF);
            return;
        }

        float offsetX = x;
        int maxLen = Math.max(newText.length(), oldText.length());
        String paddedNew = padLeft(newText, maxLen);
        String paddedOld = padLeft(oldText, maxLen);

        for (int i = 0; i < paddedNew.length(); i++) {
            char newChar = paddedNew.charAt(i);
            char oldChar = paddedOld.charAt(i);
            if (newChar == ' ' && oldChar == ' ') continue;
            float charWidth = Fonts.TEST.getWidth(String.valueOf(newChar != ' ' ? newChar : oldChar), size);
            boolean isDigit = Character.isDigit(newChar) || newChar == '.' || Character.isDigit(oldChar) || oldChar == '.';

            if (newChar == oldChar || !isDigit) {
                if (newChar != ' ') Fonts.BOLD.draw(String.valueOf(newChar), offsetX, y, size, -1);
            } else {
                float eased = easeOutCubic(progress);
                if (oldChar != ' ') {
                    int oAlpha = clampAlpha(1.0f - eased);
                    if (oAlpha > 0) Fonts.BOLD.draw(String.valueOf(oldChar), offsetX, y + (eased * ANIMATION_OFFSET), size, (oAlpha << 24) | 0xFFFFFF);
                }
                if (newChar != ' ') {
                    int nAlpha = clampAlpha(eased);
                    if (nAlpha > 0) Fonts.BOLD.draw(String.valueOf(newChar), offsetX, y + ((1.0f - eased) * -ANIMATION_OFFSET), size, (nAlpha << 24) | 0xFFFFFF);
                }
            }
            if (newChar != ' ') offsetX += charWidth;
        }
    }

    private String padLeft(String text, int length) {
        if (text.length() >= length) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - text.length(); i++) sb.append(' ');
        sb.append(text);
        return sb.toString();
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 3);
    }
}