package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import accident.client.draggables.AbstractHudElement;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.math.Projection;
import accident.util.network.Network;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.timer.StopWatch;

import accident.modules.module.setting.implement.SelectSetting;

import java.awt.*;

import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SelectSetting;

public class TargetHud extends AbstractHudElement {

    public static final SelectSetting targetHudMode = new SelectSetting("accident.module.hud.setting.targethudmode.name", "accident.module.hud.setting.targethudmode.desc")
            .value("Vanilla", "ScoreBoard")
            .selected("ScoreBoard");

    public static final SelectSetting hpDisplayMode = new SelectSetting("accident.module.targethud.setting.hpdisplay.name", "accident.module.targethud.setting.hpdisplay.desc")
            .value("hp", "percent")
            .selected("hp");

    public static final BooleanSetting targetFollow = new BooleanSetting("accident.module.targethud.setting.follow.name", "accident.module.targethud.setting.follow.desc")
            .setValue(false);

    private final StopWatch stopWatch = new StopWatch();
    private LivingEntity lastTarget;
    private LivingEntity delayedTarget;
    private long lastTargetTime = 0;
    private static final long TARGET_HOLD_TIME = 2000;

    private float healthAnimation = 0;
    private float trailAnimation = 0;
    private float absorptionAnimation = 0;
    private float displayedHealth = 0;
    private long  lastUpdateTime = System.currentTimeMillis();
    private long  startTime = System.currentTimeMillis();
    private long  lastTimeNs = 0L;
    private static final float WIDTH = 130f;
    private static final float HEIGHT = 36f;
    private static final float FACE_SIZE = 24f;
    private static final float FACE_X_OFFSET = 6f;
    private static final float FACE_Y_OFFSET = 5f;
    private static final float CONTENT_X_OFFSET = FACE_X_OFFSET + FACE_SIZE + 6f;
    private static final float BAR_HEIGHT = 2f;
    private static final float BAR_BOTTOM_MARGIN = 4f;
    private float followScreenX = 0f;
    private float followScreenY = 0f;
    private boolean followVisible = false;

    public TargetHud() {
        super("TargetHud", 10, 80, (int) WIDTH, (int) HEIGHT, true);
        settings(targetHudMode, hpDisplayMode, targetFollow);
    }

    @Override
    public boolean visible() {
        return isEnabled();
    }

    private boolean canHitEntity(LivingEntity target) {
        if (mc.player == null || target == null) return false;

        if (mc.crosshairTarget == null) return false;
        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return false;

        EntityHitResult hitResult = (EntityHitResult) mc.crosshairTarget;
        return hitResult.getEntity() == target;
    }

    private void updateFollowPosition() {
        LivingEntity targetToFollow = delayedTarget != null ? delayedTarget : lastTarget;
        if (targetToFollow == null) {
            followVisible = false;
            return;
        }

        Vec3d entityPos = targetToFollow.getLerpedPos(lastTickDelta);
        double torsoY  = entityPos.y + targetToFollow.getHeight() * 0.6;
        Vec3d torsoPos = new Vec3d(entityPos.x, torsoY, entityPos.z);

        Vec3d screen = Projection.worldSpaceToScreenSpace(torsoPos);

        if (screen == null || screen.z < 0 || screen.z > 1) {
            followVisible = false;
            return;
        }

        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();

        if (screen.x < -WIDTH || screen.x > screenW + WIDTH
                || screen.y < -HEIGHT || screen.y > screenH + HEIGHT) {
            followVisible = false;
            return;
        }

        followScreenX = (float) screen.x - WIDTH  * 0.5f;
        followScreenY = (float) screen.y - HEIGHT * 0.5f;
        followVisible = true;
    }

