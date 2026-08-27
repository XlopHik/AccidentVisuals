package accident.mixin;

import accident.modules.impl.render.HitModules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.IMinecraft;
import accident.Initialization;
import accident.events.api.EventManager;
import accident.events.impl.PlayerTravelEvent;
import accident.events.impl.PushEvent;
import accident.events.impl.SwimmingEvent;
import accident.util.camera.AngleConnection;
import accident.modules.impl.render.SmallModel;


@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements IMinecraft {

    @Inject(method = "isPushedByFluids", at = @At("HEAD"), cancellable = true)
    public void isPushedByFluids(CallbackInfoReturnable<Boolean> cir) {
        PushEvent event = new PushEvent(PushEvent.Type.WATER);
        EventManager.callEvent(event);
        if (event.isCancelled()) cir.setReturnValue(false);
    }

    @ModifyExpressionValue(method = "knockbackTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float hookKnockbackRotation(float original) {
        if ((Object) this == mc.player && AngleConnection.INSTANCE.getMoveRotation() != null) {
            return AngleConnection.INSTANCE.getMoveRotation().getYaw();
        }
        return original;
    }

    @ModifyExpressionValue(method = "doSweepingAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float hookSweepRotation(float original) {
        if ((Object) this == mc.player && AngleConnection.INSTANCE.getMoveRotation() != null) {
            return AngleConnection.INSTANCE.getMoveRotation().getYaw();
        }
        return original;
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3d movementInput, CallbackInfo ci) {
        if (mc.player == null) return;
        PlayerTravelEvent event = new PlayerTravelEvent(movementInput, true);
        EventManager.callEvent(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
    public Vec3d travelHook(Vec3d vec3d) {
        SwimmingEvent event = new SwimmingEvent(vec3d);
        EventManager.callEvent(event);
        return event.getVector();
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelPost(Vec3d movementInput, CallbackInfo ci) {
        if (mc.player == null) return;
        PlayerTravelEvent event = new PlayerTravelEvent(movementInput, false);
        EventManager.callEvent(event);
    }

    @Inject(method = "playAttackSound", at = @At("HEAD"), cancellable = true)
    private void onPlayAttackSound(SoundEvent sound, CallbackInfo ci) {
        HitModules hitModules = HitModules.getInstance();
        if (hitModules != null && hitModules.isCustomSoundActive()) {
            ci.cancel();
        }
    }
}