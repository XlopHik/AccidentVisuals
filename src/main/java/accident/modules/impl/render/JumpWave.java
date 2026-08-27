package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import accident.events.api.EventHandler;
import accident.events.impl.JumpEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.ColorUtil;
import accident.util.Instance;
import accident.util.render.Render3D;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class JumpWave extends ModuleStructure {
    public static JumpWave getInstance() {
        return Instance.get(JumpWave.class);
    }

    static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JumpWave-BFS");
        t.setDaemon(true);
        return t;
    });

    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}
    };

    final List<WaveEffect> waveEffects = Collections.synchronizedList(new ArrayList<>());

    public SelectSetting typeSetting = new SelectSetting("accident.module.jumpwave.setting.typesetting.name", "accident.module.jumpwave.setting.typesetting.desc")
            .value("Круг", "Квадрат")
            .selected("Круг");

    public ColorSetting colorSetting = new ColorSetting("accident.module.jumpwave.setting.colorsetting.name", "accident.module.jumpwave.setting.colorsetting.desc")
            .setColor(java.awt.Color.CYAN.getRGB());

    public SliderSettings alphaSetting = new SliderSettings("accident.module.jumpwave.setting.alphasetting.name", "accident.module.jumpwave.setting.alphasetting.desc")
            .setValue(180f).range(50f, 255f);

    public SliderSettings durationSetting = new SliderSettings("accident.module.jumpwave.setting.durationsetting.name", "accident.module.jumpwave.setting.durationsetting.desc")
            .setValue(1200f).range(200f, 3000f);

    public SliderSettings speedSetting = new SliderSettings("accident.module.jumpwave.setting.speedsetting.name", "accident.module.jumpwave.setting.speedsetting.desc")
            .setValue(1.0f).range(0.1f, 5.0f);

    public SliderSettings radiusSetting = new SliderSettings("accident.module.jumpwave.setting.radiusssetting.name", "accident.module.jumpwave.setting.radiusssetting.desc")
            .setValue(10f)
            .range(3f, 24f);

    public JumpWave() {
        super("accident.module.jumpwave.name", "accident.module.jumpwave.desc", ModuleCategory.RENDER);
        settings(typeSetting, colorSetting, alphaSetting, durationSetting, speedSetting, radiusSetting);
    }

    @EventHandler
    public void onJump(JumpEvent event) {
        if (mc.player == null || event.getPlayer() != mc.player) return;
        BlockPos pos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.1, mc.player.getZ());
        addWave(pos);
    }

    public void addWave(BlockPos pos) {
        if (mc.world == null || pos == null) return;
        BlockPos groundPos = findGround(pos);
        if (groundPos == null) return;

        final boolean isCircle = typeSetting.isSelected("Круг");
        final int radius = (int) radiusSetting.getValue();

        WaveEffect wave = new WaveEffect(groundPos, System.currentTimeMillis(), radius);
        waveEffects.add(wave);

        final net.minecraft.world.World worldSnapshot = mc.world;
        EXECUTOR.submit(() -> wave.calculateReachableBlocks(worldSnapshot, isCircle));
    }

    private BlockPos findGround(BlockPos pos) {
        for (int y = 0; y <= 5; y++) {
            BlockPos down = pos.down(y);
            if (mc.world.isInBuildLimit(down) && !mc.world.getBlockState(down).isAir()) {
                return down;
            }
        }
        return pos;
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (waveEffects.isEmpty() || mc.world == null) return;

        synchronized (waveEffects) {
            Iterator<WaveEffect> iterator = waveEffects.iterator();
            while (iterator.hasNext()) {
                WaveEffect wave = iterator.next();
                if (wave.isExpired()) {
                    iterator.remove();
                    continue;
                }
                wave.render();
            }
        }
    }

    private class WaveEffect {
        private final BlockPos centerPos;
        private final long startTime;
        private final int maxRadius;
        private final int maxPerFrame = 600;

        private volatile Map<Long, Float> reachableBlocks = null;

        public WaveEffect(BlockPos centerPos, long startTime, int maxRadius) {
            this.centerPos = centerPos;
            this.startTime = startTime;
            this.maxRadius = maxRadius;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - startTime > (long) durationSetting.getValue();
        }

        public void calculateReachableBlocks(net.minecraft.world.World world, boolean isCircle) {
            Map<Long, Float> result = new HashMap<>();

            BlockPos startPos = centerPos;
            if (world.getBlockState(startPos).isAir()) {
                for (int y = 1; y <= 5; y++) {
                    BlockPos down = startPos.down(y);
                    if (!world.getBlockState(down).isAir()) {
                        startPos = down;
                        break;
                    }
                }
            }

            int centerY = startPos.getY();

            for (int x = -maxRadius; x <= maxRadius; x++) {
                for (int z = -maxRadius; z <= maxRadius; z++) {

                    float dist = isCircle
                            ? (float) Math.sqrt(x * x + z * z)
                            : Math.abs(x) + Math.abs(z);

                    if (dist > maxRadius) continue;

                    for (int dy = 3; dy >= -3; dy--) {
                        BlockPos candidate = centerPos.add(x, dy, z);
                        if (!world.isInBuildLimit(candidate)) continue;

                        BlockState state = world.getBlockState(candidate);
                        if (state.isAir()) continue;

                        VoxelShape shape = state.getOutlineShape(world, candidate);
                        if (shape.isEmpty()) continue;

                        BlockPos above = candidate.up();
                        if (!world.getBlockState(above).isAir()) continue;

                        result.put(candidate.asLong(), dist);
                        break;
                    }
                }
            }

            this.reachableBlocks = result;
        }

        public void render() {
            Map<Long, Float> blocks = reachableBlocks;
            if (blocks == null || blocks.isEmpty()) return;
            if (mc.world == null) return;

            long elapsed = System.currentTimeMillis() - startTime;
            float duration = durationSetting.getValue();
            float progress = (float) elapsed / duration;
            float currentRadius = progress * maxRadius * speedSetting.getValue();
            float waveWidth = 2.5f;

            float globalAlpha = (float) Math.pow(1.0f - progress, 0.5);

            int rendered = 0;
            for (Map.Entry<Long, Float> entry : blocks.entrySet()) {
                if (rendered >= maxPerFrame) break;

                float blockDistance = entry.getValue();
                if (blockDistance < currentRadius - waveWidth || blockDistance > currentRadius + 0.5f) continue;

                BlockPos pos = BlockPos.fromLong(entry.getKey());
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir()) continue;

                VoxelShape shape = state.getOutlineShape(mc.world, pos);
                if (shape.isEmpty()) continue;

                rendered++;

                float localAlpha = 1.0f - Math.abs(blockDistance - currentRadius) / waveWidth;
                localAlpha = Math.max(0f, Math.min(1f, localAlpha)) * globalAlpha;

                if (localAlpha > 0.02f) {
                    int alphaValue = (int) (localAlpha * alphaSetting.getValue());
                    int color = ColorUtil.setAlpha(colorSetting.getColor(), alphaValue);
                    try {
                        Render3D.drawShapeAlternative(pos, shape, color, 1f, true, true);
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}


