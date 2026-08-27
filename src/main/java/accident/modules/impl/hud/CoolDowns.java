package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import accident.client.draggables.AbstractHudElement;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.ColorUtil;
import accident.util.animations.Direction;
import accident.util.render.Render2D;
import accident.util.render.shader.Scissor;
import accident.util.render.font.Fonts;
import accident.util.render.item.ItemRender;

import java.awt.*;
import java.util.*;
import java.util.List;

public class CoolDowns extends AbstractHudElement {

    private static class CoolDownInfo {
        Item item;
        long startTime;
        float startProgress;
        long estimatedTotalMs;
        int displaySeconds = -1;
        long nextTickTime = 0;
        boolean estimateReady = false;

        CoolDownInfo(Item item, float progress) {
            this.item = item;
            this.startTime = System.currentTimeMillis();
            this.startProgress = progress;
            this.estimatedTotalMs = 0;
            this.nextTickTime = 0;
            this.estimateReady = false;
        }

        void updateEstimate(float currentProgress) {
            if (estimateReady) return;

            long now = System.currentTimeMillis();
            long elapsed = now - startTime;

            if (elapsed < 200) return;

            if (startProgress > currentProgress && startProgress > 0.01f) {
                float progressConsumed = startProgress - currentProgress;
                if (progressConsumed > 0.01f) {
                    estimatedTotalMs = (long) (elapsed / progressConsumed);

                    long remainingMs = (long) (currentProgress * estimatedTotalMs);
                    displaySeconds = (int) Math.ceil(remainingMs / 1000.0);
                    nextTickTime = now + 1000;
                    estimateReady = true;
                }
            }
        }

        int getDisplaySeconds(float currentProgress) {
            if (currentProgress <= 0) {
                displaySeconds = 0;
                return 0;
            }

            if (!estimateReady) {
                return -1;
            }

            long now = System.currentTimeMillis();

            if (now >= nextTickTime && nextTickTime > 0) {
                displaySeconds = Math.max(0, displaySeconds - 1);
                nextTickTime = now + 1000;

                int calculatedSeconds;
                if (estimatedTotalMs > 0) {
                    long remainingMs = (long) (currentProgress * estimatedTotalMs);
                    calculatedSeconds = (int) Math.ceil(remainingMs / 1000.0);
                } else {
                    calculatedSeconds = displaySeconds;
                }

                if (Math.abs(displaySeconds - calculatedSeconds) > 2) {
                    displaySeconds = calculatedSeconds;
                }
            }

            return Math.max(0, displaySeconds);
        }
    }

    private final Map<Item, CoolDownInfo> cooldownMap = new LinkedHashMap<>();
    private final Map<Item, Float> cooldownAnimations = new LinkedHashMap<>();
    private final Set<Item> activeCooldowns = new HashSet<>();

    private float smoothWidth = 80;
    private long lastTimeNs = 0L;
    private final float[] rowSlideOffset = new float[64];

    private long lastItemChange = 0;
    private int currentItemIndex = 0;

    private static final float SIZE_SMOOTH_SPEED = 12f;
    private static final float ROW_SLIDE_SPEED = 8f;
    private static final float MARGIN = 5f;
    private static final float HEADER_HEIGHT = 23f;
    private static final float ROW_HEIGHT = 16f;
    private static final float ROW_SPACING = 2f;
    private static final float ICON_SIZE = 9f;
    private static final String TIMER_TEMPLATE = "00:00";
    private static final Item[] EXAMPLE_ITEMS = {
            Items.ENDER_EYE, Items.ENDER_PEARL, Items.SUGAR, Items.MACE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.TRIDENT, Items.CROSSBOW, Items.DRIED_KELP, Items.NETHERITE_SCRAP
    };

