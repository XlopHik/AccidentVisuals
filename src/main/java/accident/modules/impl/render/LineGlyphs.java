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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import accident.events.api.EventHandler;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.ColorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class LineGlyphs extends ModuleStructure {

    public BooleanSetting glowing = new BooleanSetting("accident.module.lineglyphs.setting.glowing.name", "accident.module.lineglyphs.setting.glowing.desc").setValue(true);

    public SliderSettings radius   = new SliderSettings("accident.module.lineglyphs.setting.radius.name", "accident.module.lineglyphs.setting.radius.desc")
            .range(3.0f, 20.0f).setValue(10.0f);

    public SliderSettings distance = new SliderSettings("accident.module.lineglyphs.setting.distance.name", "accident.module.lineglyphs.setting.distance.desc")
            .range(10.0f, 100.0f).setValue(50.0f);

    public SliderSettings lineSpd  = new SliderSettings("accident.module.lineglyphs.setting.linespd.name", "accident.module.lineglyphs.setting.linespd.desc")
            .range(0.01f, 0.5f).setValue(0.07f);

    private static final int BASE_COLOR = 0xFF808ED7;
    private static final int GLOW_COLOR = 0x40808ED7; // Полупрозрачный для свечения

    private static final int LINE_COUNT  = 120;
    private static final int LINE_LENGTH = 17;

    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{ RenderPipelines.POSITION_COLOR_SNIPPET })
                    .withLocation(Identifier.of("accident", "lineglyphs_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderLayer LINES_LAYER = RenderLayer.of(
            "lineglyphs_lines",
            RenderSetup.builder(LINES_PIPELINE).expectedBufferSize(1024).translucent().build());

    private final List<GridLine> lines = new ArrayList<>();
    private final Random random = new Random();

    public LineGlyphs() {
        super("accident.module.lineglyphs.name", "accident.module.lineglyphs.desc", ModuleCategory.RENDER);
        settings(glowing, radius, distance, lineSpd);
    }

    @Override
    public boolean activate() {
        generateLines();
        return super.activate();
    }

    @Override
    public boolean deactivate() {
        lines.clear();
        return super.deactivate();
    }

    @EventHandler
    public void onRender3D(WorldRenderEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float r = radius.getValue();
        float spd = lineSpd.getValue();

        int linesToSpawn = LINE_COUNT - lines.size();

        Iterator<GridLine> iterator = lines.iterator();
        while (iterator.hasNext()) {
            GridLine line = iterator.next();
            line.update(spd, LINE_LENGTH);
            if (line.shouldRespawn(playerPos, r)) {
                iterator.remove();
                linesToSpawn++;
            }
        }

        for (int i = 0; i < linesToSpawn; i++) {
            lines.add(new GridLine(playerPos, r));
        }

        renderLines(event.getStack());
    }

    private void renderLines(MatrixStack matrices) {
        if (lines.isEmpty() || mc.gameRenderer == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        float maxDist = distance.getValue();

        VertexConsumerProvider.Immediate immediate =
                mc.getBufferBuilders().getEntityVertexConsumers();

        for (GridLine line : lines) {
            List<Vec3d> pts = line.points;
            if (pts.size() < 2) continue;

            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3d start = pts.get(i);
                Vec3d end = pts.get(i + 1);

                if (start.distanceTo(camPos) > maxDist || end.distanceTo(camPos) > maxDist) continue;

                float segmentAlpha = (float) (i + 1) / pts.size();
                int segmentColor = ColorUtil.replAlpha(BASE_COLOR, segmentAlpha);
                int glowSegmentColor = ColorUtil.replAlpha(GLOW_COLOR, segmentAlpha * 0.5f);

                // Рисуем основную линию
                drawLine(matrices, immediate, start, end, segmentColor, camPos);

                // Если свечение включено - рисуем более толстую полупрозрачную линию поверх
                if (glowing.isValue()) {
                    drawGlowLine(matrices, immediate, start, end, glowSegmentColor, camPos);
                }
            }
        }

        immediate.draw();
    }

    private void drawLine(MatrixStack matrices,
                          VertexConsumerProvider.Immediate immediate,
                          Vec3d start, Vec3d end,
                          int color, Vec3d camPos) {
        VertexConsumer buffer = immediate.getBuffer(LINES_LAYER);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        buffer.vertex(matrix,
                (float) (start.x - camPos.x),
                (float) (start.y - camPos.y),
                (float) (start.z - camPos.z)).color(r, g, b, a);
        buffer.vertex(matrix,
                (float) (end.x - camPos.x),
                (float) (end.y - camPos.y),
                (float) (end.z - camPos.z)).color(r, g, b, a);
    }

    // Метод для отрисовки свечения (немного толще и размытее)
    private void drawGlowLine(MatrixStack matrices,
                              VertexConsumerProvider.Immediate immediate,
                              Vec3d start, Vec3d end,
                              int color, Vec3d camPos) {
        VertexConsumer buffer = immediate.getBuffer(LINES_LAYER);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int)((color >> 24) & 0xFF) * 2; // Делаем свечение более ярким

        // Рисуем линию дважды с небольшим смещением для эффекта свечения
        for (float offset = -0.03f; offset <= 0.03f; offset += 0.03f) {
            buffer.vertex(matrix,
                    (float) (start.x - camPos.x) + offset,
                    (float) (start.y - camPos.y) + offset,
                    (float) (start.z - camPos.z)).color(r, g, b, a);
            buffer.vertex(matrix,
                    (float) (end.x - camPos.x) + offset,
                    (float) (end.y - camPos.y) + offset,
                    (float) (end.z - camPos.z)).color(r, g, b, a);
        }
    }

    private void generateLines() {
        lines.clear();
        if (mc.player == null) return;
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float r = radius.getValue();
        for (int i = 0; i < LINE_COUNT; i++) {
            lines.add(new GridLine(playerPos, r));
        }
    }

    private class GridLine {
        final List<Vec3d> points = new ArrayList<>();
        final Random random = new Random();

        double gridX, gridY, gridZ;
        double nextX, nextY, nextZ;
        int dirX, dirY, dirZ;
        double progress = 0;

        GridLine(Vec3d playerPos, float radius) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double r = radius * (0.7 + random.nextDouble() * 0.6);

            gridX = Math.floor(playerPos.x + Math.cos(angle) * r) + 0.5;
            gridZ = Math.floor(playerPos.z + Math.sin(angle) * r) + 0.5;
            gridY = Math.floor(playerPos.y + 2.0 + random.nextDouble() * 8.0) + 0.5;

            chooseDir();
            nextX = gridX + dirX;
            nextY = gridY + dirY;
            nextZ = gridZ + dirZ;

            points.add(new Vec3d(gridX, gridY, gridZ));
        }

        void update(float speed, int maxLength) {
            if (points.isEmpty()) return;

            progress += speed;

            double t = Math.min(progress, 1.0);
            double ix = gridX + (nextX - gridX) * t;
            double iy = gridY + (nextY - gridY) * t;
            double iz = gridZ + (nextZ - gridZ) * t;
            points.add(new Vec3d(ix, iy, iz));

            if (progress >= 1.0) {
                progress -= 1.0;

                gridX = nextX;
                gridY = nextY;
                gridZ = nextZ;

                if (random.nextInt(3) == 0) chooseDir();

                nextX = gridX + dirX;
                nextY = gridY + dirY;
                nextZ = gridZ + dirZ;
            }

            while (points.size() > maxLength * 6) {
                points.remove(0);
            }
        }

        boolean shouldRespawn(Vec3d playerPos, float radius) {
            if (points.isEmpty()) return true;
            return points.get(points.size() - 1).distanceTo(playerPos) > radius + 8.0;
        }

        private void chooseDir() {
            int[][] dirs = {
                    {1, 0, 0}, {-1, 0, 0},
                    {0, 1, 0}, {0, -1, 0},
                    {0, 0, 1}, {0, 0, -1}
            };
            List<int[]> valid = new ArrayList<>();
            for (int[] d : dirs) {
                if (d[0] == -dirX && d[1] == -dirY && d[2] == -dirZ) continue;
                valid.add(d);
            }
            int[] chosen = valid.get(random.nextInt(valid.size()));
            dirX = chosen[0];
            dirY = chosen[1];
            dirZ = chosen[2];
        }
    }
}


