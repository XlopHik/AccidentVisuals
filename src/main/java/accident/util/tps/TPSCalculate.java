package accident.util.tps;

import lombok.Getter;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;
import accident.events.api.EventHandler;
import accident.events.api.EventManager;
import accident.events.impl.PacketEvent;
import java.util.ArrayDeque;

@Getter
public class TPSCalculate {

    @Getter
    private static TPSCalculate instance;
    private final ArrayDeque<Float> tpsQueue = new ArrayDeque<>(20);
    private float tps = 20;
    private long lastPacketTime;
    @Getter
    private long tickTime;

    public TPSCalculate() {
        instance = this;
        EventManager.register(this);
    }

    @EventHandler
    private void onPacket(PacketEvent e) {

        if (e.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            updateTPS();
        }
    }

    private void updateTPS() {
        long now = System.currentTimeMillis();

        if (lastPacketTime != 0) {
            tickTime = now - lastPacketTime;

            if (tpsQueue.size() > 20) {
                tpsQueue.poll();
            }

            float instantTps = 20.0f * (1000.0f / tickTime);
            tpsQueue.add(MathHelper.clamp(instantTps, 0, 20));

            float average = 0.0f;
            for (float value : tpsQueue) {
                average += value;
            }
            tps = average / tpsQueue.size();
        }

        lastPacketTime = now;
    }

    public float getTps() {
        return round(tps);
    }

    public float getTps2() {
        return round(20.0f * (1000.0f / tickTime));
    }

    private float round(double value) {
        return Math.round(value * 100.0f) / 100.0f;
    }

    public float getTpsRounded() {
        return round(tps);
    }
}