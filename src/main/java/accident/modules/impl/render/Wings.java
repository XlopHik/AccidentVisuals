package accident.modules.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import accident.events.api.EventHandler;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;


public class Wings extends ModuleStructure {

    public BooleanSetting self = new BooleanSetting("accident.module.wings.setting.self.name", "accident.module.wings.setting.self.desc").setValue(true);
    public BooleanSetting players = new BooleanSetting("accident.module.wings.setting.players.name", "accident.module.wings.setting.players.desc").setValue(false);
    public SliderSettings size = new SliderSettings("accident.module.wings.setting.size.name", "accident.module.wings.setting.size.desc").range(0.75f, 1.35f).setValue(1.0f);

    private static final WingPoint[] SHAPE = {
            new WingPoint(0.08f,  0.10f,  0.88f),
            new WingPoint(0.28f,  0.34f,  0.78f),
            new WingPoint(0.56f,  0.82f,  0.62f),
            new WingPoint(0.86f,  0.30f,  0.52f),
            new WingPoint(1.14f,  0.46f,  0.40f),
            new WingPoint(1.24f,  0.04f,  0.30f),
            new WingPoint(1.02f, -0.18f,  0.28f),
            new WingPoint(1.18f, -0.64f,  0.22f),
            new WingPoint(0.86f, -0.46f,  0.20f),
            new WingPoint(0.80f, -0.98f,  0.14f),
            new WingPoint(0.54f, -0.74f,  0.16f),
            new WingPoint(0.30f, -1.16f,  0.12f),
            new WingPoint(0.10f, -0.54f,  0.18f)
    };

    private static final float DEFAULT_SPREAD = 8.0f;
    private static final int   DEFAULT_ALPHA  = 220;
    private static final int   BASE_COLOR     = 0xFF808ED7;

