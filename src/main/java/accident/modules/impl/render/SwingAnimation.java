package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import accident.events.api.EventHandler;
import accident.events.impl.HandAnimationEvent;
import accident.events.impl.SwingDurationEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SwingAnimation extends ModuleStructure {
    SelectSetting swingType = new SelectSetting("accident.module.swinganimation.setting.swingtype.name", "accident.module.swinganimation.setting.swingtype.desc")
            .value("Chop", "Swipe", "Down", "Smooth", "Smooth 2", "Power", "Feast", "Twist", "Spin", "Spin 2", "Uppercut", "Jab", "Slap", "Snake", "Godlike", "Drunk", "Default");
    SliderSettings hitStrengthSetting = new SliderSettings("accident.module.swinganimation.setting.hitstrength.name", "accident.module.swinganimation.setting.hitstrength.desc")
            .range(0.5F, 3.0F).setValue(1.0F);
    SliderSettings swingSpeedSetting = new SliderSettings("accident.module.swinganimation.setting.swingspeed.name", "accident.module.swinganimation.setting.swingspeed.desc")
            .range(0.5F, 4.0F).setValue(1.0F);

    BooleanSetting onlySwing = new BooleanSetting("accident.module.swinganimation.setting.onlyswing.name", "accident.module.swinganimation.setting.onlyswing.desc")
            .setValue(false);

    public SwingAnimation() {
        super("accident.module.swinganimation.name", "accident.module.swinganimation.desc", ModuleCategory.RENDER);
        settings(swingType, hitStrengthSetting, swingSpeedSetting, onlySwing);
    }

    @EventHandler
    public void onSwingDuration(SwingDurationEvent e) {
            e.setAnimation(swingSpeedSetting.getValue());
            e.cancel();
    }

    @NonFinal
    private float spinAngle = 0.0F;
    @NonFinal
    private float spinBackTimer = 0.0F;
    @NonFinal
    private boolean wasSwinging = false;

    @EventHandler
    public void onHandAnimation(HandAnimationEvent e) {
        boolean isMainHand = e.getHand().equals(Hand.MAIN_HAND);
        if (isMainHand) {
            MatrixStack matrix = e.getMatrices();
            float swingProgress = e.getSwingProgress();
            int i = mc.player.getMainArm().equals(Arm.RIGHT) ? 1 : -1;
            float sin1 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
            float sin2 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
            float sinSmooth = (float) (Math.sin(swingProgress * Math.PI) * 0.5F);
            float strength = hitStrengthSetting.getValue();

            if (!onlySwing.isValue() || mc.player.handSwingTicks != 0) {
                switch (swingType.getSelected()) {
                        case "Chop" -> {
                            matrix.translate(0.56F * i, -0.44F, -0.72F);
                            matrix.translate(0.0F, 0.33F * -0.6F, 0.0F);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * i));
                            float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
                            float f2 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
                            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f2 * -20.0F * i * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f2 * -80.0F * strength));
                            matrix.translate(0.4F, 0.2F, 0.2F);
                            matrix.translate(-0.5F, 0.08F, 0.0F);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
                        }
                        case "Twist" -> {
                            matrix.translate(i * 0.56F, -0.36F, -0.72F);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80 * i));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -90 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((sin1 - sin2) * 60 * i * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30));
                            matrix.translate(0, -0.1F, 0.05F);
                        }
                        case "Swipe" -> {
                            matrix.translate(0.56F * i, -0.32F, -0.72F);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70 * i));
                            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20 * i));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((sin2 * sin1) * -5 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin2 * sin1) * -120 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70));
                        }
                        case "Default" -> {
                            matrix.translate(i * 0.56F, -0.52F - (sin2 * 0.5F * strength), -0.72F);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45 * i));
                        }
                        case "Down" -> {
                            matrix.translate(i * 0.56F, -0.32F, -0.72F);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76 * i));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * -5 * strength));
                            matrix.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -100 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -155 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100));
                        }
                        case "Smooth" -> {
                            matrix.translate(i * 0.56F, -0.42F, -0.72F);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + sin1 * -20.0F * strength)));
                            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * sin2 * -20.0F * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
                            matrix.translate(0, -0.1, 0);
                        }
                        case "Smooth 2" -> {
                            matrix.translate(i * 0.56F, -0.42F, -0.72F);
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
                            matrix.translate(0, -0.1, 0);
                        }
                        case "Power" -> {
                            matrix.translate(i * 0.56F, -0.32F, -0.72F);
                            matrix.translate((-sinSmooth * sinSmooth * sin1) * i * strength, 0, 0);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(61 * i));
                            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((sin2 * sin1) * -5 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin2 * sin1) * -30 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSmooth * -60 * strength));
                        }
                        case "Feast" -> {
                            matrix.translate(i * 0.56F, -0.32F, -0.72F);
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30 * i));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 75 * i * strength));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -45 * strength));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30 * i));
                            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80));
                            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35 * i));
                        }
                    case "Spin" -> {
                        matrix.translate(i * 0.56F, -0.42F, -0.72F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * -15 * i * strength));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -30 * strength));
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swingProgress * 360 * strength * i));
                        matrix.translate(0, -0.05F, 0);
                    }
                    case "Spin 2" -> {
                        matrix.translate(i * 0.56F, -0.52F, -0.72F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingProgress * -360 * strength));
                    }
                    case "Uppercut" -> {
                        matrix.translate(i * 0.56F, -0.32F + sin2 * -0.3F * strength, -0.72F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(60 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * 120 * strength));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin1 * -20 * i * strength));
                        matrix.translate(0, -0.1F, 0.05F);
                    }
                    case "Jab" -> {
                        matrix.translate(i * 0.56F, -0.42F, -0.72F - sin2 * 0.4F * strength);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -20 * strength));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin1 * -10 * i * strength));
                    }
                    case "Slap" -> {
                        matrix.translate(i * 0.56F + sin2 * -0.3F * i * strength, -0.42F, -0.72F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * -60 * i * strength));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin1 * -20 * strength));
                        matrix.translate(0, -0.05F, 0.05F);
                    }
                    case "Snake" -> {
                        float wave = (float) Math.sin(swingProgress * Math.PI * 2) * strength;
                        matrix.translate(i * 0.56F + wave * 0.1F, -0.42F + wave * 0.05F, -0.72F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(wave * 30 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -60 * strength));
                    }
                    case "Godlike" -> {
                        float back = MathHelper.sin(swingProgress * (float) Math.PI) * strength;
                        matrix.translate(i * 0.56F, -0.42F - back * 0.15F, -0.72F + back * 0.2F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(back * -120));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin1 * -25 * i * strength));
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swingProgress * 180 * i * strength));
                    }
                    case "Drunk" -> {
                        float wobble1 = (float) Math.sin(swingProgress * Math.PI * 3) * strength;
                        float wobble2 = (float) Math.cos(swingProgress * Math.PI * 2) * strength;
                        matrix.translate(i * 0.56F + wobble1 * 0.08F, -0.42F + wobble2 * 0.06F, -0.72F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(wobble1 * 25 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -50 * strength + wobble2 * 20));
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(wobble2 * 15 * i));
                    }
                    }
                } else {
                    matrix.translate(i * 0.56F, -0.52F, -0.72F);
                }
            } else {
                return;
            }
            e.cancel();
        }
    }


