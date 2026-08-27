package accident.util.timer;

/**
 * Глобальный таймер тиков.
 * 1.0 = нормальная скорость
 * 0.5 = половина скорости (замедление)
 * 2.0 = двойная скорость (ускорение)
 *
 * Применяется через TickTimerMixin — подключи его в fabricmc.mixin.json
 */
public class TickTimer {
    public static float TIMER = 1.0f;

    public static void set(float value) {
        TIMER = Math.max(0.1f, value);
    }

    public static void reset() {
        TIMER = 1.0f;
    }
}