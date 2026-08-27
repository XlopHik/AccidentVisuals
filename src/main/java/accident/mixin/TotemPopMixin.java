package accident.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.events.api.EventManager;
import accident.events.impl.TotemPopEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(EntityStatusS2CPacket.class)
public class TotemPopMixin {

    @Unique
    private static final Map<UUID, Integer> POP_COUNTER = new HashMap<>();

    @Inject(method = "apply", at = @At("HEAD"))
    private void onTotemPop(CallbackInfo ci) {
        EntityStatusS2CPacket packet = (EntityStatusS2CPacket) (Object) this;


        if (packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) {
            Entity entity = packet.getEntity(net.minecraft.client.MinecraftClient.getInstance().world);


            if (packet.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING) {

            if (entity instanceof PlayerEntity player) {
                int pops = POP_COUNTER.getOrDefault(player.getUuid(), 0) + 1;
                POP_COUNTER.put(player.getUuid(), pops);

                    TotemPopEvent event = new TotemPopEvent(player, pops);
                    EventManager.callEvent(event);
                }
            }
        }
    }
}