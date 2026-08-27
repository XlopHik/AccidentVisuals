package accident.util.animations;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import accident.util.timer.TimerUtil;

@Setter
@Accessors(chain = true)
public class Animation implements AnimationCalculation {
    public final TimerUtil counter = new TimerUtil();
    protected int ms;
    protected double value;
    @Getter
    protected Direction direction = Direction.FORWARDS;

    public void reset() { counter.resetCounter(); }
    public void update() { }

    public boolean isDone() { return counter.isReached(ms); }
    public boolean isFinished(Direction direction) { return this.direction == direction && isDone(); }

    public void setDirection(Direction direction) {
        if (this.direction != direction) {
            this.direction = direction;
            adjustTimer();
        }
    }

    public boolean isDirection(Direction direction) { return this.direction == direction; }

    private void adjustTimer() {
        counter.setTime(
                System.currentTimeMillis() - ((long) ms - Math.min(ms, counter.getTime()))
        );
    }

    public Double getOutput() {
        double time = (1 - calculation(counter.getTime())) * value;

        return direction == Direction.FORWARDS
                ? endValue()
                : isDone() ? 0.0 : time;
    }

    protected double endValue() {
        return isDone()
                ? value
                : calculation(counter.getTime()) * value;
    }
}