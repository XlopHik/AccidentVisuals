package accident.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.render.Charms;
import accident.util.render.EntityRenderInterceptor;

@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin {

    // перехватывает рендер целевых сущностей (игроков) и откладывает на второй проход
    // остальные сущности рисуются как обычно в первом RenderDispatcher.render(),
    // потом WorldRendererMixin снимает "before", реплеит отложенное, и второй проход
    // рисует их отдельно - RenderDispatcherMixin.TAIL снимает "after" и композит Charms
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <S extends EntityRenderState> void onRenderEntityHead(
            S renderState,
            CameraRenderState cameraState,
            double offsetX, double offsetY, double offsetZ,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CallbackInfo ci
    ) {
        // Never intercept during our own replay pass
        if (EntityRenderInterceptor.skipInterception) return;

        Charms charmsMod = Charms.getInstance();
        if (charmsMod == null || !charmsMod.isState()) return;
        if (!charmsMod.shouldApplyTo(renderState)) return;

        // проверка видимости - без неё в маске появляются "призрачные" силуэты:
        // - под водой: прозрачный terrain рендерится после наших depth-снимков,
        //   вода не окклюдит, и утопленников/рыбу видно сквозь неё
        // - в ещё не скомпилированных чанках: блоки уже есть в мире, но меш на GPU нет
        // CPU-рейкаст читает данные мира напрямую и покрывает оба случая
        // (плюс листва и паутина - отдельным проходом ниже)
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.player != null) {
            Vec3d entityPos = cameraState.pos.add(offsetX, offsetY + renderState.height * 0.5, offsetZ);
            if (isOccluded(mc, cameraState.pos, renderState)
                    || isBehindFoliage(mc.world, cameraState.pos, entityPos)) {
                return; // no cancel — normal render proceeds, just no Charms interception
            }
        }

        // пропускаем сущности, чей ЦЕНТР слишком близко к камере - центр, а не ноги,
        // чтобы не ловить ложные срабатывания когда камеру прижимает к земле рельефом
        double centerOffsetY = offsetY + renderState.height * 0.5;
        double distSqCenter = offsetX * offsetX + centerOffsetY * centerOffsetY + offsetZ * offsetZ;
        if (distSqCenter < 0.25) return; // < 0.5 blocks from center: camera inside entity

        // Defer to second isolated pass; skip normal queue population for this entity
        EntityRenderInterceptor.getInstance().store(
                renderState, cameraState,
                offsetX, offsetY, offsetZ,
                matrices, queue
        );
        ci.cancel();
    }

    // true если нет прямой видимости ни на голову, ни на ноги сущности.
    // вода блокирует только если камера сама не под водой - иначе смотря в океан
    // сверху подсвечивали бы утопленников/рыбу под поверхностью
    // ShapeType.COLLIDER: блокируют твёрдые блоки и листва, трава/цветы - нет
    private static boolean isOccluded(MinecraftClient mc, Vec3d camPos, EntityRenderState state) {
        RaycastContext.FluidHandling fluids = mc.player.isSubmergedInWater()
                ? RaycastContext.FluidHandling.NONE
                : RaycastContext.FluidHandling.ANY;

        Vec3d head = new Vec3d(state.x, state.y + state.height * 0.85, state.z);
        if (mc.world.raycast(new RaycastContext(camPos, head,
                RaycastContext.ShapeType.COLLIDER, fluids, mc.player)).getType() == HitResult.Type.MISS) {
            return false;
        }
        Vec3d feet = new Vec3d(state.x, state.y + state.height * 0.15, state.z);
        return mc.world.raycast(new RaycastContext(camPos, feet,
                RaycastContext.ShapeType.COLLIDER, fluids, mc.player)).getType() != HitResult.Type.MISS;
    }

    private static boolean isBehindFoliage(World world, Vec3d from, Vec3d to) {
        Vec3d dir = to.subtract(from);
        int steps = (int) Math.ceil(dir.length() * 2);
        if (steps == 0) return false;
        Vec3d step = dir.multiply(1.0 / steps);
        Vec3d cur = from;
        for (int i = 1; i < steps; i++) {
            cur = cur.add(step);
            var blockState = world.getBlockState(BlockPos.ofFloored(cur));
            if (blockState.isIn(BlockTags.LEAVES) || blockState.isOf(Blocks.COBWEB)) return true;
        }
        return false;
    }
}
