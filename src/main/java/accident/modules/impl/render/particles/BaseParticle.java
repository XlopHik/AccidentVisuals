package accident.modules.impl.render.particles;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import accident.IMinecraft;
import accident.util.animations.Animation;
import accident.util.animations.Direction;
import accident.util.animations.EaseInOutQuad;
import accident.util.render.сliemtpipeline.ClientPipelines;

import java.util.concurrent.ThreadLocalRandom;

public abstract class BaseParticle implements IMinecraft {

    protected static final Identifier TEXTURE_CROWN      = Identifier.of("accident", "textures/world/crown.png");
    protected static final Identifier TEXTURE_CUBE_BLAST = Identifier.of("accident", "textures/world/cubeblast1.png");
    protected static final Identifier TEXTURE_DOLLAR     = Identifier.of("accident", "textures/world/dollar.png");
    protected static final Identifier TEXTURE_HEART      = Identifier.of("accident", "textures/world/heart.png");
    protected static final Identifier TEXTURE_LIGHTNING  = Identifier.of("accident", "textures/world/lightning.png");
    protected static final Identifier TEXTURE_LINE       = Identifier.of("accident", "textures/world/line.png");
    protected static final Identifier TEXTURE_RHOMBUS    = Identifier.of("accident", "textures/world/rhombus.png");
    protected static final Identifier TEXTURE_SNOWFLAKE  = Identifier.of("accident", "textures/world/snowflake.png");
    protected static final Identifier TEXTURE_STAR       = Identifier.of("accident", "textures/world/star.png");
    protected static final Identifier TEXTURE_STAR_ALT   = Identifier.of("accident", "textures/world/star1.png");
    protected static final Identifier TEXTURE_TRIANGLE   = Identifier.of("accident", "textures/world/triangle.png");
    protected static final Identifier TEXTURE_GLOW       = Identifier.of("accident", "textures/world/dashbloom.png");
    protected static final Identifier GLOW_BLOOM         = Identifier.of("accident", "textures/world/dashbloom.png");
    protected static final Identifier GLOW_BLOOM_SAMPLE  = Identifier.of("accident", "textures/world/dashbloomsample.png");

    public static final RenderLayer LAYER_BLOOM  = ClientPipelines.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM);
    public static final RenderLayer LAYER_SAMPLE = ClientPipelines.WORLD_PARTICLES_GLOW.apply(GLOW_BLOOM_SAMPLE);

    protected static final int[] RANDOM_COLORS = {
            0xFFFF0000, 0xFFFF7F00, 0xFFFFFF00, 0xFF00FF00,
            0xFF00FFFF, 0xFF0000FF, 0xFF8B00FF, 0xFFFF00FF,
            0xFFFF1493, 0xFFFFFFFF, 0xFF00FF7F, 0xFFFF6347
    };

    public double x;
    public double y;
    public double z;
    protected double prevX, prevY, prevZ;
    protected long start;
    protected long lifeTimeMs;
    protected float cachedAlpha = 0f;
    protected long lastAlphaUpdate = 0L;
    protected boolean fadingOut = false;
    protected boolean forceFadeOut = false;
    protected float phase;
    protected float rotation;
    protected float rotationSpeed;
    protected Animation fadeInAnimation;
    protected Animation fadeOutAnimation;

    protected void initBase(long lifeTimeMs, int fadeInMs, int fadeOutMs) {
        long now = System.currentTimeMillis();
        this.start          = now;
        this.lifeTimeMs     = lifeTimeMs;
        this.phase          = ThreadLocalRandom.current().nextFloat() * 100f;
        this.rotation       = ThreadLocalRandom.current().nextFloat() * 360f;
        this.rotationSpeed  = ThreadLocalRandom.current().nextFloat() * 1.5f + 0.5f;
        this.cachedAlpha    = 0f;
        this.lastAlphaUpdate = 0L;
        this.fadingOut      = false;
        this.forceFadeOut   = false;

        this.fadeInAnimation = new EaseInOutQuad().setMs(fadeInMs).setValue(1.0);
        this.fadeInAnimation.setDirection(Direction.FORWARDS);
        this.fadeOutAnimation = new EaseInOutQuad().setMs(fadeOutMs).setValue(1.0);
        this.fadeOutAnimation.setDirection(Direction.FORWARDS);
    }

    protected void updateAlpha(long now) {
        if (!fadingOut && now - this.start > lifeTimeMs) {
            fadingOut = true;
            this.fadeOutAnimation.setDirection(Direction.BACKWARDS);
        }
        if (now - this.lastAlphaUpdate > 16L) {
            this.cachedAlpha = fadingOut
                    ? this.fadeOutAnimation.getOutput().floatValue()
                    : this.fadeInAnimation.getOutput().floatValue();
            this.lastAlphaUpdate = now;
        }
    }

    protected boolean isHit(double px, double py, double pz) {
        if (mc.world == null) return false;
        BlockPos pos = BlockPos.ofFloored(px, py, pz);
        return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
    }

    protected int generateRandomColor() {
        return RANDOM_COLORS[ThreadLocalRandom.current().nextInt(RANDOM_COLORS.length)];
    }

    public void startFadeOut() {
        if (!fadingOut) {
            fadingOut     = true;
            forceFadeOut  = true;
            this.fadeOutAnimation.setDirection(Direction.BACKWARDS);
        }
    }

    public float getAlpha()      { return cachedAlpha; }
    public boolean isFadingOut() { return fadingOut; }
}