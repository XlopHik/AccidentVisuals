package accident.modules.impl.render.worldparticles;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import accident.modules.impl.render.particles.BaseParticle;
import accident.util.ColorUtil;
import accident.util.render.сliemtpipeline.ClientPipelines;

import java.util.concurrent.ThreadLocalRandom;

public class Particle extends BaseParticle {

    public enum ParticleType {
        CUBE_3D,
        CROWN, CUBE_BLAST, DOLLAR, HEART, LIGHTNING,
        LINE, RHOMBUS, SNOWFLAKE, STAR, STAR_ALT,
        TRIANGLE, GLOW, RANDOM
    }

    private static final ParticleType[] RANDOM_TYPES = {
            ParticleType.CROWN, ParticleType.CUBE_BLAST, ParticleType.DOLLAR,
            ParticleType.HEART, ParticleType.LIGHTNING,  ParticleType.LINE,
            ParticleType.RHOMBUS, ParticleType.SNOWFLAKE, ParticleType.STAR,
            ParticleType.STAR_ALT, ParticleType.TRIANGLE, ParticleType.GLOW
    };

    private double mX, mY, mZ;
    private ParticleType type;
    private ParticleType actualType;
    private net.minecraft.client.render.RenderLayer cachedTextureLayer;
    private float colorProgress = 0f;
    private int randomColor;
    private long spawnTime;
    private boolean physicsEnabled = true;
    private float size = 0.5f;

    public Particle(double x, double y, double z, long lifeTimeMs) {
        initBase(lifeTimeMs, 600, 400);
        this.x = x; this.y = y; this.z = z;
        this.prevX = x; this.prevY = y; this.prevZ = z;
        this.spawnTime = this.start;
        this.mX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.04;
        this.mY = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.04;
        this.mZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.04;
        this.randomColor = generateRandomColor();
        this.type = ParticleType.CUBE_3D;
        this.actualType = ParticleType.CUBE_3D;
    }

    public Particle(double x, double y, double z, double mx, double my, double mz, long lifeTimeMs) {
        this(x, y, z, lifeTimeMs);
        this.mX = mx;
        this.mY = my;
        this.mZ = mz;
    }

    public Particle setType(ParticleType type) {
        this.type = type;
        this.actualType = (type == ParticleType.RANDOM)
                ? RANDOM_TYPES[ThreadLocalRandom.current().nextInt(RANDOM_TYPES.length)]
                : type;
        var texture = getTextureForType(this.actualType);
        this.cachedTextureLayer = (texture != null)
                ? ClientPipelines.WORLD_PARTICLES_GLOW.apply(texture)
                : null;
        return this;
    }

    public Particle setPhysics(boolean enabled) {
        this.physicsEnabled = enabled;
        return this;
    }

    public Particle setSize(float size) {
        this.size = size;
        return this;
    }

    public void reset(double x, double y, double z, double mx, double my, double mz,
                      long lifeTimeMs, ParticleType type, boolean physics, float size) {
        initBase(lifeTimeMs, 600, 400);
        this.x = x; this.y = y; this.z = z;
        this.prevX = x; this.prevY = y; this.prevZ = z;
        this.mX = mx; this.mY = my; this.mZ = mz;
        this.spawnTime = this.start;
        this.colorProgress = 0f;
        this.randomColor = generateRandomColor();
        this.physicsEnabled = physics;
        this.size = size;
        setType(type);
    }

    public void update(long now) {
        if (mc.world == null) return;

        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;

        double velMagSq = this.mX * this.mX + this.mY * this.mY + this.mZ * this.mZ;
        if (velMagSq > 1.0E-4) {
            if (isHit(this.x + this.mX, this.y, this.z))       this.mX *= -0.8; else this.x += this.mX;
            if (isHit(this.x, this.y + this.mY, this.z))       this.mY *= -0.8; else this.y += this.mY;
            if (isHit(this.x, this.y, this.z + this.mZ))       this.mZ *= -0.8; else this.z += this.mZ;
        } else {
            this.x += this.mX;
            this.y += this.mY;
            this.z += this.mZ;
        }

        this.mX *= 0.99;
        this.mY *= 0.99;
        this.mZ *= 0.99;

        if (physicsEnabled) this.mY -= 0.0002;

        this.rotation += this.rotationSpeed;

        updateAlpha(now);

        long timeSinceSpawn = now - spawnTime;
        this.colorProgress = timeSinceSpawn < 7000L ? (float) timeSinceSpawn / 7000f : 1f;
    }

    public int getColor(int baseColor, boolean useRandomColor, boolean whiteOnSpawn) {
        if (useRandomColor) return ColorUtil.multAlpha(randomColor, cachedAlpha);

        if (whiteOnSpawn && colorProgress < 1f) {
            int targetR = (baseColor >> 16) & 0xFF;
            int targetG = (baseColor >> 8)  & 0xFF;
            int targetB =  baseColor        & 0xFF;
            int targetA = (baseColor >> 24) & 0xFF;

            int r = (int) (255 + (targetR - 255) * colorProgress);
            int g = (int) (255 + (targetG - 255) * colorProgress);
            int b = (int) (255 + (targetB - 255) * colorProgress);

            return ColorUtil.multAlpha((targetA << 24) | (r << 16) | (g << 8) | b, cachedAlpha);
        }

        return ColorUtil.multAlpha(baseColor, cachedAlpha);
    }

