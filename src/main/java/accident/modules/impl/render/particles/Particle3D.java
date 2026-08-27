package accident.modules.impl.render.particles;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import accident.modules.impl.render.worldparticles.ParticleRenderer;
import accident.util.ColorUtil;
import accident.util.render.сliemtpipeline.ClientPipelines;

import java.util.concurrent.ThreadLocalRandom;

public class Particle3D extends BaseParticle {

    public enum ParticleMode {
        CUBES,
        CROWN, CUBE_BLAST, DOLLAR, HEART, LIGHTNING,
        LINE, RHOMBUS, SNOWFLAKE, STAR, STAR_ALT,
        TRIANGLE, RANDOM
    }

    public enum GlowMode {
        BLOOM,
        BLOOM_SAMPLE,
        BOTH
    }

    private static final ParticleMode[] RANDOM_MODES = {
            ParticleMode.CUBES,    ParticleMode.CROWN,     ParticleMode.CUBE_BLAST,
            ParticleMode.DOLLAR,   ParticleMode.HEART,     ParticleMode.LIGHTNING,
            ParticleMode.LINE,     ParticleMode.RHOMBUS,   ParticleMode.SNOWFLAKE,
            ParticleMode.STAR,     ParticleMode.STAR_ALT,  ParticleMode.TRIANGLE
    };

    private double lastX, lastY, lastZ;
    private double velocityX, velocityY, velocityZ;
    private int color;
    private float scale;

    private float gravityStrength    = 0.04f;
    private float velocityMultiplier = 0.98f;
    private boolean collidesWithWorld = true;
    private boolean spinning          = true;

    private ParticleMode mode       = ParticleMode.CUBES;
    private ParticleMode actualMode = ParticleMode.CUBES;
    private GlowMode glowMode       = GlowMode.BOTH;

    private RenderLayer cachedTextureLayer = null;

    public Particle3D(Vec3d pos, Vec3d velocity, int color, float scale, float maxAgeSeconds) {
        initBase((long) (maxAgeSeconds * 1000), 150, 250);
        this.x = pos.x; this.y = pos.y; this.z = pos.z;
        this.lastX = pos.x; this.lastY = pos.y; this.lastZ = pos.z;
        this.prevX = pos.x; this.prevY = pos.y; this.prevZ = pos.z;
        this.velocityX = velocity.x;
        this.velocityY = velocity.y;
        this.velocityZ = velocity.z;
        this.color = color;
        this.scale = scale;
    }

    public Particle3D setGravity(float gravity) {
        this.gravityStrength = gravity;
        return this;
    }

    public Particle3D setVelocityMultiplier(float mult) {
        this.velocityMultiplier = mult;
        return this;
    }

    public Particle3D setCollision(boolean collision) {
        this.collidesWithWorld = collision;
        return this;
    }

    public Particle3D setMode(ParticleMode mode) {
        this.mode = mode;
        this.actualMode = (mode == ParticleMode.RANDOM)
                ? RANDOM_MODES[ThreadLocalRandom.current().nextInt(RANDOM_MODES.length)]
                : mode;
        var texture = getTextureForMode(this.actualMode);
        this.cachedTextureLayer = (texture != null)
                ? ClientPipelines.WORLD_PARTICLES_GLOW.apply(texture)
                : null;
        return this;
    }

    public Particle3D setGlowMode(GlowMode glowMode) {
        this.glowMode = glowMode;
        return this;
    }

    public Particle3D setSpinning(boolean spinning) {
        this.spinning = spinning;
        return this;
    }

    public void update() {
        long now = System.currentTimeMillis();

        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;

        this.velocityY -= gravityStrength;

        if (collidesWithWorld && mc.world != null) {
            if (isHit(this.x + this.velocityX, this.y, this.z)) {
                this.velocityX *= -0.8;
            } else {
                this.x += this.velocityX;
            }
            if (isHit(this.x, this.y + this.velocityY, this.z)) {
                this.velocityX *= 0.999;
                this.velocityZ *= 0.999;
                this.velocityY *= -0.7;
            } else {
                this.y += this.velocityY;
            }
            if (isHit(this.x, this.y, this.z + this.velocityZ)) {
                this.velocityZ *= -0.8;
            } else {
                this.z += this.velocityZ;
            }
        } else {
            this.x += this.velocityX;
            this.y += this.velocityY;
            this.z += this.velocityZ;
        }

        this.velocityX /= 0.999999;
        this.velocityZ /= 0.999999;

        if (spinning) this.rotation += 2f;

        updateAlpha(now);
    }

    public boolean isDead() { return fadingOut && cachedAlpha <= 0f; }

