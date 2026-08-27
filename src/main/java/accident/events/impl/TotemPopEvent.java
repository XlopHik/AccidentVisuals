package accident.events.impl;

import net.minecraft.entity.player.PlayerEntity;
import accident.events.api.events.callables.EventCancellable;


public class TotemPopEvent extends EventCancellable {
    private final PlayerEntity entity;
    private int pops;

    public TotemPopEvent(PlayerEntity entity,int pops) {
        this.entity = entity;
        this.pops = pops;
    }

    public PlayerEntity getEntity() {
        return this.entity;
    }

    public int getPops() {
        return this.pops;
    }
}