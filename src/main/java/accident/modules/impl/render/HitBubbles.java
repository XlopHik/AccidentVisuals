package accident.modules.impl.render;

import accident.events.api.EventHandler;
import accident.events.impl.AttackEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;
import accident.util.render.сliemtpipeline.ClientPipelines;
import com.google.common.collect.Lists;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;

public class HitBubbles extends ModuleStructure {

    public static HitBubbles getInstance() {
        return Instance.get(HitBubbles.class);
    }

    private static final Identifier BUBBLE_TEXTURE = Identifier.of("accident", "textures/world/bubble.png");

    private final SliderSettings lifeTime = new SliderSettings("accident.module.hitbubbles.setting.lifetime.name", "accident.module.hitbubbles.setting.lifetime.desc")
            .range(500, 5000)
            .setValue(1500);

    private final ArrayList<HitBubble> bubbles = new ArrayList<>();

    public HitBubbles() {
        super("accident.module.hitbubbles.name", "accident.module.hitbubbles.desc", ModuleCategory.RENDER);
        settings(lifeTime);
    }

    @Override
    public boolean deactivate() {
        bubbles.clear();
        return true;
    }

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (mc.player == null) return;

        Entity target = e.getTarget();
        if (target == null) return;

        Vec3d playerEye = mc.player.getEyePos();

        // Реальная точка пересечения луча взгляда с хитбоксом цели по высоте
        float pitch = (float) Math.toRadians(mc.player.getPitch());
        float yaw = (float) Math.toRadians(mc.player.getYaw());
        double dist = playerEye.distanceTo(target.getEntityPos());
        double hitY = playerEye.y - Math.sin(pitch) * dist;

        // Клампим в пределах тела цели
        hitY = Math.max(target.getY(), Math.min(target.getY() + target.getHeight(), hitY));

        Vec3d targetCenter = new Vec3d(target.getX(), hitY, target.getZ());
        Vec3d dir = playerEye.subtract(targetCenter).normalize();
        Vec3d spawnPos = targetCenter.add(dir.multiply(target.getWidth() * 0.5 + 0.1));

        bubbles.add(new HitBubble(
                (float) spawnPos.x,
                (float) spawnPos.y,
                (float) spawnPos.z,
                -mc.player.getYaw(),
                System.currentTimeMillis()
        ));
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long maxAge = (long) lifeTime.getValue();

        bubbles.removeIf(b -> now - b.spawnTime > maxAge);
        if (bubbles.isEmpty()) return;

        MatrixStack matrices = event.getStack();
        VertexConsumerProvider.Immediate immediate = (VertexConsumerProvider.Immediate) event.getVertexConsumers();
        Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();

        for (HitBubble b : Lists.newArrayList(bubbles)) {
            float factor = (float)(now - b.spawnTime) / maxAge;
            if (factor >= 1f) continue;

            float angle = -(now - b.spawnTime) / 4f;

            matrices.push();
            matrices.translate(b.x - cam.x, b.y - cam.y, b.z - cam.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(b.yaw));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));

            float scale = factor * 1.3f;
            float alpha = 1f - factor;

            drawBubble(matrices, immediate, scale, alpha);
            matrices.pop();
        }

        immediate.draw(ClientPipelines.HIT_BUBBLE_LAYER.apply(BUBBLE_TEXTURE));
    }

    private void drawBubble(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, float scale, float alpha) {
        VertexConsumer buffer = immediate.getBuffer(ClientPipelines.HIT_BUBBLE_LAYER.apply(BUBBLE_TEXTURE));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        float half = scale / 2f;

        // Цвет с альфой
        int a = (int)(alpha * 200);
        int r = 255, g = 255, b = 255;

        // UV координаты (вся текстура)
        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;

        // Квад смотрит на камеру (billboard) — просто плоский квад в локальных координатах после rotate
        buffer.vertex(mat, -half, -half, 0f).color(r, g, b, a).texture(u0, v1);
        buffer.vertex(mat,  half, -half, 0f).color(r, g, b, a).texture(u1, v1);
        buffer.vertex(mat,  half,  half, 0f).color(r, g, b, a).texture(u1, v0);
        buffer.vertex(mat, -half,  half, 0f).color(r, g, b, a).texture(u0, v0);
    }

    public record HitBubble(float x, float y, float z, float yaw, long spawnTime) {}
}


