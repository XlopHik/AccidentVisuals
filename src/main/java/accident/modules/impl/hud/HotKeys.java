package accident.modules.impl.hud;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import accident.Initialization;
import accident.client.draggables.AbstractHudElement;
import accident.modules.module.ModuleStructure;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.animations.Direction;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.string.KeyHelper;

import java.util.*;

public class HotKeys extends AbstractHudElement {
    private List<ModuleStructure> bindings = Lists.newArrayList();
    private Map<String, Float> moduleAnimations = new LinkedHashMap<>();
    private Map<String, ModuleStructure> cachedModules = new LinkedHashMap<>();
    private Set<String> activeModuleIds = new HashSet<>();

    private long lastKeyChange = 0;
    private String currentRandomKey = "F";

    private float exampleTransition = 0f;

    private static final List<String> RANDOM_KEYS = List.of("F", "Shift", "V", "R", "X", "G", "B", "C");

    private static final float SIZE_SMOOTH_SPEED = 12f;
    private static final float ROW_SLIDE_SPEED = 8f;
    private static final float EXAMPLE_TRANSITION_SPEED = 8f;
    private static final float ROW_SIZE = 6.7F;
    private static final float MARGIN = 5f;
    private static final float HEADER_HEIGHT = 23f;
    private static final float ROW_HEIGHT = 16f;
    private static final float ROW_SPACING = 2f;

    private float smoothWidth = 80;
    private long lastTimeNs = 0L;

    private final float[] rowSlideOffset = new float[64];

    public HotKeys() {
        super("HotKeys", 10, 150, 100, 46, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (Initialization.getInstance() == null ||
                Initialization.getInstance().getManager() == null) return;

        List<ModuleStructure> allModules = Initialization.getInstance().getManager().getModuleProvider().getModuleStructures();

        bindings.clear();
        for (ModuleStructure module : allModules) {
            if (shouldDisplay(module)) {
                bindings.add(module);
            }
        }

        Set<String> newActiveIds = new HashSet<>();
        for (ModuleStructure module : bindings) {
            String id = module.getName() + "_" + module.getKey();
            newActiveIds.add(id);

            if (!cachedModules.containsKey(id)) {
                cachedModules.put(id, module);
                moduleAnimations.put(id, 0f);
            }
        }

        for (String id : cachedModules.keySet()) {
            float targetAnim = newActiveIds.contains(id) ? 1f : 0f;
            if (!moduleAnimations.containsKey(id)) {
                moduleAnimations.put(id, targetAnim);
            }
        }

        activeModuleIds = newActiveIds;

        boolean hasActiveKeys = !activeModuleIds.isEmpty() || !moduleAnimations.isEmpty();
        boolean inChat = mc.currentScreen instanceof ChatScreen;

        if (hasActiveKeys || inChat) {
            startAnimation();
        } else {
            stopAnimation();
        }

        if (bindings.isEmpty() && inChat) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastKeyChange >= 1000) {
                currentRandomKey = RANDOM_KEYS.get(new Random().nextInt(RANDOM_KEYS.size()));
                lastKeyChange = currentTime;
            }
        }
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

