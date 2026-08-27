package accident.util.timer;

import lombok.Getter;
import java.time.Instant;

@Getter
public class TimerUtil {
    public TimerUtil() { this.resetCounter(); }

    private long lastMS = System.currentTimeMillis();
    private long startTime;
    private long time;

    public boolean passedMs(long ms) { return getMs(System.nanoTime() - time) >= ms; }
    public long getMs(long time) { return time / 1000000L; }

    public void reset() { lastMS = Instant.now().toEpochMilli(); }
    public void resetCounter() { lastMS = System.currentTimeMillis(); }

    public static TimerUtil create() { return new TimerUtil(); }

    public boolean isReached(long time) { return System.currentTimeMillis() - lastMS > time; }
    public boolean isRunning() { return System.currentTimeMillis() - lastMS <= 0; }

    public void setLastMS(long newValue) { lastMS = System.currentTimeMillis() + newValue; }
    public void setTime(long time) { lastMS = time; }

    public long getTime() { return System.currentTimeMillis() - lastMS; }

    public boolean hasTimeElapsed(long time) { return System.currentTimeMillis() - lastMS > time; }
    public boolean hasTimeElapsed() { return lastMS < System.currentTimeMillis(); }

    public boolean finished(final double delay) { return System.currentTimeMillis() - delay >= startTime; }
}