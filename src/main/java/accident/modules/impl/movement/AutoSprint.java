package accident.modules.impl.movement;

import accident.events.api.EventHandler;
import accident.events.impl.TickEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.util.Instance;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class AutoSprint extends ModuleStructure {
    public static AutoSprint getInstance() {
        return Instance.get(AutoSprint.class);
    }

    public AutoSprint() {
        super("accident.module.autosprint.name", "accident.module.autosprint.desc", ModuleCategory.MOVEMENT);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null) return;

        // Просто включаем спринт если идём вперёд
        if (mc.player.forwardSpeed > 0 && !mc.player.isSprinting()) {
            mc.player.setSprinting(true);
            mc.player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING)
            );
        }
    }

    @Override
    public boolean deactivate() {
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            mc.player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING)
            );
        }
        return false;
    }
}