package accident.events.impl;

import net.minecraft.entity.player.PlayerEntity;
import accident.events.api.events.callables.EventCancellable;

public class EventDeath extends EventCancellable {
    private final PlayerEntity player;

    public EventDeath(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity getPlayer(){
        return player;
    }
}
