package accident.util.move;

import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import accident.IMinecraft;
import accident.util.camera.AngleConnection;

import java.util.Objects;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class MoveUtil implements IMinecraft {

    public static double getSpeed() {
        double motionX = mc.player.getVelocity().x;
        double motionZ = mc.player.getVelocity().z;
        return Math.sqrt(motionX * motionX + motionZ * motionZ);
    }

    public static final boolean moveKeyPressed(int keyNumber) {
        boolean w = mc.options.forwardKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();
        return keyNumber == 0 ? w : (keyNumber == 1 ? a : (keyNumber == 2 ? s : keyNumber == 3 && d));
    }

    public static final boolean w() {
        return moveKeyPressed(0);
    }

    public static final boolean a() {
        return moveKeyPressed(1);
    }

    public static final boolean s() {
        return moveKeyPressed(2);
    }

    public static final boolean d() {
        return moveKeyPressed(3);
    }

    public static float calculateBodyYaw(float yaw, float prevBodyYaw, double prevX, double prevZ, double currentX, double currentZ, float handSwingProgress) {
        double motionX = currentX - prevX;
        double motionZ = currentZ - prevZ;
        float motionSquared = (float)(motionX * motionX + motionZ * motionZ);
        float bodyYaw = prevBodyYaw;
        float swing = mc.player.handSwingProgress;

        if (motionSquared > 0.0025000002F) {
            float movementYaw = (float) MathHelper.atan2(motionZ, motionX) * (180F / (float)Math.PI) - 90.0F;
            float yawDiff = MathHelper.abs(MathHelper.wrapDegrees(yaw) - movementYaw);
            if (95.0F < yawDiff && yawDiff < 265.0F) {
                bodyYaw = movementYaw - 180.0F;
            } else {
                bodyYaw = movementYaw;
            }
        }

        if (mc.player != null && mc.player.handSwingProgress - 0.2F > 0F) {
            bodyYaw = yaw;
        }

        float deltaYaw = MathHelper.wrapDegrees(bodyYaw - prevBodyYaw);
        bodyYaw = prevBodyYaw + deltaYaw * 0.3F;

        float yawOffsetDiff = MathHelper.wrapDegrees(yaw - bodyYaw);
        float maxHeadRotation = 52.0F;
        if (Math.abs(yawOffsetDiff) > maxHeadRotation) {
            bodyYaw += yawOffsetDiff - (float)MathHelper.sign((double)yawOffsetDiff) * maxHeadRotation;
        }

        return bodyYaw;
    }
}