    private int getMixedColor(float alphaFactor) {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;

        int[] palette = theme.palette;

        if (palette == null || palette.length == 0) {
            int alpha = Math.max(0, Math.min(255, (int) (alphaFactor * 255)));
            return (alpha << 24) | 0xFFFFFF;
        }
        if (palette.length == 1) {
            int alpha = Math.max(0, Math.min(255, (int) (alphaFactor * 255)));
            return (alpha << 24) | (palette[0] & 0xFFFFFF);
        }

        long timeMs = System.currentTimeMillis();
        double speed = 6000.0;
        float indexProgress = (float) ((timeMs % (long)speed) / speed * palette.length);
        int index1 = (int) indexProgress % palette.length;
        int index2 = (index1 + 1) % palette.length;
        float fraction = indexProgress - (int) indexProgress;

        int mixed = interpolateColor(palette[index1], palette[index2], fraction);
        int alpha = Math.max(0, Math.min(255, (int) (alphaFactor * 255)));
        return (alpha << 24) | (mixed & 0xFFFFFF);
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;

        float dt = computeDtSeconds();
        float alphaFactor = alpha / 255.0f;

        int finalCustomColor = getMixedColor(alphaFactor);

        List<String> toRemove = new ArrayList<>();
        boolean hasAnyVisibleAnimation = false;

        for (Map.Entry<String, Float> entry : moduleAnimations.entrySet()) {
            String id = entry.getKey();
            float currentAnim = entry.getValue();
            float targetAnim = activeModuleIds.contains(id) ? 1f : 0f;
            float newAnim = smoothTowards(currentAnim, targetAnim, dt, ROW_SLIDE_SPEED);

            if (Math.abs(newAnim - targetAnim) < 0.01f) {
                newAnim = targetAnim;
            }

            if (newAnim <= 0.01f && targetAnim == 0f) {
                toRemove.add(id);
            } else {
                moduleAnimations.put(id, newAnim);
                if (newAnim > 0.01f) {
                    hasAnyVisibleAnimation = true;
                }
            }
        }

        for (String id : toRemove) {
            moduleAnimations.remove(id);
            cachedModules.remove(id);
        }

        boolean showExample = !hasAnyVisibleAnimation && mc.currentScreen instanceof ChatScreen;
        float targetTransition = showExample ? 1f : 0f;
        exampleTransition = smoothTowards(exampleTransition, targetTransition, dt, EXAMPLE_TRANSITION_SPEED);
        float targetWidth = computeTargetWidth(hasAnyVisibleAnimation);
        smoothWidth = smoothTowards(smoothWidth, targetWidth, dt, SIZE_SMOOTH_SPEED);

        float x = getRenderX();
        float y = getRenderY();
        float width = getWidth();
        int glassAlpha = (int)(alphaFactor * 60);
        int bgColor = (glassAlpha << 24) | 0x0F121F;

        Render2D.blur(x, y, width, HEADER_HEIGHT, 10f, 4f, bgColor);
        Fonts.ICONKIHUD.draw("F", x + MARGIN, y + MARGIN - 0.5f, 9, (255 << 24) | finalCustomColor);
        Fonts.TEST.draw("Hotkeys", x + MARGIN + 15, y + MARGIN + 0.5f, 6, (255 << 24) | 0xFFFFFF);
        Render2D.rect(
                x + MARGIN + 13,
                y + 4,
                0.5f,
                8,
                (int)(0.1f * scaleAnimation.getOutput() * alphaFactor * 255) << 24 | 0xFFFFFF
        );

        if (exampleTransition < 0.99f) {
            int keysCount = activeModuleIds.isEmpty() ? 1 : activeModuleIds.size();
            String countText = String.valueOf(keysCount);
            float countWidth = Fonts.TEST.getWidth(countText, 6);
            float countX = x + width - countWidth - MARGIN - 5;

            Render2D.rect(countX, y + MARGIN - 1, countWidth + 6, 10,
                    (glassAlpha << 24) | 0x232429, 3);
            Fonts.TEST.draw(countText, countX + 3, y + MARGIN + 1, 6, (255 << 24) | 0xFFFFFF);
        }

        float offsetY = 0f;
        float ys = 15f;
        int rowIndex = 0;

        for (Map.Entry<String, Float> entry : moduleAnimations.entrySet()) {
            String id = entry.getKey();
            float animation = entry.getValue();
            if (animation <= 0.001f) continue;

            ModuleStructure module = cachedModules.get(id);
            if (module == null) continue;

            float rawSlide = rowSlideOffset[rowIndex];
            float slidePC = easeOutCubic(rawSlide);

            rowSlideOffset[rowIndex] = smoothTowards(rawSlide, 1f, dt, ROW_SLIDE_SPEED);
            rowSlideOffset[rowIndex] = MathHelper.clamp(rowSlideOffset[rowIndex], 0f, 1f);

            float animPC = animation * slidePC;
            float baseY = y + ys + offsetY;
            float rowX = x - width + width * slidePC;

            float combinedAlpha = animation * (1f - exampleTransition);
            int rowAlpha = (int)(glassAlpha * combinedAlpha);
            int textAlpha = (int)(255 * alphaFactor * combinedAlpha);

            if (textAlpha > 1) {
                int rowColor = (rowAlpha << 24) | 0x141623;
                Render2D.blur(rowX, baseY, width, ROW_HEIGHT, 10f, 4f, rowColor);
                Fonts.TEST.draw(module.getName(), rowX + 6, baseY + 6, 6, (textAlpha << 24) | 0xFFFFFF);

                String keyName = formatKeyName(KeyHelper.getKeyName(module.getKey()));
                float keyWidth = Fonts.TEST.getWidth(keyName, 6);
                float keyX = rowX + width - keyWidth - MARGIN - 5;

                Render2D.rect(keyX + 1, baseY + 3, keyWidth + 4, 10,
                        (rowAlpha << 24) | 0x232429, 3);
                Fonts.TEST.draw(keyName, keyX + 3, baseY + 5, 6, (textAlpha << 24) | 0xFFFFFF);
            }

            offsetY += (ROW_HEIGHT + ROW_SPACING) * animPC * (1f - exampleTransition);
            rowIndex++;
        }

        if (exampleTransition > 0.01f) {
            float rawSlide = rowSlideOffset[rowIndex];
            float slidePC = easeOutCubic(rawSlide);

            rowSlideOffset[rowIndex] = smoothTowards(rawSlide, 1f, dt, ROW_SLIDE_SPEED);

            float baseY = y + ys + offsetY;
            float rowX = x - width + width * slidePC;

            int exampleAlpha = (int)(255 * alphaFactor * exampleTransition);
            int exampleBgAlpha = (int)(glassAlpha * exampleTransition);
            int rowColor = (exampleBgAlpha << 24) | 0x141623;
            Render2D.blur(rowX, baseY, width, ROW_HEIGHT, 10f, 4f, rowColor);
            Fonts.TEST.draw("Module", rowX + 7, baseY + 6, 6, (exampleAlpha << 24) | 0xFFFFFF);

            String keyName = formatKeyName(currentRandomKey);
            float keyWidth = Fonts.TEST.getWidth(keyName, 6);
            float keyX = rowX + width - keyWidth - MARGIN - 5;

            Render2D.rect(keyX - 2, baseY + 3, keyWidth + 4, 10,
                    (exampleBgAlpha << 24) | 0x232429, 3);
            Fonts.TEST.draw(keyName, keyX, baseY + 6, 6, (exampleAlpha << 24) | 0xFFFFFF);
        }

        float headerBottomMargin = 4f;
        float targetHeight = HEADER_HEIGHT + headerBottomMargin + offsetY + MARGIN;
        if (offsetY < 0.1f && exampleTransition > 0.01f) {
            targetHeight += ROW_HEIGHT;
        }

        setWidth((int) Math.ceil(smoothWidth));
        setHeight((int) Math.ceil(targetHeight));
    }

