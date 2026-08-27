package accident.events.impl;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.Packet;
import accident.events.api.events.callables.EventCancellable;

@Getter @Setter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PacketEvent extends EventCancellable {
    Packet<?> packet;
    Type type;

    public static class Receive extends PacketEvent {
        public Receive(Packet<?> packet) {
            super(packet, Type.RECEIVE);
        }
    }

    public static class Send extends PacketEvent {
        public Send(Packet<?> packet) {
            super(packet, Type.SEND);
        }
    }

    public boolean isSend() {
        return type.equals(Type.SEND);
    }

    public enum Type {
        SEND, RECEIVE
    }
}
