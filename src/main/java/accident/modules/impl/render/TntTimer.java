package accident.modules.impl.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import accident.events.api.EventHandler;
import accident.events.impl.DrawEvent;
import accident.events.impl.TickEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.util.math.Projection;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.render.pipeline.LiquidRingPipeline;
import accident.util.render.shader.SkyRenderer;
import accident.util.timer.StopWatch;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// таймер над каждым тнт + опционально ударная волна при взрыве (тот же рефракшн, что у liquid glass jump ring)
public class TntTimer extends ModuleStructure {

    private static final float NEAR_PLANE = 0.05f;

    private final BooleanSetting explosionEffect = new BooleanSetting(
            "accident.module.tnttimer.setting.explosion.name",
            "accident.module.tnttimer.setting.explosion.desc").setValue(true);

    private final SliderSettings blastSize = new SliderSettings(
            "accident.module.tnttimer.setting.blastsize.name",
            "accident.module.tnttimer.setting.blastsize.desc")
            .setValue(9f).range(2f, 25f)
            .visible(explosionEffect::isValue);

    private final SliderSettings blastTime = new SliderSettings(
            "accident.module.tnttimer.setting.blasttime.name",
            "accident.module.tnttimer.setting.blasttime.desc")
            .setValue(900f).range(200f, 2500f)
            .visible(explosionEffect::isValue);

    private final SliderSettings blastStrength = new SliderSettings(
            "accident.module.tnttimer.setting.blaststrength.name",
            "accident.module.tnttimer.setting.blaststrength.desc")
            .setValue(1.4f).range(0.1f, 3.0f)
            .visible(explosionEffect::isValue);

    private final ColorSetting blastTint = new ColorSetting(
            "accident.module.tnttimer.setting.blasttint.name",
            "accident.module.tnttimer.setting.blasttint.desc")
            .value(new Color(255, 180, 120, 90).getRGB())
            .visible(explosionEffect::isValue);

    public TntTimer() {
        super("accident.module.tnttimer.name", "accident.module.tnttimer.desc", ModuleCategory.RENDER);
        settings(explosionEffect, blastSize, blastTime, blastStrength, blastTint);
    }

    private record Blast(Vec3d pos, StopWatch timer) {}

    private final List<Blast> blasts = new ArrayList<>();

    // последняя известная позиция тнт - после исчезновения сущности спросить уже не у кого
    private final Map<Integer, Vec3d> tracked = new HashMap<>();

    private LiquidRingPipeline liquidRing;

    @Override
    public boolean deactivate() {
        blasts.clear();
        tracked.clear();
        if (liquidRing != null) {
            liquidRing.close();
            liquidRing = null;
        }
        return false;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.world == null) return;

