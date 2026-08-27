package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import accident.Initialization;
import accident.events.api.EventHandler;
import accident.events.impl.AttackEvent;
import accident.events.impl.DrawEvent;
import accident.events.impl.TickEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.ColorUtil;
import accident.util.Instance;
import accident.util.accesors.OverlayTextureAccessor;
import accident.util.math.Projection;
import accident.util.render.Render3D;
import accident.util.render.font.FontRenderer;
import accident.util.render.font.Fonts;
import accident.util.render.сliemtpipeline.ClientPipelines;
import accident.util.render.pipeline.LiquidRingPipeline;
import accident.util.render.shader.SkyRenderer;
import accident.util.sounds.SoundManager;
import com.google.common.collect.Lists;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

// раньше bubbles/color/effect/sound были отдельными модулями, но по одному их включать не имело смысла - объединили в один с общим тогглом
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HitModules extends ModuleStructure {

    public static HitModules getInstance() {
        return Instance.get(HitModules.class);
    }

    private static final Identifier BUBBLE_TEXTURE = Identifier.of("accident", "textures/world/bubble.png");

    // ---- Bubbles ------------------------------------------------------
    public BooleanSetting enableBubbles = new BooleanSetting("accident.module.hitmodules.setting.enablebubbles.name", "accident.module.hitmodules.setting.enablebubbles.desc")
            .setValue(true);

    public SliderSettings bubbleLifeTime = new SliderSettings("accident.module.hitmodules.setting.bubblelifetime.name", "accident.module.hitmodules.setting.bubblelifetime.desc")
            .range(500, 5000).setValue(1500).visible(enableBubbles::isValue);

    public SelectSetting bubbleStyle = new SelectSetting("accident.module.hitmodules.setting.bubblestyle.name", "accident.module.hitmodules.setting.bubblestyle.desc")
            .value("Sprite", "Liquid Glass").selected("Sprite").visible(enableBubbles::isValue);

    public SliderSettings bubbleGlassSize = new SliderSettings("accident.module.hitmodules.setting.bubbleglasssize.name", "accident.module.hitmodules.setting.bubbleglasssize.desc")
            .range(0.4f, 3.0f).setValue(1.1f).visible(() -> enableBubbles.isValue() && bubbleStyle.isSelected("Liquid Glass"));

    public SliderSettings bubbleGlassStrength = new SliderSettings("accident.module.hitmodules.setting.bubbleglassstrength.name", "accident.module.hitmodules.setting.bubbleglassstrength.desc")
            .range(0.2f, 3.0f).setValue(1.0f).visible(() -> enableBubbles.isValue() && bubbleStyle.isSelected("Liquid Glass"));

    public ColorSetting bubbleGlassTint = new ColorSetting("accident.module.hitmodules.setting.bubbleglasstint.name", "accident.module.hitmodules.setting.bubbleglasstint.desc")
            .value(new Color(200, 230, 255, 200).getRGB()).visible(() -> enableBubbles.isValue() && bubbleStyle.isSelected("Liquid Glass"));

    final List<HitBubble> bubbles = new ArrayList<>();
    private LiquidRingPipeline liquidRing;
    private static final float NEAR_PLANE = 0.05f;

    // ---- Color (hurt vignette) -----------------------------------------
    public BooleanSetting enableColor = new BooleanSetting("accident.module.hitmodules.setting.enablecolor.name", "accident.module.hitmodules.setting.enablecolor.desc")
            .setValue(true);

    public ColorSetting damageColor = new ColorSetting("accident.module.hitmodules.setting.damagecolor.name", "accident.module.hitmodules.setting.damagecolor.desc")
            .value(new Color(255, 0, 0, 150).getRGB()).visible(enableColor::isValue);

    // ---- Effect (ground wave) ------------------------------------------
    public BooleanSetting enableEffect = new BooleanSetting("accident.module.hitmodules.setting.enableeffect.name", "accident.module.hitmodules.setting.enableeffect.desc")
            .setValue(true);

    public ColorSetting waveColor = new ColorSetting("accident.module.hitmodules.setting.wavecolor.name", "accident.module.hitmodules.setting.wavecolor.desc")
            .setColor(new Color(137, 97, 72, 255).getRGB()).visible(enableEffect::isValue);

    final List<WaveEffect> waveEffects = Collections.synchronizedList(new ArrayList<>());

    // ---- Sound ----------------------------------------------------------
    public BooleanSetting enableSound = new BooleanSetting("accident.module.hitmodules.setting.enablesound.name", "accident.module.hitmodules.setting.enablesound.desc")
            .setValue(true);

    public SelectSetting soundType = new SelectSetting("accident.module.hitmodules.setting.soundtype.name", "accident.module.hitmodules.setting.soundtype.desc")
            .value("Metallic", "Moan1", "Moan2", "Moan3", "Moan4", "Bell", "Bell2", "Bell3", "Neverlose", "Boom", "Swap")
            .selected("Metallic").visible(enableSound::isValue);

    public SliderSettings soundVolume = new SliderSettings("accident.module.hitmodules.setting.volume.name", "accident.module.hitmodules.setting.volume.desc")
            .range(0.1f, 2.0f).setValue(1.0f).visible(enableSound::isValue);

    public SliderSettings soundPitch = new SliderSettings("accident.module.hitmodules.setting.pitch.name", "accident.module.hitmodules.setting.pitch.desc")
            .range(0.5f, 2.0f).setValue(1.0f).visible(enableSound::isValue);

    // ---- Damage (floating combat text, new) ------------------------------
    public BooleanSetting enableDamage = new BooleanSetting("accident.module.hitmodules.setting.enabledamage.name", "accident.module.hitmodules.setting.enabledamage.desc")
            .setValue(true);

    public ColorSetting damageTextColor = new ColorSetting("accident.module.hitmodules.setting.damagetextcolor.name", "accident.module.hitmodules.setting.damagetextcolor.desc")
            .value(new Color(255, 40, 40, 255).getRGB()).visible(enableDamage::isValue);

    public SliderSettings damageSize = new SliderSettings("accident.module.hitmodules.setting.damagesize.name", "accident.module.hitmodules.setting.damagesize.desc")
            .range(6f, 20f).setValue(11f).visible(enableDamage::isValue);

    public SliderSettings damageDuration = new SliderSettings("accident.module.hitmodules.setting.damageduration.name", "accident.module.hitmodules.setting.damageduration.desc")
            .range(500, 2500).setValue(1100).visible(enableDamage::isValue);

    public SliderSettings damageGlow = new SliderSettings("accident.module.hitmodules.setting.damageglow.name", "accident.module.hitmodules.setting.damageglow.desc")
            .range(0f, 3f).setValue(1.6f).visible(enableDamage::isValue);

    // ждём реальный урон от сервера вместо расчёта на клиенте - клиент не знает броню/зачарования/крит
    // ключ - id сущности, так что повторный удар до резолва первого просто обновляет ожидание
    final Map<Integer, PendingHit> pendingHits = new HashMap<>();
    final List<FloatingDamage> floatingDamages = new ArrayList<>();
    private static final long PENDING_TIMEOUT_MS = 1200L;
    private static final float HEALTH_EPSILON = 0.01f;

    public HitModules() {
        super("accident.module.hitmodules.name", "accident.module.hitmodules.desc", ModuleCategory.RENDER);
        settings(
                enableBubbles, bubbleLifeTime, bubbleStyle, bubbleGlassSize, bubbleGlassStrength, bubbleGlassTint,
                enableColor, damageColor,
                enableEffect, waveColor,
                enableSound, soundType, soundVolume, soundPitch,
                enableDamage, damageTextColor, damageSize, damageDuration, damageGlow
        );
    }

    @Override
    public boolean activate() {
        refreshOverlay();
        return false;
    }

    @Override
    public boolean deactivate() {
        bubbles.clear();
        waveEffects.clear();
        pendingHits.clear();
        floatingDamages.clear();
        refreshOverlay();
        if (liquidRing != null) { liquidRing.close(); liquidRing = null; }
        return false;
    }

    // ---- Attack intake ----------------------------------------------------

    @EventHandler
    public void onAttack(AttackEvent e) {
        if (!isState() || mc.player == null) return;

        Entity target = e.getTarget();
        if (target == null) return;

        if (enableBubbles.isValue()) spawnBubble(target);
        if (enableEffect.isValue()) spawnWave(target);
        if (enableSound.isValue()) playHitSound();

        if (enableDamage.isValue() && target instanceof LivingEntity living
                && living != mc.player && living.getHealth() > 0f && !living.isRemoved()) {
            PendingHit pending = pendingHits.get(living.getId());
            if (pending == null) {
                pendingHits.put(living.getId(), new PendingHit(living, living.getHealth(), System.currentTimeMillis()));
            } else {
                pending.lastAttackTime = System.currentTimeMillis();
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        refreshOverlay();
        if (mc.world == null) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, PendingHit>> it = pendingHits.entrySet().iterator();
        while (it.hasNext()) {
            PendingHit pending = it.next().getValue();

            // сущность могла исчезнуть не от удара (выгрузка чанка и тд) - сравнивать здоровье не с чем, просто выходим
            if (pending.entity.isRemoved()) {
                it.remove();
                continue;
            }

            float currentHealth = pending.entity.getHealth();
            if (currentHealth < pending.preHealth - HEALTH_EPSILON) {
                float damage = pending.preHealth - currentHealth;
                if (enableDamage.isValue()) spawnFloatingDamage(pending, damage);
                it.remove();
                continue;
            }

            if (now - pending.lastAttackTime > PENDING_TIMEOUT_MS) {
                it.remove();
            }
        }
    }

    // ---- Bubbles ------------------------------------------------------

    private void spawnBubble(Entity target) {
        Vec3d playerEye = mc.player.getEyePos();

        float pitch = (float) Math.toRadians(mc.player.getPitch());
        double dist = playerEye.distanceTo(target.getEntityPos());
        double hitY = playerEye.y - Math.sin(pitch) * dist;
        hitY = Math.max(target.getY(), Math.min(target.getY() + target.getHeight(), hitY));

        Vec3d targetCenter = new Vec3d(target.getX(), hitY, target.getZ());
        Vec3d dir = playerEye.subtract(targetCenter).normalize();
        Vec3d spawnPos = targetCenter.add(dir.multiply(target.getWidth() * 0.5 + 0.1));

        bubbles.add(new HitBubble(
                (float) spawnPos.x, (float) spawnPos.y, (float) spawnPos.z,
                -mc.player.getYaw(), System.currentTimeMillis()
        ));
    }

    private void renderBubbles(WorldRenderEvent event) {
        long now = System.currentTimeMillis();
        long maxAge = (long) bubbleLifeTime.getValue();

        bubbles.removeIf(b -> now - b.spawnTime > maxAge);
        if (bubbles.isEmpty()) return;

        if (bubbleStyle.isSelected("Liquid Glass")) {
            renderLiquidBubbles(now, maxAge);
            return;
        }

        MatrixStack matrices = event.getStack();
        VertexConsumerProvider.Immediate immediate = (VertexConsumerProvider.Immediate) event.getVertexConsumers();
        Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();

        for (HitBubble b : Lists.newArrayList(bubbles)) {
            float factor = (float) (now - b.spawnTime) / maxAge;
            if (factor >= 1f) continue;

            float angle = -(now - b.spawnTime) / 4f;

            matrices.push();
            matrices.translate(b.x - cam.x, b.y - cam.y, b.z - cam.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(b.yaw));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));

            drawBubble(matrices, immediate, factor * 1.3f, 1f - factor);
            matrices.pop();
        }

        immediate.draw(ClientPipelines.HIT_BUBBLE_LAYER.apply(BUBBLE_TEXTURE));
    }

    private void drawBubble(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, float scale, float alpha) {
        VertexConsumer buffer = immediate.getBuffer(ClientPipelines.HIT_BUBBLE_LAYER.apply(BUBBLE_TEXTURE));
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float half = scale / 2f;
        int a = (int) (alpha * 200);

        buffer.vertex(mat, -half, -half, 0f).color(255, 255, 255, a).texture(0f, 1f);
        buffer.vertex(mat, half, -half, 0f).color(255, 255, 255, a).texture(1f, 1f);
        buffer.vertex(mat, half, half, 0f).color(255, 255, 255, a).texture(1f, 0f);
        buffer.vertex(mat, -half, half, 0f).color(255, 255, 255, a).texture(0f, 0f);
    }

    // тот же эффект преломления, что у TntTimer на взрыве, но уменьшенный под удар
    private void renderLiquidBubbles(long now, long maxAge) {
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;

        if (liquidRing == null) liquidRing = new LiquidRingPipeline();

        int count = Math.min(bubbles.size(), LiquidRingPipeline.MAX_RINGS);
        float[] rx = new float[count], ry = new float[count], rz = new float[count];
        float[] radius = new float[count], rimWidth = new float[count];
        float[] envelope = new float[count], domeAmp = new float[count];

        float footprint = bubbleGlassSize.getValue() * 0.5f;

        for (int i = 0; i < count; i++) {
            HitBubble b = bubbles.get(i);
            float progress = Math.min((now - b.spawnTime) / (float) maxAge, 1f);

            // кубическое ease-out - рост в основном в начале, как настоящая ударная волна
            float eased = 1f - (float) Math.pow(1f - progress, 3);

            rx[i] = b.x;
            ry[i] = b.y;
            rz[i] = b.z;
            radius[i] = eased * footprint;
            rimWidth[i] = Math.max(0.06f, footprint * 0.2f);

            float fadeIn = clamp01(progress / 0.08f);
            float fadeOut = clamp01((1f - progress) / 0.5f);
            envelope[i] = fadeIn * fadeOut;

            domeAmp[i] = 0.02f * bubbleGlassStrength.getValue() * envelope[i];
        }

        Quaternionf rotation = new Quaternionf(camera.getRotation());
        Vector3f camRight = rotation.transform(new Vector3f(1, 0, 0));
        Vector3f camUp = rotation.transform(new Vector3f(0, 1, 0));

        Vec3d camPosD = camera.getCameraPos();
        Vector3f camPos = new Vector3f((float) camPosD.x, (float) camPosD.y, (float) camPosD.z);

        SkyRenderer sky = SkyRenderer.getInstance();
        Matrix4f invViewProj = new Matrix4f(sky.getFrameProjection()).mul(sky.getFrameView()).invert();
        float far = mc.gameRenderer.getFarPlaneDistance();

        int tint = bubbleGlassTint.getColor();
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

    public record HitBubble(float x, float y, float z, float yaw, long spawnTime) {}

    // ---- Color (hurt vignette) -----------------------------------------

    @Override
    public void setState(boolean state) {
        super.setState(state);
        refreshOverlay();
    }

    private void refreshOverlay() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) return;

        OverlayTexture overlay = client.gameRenderer.getOverlayTexture();
        ((OverlayTextureAccessor) overlay).accident$recolor();
    }

    public boolean isOverlayActive() {
        return isState() && enableColor.isValue();
    }

    public int getDamageVignetteColor() {
        return damageColor.getColor();
    }

    public boolean isCustomSoundActive() {
        return isState() && enableSound.isValue();
    }

    // ---- Effect (ground wave) -------------------------------------------

    private void spawnWave(Entity target) {
        BlockPos groundPos = findGround(target.getBlockPos());
        if (groundPos != null) {
            waveEffects.add(new WaveEffect(groundPos, System.currentTimeMillis()));
        }
    }

    private BlockPos findGround(BlockPos pos) {
        for (int y = 0; y <= 10; y++) {
            BlockPos down = pos.down(y);
            if (mc.world.isInBuildLimit(down) && !mc.world.getBlockState(down).isAir()) {
                return down;
            }
        }
        return pos;
    }

    private void renderWaves() {
        if (waveEffects.isEmpty() || mc.world == null) return;

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

    private class WaveEffect {
        private final BlockPos centerPos;
        private final long startTime;
        private final long duration = 475;
        private final int maxRadius = 8;
        private Map<Long, Integer> reachableBlocks;
        private boolean calculated = false;

        WaveEffect(BlockPos centerPos, long startTime) {
            this.centerPos = centerPos;
            this.startTime = startTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > duration;
        }

        private void calculateReachableBlocks() {
            if (calculated) return;
            calculated = true;

            reachableBlocks = new HashMap<>();
            Queue<BlockPos> queue = new LinkedList<>();
            Map<Long, Integer> visited = new HashMap<>();

            BlockPos startPos = centerPos;
            if (mc.world.getBlockState(startPos).isAir()) {
                for (int y = 1; y <= 5; y++) {
                    BlockPos down = startPos.down(y);
                    if (!mc.world.getBlockState(down).isAir()) {
                        startPos = down;
                        break;
                    }
                }
            }

            queue.add(startPos);
            visited.put(startPos.asLong(), 0);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                int currentDistance = visited.get(current.asLong());

                if (currentDistance > maxRadius) continue;

                BlockState state = mc.world.getBlockState(current);
                if (!state.isAir()) {
                    VoxelShape shape = state.getOutlineShape(mc.world, current);
                    if (!shape.isEmpty()) {
                        reachableBlocks.put(current.asLong(), currentDistance);
                    }
                }

                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.offset(dir);
                    if (!mc.world.isInBuildLimit(neighbor)) continue;

                    long neighborLong = neighbor.asLong();
                    int newDistance = currentDistance + 1;

                    if (visited.containsKey(neighborLong) && visited.get(neighborLong) <= newDistance) continue;
                    if (newDistance > maxRadius) continue;

                    BlockState neighborState = mc.world.getBlockState(neighbor);

                    if (!neighborState.isAir()) {
                        visited.put(neighborLong, newDistance);
                        queue.add(neighbor);
                    } else {
                        BlockPos below = neighbor.down();
                        if (mc.world.isInBuildLimit(below) && !mc.world.getBlockState(below).isAir()) {
                            long belowLong = below.asLong();
                            if (!visited.containsKey(belowLong) || visited.get(belowLong) > newDistance) {
                                visited.put(belowLong, newDistance);
                                queue.add(below);
                            }
                        }

                        BlockPos above = neighbor.up();
                        if (mc.world.isInBuildLimit(above) && !mc.world.getBlockState(above).isAir()) {
                            long aboveLong = above.asLong();
                            if (!visited.containsKey(aboveLong) || visited.get(aboveLong) > newDistance) {
                                visited.put(aboveLong, newDistance);
                                queue.add(above);
                            }
                        }
                    }
                }
            }
        }

        void render() {
            if (mc.world == null) return;

            calculateReachableBlocks();
            if (reachableBlocks == null || reachableBlocks.isEmpty()) return;

            long elapsed = System.currentTimeMillis() - startTime;
            float progress = (float) elapsed / duration;
            float currentRadius = progress * maxRadius;
            float waveWidth = 2.5f;

            float globalAlpha = (float) Math.pow(1.0f - progress, 0.5);

            int rendered = 0;
            int maxPerFrame = 500;

            for (Map.Entry<Long, Integer> entry : reachableBlocks.entrySet()) {
                if (rendered >= maxPerFrame) break;

                int blockDistance = entry.getValue();
                if (blockDistance < currentRadius - waveWidth || blockDistance > currentRadius + 0.5f) continue;

                BlockPos pos = BlockPos.fromLong(entry.getKey());
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir()) continue;

                VoxelShape shape = state.getOutlineShape(mc.world, pos);
                if (shape.isEmpty()) continue;

                rendered++;

                float localAlpha = 1.0f - Math.abs(blockDistance - currentRadius) / waveWidth;
                localAlpha = Math.max(0, Math.min(1, localAlpha));
                localAlpha *= globalAlpha;

                if (localAlpha > 0.02f) {
                    int color = ColorUtil.setAlpha(waveColor.getColor(), (int) (localAlpha * 75));
                    try {
                        Render3D.drawShapeAlternative(pos, shape, color, 1f, true, true);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    // ---- Sound ------------------------------------------------------------

    private void playHitSound() {
        SoundEvent sound = getSound();
        if (sound != null) {
            SoundManager.playSoundDirect(sound, soundVolume.getValue(), soundPitch.getValue());
        }
    }

    private SoundEvent getSound() {
        return switch (soundType.getSelected()) {
            case "Metallic" -> SoundManager.METALLIC;
            case "Moan1" -> SoundManager.MOAN1;
            case "Moan2" -> SoundManager.MOAN2;
            case "Moan3" -> SoundManager.MOAN3;
            case "Moan4" -> SoundManager.MOAN4;
            case "Bell" -> SoundManager.BELL;
            case "Bell2" -> SoundManager.BELL2;
            case "Bell3" -> SoundManager.BELL3;
            case "Neverlose" -> SoundManager.NEVERLOSE;
            case "Boom" -> SoundManager.BOOM;
            case "Swap" -> SoundManager.SWAP;
            default -> SoundManager.METALLIC;
        };
    }

    // ---- Damage (floating combat text) ------------------------------------

    private void spawnFloatingDamage(PendingHit pending, float amount) {
        if (amount <= 0.05f) return;
        LivingEntity entity = pending.entity;
        Vec3d pos = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.92, entity.getZ());
        floatingDamages.add(new FloatingDamage(pos, amount, System.currentTimeMillis()));
    }

    private void renderFloatingDamage() {
        if (floatingDamages.isEmpty() || mc.player == null) return;

        long now = System.currentTimeMillis();
        long duration = (long) damageDuration.getValue();

        Iterator<FloatingDamage> it = floatingDamages.iterator();
        while (it.hasNext()) {
            FloatingDamage fd = it.next();
            long elapsed = now - fd.startTime;
            if (elapsed > duration) {
                it.remove();
                continue;
            }

            float t = elapsed / (float) duration;
            float rise = t * 0.9f;
            float alpha = t < 0.75f ? 1f : 1f - (t - 0.75f) / 0.25f;
            float scale = 1f + (1f - Math.min(t * 4f, 1f)) * 0.35f;

            Vec3d worldPos = fd.pos.add(0, rise, 0);
            Vec3d screen = Projection.worldSpaceToScreenSpace(worldPos);
            if (screen.z < 0 || screen.z > 1) continue;

            String text = String.format("-%.1f", fd.amount);
            drawGlowText(text, screen.x, screen.y, damageSize.getValue() * scale, alpha);
        }
    }

    // настоящее свечение вместо MSDF-обводки: атлас хранит distance-field только пару пикселей за краем глифа,
    // поэтому широкий glow через шейдер даёт плоский квадрат - вместо этого просто рисуем несколько копий глифа по кольцу
    private void drawGlowText(String text, double screenX, double screenY, float size, float alpha) {
        FontRenderer fr = Initialization.getInstance().getManager().getRenderCore().getFontRenderer();
        String fontName = Fonts.BOLD.getName();

        float width = fr.getTextWidth(fontName, text, size);
        float x = (float) screenX - width / 2f;
        float y = (float) screenY;

        int baseColor = damageTextColor.getColor();
        float glowStrength = damageGlow.getValue();

        if (glowStrength > 0.02f) {
            int rings = 3;
            int samplesPerRing = 10;
            for (int ring = 1; ring <= rings; ring++) {
                float ringRadius = glowStrength * 1.8f * ring / rings;
                float ringAlpha = alpha * (0.30f / ring);
                int glowColor = ColorUtil.setAlpha(baseColor, (int) (255 * ringAlpha));

                for (int s = 0; s < samplesPerRing; s++) {
                    double angle = (2 * Math.PI / samplesPerRing) * s;
                    float ox = (float) (Math.cos(angle) * ringRadius);
                    float oy = (float) (Math.sin(angle) * ringRadius);
                    fr.drawText(fontName, text, x + ox, y + oy, size, glowColor);
                }
            }
        }

        int fillColor = ColorUtil.setAlpha(0xFFFFFFFF, (int) (255 * alpha));
        int outlineColor = ColorUtil.setAlpha(baseColor, (int) (230 * alpha));
        fr.drawTextWithGlow(fontName, text, x, y, size, fillColor, outlineColor, 1.2f);
    }

    private static class PendingHit {
        final LivingEntity entity;
        final float preHealth;
        long lastAttackTime;

        PendingHit(LivingEntity entity, float preHealth, long lastAttackTime) {
            this.entity = entity;
            this.preHealth = preHealth;
            this.lastAttackTime = lastAttackTime;
        }
    }

    private record FloatingDamage(Vec3d pos, float amount, long startTime) {}

    // ---- Event wiring -------------------------------------------------

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (!isState()) return;
        if (enableBubbles.isValue()) renderBubbles(e);
        if (enableEffect.isValue()) renderWaves();
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (!isState() || !enableDamage.isValue()) return;
        renderFloatingDamage();
    }
}
