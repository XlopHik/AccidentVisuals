package accident.util.math;

import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

public class FrameRateCounter {
    public static final FrameRateCounter INSTANCE = new FrameRateCounter();
    final List<Long> records = new ArrayList<>();
    @Getter int fps = 5;

    public void recordFrame() {
        long c = System.currentTimeMillis();
        records.add(c);
        records.removeIf(aLong -> aLong + 1000 < System.currentTimeMillis());
        fps = Math.max(records.size(), 4);
    }

}