    @Override
    public void tick() {
        if (!isEnabled()) return;

        LivingEntity currentTarget = null;

        if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof LivingEntity living) {
                if (canHitEntity(living)) {
                    currentTarget = living;
                }
            }
        }

        long currentTime = System.currentTimeMillis();

        if (currentTarget != null) {
            delayedTarget = currentTarget;
            lastTargetTime = currentTime;
            lastTarget = currentTarget;
            startAnimation();
            stopWatch.reset();
        } else if (isChat(mc.currentScreen)) {
            delayedTarget = mc.player;
            lastTargetTime = currentTime;
            lastTarget = mc.player;
            startAnimation();
            stopWatch.reset();
        }

        boolean hasValidTarget = false;
        if (delayedTarget != null && (currentTime - lastTargetTime) <= TARGET_HOLD_TIME) {
            if (delayedTarget.isRemoved() || delayedTarget.getHealth() <= 0) {
                delayedTarget = null;
                hasValidTarget = false;
            } else {
                hasValidTarget = true;
            }
        } else {
            delayedTarget = null;
        }

        if (hasValidTarget && !isTargetVisible(delayedTarget)) {
            delayedTarget = null;
            hasValidTarget = false;
            stopAnimation();
        }

        if (!hasValidTarget) {
            if (stopWatch.finished(10)) {
                stopAnimation();
            }
        }
    }

    private boolean isTargetVisible(LivingEntity target) {
        if (target == null) return false;
        // Проверка на эффект невидимости
        return !target.isInvisible() && !target.isInvisibleTo(mc.player);
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

    private float lerp(float current, float target, float dt, float speed) {
        return current + (target - current) * (float) (1.0 - Math.pow(0.001, dt * speed));
    }

    private float snapToStep(float value, float step) {
        return Math.round(value / step) * step;
    }

    private float getHealth(LivingEntity entity) {
        String mode = targetHudMode.getSelected();
        if (mode.equals("ScoreBoard") && entity instanceof PlayerEntity player) {
            var scoreboard = player.getEntityWorld().getScoreboard();
            var objective  = scoreboard.getObjectiveForSlot(
                    net.minecraft.scoreboard.ScoreboardDisplaySlot.BELOW_NAME);
            if (objective != null) {
                var score = scoreboard.getScore(player, objective);
                if (score != null && score.getScore() > 0) return (float) score.getScore();
            }
        }
        if (entity.isInvisible() && !Network.isSpookyTime() && !Network.isCopyTime()) {
            return entity.getMaxHealth();
        }
        return entity.getHealth();
    }

    private String getHealthString(LivingEntity entity, float displayed) {
        if (hpDisplayMode.isSelected("percent")) {
            float max = entity.getMaxHealth();
            if (max <= 0) return "0%";
            float pct = Math.min(100f, (displayed / max) * 100f);
            return pct >= 100f ? "100%" : String.format("%.0f%%", pct);
        }
        if (displayed >= 100) return String.valueOf((int) displayed);
        if (displayed >= 10)  return String.format("%.1f", displayed);
        return String.format("%.2f", displayed);
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        LivingEntity targetToDraw = delayedTarget != null ? delayedTarget : lastTarget;

        if (targetToDraw != null && !isTargetVisible(targetToDraw)) {
            targetToDraw = null;
        }

        if (alpha <= 0 || targetToDraw == null) return;

        if (targetFollow.isValue()) {
            updateFollowPosition();
            if (!followVisible) return;
            drawHud(context, alpha, followScreenX, followScreenY, targetToDraw);
        } else {
            drawHud(context, alpha, getRenderX(), getRenderY(), targetToDraw);
        }
    }

    private void drawHud(DrawContext context, int alpha, float x, float y, LivingEntity target) {
        float dt          = computeDtSeconds();
        float alphaFactor = alpha / 255.0f;

        long  now       = System.currentTimeMillis();
        float deltaTime = Math.min((now - lastUpdateTime) / 1000.0f, 0.1f);
        lastUpdateTime  = now;

        setWidth((int) WIDTH);
        setHeight((int) HEIGHT);

        float scaleAlpha = scaleAnimation.getOutput().floatValue();
        int   baseAlpha  = (int) (255 * scaleAlpha * alphaFactor);

        int glassAlpha = (int) (alphaFactor * 60 * scaleAlpha);
        Render2D.blur(x, y, getWidth(), getHeight(), 10f, 4f, (glassAlpha << 24) | 0x0F121F);
        Render2D.outline(x, y, getWidth(), getHeight(), 0.35f,
                new Color(55, 55, 55, (int) (215 * alphaFactor * scaleAlpha)).getRGB(), 6);

        float barY = y + HEIGHT - BAR_BOTTOM_MARGIN - BAR_HEIGHT;
        drawHealthBar(barY, x + 4f, WIDTH - 8f, baseAlpha, deltaTime, target);
        drawFace(context, x + FACE_X_OFFSET, y + FACE_Y_OFFSET, scaleAlpha, alphaFactor, target);
        drawRightContent(context, x, y, baseAlpha, deltaTime, target);
    }

    private int interpolateColor(int color1, int color2, float factor) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        return ((int)(r1 + factor * (r2 - r1)) << 16)
                | ((int)(g1 + factor * (g2 - g1)) << 8)
                | (int)(b1 + factor * (b2 - b1));
    }

    private int getThemeColor(int alpha) {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;

        int[] palette = theme.palette;

        if (palette == null || palette.length == 0) {
            return (alpha << 24) | 0x808ED7;
        }
        if (palette.length == 1) {
            return (alpha << 24) | (palette[0] & 0xFFFFFF);
        }

        long timeMs = System.currentTimeMillis();
        double speed = 6000.0;
        float indexProgress = (float)((timeMs % (long)speed) / speed * palette.length);
        int idx1 = (int)indexProgress % palette.length;
        int idx2 = (idx1 + 1) % palette.length;
        float frac = indexProgress - (int)indexProgress;

        int mixed = interpolateColor(palette[idx1], palette[idx2], frac);
        return (alpha << 24) | (mixed & 0xFFFFFF);
    }

    private void drawHealthBar(float barY, float barX, float barWidth, int baseAlpha, float deltaTime, LivingEntity target) {
        float hp     = getHealth(target);
        float maxHp  = target.getMaxHealth();
        float absorp = target.getAbsorptionAmount();
        boolean inv  = target.isInvisible() && !Network.isSpookyTime() && !Network.isCopyTime();

        float targetHealth = inv ? 1.0f : hp / maxHp;
        healthAnimation = lerp(healthAnimation, targetHealth, deltaTime, 3f);
        if (targetHealth > trailAnimation) trailAnimation = targetHealth;
        trailAnimation = lerp(trailAnimation, targetHealth, deltaTime, 3.5f);

        float targetAbsorption = inv ? 0f : absorp / maxHp;
        absorptionAnimation = lerp(absorptionAnimation, targetAbsorption, deltaTime, 3f);

        float radius = BAR_HEIGHT / 2f;

        Render2D.rect(barX, barY, barWidth, BAR_HEIGHT,
                ((int) (baseAlpha * 0.3f) << 24) | 0x1E1E1E, radius);

        float hp01    = Math.max(0, Math.min(1, healthAnimation));
        float trail01 = Math.max(0, Math.min(1, trailAnimation));

        if (trail01 > hp01) {
            Render2D.rect(barX, barY, barWidth * trail01, BAR_HEIGHT,
                    ((int) (baseAlpha * 0.5f) << 24) | 0x373737, radius);
        }

        if (hp01 > 0.01f) {
            int c1 = getThemeColor(baseAlpha);
            int c2 = getThemeColor((int)(baseAlpha * 0.9f));
            int[] colors = {c1, c1, c2, c2};
            Render2D.gradientRect(barX, barY, barWidth * hp01, BAR_HEIGHT, colors, radius);
        }
        float abs01 = Math.max(0, Math.min(1, absorptionAnimation));
        if (abs01 > 0.01f && !Network.isFunTime()) {
            float phase = ((System.currentTimeMillis() - startTime) % 1200L) / 1200f * (float) Math.PI * 2f;
            int[] gc = new int[4];
            for (int i = 0; i < 2; i++) {
                float w  = ((float) Math.sin(phase - i * 1.5f) + 1f) / 2f;
                int   cg = (int) (165 + 50 * w);
                gc[i * 2] = gc[i * 2 + 1] = ((int) (baseAlpha * 0.8f) << 24) | (0xFF << 16) | (cg << 8);
            }
            Render2D.gradientRect(barX, barY, barWidth * abs01, BAR_HEIGHT, gc, radius);
        }
    }

    private void drawFace(DrawContext context, float faceX, float faceY,
                          float scaleAlpha, float alphaFactor, LivingEntity target) {
        EntityRenderer<? super LivingEntity, ?> baseRenderer =
                mc.getEntityRenderDispatcher().getRenderer(target);
        if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) return;

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer =
                (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;

        LivingEntityRenderState state    = renderer.getAndUpdateRenderState(target, lastTickDelta);
        Identifier               texture = renderer.getTexture(state);

        float hurt  = target.hurtTime > 0 ? target.hurtTime / 10.0f : 0f;
        int   alpha = (int) (255 * scaleAlpha * alphaFactor);
        int   color = new Color(255, (int) (255 * (1 - hurt)), (int) (255 * (1 - hurt)), alpha).getRGB();

        // лицо головы всегда в пикселях (8,8)-(16,16), но размер листа текстуры разный
        // (игрок 64x64, свинья 64x32) - делить V на захардкоженные 64 нельзя, поедет UV
        float texW = textureWidth(texture, 64f);
        float texH = textureHeight(texture, 64f);

        Render2D.texture(texture, faceX - 2, faceY - 1.7f, FACE_SIZE - 2, FACE_SIZE - 2,
                8f / texW, 8f / texH, 16f / texW, 16f / texH, color, 0, 4f);

        // hat-слой только у скинов игроков; у мобов на этом месте тело, будет мусор
        if (target instanceof PlayerEntity) {
            Render2D.texture(texture, faceX - 2, faceY - 1.7f, FACE_SIZE - 2, FACE_SIZE - 2,
                    40f / texW, 8f / texH, 48f / texW, 16f / texH, color, 0f, 4f);
        }
    }

    private float textureWidth(Identifier id, float fallback) {
        var texture = mc.getTextureManager().getTexture(id);
        if (texture == null || texture.getGlTexture() == null) return fallback;
        int width = texture.getGlTexture().getWidth(0);
        return width > 0 ? width : fallback;
    }

    private float textureHeight(Identifier id, float fallback) {
        var texture = mc.getTextureManager().getTexture(id);
        if (texture == null || texture.getGlTexture() == null) return fallback;
        int height = texture.getGlTexture().getHeight(0);
        return height > 0 ? height : fallback;
    }

    private void drawRightContent(DrawContext context, float x, float y, int baseAlpha, float deltaTime, LivingEntity target) {
        float contentX = x + CONTENT_X_OFFSET;
        float contentW = WIDTH - CONTENT_X_OFFSET - 4f;

        float hp     = getHealth(target);
        float maxHp  = target.getMaxHealth();
        float absorp = target.getAbsorptionAmount();
        boolean inv  = target.isInvisible() && !Network.isSpookyTime() && !Network.isCopyTime();

        float targetDisplayHealth = inv ? maxHp : hp + absorp;
        displayedHealth = lerp(displayedHealth, targetDisplayHealth, deltaTime, 5f);
        float snapped   = snapToStep(displayedHealth, 0.25f);

        String hpStr  = getHealthString(target, snapped);
        float  hpStrW = Fonts.TEST.getWidth(hpStr, 5f);

        String name = target.getName().getString();
        float  maxNameWidth = contentW - hpStrW - 4f;
        if (Fonts.TEST.getWidth(name, 5f) > maxNameWidth) {
            while (Fonts.TEST.getWidth(name + "...", 5f) > maxNameWidth && !name.isEmpty()) {
                name = name.substring(0, name.length() - 1);
            }
            name += "...";
        }

        float nameY = y + 5f;
        Fonts.TEST.draw(name, contentX - 3, nameY, 5f, (baseAlpha << 24) | 0xFFFFFF);
        Fonts.TEST.draw(hpStr, x + WIDTH - 4f - hpStrW, nameY, 5f, (baseAlpha << 24) | 0xD7D7D7);

        float barY   = y + HEIGHT - BAR_BOTTOM_MARGIN - BAR_HEIGHT;
        float itemsY = barY - 10f;

        if (target instanceof PlayerEntity player) {
            float lastX = drawItems(context, player, contentX - 1f, itemsY);
            drawWinLose(lastX + 4f, itemsY, snapped, baseAlpha);
        }
    }

    private float drawItems(DrawContext context, PlayerEntity player, float posX, float posY) {
        ItemStack[] items = {
                player.getEquippedStack(EquipmentSlot.HEAD),
                player.getEquippedStack(EquipmentSlot.CHEST),
                player.getEquippedStack(EquipmentSlot.LEGS),
                player.getEquippedStack(EquipmentSlot.FEET),
                player.getMainHandStack(),
                player.getOffHandStack()
        };

        float iconX = posX;
        float scale = 0.5f;
        float pad   = 9f;

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(iconX, posY);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-8.0f, -8.0f);

            context.drawItem(stack, 0, 0);
            context.drawStackOverlay(mc.textRenderer, stack, 0, 0);

            context.getMatrices().popMatrix();

            iconX += pad;
        }
        return iconX;
    }

    private void drawWinLose(float x, float y, float targetHP, int baseAlpha) {
        float myHP = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (Math.abs(myHP - targetHP) < 0.1f) return;

        String text;
        int color;
        if (myHP > targetHP) {
            text  = "WIN";
            color = 0x55FF55;
        } else {
            text  = "LOSE";
            color = 0xFF5555;
        }
        Fonts.TEST.draw(text, x, y - 2.5f, 4.5f, (baseAlpha << 24) | color);
    }
}