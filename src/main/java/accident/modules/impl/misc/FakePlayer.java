package accident.modules.impl.misc;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import accident.events.api.EventHandler;
import accident.events.impl.AttackEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.TextSetting;


import java.util.UUID;

public class FakePlayer extends ModuleStructure {

    private final TextSetting name = new TextSetting("accident.module.fakeplayer.setting.name.name", "accident.module.fakeplayer.setting.name.desc").setText("FAKE");

    public static OtherClientPlayerEntity fakePlayer;

    public FakePlayer() {
        super("accident.module.fakeplayer.name", "accident.module.fakeplayer.desc", ModuleCategory.MISC);
        settings(name);
    }

    @Override
    public boolean activate() {
        if (mc.world == null || mc.player == null) return false;

        GameProfile profile = new GameProfile(
                UUID.fromString("66123666-6666-6666-6666-666666666600"),
                name.getText()
        );

        fakePlayer = new OtherClientPlayerEntity(mc.world, profile);
        fakePlayer.copyPositionAndRotation(mc.player);

        mc.world.addEntity(fakePlayer);

        return super.activate();
    }

    @Override
    public boolean deactivate() {
        if (fakePlayer == null) return false;

        fakePlayer.setRemoved(Entity.RemovalReason.KILLED);
        fakePlayer = null;

        return true;
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (fakePlayer == null || event.getTarget() != fakePlayer) return;
        if (fakePlayer.hurtTime > 0) return;

        // Проигрываем звуки и анимацию, но не отнимаем здоровье (бессмертие)
        mc.world.playSound(mc.player, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);

        if (mc.player.fallDistance > 0) {
            mc.world.playSound(mc.player, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1f, 1f);
        }

        fakePlayer.animateDamage(fakePlayer.getYaw());
        fakePlayer.hurtTime = 10;

        // Здоровье (fakePlayer.setHealth) намеренно не изменяется, что гарантирует бессмертие
    }
}