        Set<Integer> alive = new HashSet<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof TntEntity tnt)) continue;
            alive.add(tnt.getId());
            tracked.put(tnt.getId(), tnt.getEntityPos());
        }

        // если было в прошлом тике и пропало сейчас - значит взорвалось (или удалили, но это одно и то же для нас)
        Iterator<Map.Entry<Integer, Vec3d>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Vec3d> entry = it.next();
            if (alive.contains(entry.getKey())) continue;

            if (explosionEffect.isValue()) {
                blasts.add(new Blast(entry.getValue(), new StopWatch()));
            }
            it.remove();
        }
    }

    /** The countdown badge, drawn flat on the screen over each TNT. */
    @EventHandler
    public void onDraw(DrawEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof TntEntity tnt)) continue;

            Vec3d above = tnt.getLerpedPos(mc.getRenderTickCounter().getTickProgress(false))
                    .add(0, 0.9, 0);

            Vec3d screen = Projection.worldSpaceToScreenSpace(above);
            if (screen == null || screen.z <= 0 || screen.z >= 1) continue;

            float seconds = tnt.getFuse() / 20f;
            String text = String.format("%.1f", seconds);

            float fontSize = 7f;
            float textW = Fonts.BOLD.getWidth(text, fontSize);
            float boxW = Math.max(textW + 8f, 16f);
            float boxH = 12f;

            float x = (float) screen.x - boxW / 2f;
            float y = (float) screen.y - boxH / 2f;

            // цвет от спокойного к тревожному по мере догорания фитиля
            float urgency = 1f - clamp01(seconds / 4f);
            int r = (int) (120 + 135 * urgency);
            int g = (int) (200 - 150 * urgency);
            int b = (int) (255 - 200 * urgency);

            Render2D.blur(x, y, boxW, boxH, 10f, 4f, new Color(15, 17, 31, 120).getRGB());
            Render2D.outline(x, y, boxW, boxH, 0.8f, new Color(r, g, b, 200).getRGB(), 4f);
            Fonts.BOLD.drawCentered(text, x + boxW / 2f, y + (boxH - fontSize) / 2f + 0.5f,
                    fontSize, new Color(255, 255, 255, 235).getRGB());
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (!explosionEffect.isValue()) {
            blasts.clear();
            return;
        }

        long maxTime = (long) blastTime.getValue();
        blasts.removeIf(blast -> blast.timer().elapsedTime() > maxTime);
        if (blasts.isEmpty()) return;

        renderBlasts();
    }

    // тот же рефракшн-пасс что у jump ring, но от списка взрывов; кольцо растёт почти сразу и затухает к концу жизни
    private void renderBlasts() {
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;

        if (liquidRing == null) liquidRing = new LiquidRingPipeline();

        int count = Math.min(blasts.size(), LiquidRingPipeline.MAX_RINGS);
        float[] rx = new float[count], ry = new float[count], rz = new float[count];
        float[] radius = new float[count], rimWidth = new float[count];
        float[] envelope = new float[count], domeAmp = new float[count];

        float maxTime = blastTime.getValue();
        float footprint = blastSize.getValue() * 0.5f;

        for (int i = 0; i < count; i++) {
            Blast blast = blasts.get(i);
            float progress = Math.min(blast.timer().elapsedTime() / maxTime, 1f);

            // кубический ease-out - как настоящая волна, рост в основном в первые моменты
            float eased = 1f - (float) Math.pow(1f - progress, 3);

            Vec3d pos = blast.pos();
            rx[i] = (float) pos.x;
            ry[i] = (float) pos.y;
            rz[i] = (float) pos.z;

            radius[i] = eased * footprint;
            rimWidth[i] = Math.max(0.08f, footprint * 0.16f);

            float fadeIn = clamp01(progress / 0.06f);
            float fadeOut = clamp01((1f - progress) / 0.55f);
            envelope[i] = fadeIn * fadeOut;

            domeAmp[i] = 0.02f * blastStrength.getValue() * envelope[i];
        }

        Quaternionf rotation = new Quaternionf(camera.getRotation());
        Vector3f camRight = rotation.transform(new Vector3f(1, 0, 0));
        Vector3f camUp = rotation.transform(new Vector3f(0, 1, 0));

        Vec3d camPosD = camera.getCameraPos();
        Vector3f camPos = new Vector3f((float) camPosD.x, (float) camPosD.y, (float) camPosD.z);

        SkyRenderer sky = SkyRenderer.getInstance();
        Matrix4f invViewProj = new Matrix4f(sky.getFrameProjection()).mul(sky.getFrameView()).invert();
        float far = mc.gameRenderer.getFarPlaneDistance();

        int tint = blastTint.getColor();
        float tintR = ((tint >> 16) & 0xFF) / 255f;
        float tintG = ((tint >> 8) & 0xFF) / 255f;
        float tintB = (tint & 0xFF) / 255f;
        float tintAmount = ((tint >>> 24) & 0xFF) / 255f;

        liquidRing.render(
                fb.getColorAttachmentView(),
                fb.getColorAttachment(), fb.getDepthAttachment(),
                fb.textureWidth, fb.textureHeight,
                camPos, NEAR_PLANE, far,
                camRight, camUp,
                invViewProj,
                tintR, tintG, tintB, tintAmount,
                false, 0f, 0f, 0f,
                rx, ry, rz, radius, rimWidth, envelope, domeAmp, count,
                true
        );
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
