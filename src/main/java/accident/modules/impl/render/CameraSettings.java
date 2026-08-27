package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import accident.events.api.EventHandler;
import accident.events.impl.CameraEvent;
import accident.events.impl.EventCameraUpdate;
import accident.events.impl.FovEvent;
import accident.events.impl.HotBarScrollEvent;
import accident.events.impl.KeyEvent;
import accident.util.camera.MathAngle;
import accident.modules.impl.misc.FreeLook;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BindSetting;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;
import accident.util.math.MathUtils;
import accident.util.string.PlayerInteractionHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class CameraSettings extends ModuleStructure {

    float fov = 110;
    float smoothFov = 30;
    float lastChangedFov = 30;

    // Настройки дистанции и зума
    SliderSettings distanceSetting = new SliderSettings("accident.module.camerasettings.setting.distancesetting.name", "accident.module.camerasettings.setting.distancesetting.desc")
            .setValue(3.0F).range(2.0F, 5.0F);
    BindSetting zoomSetting = new BindSetting("accident.module.camerasettings.setting.zoomsetting.name", "accident.module.camerasettings.setting.zoomsetting.desc");

    public BooleanSetting enableSmoothCamera = new BooleanSetting("accident.module.camerasettings.setting.enablesmoothcamera.name", "accident.module.camerasettings.setting.enablesmoothcamera.desc").setValue(false);

    public BooleanSetting enableFirstPOV = new BooleanSetting(
            "accident.module.camerasettings.setting.enablefirstpov.name", "accident.module.camerasettings.setting.enablefirstpov.desc")
            .setValue(false)
            .visible(() -> enableSmoothCamera.isValue());

    public BooleanSetting resetOnPerspectiveChange = new BooleanSetting(
            "accident.module.camerasettings.setting.resetonperspectivechange.name", "accident.module.camerasettings.setting.resetonperspectivechange.desc")
            .setValue(true)
            .visible(() -> enableSmoothCamera.isValue());

    public SliderSettings factorH = new SliderSettings(
            "accident.module.camerasettings.setting.factorh.name", "accident.module.camerasettings.setting.factorh.desc")
            .range(0.1f, 1.0f).setValue(0.9f)
            .visible(() -> enableSmoothCamera.isValue());

    public SliderSettings factorV = new SliderSettings(
            "accident.module.camerasettings.setting.factorv.name", "accident.module.camerasettings.setting.factorv.desc")
            .range(0.1f, 1.0f).setValue(0.93f)
            .visible(() -> enableSmoothCamera.isValue());

    // Данные для плавной камеры
    private Vec3d smoothPos = Vec3d.ZERO;
    private Perspective lastPerspective = null;

    public CameraSettings() {
        super("accident.module.camerasettings.name", "accident.module.camerasettings.desc", ModuleCategory.RENDER);
        settings(distanceSetting, zoomSetting, enableSmoothCamera, enableFirstPOV, resetOnPerspectiveChange, factorH, factorV);
    }

    @Override
    public boolean deactivate() {
        smoothPos = Vec3d.ZERO;
        lastPerspective = null;
        return super.deactivate();
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(zoomSetting.getKey())) {
            fov = Math.min(lastChangedFov, mc.options.getFov().getValue() - 20);
        }
        if (e.isKeyReleased(zoomSetting.getKey(), true)) {
            lastChangedFov = fov;
            fov = mc.options.getFov().getValue();
        }
    }

    @EventHandler
    public void onHotBarScroll(HotBarScrollEvent e) {
        if (PlayerInteractionHelper.isKey(zoomSetting)) {
            fov = (int) MathHelper.clamp(fov - e.getVertical() * 10, 10, mc.options.getFov().getValue());
            e.cancel();
        }
    }

    @EventHandler
    public void onFov(FovEvent e) {
        e.setFov((int) MathHelper.clamp((smoothFov = MathUtils.interpolateSmooth(1.6, smoothFov, fov)) + 1, 10, mc.options.getFov().getValue()));
        e.cancel();
    }

    @EventHandler
    public void onCamera(CameraEvent e) {
        e.setCameraClip(false);

        float dist = distanceSetting.getValue();

        // Зум от KillEffect
        KillEffect killEffect = Instance.get(KillEffect.class);
        if (killEffect != null && killEffect.isState() && killEffect.cameraZoom > 0f) {
            dist = dist - killEffect.cameraZoom * 1.5f;
            dist = Math.max(0.5f, dist);
        }

        e.setDistance(dist);

        FreeLook freeLook = Instance.get(FreeLook.class);
        if (!freeLook.isState() || !PlayerInteractionHelper.isKey(freeLook.freeLookSetting)) {
            e.setAngle(MathAngle.cameraAngle());
        }
        e.cancel();
    }

    // Метод для безопасного получения значения сглаживания (максимум 0.99)
    private float getSafeSmoothingFactor(float value) {
        if (value >= 0.99f) {
            return 0.99f;  // Максимум 0.99, чтобы камера не зависала
        }
        return value;
    }

    @EventHandler
    public void onCameraUpdate(EventCameraUpdate event) {
        if (!enableSmoothCamera.isValue()) {
            smoothPos = Vec3d.ZERO;
            lastPerspective = null;
            return;
        }

        Perspective currentPerspective = mc.options.getPerspective();

        if (resetOnPerspectiveChange.isValue() && lastPerspective != null
                && lastPerspective != currentPerspective) {
            smoothPos = new Vec3d(event.getX(), event.getY(), event.getZ());
            lastPerspective = currentPerspective;
            return;
        }

        lastPerspective = currentPerspective;

        if (!enableFirstPOV.isValue() && currentPerspective == Perspective.FIRST_PERSON) {
            smoothPos = new Vec3d(event.getX(), event.getY(), event.getZ());
            return;
        }

        if (isLikelyZero(smoothPos)) {
            smoothPos = new Vec3d(event.getX(), event.getY(), event.getZ());
        }

        // Получаем значения с ограничением (если 1 → 0.99)
        float h = getSafeSmoothingFactor(factorH.getValue());
        float v = getSafeSmoothingFactor(factorV.getValue());

        double newX = smoothPos.x * h + event.getX() * (1 - h);
        double newY = smoothPos.y * v + event.getY() * (1 - v);
        double newZ = smoothPos.z * h + event.getZ() * (1 - h);

        smoothPos = new Vec3d(newX, newY, newZ);

        event.setX(newX);
        event.setY(newY);
        event.setZ(newZ);
    }


    private boolean isLikelyZero(Vec3d vec) {
        return Math.abs(vec.x) < 0.001 && Math.abs(vec.y) < 0.001 && Math.abs(vec.z) < 0.001;
    }
}


