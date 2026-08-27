package accident.modules.impl.misc;

import accident.events.api.EventHandler;
import accident.events.impl.AttackEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.Instance;
import accident.util.sounds.SoundManager;
import net.minecraft.sound.SoundEvent;

public class HitSound extends ModuleStructure {

    public static HitSound getInstance() {
        return Instance.get(HitSound.class);
    }

    private final SelectSetting soundType = new SelectSetting("accident.module.hitsound.setting.soundtype.name", "accident.module.hitsound.setting.soundtype.desc")
            .value("Metallic", "Moan1", "Moan2", "Moan3", "Moan4", "Bell", "Bell2", "Bell3", "Neverlose", "Boom", "Swap")
            .selected("Metallic");

    private final SliderSettings volume = new SliderSettings("accident.module.hitsound.setting.volume.name", "accident.module.hitsound.setting.volume.desc")
            .range(0.1f, 2.0f)
            .setValue(1.0f);

    private final SliderSettings pitch = new SliderSettings("accident.module.hitsound.setting.pitch.name", "accident.module.hitsound.setting.pitch.desc")
            .range(0.5f, 2.0f)
            .setValue(1.0f);

    public HitSound() {
        super("accident.module.hitsound.name", "accident.module.hitsound.desc", ModuleCategory.MISC);
        settings(soundType, volume, pitch);
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getTarget() == null) return;

        playHitSound();
    }

    private void playHitSound() {
        float vol = volume.getValue();
        float pit = pitch.getValue();
        SoundEvent sound = getSound();

        if (sound != null) {
            SoundManager.playSoundDirect(sound, vol, pit);
        }
    }

    private SoundEvent getSound() {
        String selected = soundType.getSelected();

        return switch (selected) {
            case "Metallic" -> SoundManager.METALLIC;
            case "Moan1" -> SoundManager.MOAN1;
            case "Moan2" -> SoundManager.MOAN2;
            case "Moan3" -> SoundManager.MOAN3;
            case "Moan4" -> SoundManager.MOAN4;
            case "Bell" -> SoundManager.BELL;
            case "Bell2" -> SoundManager.BELL2;
            case "Bell3" -> SoundManager.BELL3;
            case "Neverlose" -> SoundManager.NEVERLOSE;
            case "Boom" -> SoundManager.BOOM;
            case "Swap" -> SoundManager.SWAP;
            default -> SoundManager.METALLIC;
        };
    }
}