    private float computeTargetWidth(boolean hasVisibleModules) {
        float maxNameWidth = 0f;
        float maxKeyWidth = Fonts.TEST.getWidth("Shift", ROW_SIZE);

        for (Map.Entry<String, Float> entry : moduleAnimations.entrySet()) {
            float animation = entry.getValue();
            if (animation <= 0.01f) continue;

            ModuleStructure module = cachedModules.get(entry.getKey());
            if (module == null) continue;

            float nameWidth = Fonts.TEST.getWidth(module.getName(), 6);
            maxNameWidth = Math.max(maxNameWidth, nameWidth);
        }

        if (maxNameWidth == 0f) {
            maxNameWidth = Fonts.TEST.getWidth("Example", 6);
        }

        return MARGIN + 20 + maxNameWidth + 15 + maxKeyWidth + MARGIN + 20;
    }

    private boolean shouldDisplay(ModuleStructure module) {
        return module.isState() && module.getKey() != GLFW.GLFW_KEY_UNKNOWN;
    }

    private String formatKeyName(String key) {
        if (key == null) return "NONE";
        return key.replace("LEFT_SHIFT", "Shift")
                .replace("RIGHT_SHIFT", "Shift")
                .replace("LEFT_CONTROL", "Ctrl")
                .replace("RIGHT_CONTROL", "Ctrl")
                .replace("LEFT_ALT", "Alt");
    }