    public void render(MatrixStack matrices, VertexConsumerProvider immediate,
                       float glowSize, float partialTicks) {

        if (cachedAlpha <= 0f) return;

        Vec3d cameraPos  = mc.gameRenderer.getCamera().getCameraPos();
        float cameraYaw  = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();

        float relX = (float) (MathHelper.lerp(partialTicks, lastX, x) - cameraPos.x);
        float relY = (float) (MathHelper.lerp(partialTicks, lastY, y) - cameraPos.y);
        float relZ = (float) (MathHelper.lerp(partialTicks, lastZ, z) - cameraPos.z);

        if (actualMode == ParticleMode.CUBES) {
            renderCube(matrices, immediate, relX, relY, relZ, glowSize, cameraYaw, cameraPitch);
        } else {
            renderTextured(matrices, immediate, relX, relY, relZ, glowSize, cameraYaw, cameraPitch);
        }
    }

    private void renderCube(MatrixStack matrices, VertexConsumerProvider immediate,
                            float relX, float relY, float relZ, float glowSize,
                            float cameraYaw, float cameraPitch) {

        long now = System.currentTimeMillis();
        float rotationAnim = (float) (now % 9000L) / 9000f * 360f;
        int glowCol = ColorUtil.multAlpha(color, cachedAlpha);
        float size = scale * 0.25f;

        matrices.push();
        matrices.translate(relX, relY, relZ);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationAnim + this.phase));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationAnim * 0.5f));
        Matrix4f mat = matrices.peek().getPositionMatrix();
        ParticleRenderer.drawCube(immediate.getBuffer(ParticleRenderer.getQuadsLayer()),  mat, ColorUtil.multAlpha(color, cachedAlpha * 0.2f), size);
        ParticleRenderer.drawLines(immediate.getBuffer(ParticleRenderer.getLinesLayer()), mat, ColorUtil.multAlpha(color, cachedAlpha * 0.4f), size);
        matrices.pop();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        Matrix4f gMat = matrices.peek().getPositionMatrix();
        renderGlowEffect(immediate, gMat, glowCol, size * glowSize, size * (glowSize / 3f));
        matrices.pop();

        matrices.pop();
    }

    private void renderTextured(MatrixStack matrices, VertexConsumerProvider immediate,
                                float relX, float relY, float relZ, float glowSize,
                                float cameraYaw, float cameraPitch) {

        if (cachedTextureLayer == null) return;

        int glowCol = ColorUtil.multAlpha(color, cachedAlpha);
        float size = scale * 0.5f;
        float half = size / 2f;

        int r = (glowCol >> 16) & 0xFF;
        int g = (glowCol >> 8)  & 0xFF;
        int b =  glowCol        & 0xFF;
        int a = (int) (255 * cachedAlpha);

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        Matrix4f gMat = matrices.peek().getPositionMatrix();
        renderGlowEffect(immediate, gMat, glowCol, size * glowSize * 0.5f, size * glowSize * 0.2f);

        if (spinning) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        Matrix4f mat = matrices.peek().getPositionMatrix();

        VertexConsumer buffer = immediate.getBuffer(cachedTextureLayer);
        buffer.vertex(mat, -half, -half, 0).texture(0, 0).color(r, g, b, a);
        buffer.vertex(mat, -half,  half, 0).texture(0, 1).color(r, g, b, a);
        buffer.vertex(mat,  half,  half, 0).texture(1, 1).color(r, g, b, a);
        buffer.vertex(mat,  half, -half, 0).texture(1, 0).color(r, g, b, a);

        matrices.pop();
    }

    private void renderGlowEffect(VertexConsumerProvider immediate, Matrix4f matrix,
                                  int color, float sizePrimary, float sizeSecondary) {
        switch (glowMode) {
            case BLOOM ->
                    ParticleRenderer.drawGlow(immediate.getBuffer(LAYER_BLOOM),  matrix, color, (int) (80f  * cachedAlpha), sizePrimary);
            case BLOOM_SAMPLE ->
                    ParticleRenderer.drawGlow(immediate.getBuffer(LAYER_SAMPLE), matrix, color, (int) (140f * cachedAlpha), sizeSecondary);
            case BOTH -> {
                ParticleRenderer.drawGlow(immediate.getBuffer(LAYER_BLOOM),  matrix, color, (int) (80f  * cachedAlpha), sizePrimary);
                ParticleRenderer.drawGlow(immediate.getBuffer(LAYER_SAMPLE), matrix, color, (int) (140f * cachedAlpha), sizeSecondary);
            }
        }
    }

    private net.minecraft.util.Identifier getTextureForMode(ParticleMode mode) {
        return switch (mode) {
            case CROWN      -> TEXTURE_CROWN;
            case CUBE_BLAST -> TEXTURE_CUBE_BLAST;
            case DOLLAR     -> TEXTURE_DOLLAR;
            case HEART      -> TEXTURE_HEART;
            case LIGHTNING  -> TEXTURE_LIGHTNING;
            case LINE       -> TEXTURE_LINE;
            case RHOMBUS    -> TEXTURE_RHOMBUS;
            case SNOWFLAKE  -> TEXTURE_SNOWFLAKE;
            case STAR       -> TEXTURE_STAR;
            case STAR_ALT   -> TEXTURE_STAR_ALT;
            case TRIANGLE   -> TEXTURE_TRIANGLE;
            default         -> null;
        };
    }
}