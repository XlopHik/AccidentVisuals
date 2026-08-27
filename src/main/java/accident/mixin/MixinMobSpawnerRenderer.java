package accident.mixin;

import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.MobSpawnerBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.MobSpawnerBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.render.NoRender;

@Mixin(MobSpawnerBlockEntityRenderer.class)
public class MixinMobSpawnerRenderer {

    @Inject(
            method = "updateRenderState",
            at = @At("TAIL")
    )
    private void onUpdateRenderState(
            MobSpawnerBlockEntity blockEntity,
            MobSpawnerBlockEntityRenderState renderState,
            float tickDelta,
            Vec3d cameraPos,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
            CallbackInfo ci
    ) {
        if (accident.Initialization.getInstance() == null) return;

        NoRender noRender = NoRender.getInstance();
        if (!noRender.shouldHideSpawner()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        BlockPos spawnerPos = blockEntity.getPos();

        if (!hasLineOfSight(mc.world, mc.player.getEyePos(), spawnerPos)) {
            renderState.displayEntityRenderState = null;
        }
    }

    private boolean hasLineOfSight(World world, Vec3d from, BlockPos to) {
        Vec3d target = new Vec3d(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);
        Vec3d direction = target.subtract(from);
        int steps = (int) Math.ceil(direction.length() * 2);
        if (steps == 0) return true;

        Vec3d step = direction.multiply(1.0 / steps);
        Vec3d current = from;

        for (int i = 1; i < steps; i++) {
            current = current.add(step);
            BlockPos checkPos = BlockPos.ofFloored(current);
            if (checkPos.equals(to)) break;

            if (world.getBlockState(checkPos).isOpaqueFullCube()) {
                return false;
            }
        }
        return true;
    }
}