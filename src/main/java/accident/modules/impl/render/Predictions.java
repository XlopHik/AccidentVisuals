package accident.modules.impl.render;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.projectile.thrown.*;
import net.minecraft.item.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import accident.events.api.EventHandler;
import accident.events.impl.DrawEvent;
import accident.events.impl.WorldRenderEvent;
import accident.util.camera.Angle;
import accident.util.camera.AngleConnection;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import accident.util.ColorUtil;
import accident.util.math.Projection;
import accident.util.render.Render2D;
import accident.util.render.Render3D;
import accident.util.render.font.Fonts;
import accident.util.render.сliemtpipeline.ClientPipelines;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class Predictions extends ModuleStructure {

    private final SliderSettings thickness = new SliderSettings("accident.module.predictions.setting.thickness.name", "accident.module.predictions.setting.thickness.desc")
            .range(0.1f, 3.0f).setValue(1.0f);

    private final BooleanSetting inHand = new BooleanSetting("accident.module.predictions.setting.inhand.name", "accident.module.predictions.setting.inhand.desc")
            .setValue(true);

    private final accident.modules.module.setting.implement.ColorSetting lineColor =
            new accident.modules.module.setting.implement.ColorSetting(
                    "accident.module.predictions.setting.linecolor.name",
                    "accident.module.predictions.setting.linecolor.desc")
                    .value(0xFF7C6CFF);

    public Predictions() {
        super("accident.module.predictions.name", "accident.module.predictions.desc", ModuleCategory.RENDER);
        settings(thickness, inHand, lineColor);
    }

    private record Point(ItemStack stack, Vec3d pos, int ticks) {}
    private final List<Point> points = new ArrayList<>();

    // траектория рисуется как одна светящаяся лента, а не набор линий
    private static final Identifier GLOW_TEXTURE = Identifier.of("accident", "images/particle/glow.png");

    private int trailColor() {
        return lineColor.getColor() & 0xFFFFFF;
    }

    // Where a shot lands, and which way the surface faces there.
    private record Impact(Vec3d pos, Direction facing, int color) {}
    private final List<Impact> impacts = new ArrayList<>();

    private record Run(List<Vec3d> points, int color) {}
    private final List<Run> runs = new ArrayList<>();
    private List<Vec3d> currentRun;

    private BufferAllocator allocator;
    private VertexConsumerProvider.Immediate immediate;

    @Override
    public boolean deactivate() {
        points.clear();
        runs.clear();
        impacts.clear();
        currentRun = null;
        if (allocator != null) {
            allocator.close();
            allocator = null;
            immediate = null;
        }
        return false;
    }

    // при потере видимости лента обрывается, а не тянется через невидимый участок
    private void extendRun(Vec3d from, Vec3d to, int color, boolean visible) {
        if (!visible) {
            currentRun = null;
            return;
        }
        if (currentRun == null) {
            currentRun = new ArrayList<>();
            currentRun.add(from);
            runs.add(new Run(currentRun, color));
        }
        currentRun.add(to);
    }

    private boolean isPositionOnScreen(Vec3d pos) {
        Vec3d screenPos = Projection.worldSpaceToScreenSpace(pos);
        if (screenPos == null) return false;
        return screenPos.z > 0 && screenPos.z < 1;
    }

    private boolean hasLineOfSight(Vec3d pos) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        BlockHitResult result = mc.world.raycast(new RaycastContext(
                eyePos, pos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        double distanceToHit = result.getPos().distanceTo(eyePos);
        double distanceToTarget = pos.distanceTo(eyePos);

        return result.getType() == HitResult.Type.MISS ||
                Math.abs(distanceToHit - distanceToTarget) < 0.5;
    }

    private boolean isPositionVisible(Vec3d pos) {
        if (!isPositionOnScreen(pos)) return false;
        return hasLineOfSight(pos);
    }

    private boolean isLineVisible(Vec3d start, Vec3d end) {
        boolean startVisible = isPositionVisible(start);
        boolean endVisible = isPositionVisible(end);

        if (startVisible || endVisible) return true;

        Vec3d midPoint = start.add(end).multiply(0.5);
        return isPositionVisible(midPoint);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        if (mc.world == null || mc.player == null) return;
        points.clear();
        runs.clear();
        impacts.clear();
        currentRun = null;

        if (inHand.isValue()) {
            drawPredictionInHand(
                    List.of(mc.player.getMainHandStack(), mc.player.getOffHandStack())
            );
        }

        getProjectiles().forEach(entity -> {
            Vec3d motion = entity.getVelocity();
            Vec3d pos = entity.getEntityPos();
            Vec3d prevPos;
            int ticks = 0;
            currentRun = null;

            for (int i = 0; i < 300; i++) {
                prevPos = pos;
                pos = pos.add(motion);
                motion = calculateMotion(entity, prevPos, motion);

                BlockHitResult result = mc.world.raycast(new RaycastContext(
                        prevPos, pos,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        entity
                ));

                if (result.getType() != HitResult.Type.MISS) {
                    pos = result.getPos();
                }

                extendRun(prevPos, pos, trailColor(), isLineVisible(prevPos, pos));

                Vec3d finalPrevPos = prevPos;
                Vec3d finalPos = pos;
                boolean inEntity = streamEntities()
                        .filter(e -> e instanceof LivingEntity living
                                && living != mc.player
                                && living.isAlive())
                        .anyMatch(e -> e.getBoundingBox()
                                .expand(0.25)
                                .intersects(finalPrevPos, finalPos));

                if (result.getType() == HitResult.Type.BLOCK
                        || pos.y < mc.world.getBottomY()
                        || inEntity) {
                    breakingBad(entity, pos, ticks);
                    break;
                }
                ticks++;
            }
        });

        renderRuns(event);
    }

    private void renderRuns(WorldRenderEvent event) {
        if (runs.isEmpty() && impacts.isEmpty()) return;

        if (allocator == null) {
            allocator = new BufferAllocator(1 << 20);
            immediate = VertexConsumerProvider.immediate(allocator);
        }

        MatrixStack stack = event.getStack();
        Vec3d cam = event.getCamera().getCameraPos();

        RenderLayer layer = ClientPipelines.WORLD_PARTICLES_GLOW.apply(GLOW_TEXTURE);
        VertexConsumer buffer = immediate.getBuffer(layer);

        stack.push();
        stack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = stack.peek().getPositionMatrix();

        float base = thickness.getValue();

        // свечение делаем несколькими проходами: широкий тусклый ореол, потом тело, потом яркая сердцевина
        for (Run run : runs) {
            emitRibbon(buffer, matrix, cam, run, base * 0.26f, 0.16f, run.color());
            emitRibbon(buffer, matrix, cam, run, base * 0.12f, 0.42f, run.color());
            emitRibbon(buffer, matrix, cam, run, base * 0.055f, 0.85f, run.color());
            emitRibbon(buffer, matrix, cam, run, base * 0.022f, 1.0f, 0xE8FFFF);
        }

        // точка попадания - тот же приём: ореол, тело, яркая сердцевина
        for (Impact impact : impacts) {
            emitImpact(buffer, matrix, impact, 0.62f, 0.16f, impact.color());
            emitImpact(buffer, matrix, impact, 0.34f, 0.38f, impact.color());
            emitImpact(buffer, matrix, impact, 0.17f, 0.75f, impact.color());
            emitImpact(buffer, matrix, impact, 0.07f, 1.0f, 0xE8FFFF);
        }

        stack.pop();
        immediate.draw(layer);
    }

    // квад на поверхности, чуть приподнят, чтобы не мерцал с блоком по глубине
    private void emitImpact(VertexConsumer buffer, Matrix4f matrix, Impact impact,
                            float radius, float alphaScale, int rgb) {
        Direction facing = impact.facing();
        Vec3d normal = new Vec3d(facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ());

        // ось "up" не важна - свечение круглое, любая перпендикулярная грани ось подойдёт
        Vec3d up = Math.abs(normal.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d right = normal.crossProduct(up).normalize().multiply(radius);
        Vec3d down = normal.crossProduct(right).normalize().multiply(radius);

        Vec3d centre = impact.pos().add(normal.multiply(0.012));

        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        int color = ColorUtil.rgba(r, g, b, (int) (alphaScale * 235));

        Vec3d p0 = centre.subtract(right).subtract(down);
        Vec3d p1 = centre.add(right).subtract(down);
        Vec3d p2 = centre.add(right).add(down);
        Vec3d p3 = centre.subtract(right).add(down);

        buffer.vertex(matrix, (float) p0.x, (float) p0.y, (float) p0.z).texture(0f, 0f).color(color);
        buffer.vertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z).texture(0f, 1f).color(color);
        buffer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).texture(1f, 1f).color(color);
        buffer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).texture(1f, 0f).color(color);
    }

    private void emitRibbon(VertexConsumer buffer, Matrix4f matrix, Vec3d cam,
                            Run run, float halfWidth, float alphaScale, int rgb) {
        List<Vec3d> pts = run.points();
        if (pts.size() < 2) return;

        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;

        for (int i = 0; i < pts.size() - 1; i++) {
            Vec3d curr = pts.get(i);
            Vec3d next = pts.get(i + 1);

            Vec3d dir = next.subtract(curr);
            if (dir.lengthSquared() < 1.0e-6) continue;
            dir = dir.normalize();

            Vec3d side = dir.crossProduct(curr.subtract(cam).normalize());
            if (side.lengthSquared() < 1.0e-6) continue;
            side = side.normalize();

            // плавное появление в начале, чтобы лента не забивала весь экран прямо у камеры
            float a1 = MathHelper.clamp(i / 25.0f, 0f, 1f);
            float a2 = MathHelper.clamp((i + 1) / 25.0f, 0f, 1f);

            int c1 = ColorUtil.rgba(r, g, b, (int) (a1 * alphaScale * 255));
            int c2 = ColorUtil.rgba(r, g, b, (int) (a2 * alphaScale * 255));

            float w1 = halfWidth * (0.35f + 0.65f * a1);
            float w2 = halfWidth * (0.35f + 0.65f * a2);

            buffer.vertex(matrix, (float) (curr.x + side.x * w1), (float) (curr.y + side.y * w1), (float) (curr.z + side.z * w1)).texture(0.5f, 0f).color(c1);
            buffer.vertex(matrix, (float) (curr.x - side.x * w1), (float) (curr.y - side.y * w1), (float) (curr.z - side.z * w1)).texture(0.5f, 1f).color(c1);
            buffer.vertex(matrix, (float) (next.x - side.x * w2), (float) (next.y - side.y * w2), (float) (next.z - side.z * w2)).texture(0.5f, 1f).color(c2);
            buffer.vertex(matrix, (float) (next.x + side.x * w2), (float) (next.y + side.y * w2), (float) (next.z + side.z * w2)).texture(0.5f, 0f).color(c2);
        }
    }


    @EventHandler
    public void onDraw(DrawEvent e) {
        if (mc.world == null || mc.player == null) return;

        for (Point point : points) {
            Vec3d displayPos = point.pos.add(0, 0.5, 0);

            if (!isPositionVisible(displayPos)) continue;

            Vec3d screenPos = Projection.worldSpaceToScreenSpace(displayPos);
            if (screenPos == null) continue;
            if (screenPos.z <= 0 || screenPos.z >= 1) continue;

            double time = point.ticks * 50 / 1000.0;
            String text = String.format("%.1f", time) + "s";

            float cx = (float) screenPos.x;
            float cy = (float) screenPos.y;

            float iconSize   = 16 * 0.7f;
            float circleR    = iconSize / 2f + 4f;
            float padding    = 3f;

            float fontSize   = 5.5f;
            float textWidth  = Fonts.BOLD.getWidth(text, fontSize);
            float textHeight = Fonts.BOLD.getHeight(fontSize);

            float bgSize = circleR * 2f;
            float bgX = cx - circleR;
            float bgY = cy - circleR;

            // матовый диск как в худе, с лёгким ореолом по краю для видимости на любом фоне
            Render2D.blur(bgX, bgY, bgSize, bgSize, 10f, circleR,
                    new Color(15, 17, 31, 70).getRGB());
            Render2D.outline(bgX - 1f, bgY - 1f, bgSize + 2f, bgSize + 2f, 1f,
                    new Color(0, 220, 255, 45).getRGB(), circleR + 1f);
            Render2D.outline(bgX, bgY, bgSize, bgSize, 0.8f,
                    new Color(120, 240, 255, 170).getRGB(), circleR);

            e.getDrawContext().getMatrices().pushMatrix();
            float iconOffX = cx - iconSize / 2f;
            float iconOffY = cy - iconSize / 2f;
            e.getDrawContext().getMatrices().translate(iconOffX, iconOffY);
            e.getDrawContext().getMatrices().scale(0.7f, 0.7f);
            e.getDrawContext().drawItem(point.stack, 0, 0);
            e.getDrawContext().getMatrices().popMatrix();

            float timerBgW = textWidth + 6f;
            float timerBgH = textHeight + 3f;
            float timerBgX = cx - timerBgW / 2f;
            float timerBgY = cy + circleR + 2f;

            Render2D.blur(timerBgX, timerBgY, timerBgW, timerBgH, 10f, timerBgH / 2f,
                    new Color(15, 17, 31, 70).getRGB());
            Render2D.outline(timerBgX, timerBgY, timerBgW, timerBgH, 0.7f,
                    new Color(120, 240, 255, 90).getRGB(), timerBgH / 2f);

            Fonts.BOLD.draw(
                    text,
                    timerBgX + 3f,
                    timerBgY + (timerBgH - textHeight) / 2f,
                    fontSize,
                    new Color(255, 255, 255, 235).getRGB()
            );
        }
    }


    public void drawPredictionInHand(List<ItemStack> stacks) {
        Item activeItem = mc.player.getActiveItem().getItem();

        for (ItemStack stack : stacks) {
            Angle currentAngle = AngleConnection.INSTANCE.getRotation();
            Vec3d look = currentAngle != null
                    ? currentAngle.toVector()
                    : mc.player.getRotationVec(mc.getRenderTickCounter().getTickProgress(false));

            List<HitResult> results = switch (stack.getItem()) {
                case ExperienceBottleItem item ->
                        checkTrajectory(look, new ExperienceBottleEntity(mc.world, mc.player, stack), 0.8);
                case SplashPotionItem item ->
                        checkTrajectory(look, new SplashPotionEntity(mc.world, mc.player, stack), 0.55);
                case TridentItem item
                        when item.equals(activeItem) && mc.player.getItemUseTime() >= 10 ->
                        checkTrajectory(look, new TridentEntity(mc.world, mc.player, stack), 2.5);
                case SnowballItem item ->
                        checkTrajectory(look, new SnowballEntity(mc.world, mc.player, stack), 1.5);
                case EggItem item ->
                        checkTrajectory(look, new EggEntity(mc.world, mc.player, stack), 1.5);
                case EnderPearlItem item ->
                        checkTrajectory(look, new EnderPearlEntity(mc.world, mc.player, stack), 1.5);
                case BowItem item
                        when item.equals(activeItem) && mc.player.isUsingItem() -> {
                    float progress = mc.getRenderTickCounter().getTickProgress(false);
                    float pull = bowPullProgress(mc.player.getItemUseTime() + progress);
                    yield checkTrajectory(look,
                            new ArrowEntity(mc.world, mc.player, stack, stack), 3 * pull);
                }
                case CrossbowItem item when CrossbowItem.isCharged(stack) -> {
                    ChargedProjectilesComponent component =
                            stack.get(DataComponentTypes.CHARGED_PROJECTILES);
                    List<HitResult> list = new ArrayList<>();
                    if (component != null) {
                        boolean isFirework = component.getProjectiles()
                                .getFirst().isOf(Items.FIREWORK_ROCKET);
                        float velocity = isFirework ? 100 : 3;

                        list.addAll(checkTrajectory(look,
                                new ArrowEntity(mc.world, mc.player, stack, stack), velocity));

                        if (component.getProjectiles().size() > 2) {
                            float pitchAbs = mc.player.getPitch() / 90f;
                            float delta = pitchAbs * pitchAbs * pitchAbs * pitchAbs * pitchAbs;
                            float yawOff   = MathHelper.lerp(Math.abs(delta), 10f, 90f);
                            float pitchOff = MathHelper.lerp(delta, 0f, 10f);

                            Vec3d leftLook  = angleToVec(
                                    mc.player.getYaw() - yawOff,
                                    mc.player.getPitch() - pitchOff);
                            Vec3d rightLook = angleToVec(
                                    mc.player.getYaw() + yawOff,
                                    mc.player.getPitch() - pitchOff);

                            list.addAll(checkTrajectory(leftLook,
                                    new ArrowEntity(mc.world, mc.player, stack, stack), velocity));
                            list.addAll(checkTrajectory(rightLook,
                                    new ArrowEntity(mc.world, mc.player, stack, stack), velocity));
                        }
                    }
                    yield list;
                }
                default -> null;
            };

            if (results != null) {
                results = results.stream().filter(Objects::nonNull).toList();
                if (!results.isEmpty()) renderProjectileResults(results);
            }
            return;
        }
    }


    public void renderProjectileResults(List<HitResult> results) {
        for (HitResult result : results) {
            Vec3d pos = result.getPos();
            if (!isPositionVisible(pos)) continue;

            impacts.add(new Impact(
                    pos,
                    getDirection(result),
                    result.getType() == HitResult.Type.ENTITY ? 0xFF5A6E : trailColor()));
        }
    }


    public List<HitResult> checkTrajectory(Vec3d look, ProjectileEntity entity, double velocity) {
        // на земле обнуляем вертикальную скорость игрока, иначе бросок чуть проседает вниз
        Vec3d playerMotion = mc.player.getVelocity();
        if (mc.player.isOnGround()) playerMotion = new Vec3d(playerMotion.x, 0, playerMotion.z);

        double distance = look.length();
        Vec3d scaledMotion = look.multiply(velocity / distance).add(playerMotion);

        Vec3d startPos = mc.player.getEyePos();

        List<HitResult> list = new ArrayList<>();
        list.add(traceTrajectory(startPos, scaledMotion, entity));
        return list;
    }

    public HitResult traceTrajectory(Vec3d pos, Vec3d motion, ProjectileEntity entity) {
        currentRun = null;
        for (int i = 0; i < 300; i++) {
            Vec3d nextPos = pos.add(motion);

            BlockHitResult result = mc.world.raycast(new RaycastContext(
                    pos, nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    entity
            ));

            boolean hitBlock = result.getType() != HitResult.Type.MISS;
            Vec3d drawEnd = hitBlock ? result.getPos() : nextPos;

            extendRun(pos, drawEnd, trailColor(), isLineVisible(pos, drawEnd));

            if (hitBlock) {
                return result;
            }

            Vec3d finalPos = pos;
            Vec3d finalNextPos = nextPos;
            boolean hitEntity = streamEntities()
                    .filter(e -> e != entity.getOwner()
                            && e instanceof LivingEntity living
                            && living != mc.player
                            && living.isAlive())
                    .anyMatch(e -> e.getBoundingBox()
                            .expand(0.3)
                            .intersects(finalPos, finalNextPos));

            if (hitEntity) {
                return new HitResult(nextPos) {
                    @Override public Type getType() { return Type.ENTITY; }
                };
            }

            if (nextPos.y < mc.world.getBottomY()) break;

            motion = calculateMotion(entity, pos, motion);
            pos = nextPos;
        }
        return null;
    }


    // как BowItem.getPullProgress(int) - натяжение нелинейное, просто useTicks/20 даёт завышенную скорость стрелы
    private static float bowPullProgress(float useTicks) {
        float f = useTicks / 20f;
        f = (f * f + f * 2f) / 3f;
        return MathHelper.clamp(f, 0f, 1f);
    }

    public Vec3d calculateMotion(Entity entity, Vec3d prevPos, Vec3d motion) {
        boolean isInWater = mc.world
                .getBlockState(BlockPos.ofFloored(prevPos))
                .getFluidState()
                .isIn(FluidTags.WATER);

        double gravity = entity.getFinalGravity();

        // разные снаряды применяют гравитацию и сопротивление в разном порядке - перепутаешь, будет неверная скорость падения
        return switch (entity) {
            // PersistentProjectileEntity/TridentEntity: сначала сопротивление, потом гравитация
            case TridentEntity e              -> motion.multiply(0.99).add(0, -gravity, 0);
            case PersistentProjectileEntity e -> motion.multiply(isInWater ? 0.6 : 0.99).add(0, -gravity, 0);
            // ThrownEntity (снежок, яйцо, жемчуг, зелье, бутылка опыта): сначала гравитация, потом сопротивление
            default -> motion.add(0, -gravity, 0).multiply(isInWater ? 0.8 : 0.99);
        };
    }


    private List<Entity> getProjectiles() {
        return streamEntities()
                .filter(e -> (e instanceof PersistentProjectileEntity
                        || e instanceof ThrownItemEntity
                        || e instanceof ItemEntity)
                        && !isStationary(e)
                        && !e.isTouchingWater())
                .toList();
    }

    private java.util.stream.Stream<Entity> streamEntities() {
        return StreamSupport.stream(mc.world.getEntities().spliterator(), false);
    }

    private boolean isStationary(Entity entity) {
        boolean posChange = entity.getX() == entity.lastRenderX
                && entity.getY() == entity.lastRenderY
                && entity.getZ() == entity.lastRenderZ;
        boolean itemOnGround = entity instanceof ItemEntity && entity.isOnGround();
        return posChange || itemOnGround;
    }

    private Vec3d angleToVec(float yaw, float pitch) {
        double yawRad   = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vec3d(
                -MathHelper.sin((float) yawRad) * MathHelper.cos((float) pitchRad),
                -MathHelper.sin((float) pitchRad),
                MathHelper.cos((float) yawRad)  * MathHelper.cos((float) pitchRad)
        );
    }

    private Vec3d circlePoint(Vec3d center, Direction dir,
                              int i, int segments, float radius, float offset) {
        double angle = i * Math.PI * 2.0 / segments;
        float cos = (float) (Math.cos(angle) * radius);
        float sin = (float) (-Math.sin(angle) * radius);

        return switch (dir) {
            case UP, DOWN -> new Vec3d(
                    center.x + cos,
                    center.y + (dir == Direction.UP ? offset : -offset),
                    center.z + sin);
            case NORTH, SOUTH -> new Vec3d(
                    center.x + cos,
                    center.y + sin,
                    center.z + (dir == Direction.SOUTH ? offset : -offset));
            default -> new Vec3d(
                    center.x + (dir == Direction.EAST ? offset : -offset),
                    center.y + sin,
                    center.z + cos);
        };
    }

    private void drawCross(Vec3d pos, Direction dir, float size, float off, int color) {
        if (!isPositionVisible(pos)) return;

        float lw = thickness.getValue();
        switch (dir) {
            case UP, DOWN -> {
                double yo = dir == Direction.UP ? off : -off;
                Render3D.drawLine(new Vec3d(pos.x - size, pos.y + yo, pos.z),
                        new Vec3d(pos.x + size, pos.y + yo, pos.z), color, lw, false);
                Render3D.drawLine(new Vec3d(pos.x, pos.y + yo, pos.z - size),
                        new Vec3d(pos.x, pos.y + yo, pos.z + size), color, lw, false);
            }
            case NORTH, SOUTH -> {
                double zo = dir == Direction.SOUTH ? off : -off;
                Render3D.drawLine(new Vec3d(pos.x - size, pos.y, pos.z + zo),
                        new Vec3d(pos.x + size, pos.y, pos.z + zo), color, lw, false);
                Render3D.drawLine(new Vec3d(pos.x, pos.y - size, pos.z + zo),
                        new Vec3d(pos.x, pos.y + size, pos.z + zo), color, lw, false);
            }
            default -> {
                double xo = dir == Direction.EAST ? off : -off;
                Render3D.drawLine(new Vec3d(pos.x + xo, pos.y, pos.z - size),
                        new Vec3d(pos.x + xo, pos.y, pos.z + size), color, lw, false);
                Render3D.drawLine(new Vec3d(pos.x + xo, pos.y - size, pos.z),
                        new Vec3d(pos.x + xo, pos.y + size, pos.z), color, lw, false);
            }
        }
    }

    private Direction getDirection(HitResult result) {
        if (result instanceof BlockHitResult bhr) return bhr.getSide();
        Vec3d diff = result.getPos().subtract(mc.player.getEyePos()).normalize();
        return Direction.getFacing(diff.x, diff.y, diff.z);
    }

    private void breakingBad(Entity entity, Vec3d pos, int ticks) {
        switch (entity) {
            case ItemEntity item        -> points.add(new Point(item.getStack(), pos, ticks));
            case ThrownItemEntity thrown -> points.add(new Point(thrown.getStack(), pos, ticks));
            case PersistentProjectileEntity p -> points.add(new Point(p.getItemStack(), pos, ticks));
            default -> {}
        }
    }
}


