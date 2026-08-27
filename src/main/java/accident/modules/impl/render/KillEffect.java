package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import accident.Initialization;
import accident.events.api.EventHandler;
import accident.events.impl.AttackEvent;
import accident.events.impl.CameraEvent;
import accident.events.impl.DrawEvent;
import accident.events.impl.FovEvent;
import accident.events.impl.TickEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.ColorUtil;
import accident.util.Instance;
import accident.util.render.Render3D;
import accident.util.sounds.SoundManager;
import accident.util.timer.TickTimer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class KillEffect extends ModuleStructure {

    public static KillEffect getInstance() { return Instance.get(KillEffect.class); }

    final BooleanSetting noMobs = new BooleanSetting("accident.module.killeffect.setting.nomobs.name", "accident.module.killeffect.setting.nomobs.desc")
            .setValue(true);

    final BooleanSetting playSound = new BooleanSetting("accident.module.killeffect.setting.playsound.name", "accident.module.killeffect.setting.playsound.desc")
            .setValue(true);

    final BooleanSetting showVignette = new BooleanSetting("accident.module.killeffect.setting.showvignette.name", "accident.module.killeffect.setting.showvignette.desc")
            .setValue(true);

    final BooleanSetting showParticles = new BooleanSetting("accident.module.killeffect.setting.showparticles.name", "accident.module.killeffect.setting.showparticles.desc")
            .setValue(true);

    final BooleanSetting slowTime = new BooleanSetting("accident.module.killeffect.setting.slowtime.name", "accident.module.killeffect.setting.slowtime.desc")
            .setValue(true);

    final BooleanSetting fovZoom = new BooleanSetting("accident.module.killeffect.setting.fovzoom.name", "accident.module.killeffect.setting.fovzoom.desc")
            .setValue(true);

    final SliderSettings fovStrength = new SliderSettings("accident.module.killeffect.setting.fovstrength.name", "accident.module.killeffect.setting.fovstrength.desc")
            .setValue(15f).range(5f, 40f);

    final BooleanSetting cameraSnap = new BooleanSetting("accident.module.killeffect.setting.camerasnap.name", "accident.module.killeffect.setting.camerasnap.desc")
            .setValue(false);

    final SliderSettings vignetteAlpha = new SliderSettings("accident.module.killeffect.setting.vignettealpha.name", "accident.module.killeffect.setting.vignettealpha.desc")
            .range(0.1f, 1.0f).setValue(0.75f)
            .visible(showVignette::isValue);

    private static final Identifier VIGNETTE_ID = Identifier.of("accident", "textures/misc/vignette.png");
    private static final float FIXED_SCALE = 2.0f;

    // Анимация виньетки
    private float strikeAnim = 0f;
    private float strikeTo = 0f;
    private boolean strikeTriggered = false;
    private LivingEntity lastKilled = null;

    // Замедление
    private boolean timerPhase1Pending = false;
    private boolean timerActive = false;
    private long timerRestoreAt = 0L;

    // Плавный поворот камеры
    private boolean snapActive = false;
    private float snapTargetYaw = 0f;
    private float snapTargetPitch = 0f;
    private long snapStartTime = 0L;
    private static final long SNAP_DURATION_MS = 3000L;

    // Плавное приближение камеры
    public float cameraZoom = 0f;        // 0 = нет зума, 1 = полный зум
    private float cameraZoomTarget = 0f;
    private static final float BASE_DISTANCE = 3.0f;
    private static final float ZOOM_EXTRA = 1.5f; // на сколько приближаем сверх базовой дистанции

    private final List<EntityDeathMemory> memories = new ArrayList<>();
    private final List<FragParticle> fragParticles = new ArrayList<>();

    public KillEffect() {
        super("accident.module.killeffect.name", "accident.module.killeffect.desc", ModuleCategory.RENDER);
        settings(noMobs, playSound, showVignette, vignetteAlpha, showParticles, slowTime, fovZoom, fovStrength, cameraSnap);
    }

    @Override
    public boolean deactivate() {
        memories.clear();
        fragParticles.clear();
        strikeAnim = 0f;
        strikeTo = 0f;
        strikeTriggered = false;
        lastKilled = null;
        timerPhase1Pending = false;
        snapActive = false;
        cameraZoom = 0f;
        cameraZoomTarget = 0f;
        restoreTimer();
        return false;
    }

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (mc.player == null || mc.world == null) return;
        Entity target = e.getTarget();
        if (!(target instanceof LivingEntity living)) return;
        if (living instanceof PlayerEntity && living == mc.player) return;
        if (noMobs.isValue() && !(living instanceof PlayerEntity)) return;
        if (living.getHealth() <= 0f || living.isRemoved()) return;

        for (EntityDeathMemory mem : memories) {
            if (mem.entity.getId() == living.getId()) {
                mem.refresh(living);
                return;
            }
        }
        if (memories.isEmpty()) {
            memories.add(new EntityDeathMemory(living, this::onKill));
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        Iterator<EntityDeathMemory> iter = memories.iterator();
        while (iter.hasNext()) {
            EntityDeathMemory mem = iter.next();
            mem.tick();
            if (!mem.isAlive()) iter.remove();
        }

        if (timerPhase1Pending && slowTime.isValue()) {
            TickTimer.set(0.1f);
            timerPhase1Pending = false;
        }

        updateStrikeAnimation();

        // Плавный зум камеры — быстро входит, плавно выходит
        float zoomSpeed = cameraZoomTarget > cameraZoom ? 0.4f : 0.08f; // было 0.25f / 0.05f
        cameraZoom = lerp(cameraZoom, cameraZoomTarget, zoomSpeed);
        if (cameraZoom < 0.001f) cameraZoom = 0f;

        // Зум активен пока анимация идёт
        cameraZoomTarget = (strikeAnim > 0.01f && fovZoom.isValue()) ? 1f : 0f;

        if (timerActive && System.currentTimeMillis() >= timerRestoreAt) {
            restoreTimer();
        }
    }

    private void onKill(LivingEntity killed) {
        lastKilled = killed;

        strikeAnim = 0.5f;
        strikeTo = 1.0f;
        strikeTriggered = false;

        if (playSound.isValue()) {
            SoundManager.playSound(SoundManager.KOLOKOLNIA_KILL, 0.5f, 1.0f);
        }

        // Замедление на 3 секунды
        if (slowTime.isValue()) {
            TickTimer.set(0.9f);
            timerPhase1Pending = true;
            timerActive = true;
            timerRestoreAt = System.currentTimeMillis() + 3000L;
        }

        // Плавный поворот камеры к цели
        if (cameraSnap.isValue() && killed != null) {
            Vec3d eyes = mc.player.getEyePos();
            Vec3d tPos = new Vec3d(killed.getX(), killed.getY() + killed.getHeight() * 0.5, killed.getZ());
            Vec3d delta = tPos.subtract(eyes);

            double horizLen = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            snapTargetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            snapTargetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizLen));
            snapStartTime = System.currentTimeMillis();
            snapActive = true;
        }

        if (showParticles.isValue() && killed != null) {
            spawnParticles(killed, 360, 2400f);
            spawnParticles(killed, 1200, 300f);
        }
    }

    private void updateStrikeAnimation() {
        if (strikeTo == 1.0f) {
            strikeAnim = lerp(strikeAnim, 1.0f, 0.07f);
            if (strikeAnim > 0.998f) {
                strikeAnim = 1.0f;
                strikeTo = 0.0f;
                if (!strikeTriggered && lastKilled != null && showParticles.isValue()) {
                    spawnParticles(lastKilled, 2400, 150f);
                    spawnParticles(lastKilled, 3600, 450f);
                    strikeTriggered = true;
                }
            }
        } else {
            // Быстрее убираем виньетку
            strikeAnim = lerp(strikeAnim, 0.0f, 0.12f);
            if (strikeAnim < 0.002f) strikeAnim = 0.0f;
        }
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (!showVignette.isValue() || strikeAnim <= 0f) return;
        if (mc.getWindow() == null) return;

        float alpha = strikeAnim * vignetteAlpha.getValue();
        if (alpha < 0.005f) return;

        float sw = mc.getWindow().getFramebufferWidth() / FIXED_SCALE;
        float sh = mc.getWindow().getFramebufferHeight() / FIXED_SCALE;

        float inset = (1.0f - alpha) * (1.0f - alpha);
        float x = -sw * 0.5f * inset;
        float y = -sh * 0.5f * inset;
        float w = sw + sw * inset;
        float h = sh + sh * inset;

        int r = 255;
        int g = (int) (255 * (1.0f - alpha / 1.25f));
        int a = (int) (190 * alpha);
        int color = (a << 24) | (r << 16) | (g << 8) | g;

        Initialization.getInstance().getManager().getRenderCore().getTexturePipeline().drawTexture(
                VIGNETTE_ID, x, y, w, h,
                0f, 0f, 1f, 1f,
                new int[]{color},
                new float[]{0f, 0f, 0f, 0f},
                1.0f, 0f
        );
    }

    @EventHandler
    public void onFov(FovEvent e) {
        if (!fovZoom.isValue() || strikeAnim <= 0f) return;
        int reduced = (int) (e.getFov() - fovStrength.getValue() * strikeAnim);
        e.setFov(reduced);
        e.setCancelled(true);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {

        if (snapActive && cameraSnap.isValue() && mc.player != null) {
            long elapsed = System.currentTimeMillis() - snapStartTime;
            float t = (float) elapsed / SNAP_DURATION_MS;

            if (t >= 1f) {
                snapActive = false;
            } else {
                float currentYaw = mc.player.getYaw();
                float currentPitch = mc.player.getPitch();

                float diffYaw = MathHelper.wrapDegrees(snapTargetYaw - currentYaw);
                float diffPitch = snapTargetPitch - currentPitch;

                // Используем реальное время для плавности — deltaTime в миллисекундах
                float speed = 0.003f; // скорость за миллисекунду
                float delta = Math.min(elapsed * speed, 1f);

                float newYaw = currentYaw + diffYaw * 0.05f;
                float newPitch = currentPitch + diffPitch * 0.05f;

                mc.player.setYaw(newYaw);
                mc.player.setPitch(newPitch);

                if (Math.abs(diffYaw) < 0.3f && Math.abs(diffPitch) < 0.3f) {
                    snapActive = false;
                }
            }
        }

        if (!showParticles.isValue() || fragParticles.isEmpty()) return;

        fragParticles.removeIf(FragParticle::isDead);
        if (fragParticles.isEmpty()) return;

        float globalAlpha = strikeAnim;
        if (globalAlpha < 0.003f) return;

        boolean drawLines = strikeTo == 0.0f;

        for (FragParticle p : fragParticles) {
            float aPC = clamp01(Math.min(p.get010PC() * 3f, 1f) * p.getAlphaPC()) * globalAlpha;
            if (aPC < 0.003f) continue;

            Vec3d pos = p.getPos();
            int alpha = (int) (255 * aPC);
            int colorA = (alpha << 24) | 0xFFFFFF;
            int colorB = (alpha << 24) | (((int)(255 * aPC)) << 16) | (((int)(255 * aPC)) << 8) | (int)(255 * aPC);
            int color = blendColors(colorA, colorB, p.randomFloat);

            if (drawLines) {
                Render3D.LINE.add(new Render3D.Line(null, p.spawnPos, pos, color, ColorUtil.multAlpha(color, 0.2f), 1.0f));
            } else {
                float size = 0.04f;
                Render3D.QUAD.add(new Render3D.Quad(null,
                        pos.add(-size, -size, 0),
                        pos.add( size, -size, 0),
                        pos.add( size,  size, 0),
                        pos.add(-size,  size, 0),
                        color));
            }
        }
    }

    private void spawnParticles(LivingEntity base, int count, float maxTime) {
        if (base == null) return;
        float startDst = 0.5f;
        float yExt = startDst / 5f;
        float yMaxRand = yExt / 2f;
        Vec3d center = new Vec3d(base.getX(), base.getY() + base.getStandingEyeHeight() / 2.0, base.getZ());

        for (int i = 0; i < count; i++) {
            float pc = (float) i / count;
            float yRandom = (float) ((-yMaxRand / 2f) + Math.random() * yMaxRand);
            float dstRand = startDst / 2f + (float) ((-startDst / 40f) + Math.random() * startDst / 20f);
            float yExtRand = (float) (yExt * Math.random());

            fragParticles.add(new FragParticle(
                    center.add(0, yRandom, 0),
                    pc * 360f, startDst, startDst + dstRand,
                    36f * (float) Math.random(), yExtRand, maxTime
            ));
        }
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static int blendColors(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab_ = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb_ = b & 0xFF, ba = (b >> 24) & 0xFF;
        return (lerp(aa, ba, t) << 24) | (lerp(ar, br, t) << 16) | (lerp(ag, bg, t) << 8) | lerp(ab_, bb_, t);
    }

    private static int lerp(int a, int b, float t) { return (int) (a + (b - a) * t); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private void restoreTimer() {
        TickTimer.reset();
        timerActive = false;
    }

    private static class EntityDeathMemory {
        private LivingEntity entity;
        private final long createdAt;
        private static final long MAX_LIFETIME_MS = 1500L;
        private boolean triggered = false;
        private final java.util.function.Consumer<LivingEntity> onTrigger;

        EntityDeathMemory(LivingEntity entity, java.util.function.Consumer<LivingEntity> onTrigger) {
            this.entity = entity;
            this.createdAt = System.currentTimeMillis();
            this.onTrigger = onTrigger;
        }

        void refresh(LivingEntity e) { this.entity = e; }

        boolean isAlive() {
            return !triggered && entity != null && !entity.isRemoved()
                    && System.currentTimeMillis() - createdAt < MAX_LIFETIME_MS;
        }

        void tick() {
            if (triggered || entity == null) return;
            if (entity.getHealth() <= 0f) {
                triggered = true;
                onTrigger.accept(entity);
            }
        }
    }

    private static class FragParticle {
        final Vec3d spawnPos;
        final long startTime = System.currentTimeMillis();
        final float startYaw, endYaw, startDst, endDst, toY, maxTime;
        final float randomFloat = (float) Math.random();

        FragParticle(Vec3d spawnPos, float yaw, float startDst, float endDst,
                     float addYaw, float addY, float maxTime) {
            this.spawnPos = spawnPos;
            this.startYaw = yaw;
            this.endYaw = yaw + addYaw;
            this.startDst = startDst;
            this.endDst = endDst;
            this.toY = addY;
            this.maxTime = maxTime;
        }

        float getTimePC() { return clamp01((float)(System.currentTimeMillis() - startTime) / maxTime); }
        float get010PC() { float t = getTimePC(); return (t > 0.5f ? 1f - t : t) * 2f; }
        float getAlphaPC() { return 1f - getTimePC(); }
        boolean isDead() { return getTimePC() >= 1f; }

        Vec3d getPos() {
            float t = getTimePC();
            float yaw = lerp(startYaw, endYaw, t);
            float dst = lerp(startDst, endDst, t);
            float yOff = toY * t;
            double rad = Math.toRadians(yaw);
            return spawnPos.add(Math.sin(rad) * dst, yOff, Math.cos(rad) * dst);
        }

        private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
        private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    }

    public static float getCameraZoom() {
        KillEffect inst = Instance.get(KillEffect.class);
        return inst != null ? inst.cameraZoom : 0f;
    }
}


