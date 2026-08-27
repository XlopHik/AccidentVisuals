package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import accident.events.api.EventHandler;
import accident.events.impl.TickEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.render.сliemtpipeline.ClientPipelines;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FireFly extends ModuleStructure {

    public SliderSettings count = new SliderSettings("accident.module.firefly.setting.count.name", "accident.module.firefly.setting.count.desc")
            .range(1, 100).setValue(15);

    public BooleanSetting randomColor = new BooleanSetting("accident.module.firefly.setting.randomcolor.name", "accident.module.firefly.setting.randomcolor.desc")
            .setValue(true);

    public ColorSetting color = new ColorSetting("accident.module.firefly.setting.color.name", "accident.module.firefly.setting.color.desc")
            .value(0xFFFFD700)
            .visible(() -> !randomColor.isValue());

    private static final int[] RANDOM_PALETTE = {0xFFD700, 0xFFFF00, 0x00FF00, 0x00FFFF, 0xFF69B4, 0xFFA500, 0x00BFFF};

    private static final float SPEED = 0.35f;
    private static final float SPAWN_RADIUS = 35f;
    private static final int TRAIL_LENGTH = 25;

    private static final Identifier GLOW_TEXTURE = Identifier.of("accident", "images/particle/glow.png");

    private static class TrailPoint {
        double x, y, z;
        TrailPoint(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private static class FireFlyEntity {
        double x, y, z, prevX, prevY, prevZ;
        double velX, velY, velZ;
        double targetVelX, targetVelY, targetVelZ;
        final int baseRandomColor;
        final long spawnTime = System.currentTimeMillis();
        long lastDirectionChange = System.currentTimeMillis();
        final List<TrailPoint> trail = new ArrayList<>();
        final int maxTrailLength;
        final Random rng = new Random();

        FireFlyEntity(double x, double y, double z,
                      double velX, double velY, double velZ,
                      int baseRandomColor, int maxTrailLength) {
            this.x = this.prevX = x;
            this.y = this.prevY = y;
            this.z = this.prevZ = z;
            this.velX = this.targetVelX = velX;
            this.velY = this.targetVelY = velY;
            this.velZ = this.targetVelZ = velZ;
            this.baseRandomColor = baseRandomColor;
            this.maxTrailLength = maxTrailLength;
        }

        void update(float speedMult, float maxSpeed, Vec3d playerPos) {
            prevX = x; prevY = y; prevZ = z;

            if (System.currentTimeMillis() - lastDirectionChange > 2000 + rng.nextInt(2000)) {
                double angle = Math.toRadians(rng.nextDouble() * 360);
                double pitch = Math.toRadians((rng.nextDouble() - 0.5) * 40);
                targetVelX = -Math.sin(angle) * Math.cos(pitch) * speedMult;
                targetVelY = Math.sin(pitch) * speedMult * 0.3;
                targetVelZ = Math.cos(angle) * Math.cos(pitch) * speedMult;
                lastDirectionChange = System.currentTimeMillis();
            }

            double dx = playerPos.x - x, dy = playerPos.y + 1.0 - y, dz = playerPos.z - z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > SPAWN_RADIUS) {
                targetVelX += (dx / dist) * speedMult * 0.15;
                targetVelY += (dy / dist) * speedMult * 0.15;
                targetVelZ += (dz / dist) * speedMult * 0.15;
            }

            double lerpF = 0.02;
            velX += (targetVelX - velX) * lerpF;
            velY += (targetVelY - velY) * lerpF;
            velZ += (targetVelZ - velZ) * lerpF;

            double wobble = 0.03;
            velX += (rng.nextDouble() - 0.5) * wobble;
            velY += (rng.nextDouble() - 0.5) * wobble;
            velZ += (rng.nextDouble() - 0.5) * wobble;

            velX = MathHelper.clamp(velX, -maxSpeed, maxSpeed);
            velY = MathHelper.clamp(velY, -maxSpeed, maxSpeed);
            velZ = MathHelper.clamp(velZ, -maxSpeed, maxSpeed);

            x += velX; y += velY; z += velZ;

            trail.add(0, new TrailPoint(x, y, z));
            while (trail.size() > maxTrailLength) trail.remove(trail.size() - 1);
        }

        boolean isDead(Vec3d playerPos) {
            double dx = x - playerPos.x, dy = y - playerPos.y, dz = z - playerPos.z;
            return dx * dx + dy * dy + dz * dz > 80 * 80;
        }

        double interpX(float td) { return MathHelper.lerp(td, prevX, x); }
        double interpY(float td) { return MathHelper.lerp(td, prevY, y); }
        double interpZ(float td) { return MathHelper.lerp(td, prevZ, z); }

        float pulseAlpha() {
            double pulse = 0.8 + 0.2 * Math.sin((System.currentTimeMillis() - spawnTime) / 200.0);
            return (float) pulse;
        }

        float lifeAlpha() {
            long age = System.currentTimeMillis() - spawnTime;
            return age < 1000 ? age / 1000f : 1f;
        }
    }

    private final List<FireFlyEntity> flies = new ArrayList<>();
    private final Random random = new Random();
    private BufferAllocator allocator;
    private VertexConsumerProvider.Immediate imm;

    public FireFly() {
        super("accident.module.firefly.name", "accident.module.firefly.desc", ModuleCategory.RENDER);
        settings(count, randomColor, color);
    }

    @Override
    public boolean deactivate() {
        flies.clear();
        if (allocator != null) {
            allocator.close();
            allocator = null;
            imm = null;
        }
        return false;
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        Vec3d playerPos = mc.player.getEntityPos();
        float maxSpeed = SPEED * 1.5f;

        flies.forEach(f -> f.update(SPEED, maxSpeed, playerPos));
        flies.removeIf(f -> f.isDead(playerPos));

        int target = count.getInt();
        while (flies.size() > target) flies.remove(flies.size() - 1);
        while (flies.size() < target) spawnFly(playerPos);
    }

    private void spawnFly(Vec3d playerPos) {
        double dist = random.nextDouble() * (SPAWN_RADIUS - 5) + 5;
        double yaw = Math.toRadians(random.nextDouble() * 360);
        double pitch = Math.toRadians((random.nextDouble() - 0.5) * 60);

        double velX = -Math.sin(yaw) * Math.cos(pitch) * SPEED;
        double velY = Math.sin(pitch) * SPEED * 0.5;
        double velZ = Math.cos(yaw) * Math.cos(pitch) * SPEED;

        int flyColor = randomColor.isValue()
                ? RANDOM_PALETTE[random.nextInt(RANDOM_PALETTE.length)]
                : (color.getColor() & 0x00FFFFFF);

        flies.add(new FireFlyEntity(
                playerPos.x - Math.sin(yaw) * dist,
                playerPos.y + (random.nextDouble() - 0.3) * 8 + 1,
                playerPos.z + Math.cos(yaw) * dist,
                velX, velY, velZ,
                flyColor, TRAIL_LENGTH
        ));
    }

    @EventHandler
    public void onRender3D(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null || flies.isEmpty()) return;

        if (allocator == null) {
            allocator = new BufferAllocator(1 << 20);
            imm = VertexConsumerProvider.immediate(allocator);
        }

        MatrixStack stack = e.getStack();
        Vec3d cam = e.getCamera().getCameraPos();
        float tickDelta = e.getPartialTicks();

        RenderLayer particleLayer = ClientPipelines.WORLD_PARTICLES_GLOW.apply(GLOW_TEXTURE);
        VertexConsumer buf = imm.getBuffer(particleLayer);

        stack.push();
        stack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f globalMat = stack.peek().getPositionMatrix();

        for (int fi = 0; fi < flies.size(); fi++) {
            FireFlyEntity fly = flies.get(fi);
            float lifeAlpha = fly.lifeAlpha();

            int maxSegments = Math.min(fly.trail.size(), 10);
            if (lifeAlpha <= 0.01f || maxSegments < 2) continue;

            int baseColor = fly.baseRandomColor;

            double headX = fly.interpX(tickDelta);
            double headY = fly.interpY(tickDelta);
            double headZ = fly.interpZ(tickDelta);
            Vec3d headPos = new Vec3d(headX, headY, headZ);

            List<Vec3d> pts = new ArrayList<>();
            pts.add(headPos);
            pts.add(new Vec3d(fly.prevX, fly.prevY, fly.prevZ));
            for (int i = 1; i < maxSegments; i++) {
                TrailPoint tp = fly.trail.get(i);
                pts.add(new Vec3d(tp.x, tp.y, tp.z));
            }

            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3d curr = pts.get(i);
                Vec3d next = pts.get(i + 1);

                float t1 = (float) i / (pts.size() - 1);
                float t2 = (float) (i + 1) / (pts.size() - 1);

                float w1 = 0.05f * (1f - t1);
                float w2 = 0.05f * (1f - t2);

                float a1 = (i == 0) ? 0 : (float) Math.pow(1f - t1, 2) * lifeAlpha;
                float a2 = (float) Math.pow(1f - t2, 2) * lifeAlpha;

                Vec3d dir = next.subtract(curr);
                if (dir.lengthSquared() < 0.0001) continue;
                dir = dir.normalize();

                Vec3d camDir = curr.subtract(cam).normalize();
                Vec3d side = dir.crossProduct(camDir).normalize();

                int col1 = replAlpha(baseColor, (int) (a1 * 255));
                int col2 = replAlpha(baseColor, (int) (a2 * 255));

                buf.vertex(globalMat, (float)(curr.x + side.x * w1), (float)(curr.y + side.y * w1), (float)(curr.z + side.z * w1)).texture(0.5f, 0f).color(col1);
                buf.vertex(globalMat, (float)(curr.x - side.x * w1), (float)(curr.y - side.y * w1), (float)(curr.z - side.z * w1)).texture(0.5f, 1f).color(col1);
                buf.vertex(globalMat, (float)(next.x - side.x * w2), (float)(next.y - side.y * w2), (float)(next.z - side.z * w2)).texture(0.5f, 1f).color(col2);
                buf.vertex(globalMat, (float)(next.x + side.x * w2), (float)(next.y + side.y * w2), (float)(next.z + side.z * w2)).texture(0.5f, 0f).color(col2);
            }
        }

        stack.pop();
        imm.draw(particleLayer);

        buf = imm.getBuffer(particleLayer);

        for (int fi = 0; fi < flies.size(); fi++) {
            FireFlyEntity fly = flies.get(fi);
            float lifeAlpha = fly.lifeAlpha();
            if (lifeAlpha <= 0.01f) continue;

            int baseColor = fly.baseRandomColor;
            float p = fly.pulseAlpha() * lifeAlpha;

            double px = fly.interpX(tickDelta);
            double py = fly.interpY(tickDelta);
            double pz = fly.interpZ(tickDelta);

            stack.push();
            stack.translate(px - cam.x, py - cam.y, pz - cam.z);
            stack.multiply(mc.gameRenderer.getCamera().getRotation());
            stack.translate(0, 0, 0.02f);
            Matrix4f mat = stack.peek().getPositionMatrix();

            emitQuad(buf, mat, 1.7f,  replAlpha(baseColor,   (int)(p * 0.15f * 255)));
            emitQuad(buf, mat, 0.95f, replAlpha(baseColor,   (int)(p * 0.40f * 255)));
            emitQuad(buf, mat, 0.40f, replAlpha(baseColor,   (int)(p * 0.85f * 255)));
            emitQuad(buf, mat, 0.15f, replAlpha(0x00FFFFFF,  (int)(p * 255)));

            stack.pop();
        }

        imm.draw();
    }

    private static int replAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static void emitQuad(VertexConsumer buf, Matrix4f mat, float size, int argb) {
        float h = size * 0.5f;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF, a = (argb >>> 24);
        buf.vertex(mat, -h, -h, 0).texture(0, 1).color(r, g, b, a);
        buf.vertex(mat,  h, -h, 0).texture(1, 1).color(r, g, b, a);
        buf.vertex(mat,  h,  h, 0).texture(1, 0).color(r, g, b, a);
        buf.vertex(mat, -h,  h, 0).texture(0, 0).color(r, g, b, a);
    }
}