    private float smoothTowards(float current, float target, float dt, float speedPerSec) {
        if (!Float.isFinite(dt) || dt <= 0f) return target;
        float k = 1f - (float) Math.exp(-speedPerSec * dt);
        return current + (target - current) * k;
    }

    private float computeDtSeconds() {
        long now = System.nanoTime();
        if (lastTimeNs == 0L) {
            lastTimeNs = now;
            return 1f / 60f;
        }
        long d = now - lastTimeNs;
        lastTimeNs = now;
        return (float) Math.min(Math.max(d / 1_000_000_000.0, 0.0), 0.1);
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - MathHelper.clamp(t, 0, 1), 3.0);
    }

    private void drawAlternative(DrawContext context, int alpha, float dt, float alphaFactor, int accentColor) {
        boolean inChat = mc.currentScreen instanceof ChatScreen;
        List<String> toRemove = new ArrayList<>();
        boolean hasAnyVisibleAnimation = false;
        for (Map.Entry<String, Float> entry : moduleAnimations.entrySet()) {
            String id = entry.getKey();
            float cur = entry.getValue();
            float tgt = activeModuleIds.contains(id) ? 1f : 0f;
            float nv = smoothTowards(cur, tgt, dt, ROW_SLIDE_SPEED);
            if (Math.abs(nv - tgt) < 0.01f) nv = tgt;
            if (nv <= 0.01f && tgt == 0f) toRemove.add(id);
            else {
                moduleAnimations.put(id, nv);
                if (nv > 0.01f) hasAnyVisibleAnimation = true;
            }
        }
        for (String id : toRemove) { moduleAnimations.remove(id); cachedModules.remove(id); }

        boolean showExample = !hasAnyVisibleAnimation && inChat;
        float targetTransition = showExample ? 1f : 0f;
        exampleTransition = smoothTowards(exampleTransition, targetTransition, dt, EXAMPLE_TRANSITION_SPEED);

        float x = getRenderX();
        float y = getRenderY();
        float width = getWidth();

        int glassAlpha = (int)(alphaFactor * 70);
        Render2D.blur(x, y, width, HEADER_HEIGHT, HEADER_HEIGHT / 2f, 4f, (glassAlpha << 24) | 0x0A0C14);
        Fonts.ICONKIHUD.draw("F", x + MARGIN, y + MARGIN - 0.5f, 9, (255 << 24) | accentColor);
        Render2D.rect(x + MARGIN + 13, y + 4, 0.5f, 8,
                (int)(0.15f * alphaFactor * 255) << 24 | 0xFFFFFF);
        Fonts.TEST.draw("Hotkeys", x + MARGIN + 17, y + MARGIN + 0.5f, 6, (255 << 24) | 0xFFFFFF);
        float offsetY = 0f;
        float ys = HEADER_HEIGHT + 3f;
        int rowIndex = 0;

        for (Map.Entry<String, Float> entry : moduleAnimations.entrySet()) {
            String id = entry.getKey();
            float animation = entry.getValue();
            if (animation <= 0.001f) continue;

            ModuleStructure module = cachedModules.get(id);
            if (module == null) continue;

            float rawSlide = rowSlideOffset[rowIndex];
            rowSlideOffset[rowIndex] = MathHelper.clamp(
                    smoothTowards(rawSlide, 1f, dt, ROW_SLIDE_SPEED), 0f, 1f);
            float slidePC = easeOutCubic(rawSlide);

            float combinedAlpha = animation * slidePC * (1f - exampleTransition);
            int rowBgAlpha = (int)(glassAlpha * combinedAlpha);
            int textAlpha = (int)(255 * alphaFactor * combinedAlpha);

            if (textAlpha > 1) {
                float baseY = y + ys + offsetY;
                float rowX = x - width + width * slidePC;
                Render2D.blur(rowX, baseY, width, ROW_HEIGHT, ROW_HEIGHT / 2f, 4f,
                        (rowBgAlpha << 24) | 0x0A0C14);

                String keyName = formatKeyName(KeyHelper.getKeyName(module.getKey()));
                float toggleW = 26f;
                float toggleH = 12f;
                float toggleX = rowX + MARGIN;
                float toggleY = baseY + (ROW_HEIGHT - toggleH) / 2f;
                int toggleBgColor = (rowBgAlpha << 24) | 0x232429;
                Render2D.rect(toggleX, toggleY, toggleW, toggleH, toggleBgColor, 6);
                float keyTxtW = Fonts.TEST.getWidth(keyName, 5.5f);
                Fonts.TEST.draw(keyName,
                        toggleX + (toggleW - keyTxtW) / 2f,
                        toggleY + 2.5f,
                        5.5f,
                        (textAlpha << 24) | accentColor);
                Fonts.TEST.draw(module.getName(),
                        rowX + MARGIN + toggleW + 6f,
                        baseY + (ROW_HEIGHT - 6f) / 2f,
                        6f,
                        (textAlpha << 24) | 0xFFFFFF);
            }

            offsetY += (ROW_HEIGHT + ROW_SPACING) * easeOutCubic(rowSlideOffset[rowIndex]) * (1f - exampleTransition);
            rowIndex++;
        }

        if (exampleTransition > 0.01f) {
            float rawSlide = rowSlideOffset[rowIndex];
            rowSlideOffset[rowIndex] = MathHelper.clamp(
                    smoothTowards(rawSlide, 1f, dt, ROW_SLIDE_SPEED), 0f, 1f);
            float slidePC = easeOutCubic(rawSlide);

            float baseY = y + ys + offsetY;
            float rowX = x - width + width * slidePC;
            int exAlpha = (int)(255 * alphaFactor * exampleTransition);
            int exBgAlpha = (int)(glassAlpha * exampleTransition);

            Render2D.blur(rowX, baseY, width, ROW_HEIGHT, ROW_HEIGHT / 2f, 4f,
                    (exBgAlpha << 24) | 0x0A0C14);

            float toggleW = 26f;
            float toggleH = 12f;
            float toggleX = rowX + MARGIN;
            float toggleY = baseY + (ROW_HEIGHT - toggleH) / 2f;

            Render2D.rect(toggleX, toggleY, toggleW, toggleH,
                    (exBgAlpha << 24) | 0x232429, 6);

            float keyTxtW = Fonts.TEST.getWidth(currentRandomKey, 5.5f);
            Fonts.TEST.draw(currentRandomKey,
                    toggleX + (toggleW - keyTxtW) / 2f,
                    toggleY + 2.5f,
                    5.5f,
                    (exAlpha << 24) | accentColor);

            Fonts.TEST.draw("Module",
                    rowX + MARGIN + toggleW + 6f,
                    baseY + (ROW_HEIGHT - 6f) / 2f,
                    6f,
                    (exAlpha << 24) | 0xFFFFFF);
        }

        float targetW = computeTargetWidthAlt();
        smoothWidth = smoothTowards(smoothWidth, targetW, dt, SIZE_SMOOTH_SPEED);
        setWidth((int) Math.ceil(smoothWidth));

        float headerBottomMargin = 4f;
        float targetH = HEADER_HEIGHT + headerBottomMargin + offsetY + MARGIN;
        if (offsetY < 0.1f && exampleTransition > 0.01f) targetH += ROW_HEIGHT;
        setHeight((int) Math.ceil(targetH));
    }

    private float computeTargetWidthAlt() {
        float maxNameWidth = 0f;
        float toggleW = 26f;

        for (Map.Entry<String, Float> entry : moduleAnimations.entrySet()) {
            if (entry.getValue() <= 0.01f) continue;
            ModuleStructure module = cachedModules.get(entry.getKey());
            if (module == null) continue;
            maxNameWidth = Math.max(maxNameWidth, Fonts.TEST.getWidth(module.getName(), 6f));
        }

        if (maxNameWidth == 0f) maxNameWidth = Fonts.TEST.getWidth("Module", 6f);

        return MARGIN + toggleW + 6f + maxNameWidth + MARGIN * 2f + 10f;
    }
}