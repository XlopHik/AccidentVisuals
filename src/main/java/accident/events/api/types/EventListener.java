package accident.events.api.types;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import accident.Initialization;
import accident.events.api.EventHandler;
import accident.events.impl.PacketEvent;
import accident.events.impl.TickEvent;
import accident.events.impl.UsingItemEvent;

public class EventListener implements Listener {
    public static boolean serverSprint;
    public static int selectedSlot;

    @EventHandler
    public void onTick(TickEvent e) {
        if (Initialization.getInstance().getManager().getHudManager() != null) {
            Initialization.getInstance().getManager().getHudManager().tick();
        }
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        switch (e.getPacket()) {
            case ClientCommandC2SPacket command -> serverSprint = switch (command.getMode()) {
                case ClientCommandC2SPacket.Mode.START_SPRINTING -> true;
                case ClientCommandC2SPacket.Mode.STOP_SPRINTING -> false;
                default -> serverSprint;
            };
            case UpdateSelectedSlotC2SPacket slot -> selectedSlot = slot.getSelectedSlot();
            default -> {}
        }

        Initialization.getInstance().getManager().getHudManager().onPacket(e);
    }

}