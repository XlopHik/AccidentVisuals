package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import accident.events.api.EventHandler;
import accident.events.impl.PacketEvent;
import accident.events.impl.TickEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.modules.module.setting.implement.ColorSetting;

import accident.util.Instance;
import java.awt.Color;

@FieldDefaults(level = AccessLevel.PUBLIC)
public class Ambience extends ModuleStructure {
    public static Ambience getInstance() { return Instance.get(Ambience.class); }

    public SliderSettings time = new SliderSettings("accident.module.ambience.setting.time.name", "accident.module.ambience.setting.time.desc")
            .range(0, 24000)
            .setValue(1000);

    public ColorSetting worldColor = new ColorSetting("accident.module.ambience.setting.worldcolor.name", "accident.module.ambience.setting.worldcolor.desc")
            .value(new Color(100, 150, 255).getRGB());

    public BooleanSetting customFog = new BooleanSetting("accident.module.ambience.setting.customfog.name", "accident.module.ambience.setting.customfog.desc").setValue(false);

    public ColorSetting fogColor = new ColorSetting("accident.module.ambience.setting.fogcolor.name", "accident.module.ambience.setting.fogcolor.desc")
            .value(Color.MAGENTA.getRGB())
            .visible(() -> customFog.isValue());

    public SliderSettings fogStart = new SliderSettings("accident.module.ambience.setting.fogstart.name", "accident.module.ambience.setting.fogstart.desc")
            .range(0, 32)
            .setValue(0)
            .visible(() -> customFog.isValue()); // ← ДОБАВЬ ЭТУ СТРОКУ

    private double animatedTime = 1000;

    public Ambience() {
        super("accident.module.ambience.name", "accident.module.ambience.desc", ModuleCategory.RENDER);
        settings(time, worldColor, customFog, fogColor, fogStart);
    }

    @Override
    public boolean activate() {
        animatedTime = time.getValue();
        return false;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        double targetTime = time.getValue();
        double speed = 150 / 1000.0;
        double diff = targetTime - animatedTime;
        animatedTime += diff * speed;
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            event.cancel();
        }
    }

    public long getCustomTime() { return (long) animatedTime; }
    public float getFogRed() { return ((fogColor.getColor() >> 16) & 0xFF) / 255.0f; }
    public float getFogGreen() { return ((fogColor.getColor() >> 8) & 0xFF) / 255.0f; }
    public float getFogBlue() { return (fogColor.getColor() & 0xFF) / 255.0f; }

    public float getWorldRed() { return ((worldColor.getColor() >> 16) & 0xFF) / 255.0f; }
    public float getWorldGreen() { return ((worldColor.getColor() >> 8) & 0xFF) / 255.0f; }
    public float getWorldBlue() { return (worldColor.getColor() & 0xFF) / 255.0f; }
}


