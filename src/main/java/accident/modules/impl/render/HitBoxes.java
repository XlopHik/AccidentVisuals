package accident.modules.impl.render;

import accident.events.api.EventHandler;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;
import accident.util.render.Render3D;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public class HitBoxes extends ModuleStructure {

    public static HitBoxes getInstance() {
        return Instance.get(HitBoxes.class);
    }

    private final ColorSetting color = new ColorSetting("accident.module.hitboxes.setting.color.name", "accident.module.hitboxes.setting.color.desc")
            .value(Color.RED.getRGB());

    private final SliderSettings lineWidth = new SliderSettings("accident.module.hitboxes.setting.linewidth.name", "accident.module.hitboxes.setting.linewidth.desc")
            .range(0.5f, 5.0f)
            .setValue(2.0f);

    public HitBoxes() {
        super("accident.module.hitboxes.name", "accident.module.hitboxes.desc", ModuleCategory.RENDER);
        settings(color, lineWidth);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (mc.world == null || mc.player == null) return;

        int colorRGB = color.getColor();
        float width = lineWidth.getValue();
        float partialTicks = event.getPartialTicks();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (entity.isRemoved()) continue;

            // Получаем интерполированную позицию сущности
            Vec3d interpolatedPos = getInterpolatedPosition(entity, partialTicks);

            // Получаем размеры хитбокса
            Box box = entity.getBoundingBox();
            double widthX = (box.maxX - box.minX) / 2.0;
            double height = box.maxY - box.minY;
            double widthZ = (box.maxZ - box.minZ) / 2.0;

            // Строим интерполированный bounding box
            Box interpolatedBox = new Box(
                    interpolatedPos.x - widthX,
                    interpolatedPos.y,
                    interpolatedPos.z - widthZ,
                    interpolatedPos.x + widthX,
                    interpolatedPos.y + height,
                    interpolatedPos.z + widthZ
            );

            Render3D.drawBox(interpolatedBox, colorRGB, width);
        }
    }

    private Vec3d getInterpolatedPosition(Entity entity, float partialTicks) {
        Vec3d currentPos = entity.getEntityPos();
        Vec3d prevPos = new Vec3d(
                entity.lastRenderX,
                entity.lastRenderY,
                entity.lastRenderZ
        );

        double x = prevPos.x + (currentPos.x - prevPos.x) * partialTicks;
        double y = prevPos.y + (currentPos.y - prevPos.y) * partialTicks;
        double z = prevPos.z + (currentPos.z - prevPos.z) * partialTicks;

        return new Vec3d(x, y, z);
    }
}