    private static final RenderPipeline WINGS_ADDITIVE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{ RenderPipelines.POSITION_COLOR_SNIPPET })
                    .withLocation(Identifier.of("accident", "wings_additive"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderPipeline WINGS_ALPHA = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{ RenderPipelines.POSITION_COLOR_SNIPPET })
                    .withLocation(Identifier.of("accident", "wings_alpha"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderPipeline WINGS_LINES = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{ RenderPipelines.POSITION_COLOR_SNIPPET })
                    .withLocation(Identifier.of("accident", "wings_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderLayer LAYER_ADDITIVE = RenderLayer.of(
            "wings_additive", RenderSetup.builder(WINGS_ADDITIVE).expectedBufferSize(2048).translucent().build());
    private static final RenderLayer LAYER_ALPHA = RenderLayer.of(
            "wings_alpha", RenderSetup.builder(WINGS_ALPHA).expectedBufferSize(2048).translucent().build());
    private static final RenderLayer LAYER_LINES = RenderLayer.of(
            "wings_lines", RenderSetup.builder(WINGS_LINES).expectedBufferSize(1024).translucent().build());

    private float   selfBodyYaw;
    private boolean selfBodyYawInitialized;
    private float   currentTipTilt = 0f;

    public Wings() {
        super("accident.module.wings.name", "accident.module.wings.desc", ModuleCategory.RENDER);
        settings(self, players, size);
    }

    // Проверка на невидимость игрока
    private boolean isPlayerVisible(PlayerEntity player) {
        if (player == null) return false;
        return !player.isInvisible() && !player.isInvisibleTo(mc.player);
    }

    @EventHandler
    public void onRender3D(WorldRenderEvent event) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;

        MatrixStack stack = event.getStack();
        float tickDelta = event.getPartialTicks();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        if (self.isValue() && !mc.options.getPerspective().isFirstPerson()
                && mc.player.isAlive() && !hasElytra(mc.player)) {
            try { renderWings(stack, mc.player, tickDelta, camPos, immediate); } catch (Exception ignored) {}
        }

        if (players.isValue()) {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof PlayerEntity player) || player == mc.player) continue;
                if (!player.isAlive() || hasElytra(player)) continue;
                // ПРОВЕРКА НА НЕВИДИМОСТЬ
                if (!isPlayerVisible(player)) continue;
                try { renderWings(stack, player, tickDelta, camPos, immediate); } catch (Exception ignored) {}
            }
        }

        immediate.draw();
    }

    private boolean hasElytra(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void renderWings(MatrixStack stack, PlayerEntity player, float tickDelta, Vec3d camPos, VertexConsumerProvider.Immediate immediate) {
        // Дополнительная проверка на невидимость внутри метода на всякий случай
        if (player != mc.player && !isPlayerVisible(player)) return;

        Vec3d lerped = player.getLerpedPos(tickDelta);
        double x = lerped.x - camPos.x;
        double y = lerped.y - camPos.y;
        double z = lerped.z - camPos.z;

        float bodyYaw = resolveBodyYaw(player, tickDelta);
        float move = MathHelper.clamp(player.limbAnimator.getAmplitude(tickDelta), 0f, 1f);

        WingPose pose = resolvePose(player, tickDelta);
        if (pose == null) return;

        float flap = (float) Math.sin((player.age + tickDelta) * pose.flapSpeed) * pose.flapAmplitude;
        float open = (DEFAULT_SPREAD + flap + move * pose.motionSpreadBoost) * pose.openMultiplier;
        float wingScale = size.getValue() * pose.scaleMultiplier;

        float limbSpeed = MathHelper.clamp(player.limbAnimator.getSpeed(), 0f, 1f);
        float limbAmount = MathHelper.clamp(player.limbAnimator.getAmplitude(tickDelta), 0f, 1f);

        boolean isMoving = limbAmount > 0.05f;
        boolean isSprinting = player.isSprinting() && limbAmount > 0.3f;

        float targetTipTilt;
        if (isMoving) {
            float speedFactor = MathHelper.clamp(limbSpeed * 1.8f, 0f, 1f);
            targetTipTilt = 5f + speedFactor * 15f;

            if (isSprinting) {
                targetTipTilt += 10f;
            }
        } else {
            float idlePhase = (player.age + tickDelta) * 0.05f;
            targetTipTilt = (float) Math.sin(idlePhase) * 2.5f;
        }

        currentTipTilt = MathHelper.lerp(0.1f, currentTipTilt, targetTipTilt);

        int baseColor = resolveBaseColor();
        int glowColor = resolveGlowColor(baseColor);
        int coreColor = resolveCoreColor(baseColor);
        int outlineColor = baseColor;

        stack.push();
        stack.translate(x, y, z);
        stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        if (pose.preTranslateY != 0f || pose.preTranslateZ != 0f) stack.translate(0f, pose.preTranslateY, pose.preTranslateZ);
        if (pose.pitchRotation != 0f) stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pose.pitchRotation));
        if (pose.rollRotation != 0f) stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(pose.rollRotation));
        stack.translate(0f, pose.anchorY, pose.anchorZ);
        stack.scale(wingScale, wingScale, wingScale);

        renderWingSide(stack, -1f, open, baseColor, glowColor, coreColor, outlineColor, pose, immediate);
        renderWingSide(stack,  1f, open, baseColor, glowColor, coreColor, outlineColor, pose, immediate);
        stack.pop();
    }

    // остальные методы без изменений...
    private void renderWingSide(MatrixStack stack, float side, float open,
                                int baseColor, int glowColor, int coreColor, int outlineColor,
                                WingPose pose, VertexConsumerProvider.Immediate immediate) {
        stack.push();
        stack.translate(side * pose.sideOffset, pose.sideYOffset, pose.sideZOffset);
        stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * open));
        stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(side * pose.sideRoll));
        stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pose.sidePitch));

        drawWingLayer(stack, side, 1.22f,
                setAlpha(glowColor, (int)(DEFAULT_ALPHA * 0.22f)), setAlpha(glowColor, 0),
                LAYER_ADDITIVE, immediate, currentTipTilt);
        drawWingLayer(stack, side, 0.84f,
                setAlpha(coreColor, (int)(DEFAULT_ALPHA * 0.26f)), setAlpha(coreColor, 0),
                LAYER_ADDITIVE, immediate, currentTipTilt);

        drawWingLayer(stack, side, 1.0f,
                setAlpha(baseColor, DEFAULT_ALPHA), setAlpha(baseColor, 10),
                LAYER_ALPHA, immediate, currentTipTilt);

        drawWingOutline(stack, side, 1.0f,
                setAlpha(outlineColor, (int)(DEFAULT_ALPHA * 0.62f)),
                LAYER_LINES, immediate, currentTipTilt);
        drawWingRibs(stack, side, 0.96f,
                setAlpha(glowColor, (int)(DEFAULT_ALPHA * 0.20f)),
                LAYER_LINES, immediate, currentTipTilt);

        stack.pop();
    }

    private void drawWingLayer(MatrixStack stack, float side, float scale,
                               int rootColor, int edgeColor, RenderLayer layer,
                               VertexConsumerProvider.Immediate immediate, float tipTilt) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        VertexConsumer buffer = immediate.getBuffer(layer);

        for (int i = 0; i < SHAPE.length; i++) {
            WingPoint cur  = SHAPE[i];
            WingPoint next = SHAPE[(i + 1) % SHAPE.length];

            vertex(buffer, matrix, 0f, 0f, 0f, rootColor);

            float d1 = Math.min(cur.x / 1.4f, 1.0f);
            float y1 = cur.y - (float) Math.sin(Math.toRadians(tipTilt * d1)) * cur.x * 0.35f;
            vertex(buffer, matrix, side * cur.x * scale, y1 * scale, 0f,
                    applyPointAlpha(edgeColor, cur.alphaMul));

            float d2 = Math.min(next.x / 1.4f, 1.0f);
            float y2 = next.y - (float) Math.sin(Math.toRadians(tipTilt * d2)) * next.x * 0.35f;
            vertex(buffer, matrix, side * next.x * scale, y2 * scale, 0f,
                    applyPointAlpha(edgeColor, next.alphaMul));
        }
    }

    private void drawWingOutline(MatrixStack stack, float side, float scale, int color,
                                 RenderLayer layer, VertexConsumerProvider.Immediate immediate, float tipTilt) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        VertexConsumer buffer = immediate.getBuffer(layer);

        for (WingPoint point : SHAPE) {
            float d = Math.min(point.x / 1.4f, 1.0f);
            float y = point.y - (float) Math.sin(Math.toRadians(tipTilt * d)) * point.x * 0.35f;
            vertex(buffer, matrix, side * point.x * scale, y * scale, 0f, color);
        }
        WingPoint p0 = SHAPE[0];
        vertex(buffer, matrix, side * p0.x * scale, p0.y * scale, 0f, color);
    }

    private void drawWingRibs(MatrixStack stack, float side, float scale, int color,
                              RenderLayer layer, VertexConsumerProvider.Immediate immediate, float tipTilt) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        VertexConsumer buffer = immediate.getBuffer(layer);
        int[] ribIndices = {2, 4, 7, 9, 11};

        for (int idx : ribIndices) {
            WingPoint point = SHAPE[idx];
            float d = Math.min(point.x / 1.4f, 1.0f);
            float y = point.y - (float) Math.sin(Math.toRadians(tipTilt * d)) * point.x * 0.35f;

            vertex(buffer, matrix, 0f, 0f, 0f,
                    setAlpha(color, Math.max(8, (int)(alpha(color) * 0.75f))));
            vertex(buffer, matrix, side * point.x * scale, y * scale, 0f,
                    applyPointAlpha(color, point.alphaMul));
        }
    }

    private int applyPointAlpha(int color, float multiplier) {
        return setAlpha(color, Math.max(0, Math.min(255, (int)(alpha(color) * multiplier))));
    }

    private static int setAlpha(int color, int a) {
        return (MathHelper.clamp(a, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int alpha(int color) { return (color >> 24) & 0xFF; }
    private static int red(int color)   { return (color >> 16) & 0xFF; }
    private static int green(int color) { return (color >>  8) & 0xFF; }
    private static int blue(int color)  { return  color        & 0xFF; }

    private void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, int color) {
        buffer.vertex(matrix, x, y, z)
                .color(red(color), green(color), blue(color), alpha(color));
    }

    private int resolveBaseColor() { return BASE_COLOR; }
    private int resolveGlowColor(int base) { return interpolateColor(base, 0xFFFFFFFF, 0.28f); }
    private int resolveCoreColor(int base) { return interpolateColor(base, 0xFFFFFFFF, 0.55f); }

    private static int interpolateColor(int from, int to, float t) {
        int r = (int) MathHelper.lerp(t, red(from), red(to));
        int g = (int) MathHelper.lerp(t, green(from), green(to));
        int b = (int) MathHelper.lerp(t, blue(from), blue(to));
        int a = (int) MathHelper.lerp(t, alpha(from), alpha(to));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float resolveBodyYaw(PlayerEntity player, float tickDelta) {
        float target = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);
        if (player != mc.player) return target;
        if (!selfBodyYawInitialized || player.age < 2) { selfBodyYaw = target; selfBodyYawInitialized = true; return selfBodyYaw; }
        selfBodyYaw = approachDegrees(selfBodyYaw, target, 14f);
        return selfBodyYaw;
    }

    private static float approachDegrees(float current, float target, float maxDelta) {
        float delta = MathHelper.wrapDegrees(target - current);
        delta = MathHelper.clamp(delta, -maxDelta, maxDelta);
        return current + delta;
    }

    private WingPose resolvePose(PlayerEntity player, float tickDelta) {
        float pitch = MathHelper.lerp(tickDelta, player.lastPitch, player.getPitch());

        if (player.isGliding()) {
            float progress = MathHelper.clamp(0.5f, 0f, 1f);
            float pitchRot = progress * (-90f - pitch);
            return new WingPose(0.34f, 0.46f, 0f, 0f, pitchRot, 0f, 0.76f, 0.92f, 0.10f, 0.58f, 0.05f, 0.06f, -5f, -2f, 0.13f);
        }
        if (player.isTouchingWater()) return null;
        if (player.isSneaking()) {
            return new WingPose(0f, 0f, 0.96f, 0.10f, 18f, 0f, 1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
        }
        return new WingPose(0f, 0f, 1.38f, 0.10f, 0f, 0f, 1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
    }

    @Override
    public boolean deactivate() {
        selfBodyYawInitialized = false;
        currentTipTilt = 0f;
        super.deactivate();
        return false;
    }

    private static final class WingPoint {
        final float x, y, alphaMul;
        WingPoint(float x, float y, float alphaMul) {
            this.x = x; this.y = y; this.alphaMul = alphaMul;
        }
    }

    private static final class WingPose {
        final float preTranslateY, preTranslateZ;
        final float anchorY, anchorZ;
        final float pitchRotation, rollRotation;
        final float openMultiplier, scaleMultiplier;
        final float motionSpreadBoost, flapAmplitude;
        final float sideOffset, sideYOffset, sideZOffset;
        final float sideRoll, sidePitch, flapSpeed;

        WingPose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                 float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                 float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideZOffset,
                 float sideRoll, float sidePitch, float flapSpeed) {
            this(preTranslateY, preTranslateZ, anchorY, anchorZ, pitchRotation, rollRotation,
                    openMultiplier, scaleMultiplier, motionSpreadBoost, flapAmplitude,
                    sideOffset, 0f, sideZOffset, sideRoll, sidePitch, flapSpeed);
        }

        WingPose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                 float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                 float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideYOffset,
                 float sideZOffset, float sideRoll, float sidePitch, float flapSpeed) {
            this.preTranslateY     = preTranslateY;
            this.preTranslateZ     = preTranslateZ;
            this.anchorY           = anchorY;
            this.anchorZ           = anchorZ;
            this.pitchRotation     = pitchRotation;
            this.rollRotation      = rollRotation;
            this.openMultiplier    = openMultiplier;
            this.scaleMultiplier   = scaleMultiplier;
            this.motionSpreadBoost = motionSpreadBoost;
            this.flapAmplitude     = flapAmplitude;
            this.sideOffset        = sideOffset;
            this.sideYOffset       = sideYOffset;
            this.sideZOffset       = sideZOffset;
            this.sideRoll          = sideRoll;
            this.sidePitch         = sidePitch;
            this.flapSpeed         = flapSpeed;
        }
    }
}


