package accident.util.scripting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// общий поллинг health<=0 раз в тик для всех скриптов, чтобы каждый не сканировал
// сущности сам (как раньше делал kill_burst.lua). те же ограничения по line-of-sight, что и entities.getAll()
public final class EntityDeathTracker {

    private EntityDeathTracker() {}

    private static final Set<Integer> alreadyReported = new HashSet<>();

    public static List<Entity> pollDeaths() {
        List<Entity> died = new ArrayList<>();

        MinecraftClient mc = MinecraftClient.getInstance();
        var world = mc.world;
        PlayerEntity self = mc.player;
        if (world == null || self == null) return died;

        Set<Integer> seenNow = new HashSet<>();
        for (Entity entity : world.getEntities()) {
            if (entity == self) continue;
            if (!(entity instanceof LivingEntity living)) continue;
            if (!LuaApi.hasLineOfSight(self, entity)) continue;

            seenNow.add(entity.getId());
            if (living.getHealth() <= 0 && alreadyReported.add(entity.getId())) {
                died.add(entity);
            }
        }
        alreadyReported.removeIf(id -> !seenNow.contains(id));
        return died;
    }
}
