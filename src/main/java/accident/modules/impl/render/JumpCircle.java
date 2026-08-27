package accident.modules.impl.render;

import com.mojang.blaze3d.textures.GpuTexture;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import accident.IMinecraft;
import accident.events.api.EventHandler;
import accident.events.impl.JumpEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.ColorUtil;
import accident.util.render.pipeline.LiquidRingPipeline;
import accident.util.render.shader.SkyRenderer;
import accident.util.render.сliemtpipeline.ClientPipelines;
import accident.util.timer.StopWatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class JumpCircle extends ModuleStructure implements IMinecraft {

    private final List<Circle> circles = new ArrayList<>();

    final Identifier circleTexture = Identifier.of("accident", "images/circle/circle.png");
    final Identifier glowTexture = Identifier.of("accident", "images/particle/glow.png");

    final SliderSettings maxSize = new SliderSettings("accident.module.jumprcircle.setting.maxsize.name", "accident.module.jumprcircle.setting.maxsize.desc")
            .setValue(4.0f).range(1.0f, 15.0f);

    final SliderSettings speed = new SliderSettings("accident.module.jumprcircle.setting.speed.name", "accident.module.jumprcircle.setting.speed.desc")
            .setValue(1500f).range(500f, 5000f);

    final BooleanSetting glow = new BooleanSetting("accident.module.jumprcircle.setting.glow.name", "accident.module.jumprcircle.setting.glow.desc")
            .setValue(true);

    final ColorSetting color1 = new ColorSetting("accident.module.jumprcircle.setting.color1.name", "accident.module.jumprcircle.setting.color1.desc")
            .value(ColorUtil.getColor(100, 200, 255, 255));

    final ColorSetting color2 = new ColorSetting("accident.module.jumprcircle.setting.color2.name", "accident.module.jumprcircle.setting.color2.desc")
            .value(ColorUtil.getColor(255, 255, 255, 255));

    final SelectSetting style = new SelectSetting("accident.module.jumprcircle.setting.style.name", "accident.module.jumprcircle.setting.style.desc")
            .value("Classic", "Liquid Glass")
            .selected("Classic");

    final SliderSettings ringThickness = new SliderSettings("accident.module.jumprcircle.setting.ringthickness.name", "accident.module.jumprcircle.setting.ringthickness.desc")
            .setValue(0.6f).range(0.2f, 2.0f).visible(() -> style.isSelected("Liquid Glass"));

    final ColorSetting glassTint = new ColorSetting("accident.module.jumprcircle.setting.glasstint.name", "accident.module.jumprcircle.setting.glasstint.desc")
            .value(ColorUtil.getColor(160, 210, 255, 255)).visible(() -> style.isSelected("Liquid Glass"));

    final SelectSetting easing = new SelectSetting("accident.module.jumprcircle.setting.easing.name", "accident.module.jumprcircle.setting.easing.desc")
            .value("Bounce", "Elastic", "Back Out", "Linear")
            .selected("Bounce");

    final SliderSettings dome = new SliderSettings("accident.module.jumprcircle.setting.dome.name", "accident.module.jumprcircle.setting.dome.desc")
            .setValue(1.0f).range(0.0f, 3.0f).visible(() -> style.isSelected("Liquid Glass"));

    final BooleanSetting glowColumn = new BooleanSetting("accident.module.jumprcircle.setting.glowcolumn.name", "accident.module.jumprcircle.setting.glowcolumn.desc")
            .setValue(false).visible(() -> style.isSelected("Liquid Glass"));

    final SliderSettings glowColumnHeight = new SliderSettings("accident.module.jumprcircle.setting.glowcolumnheight.name", "accident.module.jumprcircle.setting.glowcolumnheight.desc")
            .setValue(10f).range(2f, 40f).visible(() -> style.isSelected("Liquid Glass") && glowColumn.isValue());

    final SliderSettings glowColumnWidth = new SliderSettings("accident.module.jumprcircle.setting.glowcolumnwidth.name", "accident.module.jumprcircle.setting.glowcolumnwidth.desc")
            .setValue(0.5f).range(0.1f, 1.0f).visible(() -> style.isSelected("Liquid Glass") && glowColumn.isValue());

    final SliderSettings glowColumnAlpha = new SliderSettings("accident.module.jumprcircle.setting.glowcolumnalpha.name", "accident.module.jumprcircle.setting.glowcolumnalpha.desc")
            .setValue(0.5f).range(0.1f, 1.0f).visible(() -> style.isSelected("Liquid Glass") && glowColumn.isValue());

    private static final int SEGMENTS = 64;
    private static final float NEAR_PLANE = 0.05f;

    private LiquidRingPipeline liquidRing;

    public JumpCircle() {
        super("accident.module.jumprcircle.name", "accident.module.jumprcircle.desc", ModuleCategory.RENDER);
        settings(maxSize, speed, glow, color1, color2, style, ringThickness, glassTint,
                easing, dome, glowColumn, glowColumnHeight, glowColumnWidth, glowColumnAlpha);
    }

    @Override
    public boolean deactivate() {
        if (liquidRing != null) { liquidRing.close(); liquidRing = null; }
        return false;
    }

    @EventHandler
    public void onJump(JumpEvent event) {
        if (mc.player == null || event.getPlayer() != mc.player) return;

        Vec3d pos = new Vec3d(
                mc.player.getX(),
                Math.floor(mc.player.getY()) + 0.001,
                mc.player.getZ()
        );
        circles.add(new Circle(pos, new StopWatch()));
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        long maxTime = (long) speed.getValue();

        Iterator<Circle> iterator = circles.iterator();
        while (iterator.hasNext()) {
            Circle circle = iterator.next();
            if (circle.timer.elapsedTime() > maxTime) {
                iterator.remove();
            }
        }

        if (circles.isEmpty()) return;

        if (style.isSelected("Liquid Glass")) {
            renderLiquidRings();
            return;
        }

        MatrixStack matrices = e.getStack();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        for (Circle circle : circles) {
            renderSingleCircle(matrices, immediate, circle, cameraPos);
        }

        immediate.draw();
    }

    private void renderLiquidRings() {
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;

        if (liquidRing == null) liquidRing = new LiquidRingPipeline();

        int width = fb.textureWidth;
        int height = fb.textureHeight;
        float maxTime = speed.getValue();

        int count = Math.min(circles.size(), LiquidRingPipeline.MAX_RINGS);
        float[] rx = new float[count], ry = new float[count], rz = new float[count];
        float[] radius = new float[count], rimWidth = new float[count];
        float[] envelope = new float[count], domeAmp = new float[count];

        // размер пузыря в блоках, толщина кольца считается от него же
        float baseFootprint = maxSize.getValue() * 0.5f;

        for (int i = 0; i < count; i++) {
            Circle circle = circles.get(i);
            float lifeTime = circle.timer.elapsedTime();
            float progress = Math.min(lifeTime / maxTime, 1f);
            float easedProgress = getEasing(progress);

            Vec3d pos = circle.pos();
            rx[i] = (float) pos.x;
            ry[i] = (float) pos.y;
            rz[i] = (float) pos.z;
            radius[i] = easedProgress * baseFootprint;
            rimWidth[i] = Math.max(0.08f, baseFootprint * ringThickness.getValue() * 0.18f);

            // плавное появление первые 10% времени жизни и затухание последние 30%
            float fadeIn = clamp01(progress / 0.1f);
            float fadeOut = clamp01((1f - progress) / 0.3f);
            envelope[i] = fadeIn * fadeOut;

            // смещение преломления в UV, не зависит от разрешения экрана
            domeAmp[i] = 0.02f * dome.getValue() * envelope[i];
        }

        Quaternionf rotation = new Quaternionf(camera.getRotation());
        Vector3f camRight = rotation.transform(new Vector3f(1, 0, 0));
        Vector3f camUp    = rotation.transform(new Vector3f(0, 1, 0));

        Vec3d camPosD = camera.getCameraPos();
        Vector3f camPos = new Vector3f((float) camPosD.x, (float) camPosD.y, (float) camPosD.z);

        SkyRenderer sky = SkyRenderer.getInstance();
        Matrix4f invViewProj = new Matrix4f(sky.getFrameProjection()).mul(sky.getFrameView()).invert();
        float far = mc.gameRenderer.getFarPlaneDistance();

        int tint = glassTint.getColor();
        float tintR = ((tint >> 16) & 0xFF) / 255f;
        float tintG = ((tint >> 8) & 0xFF) / 255f;
        float tintB = (tint & 0xFF) / 255f;
        // альфа цвета = яркость свечения по краю, добавляется поверх сцены
        float tintAmount = ((tint >>> 24) & 0xFF) / 255f;

        liquidRing.render(
                fb.getColorAttachmentView(),
                fb.getColorAttachment(), fb.getDepthAttachment(),
                width, height,
                camPos, NEAR_PLANE, far,
                camRight, camUp,
                invViewProj,
                tintR, tintG, tintB, tintAmount,
                glowColumn.isValue(), glowColumnHeight.getValue(), glowColumnWidth.getValue(), glowColumnAlpha.getValue(),
                rx, ry, rz, radius, rimWidth, envelope, domeAmp, count,
                false
        );
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float elasticOut(float v) {
        if (v <= 0f || v >= 1f) return v;
        return (float) (Math.pow(2.0, -10.0 * v) * Math.sin((v * 10.0 - 0.75) * (2.0 * Math.PI / 3.0)) + 1.0);
    }

    private static float backOut(float v) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float f = v - 1f;
        return 1f + c3 * f * f * f + c1 * f * f;
    }

    private float getEasing(float progress) {
        float p = clamp01(progress);
        return switch (easing.getSelected()) {
            case "Elastic" -> elasticOut(p);
            case "Back Out" -> backOut(p);
            case "Linear" -> p;
            default -> bounceOut(p);
        };
    }

    private void renderSingleCircle(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Circle circle, Vec3d cameraPos) {
        float lifeTime = circle.timer.elapsedTime();
        float maxTime = speed.getValue();
        float progress = Math.min(lifeTime / maxTime, 1f);

        if (progress >= 1f) return;

        float easedProgress = bounceOut(progress);
        float scale = easedProgress * maxSize.getValue();

        float fadeInDuration = 0.15f;
        float glowStart = 0.65f;
        float fadeOutStart = 0.85f;
        float alpha;

        if (progress < fadeInDuration) {
            alpha = progress / fadeInDuration;
        } else if (progress >= fadeOutStart) {
            float fadeOutProgress = (progress - fadeOutStart) / (1f - fadeOutStart);
            alpha = 1f - fadeOutProgress;

            if (progress > glowStart) {
                float glowProgress = (progress - glowStart) / (fadeOutStart - glowStart);
                float glowPulse = (float) (Math.sin(glowProgress * Math.PI * 3) * 0.3 + 0.3);
                alpha += glowPulse * (1f - fadeOutProgress);
            }
        } else if (progress > glowStart) {
            float glowProgress = (progress - glowStart) / (fadeOutStart - glowStart);
            float glowPulse = (float) (Math.sin(glowProgress * Math.PI * 3) * 0.3 + 0.3);
            alpha = 1f + glowPulse;
        } else {
            alpha = 1f;
        }

        alpha = Math.max(0f, Math.min(1f, alpha));

        float rotationOffset = (lifeTime / 1000f) * 0.5f * 360f;

        Vec3d circlePos = circle.pos();

        if (glow.isValue()) {
            renderGradientGlow(matrices, immediate, circlePos, scale, alpha * 0.1f, rotationOffset, cameraPos);
        }

        renderGradientCircle(matrices, immediate, circlePos, scale, alpha, rotationOffset, cameraPos);
    }

    private void renderGradientCircle(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                                      Vec3d pos, float size, float alpha, float rotationOffset, Vec3d cameraPos) {
        VertexConsumer buffer = immediate.getBuffer(ClientPipelines.BLOOM_ESP.apply(circleTexture));

        matrices.push();

        float x = (float) (pos.x - cameraPos.x);
        float y = (float) (pos.y - cameraPos.y);
        float z = (float) (pos.z - cameraPos.z);

        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float radius = size / 2f;

        int c1 = color1.getColor();
        int c2 = color2.getColor();

        for (int i = 0; i < SEGMENTS; i++) {
            float angle1 = (float) (2 * Math.PI * i / SEGMENTS);
            float angle2 = (float) (2 * Math.PI * (i + 1) / SEGMENTS);

            float t = (float) i / SEGMENTS;
            float tNext = (float) (i + 1) / SEGMENTS;

            float adjustedT = (t + rotationOffset / 360f) % 1f;
            float adjustedTNext = (tNext + rotationOffset / 360f) % 1f;

            int currentColor = getGradientColor(c1, c2, adjustedT, alpha);
            int nextColor = getGradientColor(c1, c2, adjustedTNext, alpha);

            float x1 = (float) (Math.cos(angle1) * radius);
            float z1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float z2 = (float) (Math.sin(angle2) * radius);

            float u1 = (float) (0.5 + 0.5 * Math.cos(angle1));
            float v1 = (float) (0.5 + 0.5 * Math.sin(angle1));
            float u2 = (float) (0.5 + 0.5 * Math.cos(angle2));
            float v2 = (float) (0.5 + 0.5 * Math.sin(angle2));

            int centerColor = ColorUtil.lerpColor(currentColor, nextColor, 0.5f);

            buffer.vertex(matrix, 0, 0, 0).texture(0.5f, 0.5f).color(centerColor);
            buffer.vertex(matrix, x1, z1, 0).texture(u1, v1).color(currentColor);
            buffer.vertex(matrix, x2, z2, 0).texture(u2, v2).color(nextColor);
            buffer.vertex(matrix, x2, z2, 0).texture(u2, v2).color(nextColor);
        }

        matrices.pop();
    }

    private void renderGradientGlow(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                                    Vec3d pos, float scale, float alpha, float rotationOffset, Vec3d cameraPos) {
        int c1 = color1.getColor();
        int c2 = color2.getColor();

        for (int layer = 0; layer < 3; layer++) {
            float layerScale = scale * (1.3f + layer * 0.4f);
            float layerAlpha = alpha * (0.35f - layer * 0.1f);

            renderGlowLayer(matrices, immediate, pos, layerScale, layerAlpha, rotationOffset, c1, c2, cameraPos);
        }

        float coreAlpha = alpha * 0.2f;
        int coreColor1 = ColorUtil.multAlpha(c1, coreAlpha);
        int coreColor2 = ColorUtil.multAlpha(c2, coreAlpha);
        int mixedCore = ColorUtil.lerpColor(coreColor1, coreColor2, 0.5f);
        renderTexturedQuad(matrices, immediate, pos, scale * 2.5f, mixedCore, glowTexture, cameraPos);
    }

    private void renderGlowLayer(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                                 Vec3d pos, float size, float alpha, float rotationOffset,
                                 int c1, int c2, Vec3d cameraPos) {
        VertexConsumer buffer = immediate.getBuffer(ClientPipelines.BLOOM_ESP.apply(glowTexture));

        int glowSegments = 16;
        float radius = size / 2f;

        for (int i = 0; i < glowSegments; i++) {
            float angle = (float) (2 * Math.PI * i / glowSegments);
            float t = (float) i / glowSegments;

            float adjustedT = (t + rotationOffset / 360f) % 1f;
            int glowColor = getGradientColor(c1, c2, adjustedT, alpha);

            float glowX = (float) (pos.x + Math.cos(angle) * radius * 0.8f);
            float glowZ = (float) (pos.z + Math.sin(angle) * radius * 0.8f);
            Vec3d glowPos = new Vec3d(glowX, pos.y, glowZ);

            float glowSize = size * 0.4f;
            renderTexturedQuadAtPos(matrices, buffer, glowPos, glowSize, glowColor, cameraPos);
        }
    }

    private void renderTexturedQuadAtPos(MatrixStack matrices, VertexConsumer buffer, Vec3d pos, float size, int color, Vec3d cameraPos) {
        matrices.push();

        float x = (float) (pos.x - cameraPos.x);
        float y = (float) (pos.y - cameraPos.y);
        float z = (float) (pos.z - cameraPos.z);

        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float half = size / 2f;

        buffer.vertex(matrix, -half, -half, 0).texture(0, 0).color(color);
        buffer.vertex(matrix, half, -half, 0).texture(1, 0).color(color);
        buffer.vertex(matrix, half, half, 0).texture(1, 1).color(color);
        buffer.vertex(matrix, -half, half, 0).texture(0, 1).color(color);

        matrices.pop();
    }

    private int getGradientColor(int c1, int c2, float t, float alpha) {
        float gradientT;
        if (t <= 0.5f) {
            gradientT = t * 2f;
        } else {
            gradientT = (1f - t) * 2f;
        }

        int color = ColorUtil.lerpColor(c1, c2, gradientT);
        return ColorUtil.multAlpha(color, alpha);
    }

    private void renderTexturedQuad(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Vec3d pos, float size, int color, Identifier texture, Vec3d cameraPos) {
        VertexConsumer buffer = immediate.getBuffer(ClientPipelines.BLOOM_ESP.apply(texture));

        matrices.push();

        float x = (float) (pos.x - cameraPos.x);
        float y = (float) (pos.y - cameraPos.y);
        float z = (float) (pos.z - cameraPos.z);

        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float half = size / 2f;

        buffer.vertex(matrix, -half, -half, 0).texture(0, 0).color(color);
        buffer.vertex(matrix, half, -half, 0).texture(1, 0).color(color);
        buffer.vertex(matrix, half, half, 0).texture(1, 1).color(color);
        buffer.vertex(matrix, -half, half, 0).texture(0, 1).color(color);

        matrices.pop();
    }

    private float bounceOut(float value) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (value < 1.0f / d1) {
            return n1 * value * value;
        } else if (value < 2.0f / d1) {
            return n1 * (value -= 1.5f / d1) * value + 0.75f;
        } else if (value < 2.5f / d1) {
            return n1 * (value -= 2.25f / d1) * value + 0.9375f;
        } else {
            return n1 * (value -= 2.625f / d1) * value + 0.984375f;
        }
    }

    public record Circle(Vec3d pos, StopWatch timer) {}
}