    public boolean shouldRemove() { return fadingOut && cachedAlpha <= 0f; }

    public double getDistanceSquaredTo(Vec3d pos) {
        double dx = x - pos.x, dy = y - pos.y, dz = z - pos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double getHorizontalDistanceSquaredTo(Vec3d pos) {
        double dx = x - pos.x, dz = z - pos.z;
        return dx * dx + dz * dz;
    }

    public void render(MatrixStack matrices, VertexConsumerProvider immediate, Vec3d cameraPos,
                       int baseColor, float globalRotation, float cameraYaw, float cameraPitch,
                       float glowSize, boolean useRandomColor, boolean whiteOnSpawn,
                       boolean whiteCenter, float partialTicks) {

        if (cachedAlpha <= 0f) return;

        float relX = (float) (prevX + (x - prevX) * partialTicks - cameraPos.x);
        float relY = (float) (prevY + (y - prevY) * partialTicks - cameraPos.y);
        float relZ = (float) (prevZ + (z - prevZ) * partialTicks - cameraPos.z);

        int color = getColor(baseColor, useRandomColor, whiteOnSpawn);

        if (actualType == ParticleType.CUBE_3D) {
            renderCube3D(matrices, immediate, relX, relY, relZ, color, globalRotation, cameraYaw, cameraPitch, glowSize);
        } else {
            renderTextured(matrices, immediate, relX, relY, relZ, color, cameraYaw, cameraPitch, glowSize, whiteCenter);
        }
    }

    private void renderCube3D(MatrixStack matrices, VertexConsumerProvider immediate,
                              float relX, float relY, float relZ, int color,
                              float globalRotation, float cameraYaw, float cameraPitch, float glowSize) {

        float cubeSize = size * 0.5f;
        float rotY = globalRotation + this.phase;
        float rotX = globalRotation * 0.5f;

        matrices.push();
        matrices.translate(relX, relY, relZ);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        Matrix4f mat = matrices.peek().getPositionMatrix();
        ParticleRenderer.drawCube(immediate.getBuffer(ParticleRenderer.getQuadsLayer()), mat, ColorUtil.multAlpha(color, cachedAlpha * 0.2f), cubeSize);
        ParticleRenderer.drawLines(immediate.getBuffer(ParticleRenderer.getLinesLayer()), mat, ColorUtil.multAlpha(color, cachedAlpha * 0.4f), cubeSize);
        matrices.pop();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        Matrix4f gMat = matrices.peek().getPositionMatrix();
        ParticleRenderer.drawGlow(immediate.getBuffer(ParticleRenderer.getGlowLayer()),          gMat, color, (int) (80f  * cachedAlpha), cubeSize * glowSize);
        ParticleRenderer.drawGlow(immediate.getBuffer(ParticleRenderer.getGlowLayerSecondary()), gMat, color, (int) (140f * cachedAlpha), cubeSize * (glowSize / 3f));
        matrices.pop();

        matrices.pop();
    }

    private void renderTextured(MatrixStack matrices, VertexConsumerProvider immediate,
                                float relX, float relY, float relZ, int color,
                                float cameraYaw, float cameraPitch, float glowSize, boolean whiteCenter) {

        if (cachedTextureLayer == null) return;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8)  & 0xFF;
        int b =  color        & 0xFF;
        int a = (int) (255 * cachedAlpha);
        float half = size * 0.25f;

        matrices.push();
        matrices.translate(relX, relY, relZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

        Matrix4f gMat = matrices.peek().getPositionMatrix();
        ParticleRenderer.drawGlow(immediate.getBuffer(LAYER_BLOOM),  gMat, color, (int) (80f  * cachedAlpha), half * glowSize);
        ParticleRenderer.drawGlow(immediate.getBuffer(LAYER_SAMPLE), gMat, color, (int) (140f * cachedAlpha), half * glowSize * 0.4f);

        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        Matrix4f mat = matrices.peek().getPositionMatrix();

        VertexConsumer buffer = immediate.getBuffer(cachedTextureLayer);
        buffer.vertex(mat, -half, -half, 0).texture(0, 0).color(r, g, b, a);
        buffer.vertex(mat, -half,  half, 0).texture(0, 1).color(r, g, b, a);
        buffer.vertex(mat,  half,  half, 0).texture(1, 1).color(r, g, b, a);
        buffer.vertex(mat,  half, -half, 0).texture(1, 0).color(r, g, b, a);

        if (whiteCenter) {
            float cs = half * 0.5f;
            int whiteA = (int) (200 * cachedAlpha);
            buffer.vertex(mat, -cs, -cs, 0.001f).texture(0, 0).color(255, 255, 255, whiteA);
            buffer.vertex(mat, -cs,  cs, 0.001f).texture(0, 1).color(255, 255, 255, whiteA);
            buffer.vertex(mat,  cs,  cs, 0.001f).texture(1, 1).color(255, 255, 255, whiteA);
            buffer.vertex(mat,  cs, -cs, 0.001f).texture(1, 0).color(255, 255, 255, whiteA);
        }

        matrices.pop();
    }

    private net.minecraft.util.Identifier getTextureForType(ParticleType type) {
        return switch (type) {
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
            case GLOW       -> TEXTURE_GLOW;
            default         -> null;
        };
    }
}