package accident.modules.impl.hud;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import accident.client.draggables.AbstractHudElement;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.animations.Direction;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.util.*;

public class Potions extends AbstractHudElement {

    private List<StatusEffectInstance> effectsList = new ArrayList<>();
    private Map<String, Float> effectAnimations = new LinkedHashMap<>();
    private Map<String, StatusEffectInstance> cachedEffects = new LinkedHashMap<>();
    private Set<String> activeEffectIds = new HashSet<>();

    private float smoothWidth = 80;
    private float smoothMaxKeyWidth = -1f;
    private long lastTimeNs = 0L;

    private long lastEffectChange = 0;
    private String currentRandomEffect = "speed";

    private static final List<String> RANDOM_EFFECTS = List.of(
            "speed", "slowness", "haste", "mining_fatigue", "strength",
            "jump_boost", "regeneration", "resistance", "fire_resistance",
            "water_breathing", "invisibility", "night_vision", "hunger",
            "weakness", "poison", "wither", "health_boost", "absorption"
    );

    private static final float SIZE_SMOOTH_SPEED = 12f;
    private static final float ROW_SLIDE_SPEED = 8f;
    private static final float TITLE_SIZE = 7.0F;
    private static final float ROW_SIZE = 6.7F;
    private static final float MARGIN = 5f;
    private static final float ICON_SIZE = 9f;
    private static final int BLINK_THRESHOLD_TICKS = 100;
    private static final float HEADER_HEIGHT = 23f;
    private static final float ROW_HEIGHT = 16f;
    private static final float ROW_SPACING = 2f;

    private final float[] rowSlideOffset = new float[64];

