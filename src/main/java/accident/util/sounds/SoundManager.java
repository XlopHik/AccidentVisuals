package accident.util.sounds;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import accident.IMinecraft;
import accident.util.string.PlayerInteractionHelper;

@Setter
@Getter
@UtilityClass
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SoundManager implements IMinecraft {
    public SoundEvent KOLOKOLNIA_KILL = SoundEvent.of(Identifier.of("accident:kolokolnia_kill"));
    public SoundEvent MOAN1 = SoundEvent.of(Identifier.of("accident:moan1"));
    public SoundEvent MOAN2 = SoundEvent.of(Identifier.of("accident:moan2"));
    public SoundEvent MOAN3 = SoundEvent.of(Identifier.of("accident:moan3"));
    public SoundEvent MOAN4 = SoundEvent.of(Identifier.of("accident:moan4"));
    public SoundEvent MODULE_DISABLE = SoundEvent.of(Identifier.of("accident:module_disable"));
    public SoundEvent MODULE_ENABLE = SoundEvent.of(Identifier.of("accident:module_enable"));
    public SoundEvent OFF = SoundEvent.of(Identifier.of("accident:off"));
    public SoundEvent ON = SoundEvent.of(Identifier.of("accident:on"));
    public SoundEvent CRIME = SoundEvent.of(Identifier.of("accident:crime"));
    public SoundEvent METALLIC = SoundEvent.of(Identifier.of("accident:metallic"));
    public SoundEvent WELCOME = SoundEvent.of(Identifier.of("accident:welcome"));
    public SoundEvent BELL = SoundEvent.of(Identifier.of("accident:bell"));
    public SoundEvent BELL2 = SoundEvent.of(Identifier.of("accident:bell2"));
    public SoundEvent BELL3 = SoundEvent.of(Identifier.of("accident:bell3"));
    public SoundEvent NEVERLOSE = SoundEvent.of(Identifier.of("accident:neverlose"));
    public SoundEvent BOOM = SoundEvent.of(Identifier.of("accident:boom"));
    public SoundEvent SWAP = SoundEvent.of(Identifier.of("accident:swap"));

    public void init() {
        Registry.register(Registries.SOUND_EVENT, KOLOKOLNIA_KILL.id(), KOLOKOLNIA_KILL);
        Registry.register(Registries.SOUND_EVENT, MOAN1.id(), MOAN1);
        Registry.register(Registries.SOUND_EVENT, MOAN2.id(), MOAN2);
        Registry.register(Registries.SOUND_EVENT, MOAN3.id(), MOAN3);
        Registry.register(Registries.SOUND_EVENT, MOAN4.id(), MOAN4);
        Registry.register(Registries.SOUND_EVENT, MODULE_DISABLE.id(), MODULE_DISABLE);
        Registry.register(Registries.SOUND_EVENT, MODULE_ENABLE.id(), MODULE_ENABLE);
        Registry.register(Registries.SOUND_EVENT, OFF.id(), OFF);
        Registry.register(Registries.SOUND_EVENT, ON.id(), ON);
        Registry.register(Registries.SOUND_EVENT, CRIME.id(), CRIME);
        Registry.register(Registries.SOUND_EVENT, METALLIC.id(), METALLIC);
        Registry.register(Registries.SOUND_EVENT, WELCOME.id(), WELCOME);
        Registry.register(Registries.SOUND_EVENT, BELL.id(), BELL);
        Registry.register(Registries.SOUND_EVENT, BELL2.id(), BELL2);
        Registry.register(Registries.SOUND_EVENT, BELL3.id(), BELL3);
        Registry.register(Registries.SOUND_EVENT, NEVERLOSE.id(), NEVERLOSE);
        Registry.register(Registries.SOUND_EVENT, BOOM.id(), BOOM);
        Registry.register(Registries.SOUND_EVENT, SWAP.id(), SWAP);
    }

    public void playSound(SoundEvent sound) {
        playSound(sound, 1, 1);
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!PlayerInteractionHelper.nullCheck()) {
            mc.world.playSound(mc.player, mc.player.getBlockPos(), sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }

    public void playSoundDirect(SoundEvent sound, float volume, float pitch) {
        mc.getSoundManager().play(PositionedSoundInstance.ui(sound, pitch, volume));
    }
}