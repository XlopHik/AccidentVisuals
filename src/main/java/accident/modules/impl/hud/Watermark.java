package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import accident.client.draggables.AbstractHudElement;
import accident.manager.ServerManager;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.tps.TPSCalculate;

import accident.modules.module.setting.implement.BooleanSetting;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Watermark extends AbstractHudElement {

    public static final BooleanSetting showTps = new BooleanSetting("accident.module.hud.setting.showtps.name", "accident.module.hud.setting.showtps.desc")
            .setValue(true);

    private String lastFps = "";
    private String oldFps = "";
    private long fpsAnimationStart = 0;

    private String lastTime = "";
    private String oldTime = "";
    private long timeAnimationStart = 0;

    private String lastTps = "";
    private String oldTps = "";
    private long tpsAnimationStart = 0;

    private static final long ANIMATION_DURATION = 200;
    private static final float ANIMATION_OFFSET = 8.0f;
    private static final float MARGIN = 5f;
    private static final float HEADER_HEIGHT = 14.5f;

    // Built once. ofPattern re-parses the pattern string and rebuilds the
    // formatter every call, and this runs on every rendered frame.
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private long lastTimeNs = 0L;

    public Watermark() {
        super("Watermark", 10, 10, 200, 18, false);
        settings(showTps);
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

    public void drawIconByIndex(int index, float x, float y, float size, int color) {
        String icon = String.valueOf((char) index);
        Fonts.ICONSFORHK.draw(icon, x, y, size, color);
    }


    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;

        float dt = computeDtSeconds();
        float alphaFactor = alpha / 255.0f;

        float x = 20;
        float y = 5;

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

        String username = mc.getSession().getUsername();
        String fpsNumber = String.valueOf(mc.getCurrentFps());
        String fpsText = "fps";
        String time = LocalTime.now().format(CLOCK_FORMAT);

        boolean showTps = Watermark.showTps.isValue();

        float tpsValue = ServerManager.getInstance().getTps();
        if (TPSCalculate.getInstance() != null) {
            tpsValue = ServerManager.getInstance().getTps();
        }
        String tpsNumber = String.format("%.1f", tpsValue);
        String tpsText = "tps";

        long currentTime = System.currentTimeMillis();

        if (!fpsNumber.equals(lastFps)) {
            oldFps = lastFps;
            lastFps = fpsNumber;
            fpsAnimationStart = currentTime;
        }

        if (!time.equals(lastTime)) {
            oldTime = lastTime;
            lastTime = time;
            timeAnimationStart = currentTime;
        }

        if (!tpsNumber.equals(lastTps)) {
            oldTps = lastTps;
            lastTps = tpsNumber;
            tpsAnimationStart = currentTime;
        }

        float fpsAnimation = Math.min(1.0f, (currentTime - fpsAnimationStart) / (float) ANIMATION_DURATION);
        float timeAnimation = Math.min(1.0f, (currentTime - timeAnimationStart) / (float) ANIMATION_DURATION);
        float tpsAnimation = Math.min(1.0f, (currentTime - tpsAnimationStart) / (float) ANIMATION_DURATION);

        float usernameWidth = Fonts.TEST.getWidth(username, 6);
        float fpsNumberWidth = Fonts.TEST.getWidth(fpsNumber, 6);
        float fpsTextWidth = Fonts.TEST.getWidth(fpsText, 6);
        float timeWidth = Fonts.TEST.getWidth(time, 6);
        float tpsNumberWidth = Fonts.TEST.getWidth(tpsNumber, 6);
        float tpsTextWidth = Fonts.TEST.getWidth(tpsText, 6);

        float totalWidth = 10 + 12 + usernameWidth + 10 + 8 + 10 + 12 + fpsNumberWidth + 2 + fpsTextWidth + 10 + 8 + 10 + 12 + timeWidth - 32;
        float tpsBoxWidth = 10 + 12 + 1 + tpsNumberWidth + 2 + tpsTextWidth + 2;

        float leftBoxWidth = 48f;

        if (showTps) {
            setWidth((int) (totalWidth + tpsBoxWidth + 60));
        } else {
            setWidth((int) (totalWidth + 60));
        }
        setHeight((int) HEADER_HEIGHT);

        int glassAlpha = (int)(alphaFactor * 80);
        int bgColor = (glassAlpha << 24) | 0x0F121F;

        Render2D.blur(x - 12, y + 1, leftBoxWidth, HEADER_HEIGHT, 10f, 4f, bgColor);

        float iconX = x - 6.5f;
        float iconY = y + 2.9f;
        Fonts.LOGO.draw("A", iconX - 3.5f, iconY, 9.5f, finalCustomColor);
        Fonts.TEST.draw("| Release", iconX + 9f, iconY + 2.1f, 6, finalCustomColor);

        float mainBoxX = x + leftBoxWidth - 10;
        Render2D.blur(mainBoxX, y + 1, totalWidth, HEADER_HEIGHT, 10f, 4f, bgColor);

        float tpsBoxX = mainBoxX + totalWidth + 2;
        if (showTps) {
            Render2D.blur(tpsBoxX, y + 1, tpsBoxWidth, HEADER_HEIGHT, 10f, 4f, bgColor);
        }

        float textY = y + 4;
        float offsetX = mainBoxX + 5;

        Fonts.ICONKIHUD.draw("Z", offsetX, textY + 0.5f, 9, finalCustomColor);
        offsetX += 12;
        Fonts.TEST.draw(username, offsetX, textY + 1.5f, 5.5f, (255 << 24) | 0xFFFFFF);
        offsetX += usernameWidth + 5;

        Fonts.TEST.draw("|", offsetX, textY + 1, 7, (155 << 24) | 0x9B9B9B);
        offsetX += 12;

        Fonts.ICONKIHUD.draw("[", offsetX - 4.0f, textY + 1, 8, finalCustomColor);
        offsetX += 7;
        drawAnimatedTextPerChar(fpsNumber, oldFps, offsetX, textY + 1.5f, 5.5f, fpsAnimation);
        offsetX += fpsNumberWidth + 2;
        Fonts.TEST.draw(fpsText, offsetX, textY + 1.5f, 5.5f, (155 << 24) | 0x9B9B9B);
        offsetX += fpsTextWidth + 5;

        Fonts.TEST.draw("|", offsetX, textY + 1, 7, (155 << 24) | 0x9B9B9B);
        offsetX += 12;

        Fonts.ICONKIHUD.draw("Y", offsetX - 4f, textY + 1, 8, finalCustomColor);
        offsetX += 12;
        drawAnimatedTextPerChar(time, oldTime, offsetX - 4, textY + 1.7f, 5.5f, timeAnimation);

        if (showTps) {
            Fonts.FORHUD.draw("A", tpsBoxX + 5, textY + 1f, 8f, finalCustomColor);
            float tpsOffsetX = tpsBoxX + 19;
            drawAnimatedTextPerChar(tpsNumber, oldTps, tpsOffsetX, textY + 1.3f, 5.5f, tpsAnimation);
            tpsOffsetX += tpsNumberWidth + 2;
            Fonts.TEST.draw(tpsText, tpsOffsetX, textY + 1, 5.5f, (155 << 24) | 0x9B9B9B);
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

            if (newChar == ' ' && oldChar == ' ') {
                continue;
            }

            float charWidth = Fonts.TEST.getWidth(String.valueOf(newChar != ' ' ? newChar : oldChar), size);

            boolean isNewDigit = Character.isDigit(newChar) || newChar == '.';
            boolean isOldDigit = Character.isDigit(oldChar) || oldChar == '.';
            boolean hasChanged = newChar != oldChar;

            if (!hasChanged || (!isNewDigit && !isOldDigit)) {
                if (newChar != ' ') {
                    Fonts.TEST.draw(String.valueOf(newChar), offsetX, y, size, (255 << 24) | 0xFFFFFF);
                }
            } else {
                float easedProgress = easeOutCubic(progress);

                if (oldChar != ' ' && isOldDigit) {
                    float oldAlpha = 1.0f - easedProgress;
                    float oldOffsetY = easedProgress * ANIMATION_OFFSET;
                    int oldAlphaClamped = clampAlpha(oldAlpha);
                    if (oldAlphaClamped > 0) {
                        int oldColor = (oldAlphaClamped << 24) | 0xFFFFFF;
                        Fonts.TEST.draw(String.valueOf(oldChar), offsetX, y + oldOffsetY, size, oldColor);
                    }
                }

                if (newChar != ' ' && isNewDigit) {
                    float newAlpha = easedProgress;
                    float newOffsetY = (1.0f - easedProgress) * -ANIMATION_OFFSET;
                    int newAlphaClamped = clampAlpha(newAlpha);
                    if (newAlphaClamped > 0) {
                        int newColor = (newAlphaClamped << 24) | 0xFFFFFF;
                        Fonts.TEST.draw(String.valueOf(newChar), offsetX, y + newOffsetY, size, newColor);
                    }
                }
            }

            if (newChar != ' ') {
                offsetX += charWidth;
            }
        }
    }

    private String padLeft(String text, int length) {
        if (text.length() >= length) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - text.length(); i++) {
            sb.append(' ');
        }
        sb.append(text);
        return sb.toString();
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 3);
    }
}