    public Potions() {
        super("Potions", 300, 100, 80, 23, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            effectsList = new ArrayList<>();
            activeEffectIds.clear();
            stopAnimation();
            return;
        }

        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        effectsList = new ArrayList<>(effects.stream()
                .filter(StatusEffectInstance::shouldShowIcon)
                .toList());

        activeEffectIds.clear();
        for (StatusEffectInstance effect : effectsList) {
            String id = getEffectId(effect);
            activeEffectIds.add(id);
            cachedEffects.put(id, effect);
            if (!effectAnimations.containsKey(id)) {
                effectAnimations.put(id, 0f);
            }
        }

        boolean hasActiveEffects = !activeEffectIds.isEmpty() || !effectAnimations.isEmpty();
        boolean inChat = isChat(mc.currentScreen);

        if (hasActiveEffects || inChat) {
            startAnimation();
        } else {
            stopAnimation();
        }

        if (effectsList.isEmpty() && inChat) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastEffectChange >= 1000) {
                currentRandomEffect = RANDOM_EFFECTS.get(new Random().nextInt(RANDOM_EFFECTS.size()));
                lastEffectChange = currentTime;
            }
        }
    }

    private String getEffectId(StatusEffectInstance effect) {
        return effect.getEffectType().getKey()
                .map(key -> key.getValue().toString())
                .orElse("unknown_" + effect.hashCode());
    }

    private String formatDuration(int ticks) {
        if (ticks == -1) {
            return "∞∞";
        }
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String getEffectName(StatusEffectInstance effect) {
        return effect.getEffectType().value().getName().getString();
    }

    private String getLevelText(int amplifier) {
        if (amplifier <= 0) {
            return "";
        }
        return " " + (amplifier + 1);
    }

    private float getFullNameWidth(StatusEffectInstance effect) {
        String name = getEffectName(effect);
        int amplifier = effect.getAmplifier();
        float nameWidth = Fonts.TEST.getWidth(name, 6);
        if (amplifier > 0) {
            String levelText = getLevelText(amplifier);
            float levelWidth = Fonts.REGULAR.getWidth(levelText, 6);
            return nameWidth + 3 + levelWidth;
        }
        return nameWidth;
    }

    private Identifier getEffectTexture(RegistryEntry<StatusEffect> effect) {
        return effect.getKey()
                .map(RegistryKey::getValue)
                .map(id -> id.withPrefixedPath("mob_effect/"))
                .orElse(Identifier.ofVanilla("mob_effect/speed"));
    }

    private Identifier getRandomEffectTexture() {
        return Identifier.ofVanilla("mob_effect/" + currentRandomEffect);
    }

    private int getBlinkAlpha(int duration, int baseAlpha) {
        if (duration == -1 || duration > BLINK_THRESHOLD_TICKS) {
            return baseAlpha;
        }

        long currentTime = System.currentTimeMillis();
        double blinkSpeed = 0.008;
        double blinkWave = Math.sin(currentTime * blinkSpeed);
        float blinkFactor = (float) ((blinkWave + 1.0) / 2.0);

        int minAlpha = Math.max(50, baseAlpha - 150);
        return (int) (minAlpha + (baseAlpha - minAlpha) * (1.0f - blinkFactor));
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
        float maxTimerWidth = Fonts.TEST.getWidth("00:00", 6);

        for (Map.Entry<String, Float> entry : effectAnimations.entrySet()) {
            float animation = entry.getValue();
            if (animation <= 0) continue;

            StatusEffectInstance effect = cachedEffects.get(entry.getKey());
            if (effect == null) continue;

            float nameWidth = getFullNameWidth(effect);
            maxNameWidth = Math.max(maxNameWidth, nameWidth);
        }

        if (maxNameWidth == 0f) {
            maxNameWidth = Fonts.TEST.getWidth("Example Effect", 6) + 20;
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

        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Float> entry : effectAnimations.entrySet()) {
            String id = entry.getKey();
            float currentAnim = entry.getValue();
            float targetAnim = activeEffectIds.contains(id) ? 1f : 0f;
            float newAnim = smoothTowards(currentAnim, targetAnim, dt, ROW_SLIDE_SPEED);

            if (Math.abs(newAnim - targetAnim) < 0.01f) {
                newAnim = targetAnim;
            }

            if (newAnim <= 0.01f && targetAnim == 0f) {
                toRemove.add(id);
            } else {
                effectAnimations.put(id, newAnim);
            }
        }
        for (String id : toRemove) {
            effectAnimations.remove(id);
            cachedEffects.remove(id);
        }

        float targetWidth = computeTargetWidth();
        smoothWidth = smoothTowards(smoothWidth, targetWidth, dt, SIZE_SMOOTH_SPEED);

        float x = getRenderX();
        float y = getRenderY();
        float width = getWidth();

        boolean hasAnimatingEffects = !effectAnimations.isEmpty();
        boolean showExample = !hasAnimatingEffects && isChat(mc.currentScreen);

        int glassAlpha = (int)(alphaFactor * 60);
        int bgColor = (glassAlpha << 24) | 0x0F121F;

        Render2D.blur(x, y, width, HEADER_HEIGHT, 10f, 4f, bgColor);

        Fonts.ICONKIHUD.draw("H", x + MARGIN, y + MARGIN - 0.5f, 8, finalCustomColor);
        Fonts.TEST.draw("Potions", x + MARGIN + 15, y + MARGIN + 0.5f, 6, 0xFFFFFFFF);

        Render2D.rect(
                x + MARGIN + 13,
                y + 4,
                0.5f,
                8,
                (int)(0.1f * scaleAnimation.getOutput() * alphaFactor * 255) << 24 | 0xFFFFFF
        );

        int effectsCount = activeEffectIds.isEmpty() ? 1 : activeEffectIds.size();
        String countText = String.valueOf(effectsCount);
        float countWidth = Fonts.TEST.getWidth(countText, 6);
        float countX = x + width - countWidth - MARGIN - 5;

        Render2D.rect(countX, y + MARGIN - 1, countWidth + 6, 10,
                (glassAlpha << 24) | 0x232429, 3);
        Fonts.TEST.draw(countText, countX + 3, y + MARGIN + 1, 6, 0xFFFFFFFF);

        float offsetY = 0f;
        float ys = 15f;
        int rowIndex = 0;

        if (showExample) {
            float animPC = 1.0f;
            float rawSlide = rowSlideOffset[0];
            float slidePC = easeOutCubic(rawSlide);

            float baseY = y + ys + offsetY;
            float rowX = x - width + width * slidePC;

            int rowColor = (glassAlpha << 24) | 0x141623;
            Render2D.blur(rowX, baseY, width, ROW_HEIGHT, 10f, 4f, rowColor);

            float scale = ICON_SIZE / 18f;
            float iconX = rowX + 8;
            float iconY = baseY + 5;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(iconX, iconY);
            context.getMatrices().scale(scale, scale);
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, getRandomEffectTexture(), 0, 0, 18, 18, 0xFFFFFFFF);
            context.getMatrices().popMatrix();

            Fonts.BOLD.draw("Potions", rowX + 20, baseY + 6, 6, 0xFFFFFFFF);

            float timerWidth = Fonts.BOLD.getWidth("00:00", 6);
            float timerX = rowX + width - timerWidth - MARGIN - 2;
            Render2D.rect(timerX - 2, baseY + 3, timerWidth + 4, 10,
                    (glassAlpha << 24) | 0x232429, 3);
            Fonts.BOLD.draw("00:00", timerX, baseY + 6, 6, 0xFFFFFFFF);

            rowSlideOffset[0] = smoothTowards(rowSlideOffset[0], 1f, dt, ROW_SLIDE_SPEED);
        } else {
            for (Map.Entry<String, Float> entry : effectAnimations.entrySet()) {
                String id = entry.getKey();
                float animation = entry.getValue();
                if (animation <= 0.01f) continue;

                StatusEffectInstance effect = cachedEffects.get(id);
                if (effect == null) continue;

                float rawSlide = rowSlideOffset[rowIndex];
                float slidePC = easeOutCubic(rawSlide);

                rowSlideOffset[rowIndex] = smoothTowards(rawSlide, 1f, dt, ROW_SLIDE_SPEED);
                rowSlideOffset[rowIndex] = MathHelper.clamp(rowSlideOffset[rowIndex], 0f, 1f);

                float animPC = animation * slidePC;
                float baseY = y + ys + offsetY;
                float rowX = x - width + width * slidePC;

                int rowColor = ((int)(alphaFactor * 60) << 24) | 0x141623;
                Render2D.blur(rowX, baseY, width, ROW_HEIGHT, 10f, 4f, rowColor);

                Identifier texture = getEffectTexture(effect.getEffectType());
                int duration = effect.getDuration();
                int blinkAlpha = getBlinkAlpha(duration, 255);

                float scale = ICON_SIZE / 18f;
                float iconX = rowX + 8;
                float iconY = baseY + 5;

                context.getMatrices().pushMatrix();
                context.getMatrices().translate(iconX, iconY);
                context.getMatrices().scale(scale, scale);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 18, 18, (blinkAlpha << 24) | 0xFFFFFF);
                context.getMatrices().popMatrix();

                String name = getEffectName(effect);
                Fonts.BOLD.draw(name, rowX + 20, baseY + 6, 6,
                        (blinkAlpha << 24) | 0xFFFFFF);

                if (effect.getAmplifier() > 0) {
                    float nameWidth = Fonts.BOLD.getWidth(name, 6);
                    Fonts.BOLD.draw(getLevelText(effect.getAmplifier()),
                            rowX + 20 + nameWidth + 2, baseY + 6.7f, 5.7f,
                            (blinkAlpha << 24) | 0xAAAAAA);
                }

                String timer = formatDuration(duration);
                float timerWidth = Fonts.BOLD.getWidth(timer, 6);
                float timerX = rowX + width - timerWidth - MARGIN - 2;
                Render2D.rect(timerX - 2, baseY + 3, timerWidth + 4, 10,
                        ((int)(alphaFactor * 15) << 24) | 0x232429, 3);
                Fonts.BOLD.draw(timer, timerX, baseY + 6, 6,
                        (blinkAlpha << 24) | 0xFFFFFF);

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