    public CoolDowns() {
        super("CoolDowns", 10, 40, 80, 23, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            cooldownMap.clear();
            activeCooldowns.clear();
            cooldownAnimations.clear();
            stopAnimation();
            return;
        }

        activeCooldowns.clear();
        Set<Item> checkedItems = new HashSet<>();

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && !checkedItems.contains(stack.getItem())) {
                checkedItems.add(stack.getItem());
                checkAndUpdateCooldown(stack.getItem());
            }
        }

        ItemStack mainHand = mc.player.getMainHandStack();
        if (!mainHand.isEmpty() && !checkedItems.contains(mainHand.getItem())) {
            checkAndUpdateCooldown(mainHand.getItem());
        }
        ItemStack offHand = mc.player.getOffHandStack();
        if (!offHand.isEmpty() && !checkedItems.contains(offHand.getItem())) {
            checkAndUpdateCooldown(offHand.getItem());
        }

        boolean shouldShow = !cooldownAnimations.isEmpty() || isChat(mc.currentScreen);
        if (shouldShow) {
            startAnimation();
        } else {
            stopAnimation();
        }

        if (cooldownAnimations.isEmpty() && isChat(mc.currentScreen)) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastItemChange >= 1000) {
                currentItemIndex = (currentItemIndex + 1) % EXAMPLE_ITEMS.length;
                lastItemChange = currentTime;
            }
        }
    }

    private void checkAndUpdateCooldown(Item item) {
        if (mc.player == null) return;

        var cooldownManager = mc.player.getItemCooldownManager();
        ItemStack stack = item.getDefaultStack();

        if (cooldownManager.isCoolingDown(stack)) {
            float progress = cooldownManager.getCooldownProgress(stack, 0.0f);
            activeCooldowns.add(item);

            CoolDownInfo info = cooldownMap.get(item);
            if (info == null) {
                info = new CoolDownInfo(item, progress);
                cooldownMap.put(item, info);
            } else {
                info.updateEstimate(progress);
            }

            if (!cooldownAnimations.containsKey(item)) {
                cooldownAnimations.put(item, 0f);
            }
        }
    }

    private String formatDuration(int seconds) {
        if (seconds < 0) return "...";
        if (seconds == 0) return "0:00";
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private String getItemName(Item item) {
        return item.getDefaultStack().getName().getString();
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

    private float smoothTowards(float current, float target, float dt, float speedPerSec) {
        if (!Float.isFinite(dt) || dt <= 0f) return target;
        float k = 1f - (float) Math.exp(-speedPerSec * dt);
        return current + (target - current) * k;
    }

    private float easeOutCubic(float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return 1.0f - (float) Math.pow(1.0f - t, 3.0);
    }

    private float computeTargetWidth() {
        float maxNameWidth = 0f;
        float maxTimerWidth = Fonts.TEST.getWidth(TIMER_TEMPLATE, 6);

        for (Map.Entry<Item, Float> entry : cooldownAnimations.entrySet()) {
            float animation = entry.getValue();
            if (animation <= 0) continue;

            Item item = entry.getKey();
            String name = getItemName(item);
            float nameWidth = Fonts.TEST.getWidth(name, 6);
            maxNameWidth = Math.max(maxNameWidth, nameWidth);
        }

        if (maxNameWidth == 0f) {
            maxNameWidth = Fonts.TEST.getWidth("CoolDown", 6);
        }

        return MARGIN + 20 + maxNameWidth + 15 + maxTimerWidth + MARGIN + 20;
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

        List<Item> toRemove = new ArrayList<>();
        for (Map.Entry<Item, Float> entry : cooldownAnimations.entrySet()) {
            Item item = entry.getKey();
            float currentAnim = entry.getValue();
            float targetAnim = activeCooldowns.contains(item) ? 1f : 0f;
            float newAnim = smoothTowards(currentAnim, targetAnim, dt, ROW_SLIDE_SPEED);

            if (Math.abs(newAnim - targetAnim) < 0.01f) {
                newAnim = targetAnim;
            }

            if (newAnim <= 0.01f && targetAnim == 0f) {
                toRemove.add(item);
            } else {
                cooldownAnimations.put(item, newAnim);
            }
        }
        for (Item item : toRemove) {
            cooldownAnimations.remove(item);
            cooldownMap.remove(item);
        }

        float targetWidth = computeTargetWidth();
        smoothWidth = smoothTowards(smoothWidth, targetWidth, dt, SIZE_SMOOTH_SPEED);

        float x = getRenderX();
        float y = getRenderY();
        float width = smoothWidth;

        boolean hasAnimatingCooldowns = !cooldownAnimations.isEmpty();
        boolean showExample = !hasAnimatingCooldowns && isChat(mc.currentScreen);

        int glassAlpha = (int)(alphaFactor * 60);
        int bgColor = (glassAlpha << 24) | 0x0F121F;

        Render2D.blur(x, y, width, HEADER_HEIGHT, 10f, 4f, bgColor);
        Fonts.ICONKIHUD.draw("W", x + MARGIN, y + MARGIN - 0.5f, 9, finalCustomColor);
        Fonts.TEST.draw("CoolDowns", x + MARGIN + 15, y + MARGIN + 0.5f, 6, 0xFFFFFFFF);
        Render2D.rect(
                x + MARGIN + 13,
                y + 4,
                0.5f,
                8,
                (int)(0.1f * scaleAnimation.getOutput() * alphaFactor * 255) << 24 | 0xFFFFFF
        );

        int cooldownsCount = activeCooldowns.isEmpty() ? 1 : activeCooldowns.size();
        String countText = String.valueOf(cooldownsCount);
        float countWidth = Fonts.TEST.getWidth(countText, 6);
        float countX = x + width - countWidth - MARGIN - 5;

        Render2D.rect(countX, y + MARGIN - 1, countWidth + 6, 10,
                (glassAlpha << 24) | 0x232429, 3);
        Fonts.TEST.draw(countText, countX + 3, y + MARGIN + 1, 6, 0xFFFFFFFF);

        float offsetY = 0f;
        float ys = 15f;
        int rowIndex = 0;

        if (showExample) {
            float rawSlide = rowSlideOffset[0];
            float slidePC = easeOutCubic(rawSlide);

            float baseY = y + ys + offsetY;
            float rowX = x - width + width * slidePC;
            int rowColor = (glassAlpha << 24) | 0x141623;
            Render2D.blur(rowX, baseY, width, ROW_HEIGHT, 10f, 4f, rowColor);

            Item exampleItem = EXAMPLE_ITEMS[currentItemIndex];
            float itemX = rowX + 8;
            float itemY = baseY + 4.5f;

            if (ItemRender.needsContextRender(exampleItem.getDefaultStack())) {
                ItemRender.drawItemWithContext(context, exampleItem.getDefaultStack(), itemX, itemY, 0.5f, alphaFactor);
            } else {
                ItemRender.drawItem(exampleItem.getDefaultStack(), itemX, itemY, 0.5f, alphaFactor);
            }

            Fonts.TEST.draw("Example CoolDown", rowX + 20, baseY + 6, 6, 0xFFFFFFFF);
            float timerWidth = Fonts.TEST.getWidth("0:00", 6);
            float timerX = rowX + width - timerWidth - MARGIN - 2;
            Render2D.rect(timerX - 2, baseY + 3, timerWidth + 4, 10,
                    (glassAlpha << 24) | 0x232429, 3);
            Fonts.TEST.draw("0:00", timerX, baseY + 6, 6, 0xFFFFFFFF);

            rowSlideOffset[0] = smoothTowards(rowSlideOffset[0], 1f, dt, ROW_SLIDE_SPEED);
        } else {
            for (Map.Entry<Item, Float> entry : cooldownAnimations.entrySet()) {
                Item item = entry.getKey();
                float animation = entry.getValue();
                if (animation <= 0.01f) continue;

                CoolDownInfo info = cooldownMap.get(item);
                if (info == null) continue;

                var cooldownManager = mc.player.getItemCooldownManager();
                float currentProgress = cooldownManager.getCooldownProgress(item.getDefaultStack(), 0.0f);

                float rawSlide = rowSlideOffset[rowIndex];
                float slidePC = easeOutCubic(rawSlide);

                rowSlideOffset[rowIndex] = smoothTowards(rawSlide, 1f, dt, ROW_SLIDE_SPEED);
                rowSlideOffset[rowIndex] = MathHelper.clamp(rowSlideOffset[rowIndex], 0f, 1f);

                float animPC = animation * slidePC;
                float baseY = y + ys + offsetY;
                float rowX = x - width + width * slidePC;
                int rowColor = ((int)(alphaFactor * 60) << 24) | 0x141623;
                Render2D.blur(rowX, baseY, width, ROW_HEIGHT, 10f, 4f, rowColor);

                float itemX = rowX + 8;
                float itemY = baseY + 4.5f;

                if (ItemRender.needsContextRender(item.getDefaultStack())) {
                    ItemRender.drawItemWithContext(context, item.getDefaultStack(), itemX, itemY, 0.5f, animation * alphaFactor);
                } else {
                    ItemRender.drawItem(item.getDefaultStack(), itemX, itemY, 0.5f, animation * alphaFactor);
                }

                String name = getItemName(item);
                Fonts.TEST.draw(name, rowX + 20, baseY + 6, 6,
                        (255 << 24) | 0xFFFFFF);

                int remainingSeconds = info.getDisplaySeconds(currentProgress);
                String duration = formatDuration(remainingSeconds);
                float timerWidth = Fonts.TEST.getWidth(duration, 6);
                float timerX = rowX + width - timerWidth - MARGIN - 2;

                Render2D.rect(timerX - 2, baseY + 3, timerWidth + 4, 10,
                        ((int)(alphaFactor * 15) << 24) | 0x232429, 3);
                Fonts.TEST.draw(duration, timerX, baseY + 6, 6,
                        (255 << 24) | 0xFFFFFF);

                offsetY += (ROW_HEIGHT + ROW_SPACING) * animPC;
                rowIndex++;
            }
        }

        float headerBottomMargin = 4f;
        float targetHeight = HEADER_HEIGHT + headerBottomMargin + (offsetY > 0 ? offsetY : ROW_HEIGHT) + MARGIN;
        setWidth((int) Math.ceil(smoothWidth));
        setHeight((int) Math.ceil(targetHeight));
    }
}