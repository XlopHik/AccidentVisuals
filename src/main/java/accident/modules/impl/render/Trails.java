package accident.modules.impl.render;

import accident.events.api.EventHandler;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;
import accident.util.render.Render3D;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Trails extends ModuleStructure {

    public static Trails getInstance() {
        return Instance.get(Trails.class);
    }

    private final SelectSetting mode = new SelectSetting("accident.module.trails.setting.mode.name", "accident.module.trails.setting.mode.desc")
            .value("Cubes", "Line")
            .selected("Cubes");

    private final BooleanSetting firstPerson = new BooleanSetting("accident.module.trails.setting.firstperson.name", "accident.module.trails.setting.firstperson.desc")
            .setValue(false);

    private final ColorSetting color = new ColorSetting("accident.module.trails.setting.color.name", "accident.module.trails.setting.color.desc")
            .value(new Color(0, 200, 255, 200).getRGB());

    private final SliderSettings length = new SliderSettings("accident.module.trails.setting.length.name", "accident.module.trails.setting.length.desc")
            .range(5, 100)
            .setValue(30);

    private final SliderSettings standTime = new SliderSettings("accident.module.trails.setting.standtime.name", "accident.module.trails.setting.standtime.desc")
            .range(0.2f, 3.0f)
            .setValue(1.0f);

    private final SliderSettings size = new SliderSettings("accident.module.trails.setting.size.name", "accident.module.trails.setting.size.desc")
            .range(0.1f, 3.0f)
            .setValue(0.8f)
            .visible(() -> mode.isSelected("Cubes"));

    private final SliderSettings height = new SliderSettings("accident.module.trails.setting.height.name", "accident.module.trails.setting.height.desc")
            .range(0.5f, 5.0f)
            .setValue(2.0f)
            .visible(() -> mode.isSelected("Line"));

    private final List<TrailPoint> trailPoints = new ArrayList<>();
    private Vec3d lastPos = null;

    public Trails() {
        super("accident.module.trails.name", "accident.module.trails.desc", ModuleCategory.RENDER);
        settings(mode, firstPerson, color, length, standTime, size, height);
    }

    @Override
    public boolean deactivate() {
        trailPoints.clear();
        lastPos = null;
        return false;
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Если от 1 лица рендер выключен и камера от первого лица — пропускаем рендер
        if (!firstPerson.isValue() && !mc.gameRenderer.getCamera().isThirdPerson()) return;

        Vec3d velocity = mc.player.getVelocity();
        double horizontalSpeed = velocity.x * velocity.x + velocity.z * velocity.z;
        boolean isMoving = horizontalSpeed > 0.0001;

        Vec3d currentPos = mc.player.getEntityPos();

        if (isMoving) {
            if (lastPos == null || currentPos.distanceTo(lastPos) > 0.2) {
                trailPoints.add(0, new TrailPoint(currentPos));
                lastPos = currentPos;

                int maxLen = (int) length.getValue();
                while (trailPoints.size() > maxLen) {
                    trailPoints.remove(trailPoints.size() - 1);
                }
            }
        } else {
            lastPos = currentPos;
        }

        long currentTime = System.currentTimeMillis();
        if (!isMoving) {
            float maxLifetime = standTime.getValue() * 1000f;
            Iterator<TrailPoint> iterator = trailPoints.iterator();
            while (iterator.hasNext()) {
                TrailPoint point = iterator.next();
                if (currentTime - point.creationTime > maxLifetime) {
                    iterator.remove();
                }
            }
        }

        int baseColor = color.getColor();
        double baseSize = size.getValue();
        double baseHeight = height.getValue();

        if (mode.isSelected("Cubes")) {
            renderCubes(baseColor, baseSize, isMoving, currentTime);
        } else if (mode.isSelected("Line")) {
            renderLine(baseColor, baseHeight, isMoving, currentTime);
        }
    }

    private void renderCubes(int baseColor, double baseSize, boolean isMoving, long currentTime) {
        for (int i = 1; i < trailPoints.size(); i++) {
            TrailPoint point = trailPoints.get(i);
            Vec3d pos = point.position;

            float alpha;
            if (isMoving) {
                float progress = (float) i / (float) Math.max(1, trailPoints.size() - 1);
                alpha = 1.0f - progress;
            } else {
                float maxAge = standTime.getValue() * 1000f;
                float age = (currentTime - point.creationTime) / maxAge;
                alpha = 1.0f - age;
            }

            if (i == 1) {
                alpha *= 0.5f;
            }

            if (alpha <= 0.01f) continue;

            float scale = 1.0f - (alpha > 0.5f ? 0.0f : (1.0f - alpha * 2) * 0.7f);
            int finalColor = applyAlpha(baseColor, alpha);
            double size = baseSize * scale;
            double halfSize = size / 2.0;

            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                    pos.x - halfSize, pos.y, pos.z - halfSize,
                    pos.x + halfSize, pos.y + size, pos.z + halfSize
            );

            Render3D.drawBox(box, finalColor, 1.5f);
        }
    }

    private void renderLine(int baseColor, double baseHeight, boolean isMoving, long currentTime) {
        if (trailPoints.size() < 3) return;

        for (int i = 1; i < trailPoints.size() - 1; i++) {
            TrailPoint point1 = trailPoints.get(i);
            TrailPoint point2 = trailPoints.get(i + 1);

            Vec3d p1 = point1.position;
            Vec3d p2 = point2.position;

            float alpha1, alpha2;
            if (isMoving) {
                float progress1 = (float) i / (float) Math.max(1, trailPoints.size() - 1);
                float progress2 = (float) (i + 1) / (float) Math.max(1, trailPoints.size() - 1);
                alpha1 = (1.0f - progress1) * 0.85f;
                alpha2 = (1.0f - progress2) * 0.85f;
            } else {
                float maxAge = standTime.getValue() * 1000f;
                float age1 = (currentTime - point1.creationTime) / maxAge;
                float age2 = (currentTime - point2.creationTime) / maxAge;
                alpha1 = (1.0f - age1) * 0.85f;
                alpha2 = (1.0f - age2) * 0.85f;
            }

            if (i == 1) {
                alpha1 *= 0.5f;
            }

            if (alpha1 <= 0.01f && alpha2 <= 0.01f) continue;

            int cTop1 = applyAlpha(baseColor, alpha1);
            int cTop2 = applyAlpha(baseColor, alpha2);
            int cBot1 = applyAlpha(baseColor, alpha1 * 0.08f);
            int cBot2 = applyAlpha(baseColor, alpha2 * 0.08f);

            Vec3d p1T = p1.add(0, baseHeight, 0);
            Vec3d p2T = p2.add(0, baseHeight, 0);

            Render3D.drawTrailQuad(p1, p2, p2T, p1T, cBot1, cBot2, cTop2, cTop1);
            Render3D.drawTrailQuad(p1T, p2T, p2, p1, cTop1, cTop2, cBot2, cBot1);
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.min(255, Math.max(0, (int) (255 * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static class TrailPoint {
        Vec3d position;
        long creationTime;

        TrailPoint(Vec3d pos) {
            this.position = pos;
            this.creationTime = System.currentTimeMillis();
        }
    }
}


