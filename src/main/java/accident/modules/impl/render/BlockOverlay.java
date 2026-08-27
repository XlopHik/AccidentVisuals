package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import accident.events.api.EventHandler;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;

import accident.util.Instance;
import accident.util.render.Render3D;
import java.awt.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlockOverlay extends ModuleStructure {
    public static BlockOverlay getInstance() {
        return Instance.get(BlockOverlay.class);
    }

    double animX, animY, animZ;
    boolean hasTarget = false;

    public BlockOverlay() {
        super("accident.module.blockoverlay.name", "accident.module.blockoverlay.desc", ModuleCategory.RENDER);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (!(mc.crosshairTarget instanceof BlockHitResult result) || !result.getType().equals(HitResult.Type.BLOCK)) {
            hasTarget = false;
            return;
        }

        BlockPos pos = result.getBlockPos();
        double targetX = pos.getX();
        double targetY = pos.getY();
        double targetZ = pos.getZ();

        if (!hasTarget) {
            animX = targetX;
            animY = targetY;
            animZ = targetZ;
            hasTarget = true;
        }

        float speed = 0.4f - (float) Math.pow(0.01f, e.getPartialTicks());
        animX += (targetX - animX) * speed;
        animY += (targetY - animY) * speed;
        animZ += (targetZ - animZ) * speed;

        // Создаём виртуальный BlockPos на основе анимированной позиции
        // и рисуем форму реального блока но со смещением
        var shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;

        // Смещение от реального pos к анимированной позиции
        double offX = animX - pos.getX();
        double offY = animY - pos.getY();
        double offZ = animZ - pos.getZ();

        Vec3d vec3d = Vec3d.of(pos).add(offX, offY, offZ);

        // Рисуем линии со смещением
        int color = new Color(109, 252, 255, 230).getRGB();
        float width = 1.5f;

        shape.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) -> {
            Render3D.drawLine(
                    vec3d.add(minX, minY, minZ),
                    vec3d.add(maxX, maxY, maxZ),
                    color, width, false
            );
        });

        // Заливка
        int fillColor = new Color(109, 252, 255, 30).getRGB();
        shape.getBoundingBoxes().forEach(box -> {
            var offsetBox = box.offset(vec3d.x, vec3d.y, vec3d.z);
            Render3D.drawBox(offsetBox, fillColor, width, false, true, false);
        });
    }
}


