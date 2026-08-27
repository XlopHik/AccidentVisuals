package accident.modules.impl.render;

import accident.events.api.EventHandler;
import accident.events.impl.TickEvent;
import accident.util.accesors.OverlayTextureAccessor;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.ColorSetting;

import accident.util.Instance;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;

import java.awt.Color;

@FieldDefaults(level = AccessLevel.PUBLIC)
public class HitColor extends ModuleStructure {

    public static HitColor getInstance() {
        return Instance.get(HitColor.class);
    }

    public ColorSetting damageColor = new ColorSetting("accident.module.hitcolor.setting.damagecolor.name", "accident.module.hitcolor.setting.damagecolor.desc")
            .value(new Color(255, 0, 0, 150).getRGB());

    public HitColor() {
        super("accident.module.hitcolor.name", "accident.module.hitcolor.desc", ModuleCategory.RENDER);
        settings(damageColor);
    }

    @EventHandler
    private void onTick(TickEvent e) {
        refreshOverlay();
    }

    @Override
    public void setState(boolean state) {
        super.setState(state);
        refreshOverlay();
    }

    private void refreshOverlay() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.gameRenderer == null) return;

        OverlayTexture overlay = mc.gameRenderer.getOverlayTexture();
        ((OverlayTextureAccessor) overlay).accident$recolor();
    }

    public float getDamageRed() {
        return ((damageColor.getColor() >> 16) & 0xFF) / 255.0f;
    }

    public float getDamageGreen() {
        return ((damageColor.getColor() >> 8) & 0xFF) / 255.0f;
    }

    public float getDamageBlue() {
        return (damageColor.getColor() & 0xFF) / 255.0f;
    }

    public float getDamageAlpha() {
        return ((damageColor.getColor() >> 24) & 0xFF) / 255.0f;
    }
}


