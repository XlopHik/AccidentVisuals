package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import accident.events.api.EventHandler;
import accident.events.impl.JumpEvent;
import accident.events.impl.TickEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.ColorUtil;
import accident.util.Instance;
import accident.util.render.сliemtpipeline.ClientPipelines;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class GhostEffect extends ModuleStructure {
    public static GhostEffect getInstance() {
        return Instance.get(GhostEffect.class);
    }

    final List<Ghost> ghosts = Collections.synchronizedList(new ArrayList<>());

    final ColorSetting colorSetting = new ColorSetting("accident.module.ghosteffect.setting.colorsetting.name", "accident.module.ghosteffect.setting.colorsetting.desc")
            .setColor(new Color(255, 255, 255, 100).getRGB());

    final BooleanSetting firstPerson = new BooleanSetting("accident.module.ghosteffect.setting.firstperson.name", "accident.module.ghosteffect.setting.firstperson.desc")
            .setValue(false);

    final SliderSettings spawnDelaySetting = new SliderSettings("accident.module.ghosteffect.setting.spawndelay.name", "accident.module.ghosteffect.setting.spawndelay.desc")
            .setValue(150f)
            .range(0f, 500f);

    final SliderSettings duration = new SliderSettings("accident.module.ghosteffect.setting.duration.name", "accident.module.ghosteffect.setting.duration.desc")
            .setValue(1000f)
            .range(500f, 5000f);

    public GhostEffect() {
        super("accident.module.ghosteffect.name", "accident.module.ghosteffect.desc", ModuleCategory.RENDER);
        settings(colorSetting, firstPerson, spawnDelaySetting, duration);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || !isState()) return;
        synchronized (ghosts) {
            ghosts.removeIf(Ghost::isExpired);
        }
    }

    @EventHandler
    public void onJump(JumpEvent event) {
        if (mc.player == null || !isState()) return;
        if (event.getPlayer() != mc.player) return;
        addGhost(mc.player);
    }

    private void addGhost(AbstractClientPlayerEntity player) {
        Vec3d pos = player.getEntityPos();

        float limbPos = player.limbAnimator.getAnimationProgress();
        float limbSpeed = player.limbAnimator.getSpeed();

        ghosts.add(new Ghost(
                pos,
                player.bodyYaw,
                player.headYaw,
                player.getPitch(),
                limbPos,
                limbSpeed,
                player.handSwingProgress,
                player.forwardSpeed,
                player.sidewaysSpeed,
                player.isSneaking(),
                player.isInSwimmingPose(),
                System.currentTimeMillis(),
                (long) duration.getValue(),
                (long) spawnDelaySetting.getValue()
        ));
    }

    private void renderGhost(MatrixStack matrices, Ghost ghost) {
        float alpha = ghost.getAlpha();
        if (alpha <= 0) return;

        int baseColor = colorSetting.getColor();
        int color = ColorUtil.setAlpha(baseColor, (int) (ColorUtil.getAlpha(baseColor) * alpha));

        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        matrices.push();
        matrices.translate(
                ghost.position.x - camPos.x,
                ghost.position.y - camPos.y,
                ghost.position.z - camPos.z
        );

        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y
                .rotationDegrees(-ghost.bodyYaw + 180f));

        if (ghost.isCrawling) {
            matrices.translate(0, 0.5, 0.3);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X
                    .rotationDegrees(-90f));
        }

        VertexConsumer consumer = immediate.getBuffer(ClientPipelines.GHOSTS_ESP.apply(WHITE_CONCRETE));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        renderGhostModel(matrix, consumer, color, ghost);

        matrices.pop();
        immediate.drawCurrentLayer();
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (ghosts.isEmpty() || mc.world == null) return;
        if (!firstPerson.isValue() && mc.options.getPerspective().isFirstPerson()) return;

        MatrixStack matrices = e.getStack();

        synchronized (ghosts) {
            for (Ghost ghost : ghosts) {
                renderGhost(matrices, ghost);
            }
        }
    }

    private static final Identifier WHITE_CONCRETE =
            Identifier.ofVanilla("textures/block/white_concrete.png");

    private void renderGhostModel(Matrix4f matrix, VertexConsumer consumer, int color, Ghost ghost) {
        float legH = 0.75f;
        float bodyH = 0.75f;
        float headS = 0.5f;

        float swing = ghost.limbPos;
        float amplitude = ghost.limbSpeed * 0.5f;
        float legAngle = (float) Math.sin(swing) * amplitude;
        float armAngle = (float) -Math.sin(swing) * amplitude;

        float moveMagnitude = Math.abs(ghost.forwardSpeed) + Math.abs(ghost.sidewaysSpeed);
        if (moveMagnitude < 0.01f) {
            legAngle = 0f;
            armAngle = 0f;
        }

        float headYawOffset = (ghost.headYaw - ghost.bodyYaw) * ((float) Math.PI / 180f);
        headYawOffset = Math.max(-0.6f, Math.min(0.6f, headYawOffset));

        if (ghost.isCrawling) {
            drawBoxRotatedY(matrix, consumer,
                    -0.25f, legH + bodyH, -0.25f,
                    0.25f, legH + bodyH + headS, 0.25f,
                    0f, legH + bodyH + headS / 2f, 0f,
                    headYawOffset, color);

            drawBox(matrix, consumer,
                    -0.25f, legH, -0.125f,
                    0.25f, legH + bodyH, 0.125f, color);

            drawBoxRotatedX(matrix, consumer,
                    -0.25f, 0f, -0.125f, 0f, legH, 0.125f,
                    -0.125f, legH, 0f, legAngle, color);
            drawBoxRotatedX(matrix, consumer,
                    0f, 0f, -0.125f, 0.25f, legH, 0.125f,
                    0.125f, legH, 0f, -legAngle, color);

            drawBoxRotatedX(matrix, consumer,
                    -0.5f, legH + bodyH - 0.75f, -0.125f,
                    -0.25f, legH + bodyH, 0.125f,
                    -0.375f, legH + bodyH, 0f,
                    armAngle - (float) Math.toRadians(45f), color);
            drawBoxRotatedX(matrix, consumer,
                    0.25f, legH + bodyH - 0.75f, -0.125f,
                    0.5f, legH + bodyH, 0.125f,
                    0.375f, legH + bodyH, 0f,
                    -armAngle - (float) Math.toRadians(45f), color);

        } else if (ghost.isSneaking) {
            float offsetY = -0.2f;
            float bodyTilt = (float) Math.toRadians(20f);

            drawBoxRotatedY(matrix, consumer,
                    -0.25f, legH + bodyH + offsetY, -0.25f,
                    0.25f, legH + bodyH + headS + offsetY, 0.25f,
                    0f, legH + bodyH + headS / 2f + offsetY, 0f,
                    headYawOffset, color);

            drawBoxRotatedX(matrix, consumer,
                    -0.25f, legH + offsetY, -0.125f,
                    0.25f, legH + bodyH + offsetY, 0.125f,
                    0f, legH + bodyH / 2f + offsetY, 0f,
                    bodyTilt, color);

            drawBoxRotatedX(matrix, consumer,
                    -0.25f, 0f, -0.125f, 0f, legH, 0.125f,
                    -0.125f, legH, 0f,
                    legAngle - (float) Math.toRadians(10f), color);
            drawBoxRotatedX(matrix, consumer,
                    0f, 0f, -0.125f, 0.25f, legH, 0.125f,
                    0.125f, legH, 0f,
                    -legAngle - (float) Math.toRadians(10f), color);

            drawBoxRotatedX(matrix, consumer,
                    -0.5f, legH + bodyH - 0.75f + offsetY, -0.125f,
                    -0.25f, legH + bodyH + offsetY, 0.125f,
                    -0.375f, legH + bodyH + offsetY, 0f,
                    armAngle + bodyTilt, color);
            drawBoxRotatedX(matrix, consumer,
                    0.25f, legH + bodyH - 0.75f + offsetY, -0.125f,
                    0.5f, legH + bodyH + offsetY, 0.125f,
                    0.375f, legH + bodyH + offsetY, 0f,
                    -armAngle + bodyTilt, color);

        } else {
            drawBoxRotatedY(matrix, consumer,
                    -0.25f, legH + bodyH, -0.25f,
                    0.25f, legH + bodyH + headS, 0.25f,
                    0f, legH + bodyH + headS / 2f, 0f,
                    headYawOffset, color);

            drawBox(matrix, consumer,
                    -0.25f, legH, -0.125f,
                    0.25f, legH + bodyH, 0.125f, color);

            drawBoxRotatedX(matrix, consumer,
                    -0.25f, 0f, -0.125f, 0f, legH, 0.125f,
                    -0.125f, legH, 0f, legAngle, color);
            drawBoxRotatedX(matrix, consumer,
                    0f, 0f, -0.125f, 0.25f, legH, 0.125f,
                    0.125f, legH, 0f, -legAngle, color);

            drawBoxRotatedX(matrix, consumer,
                    -0.5f, legH + bodyH - 0.75f, -0.125f,
                    -0.25f, legH + bodyH, 0.125f,
                    -0.375f, legH + bodyH, 0f, armAngle, color);
            drawBoxRotatedX(matrix, consumer,
                    0.25f, legH + bodyH - 0.75f, -0.125f,
                    0.5f, legH + bodyH, 0.125f,
                    0.375f, legH + bodyH, 0f, -armAngle, color);
        }
    }

    private void drawBoxRotatedX(Matrix4f matrix, VertexConsumer consumer,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float pivotX, float pivotY, float pivotZ,
                                 float angleRad, int color) {
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        float[][] corners = {
                {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2},
                {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}
        };
        for (float[] c : corners) {
            float dy = c[1] - pivotY, dz = c[2] - pivotZ;
            c[1] = pivotY + dy * cos - dz * sin;
            c[2] = pivotZ + dy * sin + dz * cos;
        }
        drawBoxFromCorners(matrix, consumer, corners, color);
    }

    private void drawBoxRotatedY(Matrix4f matrix, VertexConsumer consumer,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float pivotX, float pivotY, float pivotZ,
                                 float angleRad, int color) {
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        float[][] corners = {
                {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2},
                {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}
        };
        for (float[] c : corners) {
            float dx = c[0] - pivotX, dz = c[2] - pivotZ;
            c[0] = pivotX + dx * cos + dz * sin;
            c[2] = pivotZ - dx * sin + dz * cos;
        }
        drawBoxFromCorners(matrix, consumer, corners, color);
    }

    private void drawBoxFromCorners(Matrix4f matrix, VertexConsumer consumer,
                                    float[][] c, int color) {
        quad(matrix, consumer, c[0], c[1], c[2], c[3], color);
        quad(matrix, consumer, c[7], c[6], c[5], c[4], color);
        quad(matrix, consumer, c[0], c[4], c[5], c[1], color);
        quad(matrix, consumer, c[2], c[6], c[7], c[3], color);
        quad(matrix, consumer, c[3], c[7], c[4], c[0], color);
        quad(matrix, consumer, c[1], c[5], c[6], c[2], color);
    }

    private void drawBox(Matrix4f matrix, VertexConsumer consumer,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         int color) {
        quad(matrix, consumer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, color);
        quad(matrix, consumer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, color);
        quad(matrix, consumer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, color);
        quad(matrix, consumer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, color);
        quad(matrix, consumer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, color);
        quad(matrix, consumer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, color);
    }

    private void quad(Matrix4f matrix, VertexConsumer consumer,
                      float[] p1, float[] p2, float[] p3, float[] p4, int color) {
        consumer.vertex(matrix, p1[0], p1[1], p1[2]).color(color).texture(0f, 0f);
        consumer.vertex(matrix, p2[0], p2[1], p2[2]).color(color).texture(1f, 0f);
        consumer.vertex(matrix, p3[0], p3[1], p3[2]).color(color).texture(1f, 1f);
        consumer.vertex(matrix, p4[0], p4[1], p4[2]).color(color).texture(0f, 1f);
    }

    private void quad(Matrix4f matrix, VertexConsumer consumer,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      int color) {
        consumer.vertex(matrix, x1, y1, z1).color(color).texture(0f, 0f);
        consumer.vertex(matrix, x2, y2, z2).color(color).texture(1f, 0f);
        consumer.vertex(matrix, x3, y3, z3).color(color).texture(1f, 1f);
        consumer.vertex(matrix, x4, y4, z4).color(color).texture(0f, 1f);
    }

    private static class Ghost {
        final Vec3d position;
        final float bodyYaw, headYaw, pitch;
        final float limbPos, limbSpeed;
        final float handSwingProgress;
        final float forwardSpeed, sidewaysSpeed;
        final boolean isSneaking, isCrawling;
        final long spawnTime, lifetime, spawnDelay;

        Ghost(Vec3d position, float bodyYaw, float headYaw, float pitch,
              float limbPos, float limbSpeed, float handSwingProgress,
              float forwardSpeed, float sidewaysSpeed,
              boolean isSneaking, boolean isCrawling,
              long spawnTime, long lifetime, long spawnDelay) {
            this.position = position;
            this.bodyYaw = bodyYaw;
            this.headYaw = headYaw;
            this.pitch = pitch;
            this.limbPos = limbPos;
            this.limbSpeed = limbSpeed;
            this.handSwingProgress = handSwingProgress;
            this.forwardSpeed = forwardSpeed;
            this.sidewaysSpeed = sidewaysSpeed;
            this.isSneaking = isSneaking;
            this.isCrawling = isCrawling;
            this.spawnTime = spawnTime;
            this.lifetime = lifetime;
            this.spawnDelay = spawnDelay;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - spawnTime > lifetime + spawnDelay;
        }

        float getAlpha() {
            long age = System.currentTimeMillis() - spawnTime;
            if (age < spawnDelay) return 0f;
            float progress = (float) (age - spawnDelay) / lifetime;
            return Math.max(0f, Math.min(1f, 1.0f - progress));
        }
    }
}


