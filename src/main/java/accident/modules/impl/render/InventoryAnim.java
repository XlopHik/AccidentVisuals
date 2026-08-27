package accident.modules.impl.render;

import lombok.Getter;
import accident.events.api.EventHandler;
import accident.events.impl.HandledScreenEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.animations.AnimType;

public class InventoryAnim extends ModuleStructure {

    public final SelectSetting animationType = new SelectSetting("accident.module.inventoryanim.setting.animationtype.name", "accident.module.inventoryanim.setting.animationtype.desc")
            .value("Bounce", "Slide Up", "Slide Down", "Slide Left", "Slide Right", "Flip", "Warp", "Glitch", "None")
            .selected("Bounce");

    public final SliderSettings speed = new SliderSettings("accident.module.inventoryanim.setting.speed.name", "accident.module.inventoryanim.setting.speed.desc")
            .setValue(0.15f).range(0.01f, 0.5f);

    public final BooleanSetting inventoryOnly = new BooleanSetting("accident.module.inventoryanim.setting.inventoryonly.name", "accident.module.inventoryanim.setting.inventoryonly.desc")
            .setValue(false);

    @Getter
    private static InventoryAnim instance;

    public InventoryAnim() {
        super("accident.module.inventoryanim.name", "accident.module.inventoryanim.desc", ModuleCategory.RENDER);
        settings(animationType, speed, inventoryOnly);
        instance = this;
    }

    public static boolean isActive(Object screen) {
        if (instance == null || !instance.isEnabled()) return false;
        if (instance.animationType.getSelected().equals("None")) return false;
        if (instance.inventoryOnly.isValue()) {
            return screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen;
        }
        return true;
    }

    public static AnimType getAnimType() {
        if (instance == null) return AnimType.SCALE;
        return switch (instance.animationType.getSelected()) {
            case "Bounce"     -> AnimType.BOUNCE;
            case "Slide Up"   -> AnimType.SLIDE_UP;
            case "Slide Down" -> AnimType.SLIDE_DOWN;
            case "Slide Left" -> AnimType.SLIDE_LEFT;
            case "Slide Right"-> AnimType.SLIDE_RIGHT;
            case "Flip"       -> AnimType.FLIP;
            case "Warp"       -> AnimType.WARP;
            case "Glitch"     -> AnimType.GLITCH;
            default           -> AnimType.SCALE;
        };
    }

    public static float getSpeed() {
        return instance != null ? instance.speed.getValue() : 0.15f;
    }

    @EventHandler
    public void onHandledScreen(HandledScreenEvent event) {
    }
}


