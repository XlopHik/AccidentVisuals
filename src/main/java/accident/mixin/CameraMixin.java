package accident.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.Initialization;
import accident.events.api.EventManager;
import accident.events.impl.CameraEvent;
import accident.events.impl.CameraPositionEvent;
import accident.modules.impl.render.SmallModel;
import accident.util.camera.Angle;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private Vec3d pos;
    @Shadow @Final private BlockPos.Mutable blockPos;
    @Shadow private float yaw;
    @Shadow private float pitch;

    @Shadow public abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void moveBy(float f, float g, float h);
    @Shadow protected abstract float clipToSpace(float f);
    @Shadow protected abstract void setPos(Vec3d pos);

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V", shift = At.Shift.AFTER), cancellable = true)
    private void updateHook(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {

        if (focusedEntity instanceof ClientPlayerEntity player) {
            var manager = Initialization.getInstance().getManager();
            if (manager != null) {
                SmallModel smallModel = manager.getModuleProvider().get(SmallModel.class);
                if (smallModel != null && smallModel.isState()) {
                    float scale = smallModel.scale.getValue();

                    Vec3d lerpedPos = player.getLerpedPos(tickProgress);

                    double newEyeHeight = 1.62f * scale;

                    this.setPos(new Vec3d(lerpedPos.x, lerpedPos.y + newEyeHeight, lerpedPos.z));
                }
            }
        }

        CameraEvent event = new CameraEvent(false, 4, new Angle(yaw, pitch));
        EventManager.callEvent(event);
        Angle angle = event.getAngle();

        if (event.isCancelled() && focusedEntity instanceof ClientPlayerEntity player && !player.isSleeping() && thirdPerson) {
            float pitch = inverseView ? -angle.getPitch() : angle.getPitch();
            float yaw = angle.getYaw() - (inverseView ? 180 : 0);
            float distance = event.getDistance();

            setRotation(yaw, pitch);
            // ИСПРАВЛЕНО: всегда используем clipToSpace, игнорируем isCameraClip()
            moveBy(-clipToSpace(distance), 0.0F, 0.0F);
            ci.cancel();
        }
    }

    @Inject(method = "setPos(Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), cancellable = true)
    private void posHook(Vec3d pos, CallbackInfo ci) {
        CameraPositionEvent event = new CameraPositionEvent(pos);
        EventManager.callEvent(event);
        this.pos = event.getPos();
        this.blockPos.set(this.pos.x, this.pos.y, this.pos.z);
        ci.cancel();
    }
}