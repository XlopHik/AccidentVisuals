package accident.modules.impl.misc;

   
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import accident.events.api.EventHandler;
import accident.events.impl.CameraEvent;
import accident.events.impl.FovEvent;
import accident.events.impl.KeyEvent;
import accident.events.impl.MouseRotationEvent;
import accident.util.camera.Angle;
import accident.util.camera.MathAngle;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BindSetting;

import accident.util.string.PlayerInteractionHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FreeLook extends ModuleStructure {

    Perspective perspective;
    Angle angle;
    public static BindSetting freeLookSetting = new BindSetting("accident.module.freelook.setting.freelooksetting.name", "accident.module.freelook.setting.freelooksetting.desc");

    public FreeLook() {
        super("accident.module.freelook.name", "accident.module.freelook.desc", ModuleCategory.RENDER);
        settings(freeLookSetting);
        angle = null;
    }

    @EventHandler
     
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(freeLookSetting.getKey())) {
            perspective = mc.options.getPerspective();
            if (angle == null) {
                angle = MathAngle.cameraAngle();
            }
        }
    }

    @EventHandler
      
    public void onFov(FovEvent e) {
        if (PlayerInteractionHelper.isKey(freeLookSetting)) {
            handleFreeLookActivation();
        } else if (perspective != null) {
            handleFreeLookDeactivation();
        }
    }

     
    private void handleFreeLookActivation() {
        if (mc.options.getPerspective().isFirstPerson()) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        if (angle == null) {
            angle = MathAngle.cameraAngle();
        }
    }

     
    private void handleFreeLookDeactivation() {
        mc.options.setPerspective(perspective);
        perspective = null;
        angle = null;
    }

    @EventHandler
      
    public void onMouseRotation(MouseRotationEvent e) {
        if (PlayerInteractionHelper.isKey(freeLookSetting)) {
            if (angle == null) {
                angle = MathAngle.cameraAngle();
            }
            angle.setYaw(angle.getYaw() + e.getCursorDeltaX() * 0.15F);
            angle.setPitch(MathHelper.clamp(angle.getPitch() + e.getCursorDeltaY() * 0.15F, -90F, 90F));
            e.cancel();
        } else {
            angle = null;
        }
    }

    @EventHandler
    public void onCamera(CameraEvent e) {
        if (PlayerInteractionHelper.isKey(freeLookSetting) && angle != null) {
            e.setAngle(angle);
            e.cancel();
        }
    }
}


