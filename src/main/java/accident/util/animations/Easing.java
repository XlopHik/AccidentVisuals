package accident.util.animations;

@FunctionalInterface
public interface Easing {
    double ease(double value);
}