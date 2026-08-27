package accident.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import accident.events.api.EventManager;
import accident.events.impl.EventCameraUpdate;

@Mixin(Camera.class)
public abstract class CameraPosMixin {

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"
            )
    )
    private void redirectSetPos(Camera instance, double x, double y, double z) {
        EventCameraUpdate event = new EventCameraUpdate(x, y, z);
        EventManager.callEvent(event);
        this.setPos(event.getX(), event.getY(), event.getZ());
    }
}