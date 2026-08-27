package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import accident.events.api.EventHandler;
import accident.events.impl.TickEvent;
import accident.events.impl.WorldRenderEvent;
import accident.mixin.WorldRendererAccessor;
import accident.modules.impl.render.particles.BaseParticle;
import accident.modules.impl.render.particles.ParticlePool;
import accident.modules.impl.render.worldparticles.Particle;
import accident.modules.impl.render.worldparticles.ParticleRenderer;
import accident.modules.impl.render.worldparticles.ParticleSpawner;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;
import accident.util.timer.StopWatch;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorldParticles extends ModuleStructure {

    public static WorldParticles getInstance() { return Instance.get(WorldParticles.class); }

    final ParticlePool pool = new ParticlePool(500);
    final StopWatch timer   = new StopWatch();

    Vec3d lastPlayerPos  = Vec3d.ZERO;
    Vec3d playerVelocity = Vec3d.ZERO;
    double playerSpeed   = 0;
    boolean hasLastPos   = false;

    Particle.ParticleType cachedParticleType = null;

    public SelectSetting mode = new SelectSetting("accident.module.worldparticles.setting.mode.name", "accident.module.worldparticles.setting.mode.desc")
            .value("3D Кубы", "Корона", "Куб", "Доллар", "Сердце", "Молния", "Линия",
                    "Ромб", "Снежинка", "Звезда", "Звезда 2", "Треугольник", "Свечение", "Рандом")
            .selected("Звезда");

    public SliderSettings cubeCount = new SliderSettings("accident.module.worldparticles.setting.cubecount.name", "accident.module.worldparticles.setting.cubecount.desc")
            .range(1, 200).setValue(50);

    public SliderSettings lifeTime = new SliderSettings("accident.module.worldparticles.setting.lifetime.name", "accident.module.worldparticles.setting.lifetime.desc")
            .range(1, 30).setValue(5);

    public SliderSettings size = new SliderSettings("accident.module.worldparticles.setting.size.name", "accident.module.worldparticles.setting.size.desc")
            .range(0.1f, 3.0f).setValue(0.5f);

    public SliderSettings glowSize = new SliderSettings("accident.module.worldparticles.setting.glowsize.name", "accident.module.worldparticles.setting.glowsize.desc")
            .range(0.0f, 2.0f).setValue(0.3f);

    public BooleanSetting physics = new BooleanSetting("accident.module.worldparticles.setting.physics.name", "accident.module.worldparticles.setting.physics.desc")
            .setValue(false);

    public BooleanSetting randomColor = new BooleanSetting("accident.module.worldparticles.setting.randomcolor.name", "accident.module.worldparticles.setting.randomcolor.desc")
            .setValue(false);

    public BooleanSetting whiteOnSpawn = new BooleanSetting("accident.module.worldparticles.setting.whiteonspawn.name", "accident.module.worldparticles.setting.whiteonspawn.desc")
            .setValue(false);

    public BooleanSetting whiteCenter = new BooleanSetting("accident.module.worldparticles.setting.whitecenter.name", "accident.module.worldparticles.setting.whitecenter.desc")
            .setValue(false);

    public ColorSetting cubeColor = new ColorSetting("accident.module.worldparticles.setting.cubecolor.name", "accident.module.worldparticles.setting.cubecolor.desc")
            .value(0xFF896148)
            .visible(() -> !randomColor.isValue());

    public WorldParticles() {
        super("accident.module.worldparticles.name", "accident.module.worldparticles.desc", ModuleCategory.RENDER);
        settings(mode, cubeCount, lifeTime, size, glowSize, physics, randomColor, whiteOnSpawn, whiteCenter, cubeColor);
        mode.onChange(() -> cachedParticleType = null);
    }

    @Override
    public boolean deactivate() {
        pool.clear();
        lastPlayerPos  = Vec3d.ZERO;
        playerVelocity = Vec3d.ZERO;
        playerSpeed    = 0;
        hasLastPos     = false;
        return false;
    }

    private Particle.ParticleType getParticleType() {
        if (cachedParticleType == null) {
            cachedParticleType = switch (mode.getSelected()) {
                case "3D Кубы"    -> Particle.ParticleType.CUBE_3D;
                case "Корона"     -> Particle.ParticleType.CROWN;
                case "Куб"        -> Particle.ParticleType.CUBE_BLAST;
                case "Доллар"     -> Particle.ParticleType.DOLLAR;
                case "Сердце"     -> Particle.ParticleType.HEART;
                case "Молния"     -> Particle.ParticleType.LIGHTNING;
                case "Линия"      -> Particle.ParticleType.LINE;
                case "Ромб"       -> Particle.ParticleType.RHOMBUS;
                case "Снежинка"   -> Particle.ParticleType.SNOWFLAKE;
                case "Звезда"     -> Particle.ParticleType.STAR;
                case "Звезда 2"   -> Particle.ParticleType.STAR_ALT;
                case "Треугольник"-> Particle.ParticleType.TRIANGLE;
                case "Свечение"   -> Particle.ParticleType.GLOW;
                case "Рандом"     -> Particle.ParticleType.RANDOM;
                default           -> Particle.ParticleType.CUBE_3D;
            };
        }
        return cachedParticleType;
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        Vec3d currentPos = mc.player.getEntityPos();

        if (hasLastPos) {
            playerVelocity = currentPos.subtract(lastPlayerPos);
            playerSpeed    = playerVelocity.horizontalLength();
        }
        lastPlayerPos = currentPos;
        hasLastPos    = true;

        long now = System.currentTimeMillis();

        pool.forEachActive((index, p) -> {
            p.update(now);
            if (p.shouldRemove()) pool.release(index);
        });

        double despawnDistSq = ParticleSpawner.getDespawnDistanceSquared();
        pool.forEachActive((index, p) -> {
            if (!p.isFadingOut() && p.getHorizontalDistanceSquaredTo(currentPos) > despawnDistSq) {
                p.startFadeOut();
            }
        });

        int actualDelay = ParticleSpawner.calculateSpawnDelay(playerSpeed);
        if (pool.getActiveCount() < (int) cubeCount.getValue() && timer.finished(actualDelay)) {
            int spawnCount    = ParticleSpawner.calculateSpawnCount(playerSpeed, pool.getActiveCount(), (int) cubeCount.getValue());
            long lifeTimeMs   = (long) (lifeTime.getValue() * 1000);
            Particle.ParticleType type = getParticleType();

            for (int i = 0; i < spawnCount && pool.getActiveCount() < (int) cubeCount.getValue(); i++) {
                int slot = pool.acquire();
                if (slot == -1) break;

                Particle p = pool.get(slot);
                ParticleSpawner.resetParticle(p, currentPos, playerVelocity, playerSpeed,
                        lifeTimeMs, type, physics.isValue(), size.getValue());
            }

            timer.reset();
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (pool.getActiveCount() == 0) return;

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices    = e.getStack();
        Vec3d cameraPos         = mc.gameRenderer.getCamera().getCameraPos();
        float partialTicks      = mc.getRenderTickCounter().getTickProgress(true);
        long now                = System.currentTimeMillis();
        float globalRotation    = (float) (now % 9000L) / 9000f * 360f;
        float cameraYaw         = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch       = mc.gameRenderer.getCamera().getPitch();
        int baseColor           = cubeColor.getColor();
        float glow              = glowSize.getValue();
        boolean useRandomColor  = randomColor.isValue();
        boolean useWhiteOnSpawn = whiteOnSpawn.isValue();
        boolean useWhiteCenter  = whiteCenter.isValue();

        Frustum frustum = ((WorldRendererAccessor) mc.worldRenderer).getCapturedFrustum();

        double renderDistSq = 150.0 * 150.0;

        pool.forEachActive(p -> {
            if (p.getDistanceSquaredTo(cameraPos) > renderDistSq) return;

            if (frustum != null) {
                Box box = new Box(p.x - 2, p.y - 2, p.z - 2, p.x + 2, p.y + 2, p.z + 2);
                if (!frustum.isVisible(box)) return;
            }

            p.render(matrices, immediate, cameraPos, baseColor, globalRotation,
                    cameraYaw, cameraPitch, glow, useRandomColor, useWhiteOnSpawn, useWhiteCenter, partialTicks);
        });

        immediate.draw(ParticleRenderer.getQuadsLayer());
        immediate.draw(ParticleRenderer.getLinesLayer());
        immediate.draw(ParticleRenderer.getGlowLayer());
        immediate.draw(ParticleRenderer.getGlowLayerSecondary());
        immediate.draw(BaseParticle.LAYER_BLOOM);
        immediate.draw(BaseParticle.LAYER_SAMPLE);
    }
}


