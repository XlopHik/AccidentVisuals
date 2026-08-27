package accident.modules.impl.render.particles;

import accident.modules.impl.render.worldparticles.Particle;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ParticlePool {

    private final Particle[] pool;
    private final boolean[] active;
    private final int capacity;
    private int activeCount = 0;

    public ParticlePool(int capacity) {
        this.capacity = capacity;
        this.pool = new Particle[capacity];
        this.active = new boolean[capacity];
        for (int i = 0; i < capacity; i++) {
            pool[i] = new Particle(0, 0, 0, 1000L);
        }
    }

    public int acquire() {
        if (activeCount >= capacity) return -1;
        for (int i = 0; i < capacity; i++) {
            if (!active[i]) {
                active[i] = true;
                activeCount++;
                return i;
            }
        }
        return -1;
    }

    public void release(int index) {
        if (index < 0 || index >= capacity || !active[index]) return;
        active[index] = false;
        activeCount--;
    }

    public Particle get(int index) {
        return pool[index];
    }

    public int getActiveCount() {
        return activeCount;
    }

    public void forEachActive(Consumer<Particle> action) {
        for (int i = 0; i < capacity; i++) {
            if (active[i]) action.accept(pool[i]);
        }
    }

    public void forEachActive(BiConsumer<Integer, Particle> action) {
        for (int i = 0; i < capacity; i++) {
            if (active[i]) action.accept(i, pool[i]);
        }
    }

    public void clear() {
        Arrays.fill(active, false);
        activeCount = 0;
    }
}