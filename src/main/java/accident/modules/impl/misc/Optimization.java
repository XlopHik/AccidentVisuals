package accident.modules.impl.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import accident.events.api.EventHandler;
import accident.events.impl.TickEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SliderSettings;


import java.util.HashMap;
import java.util.Map;

public class Optimization extends ModuleStructure {
    private static final double DROPPED_ITEM_GROUP_CELL = 1.5;

    private final SliderSettings threshold = new SliderSettings("accident.module.optimization.setting.threshold.name", "accident.module.optimization.setting.threshold.desc")
            .range(1, 1000).setValue(25);

    private final SliderSettings boatCullDistance = new SliderSettings("accident.module.optimization.setting.boaticulldistance.name", "accident.module.optimization.setting.boaticulldistance.desc")
            .range(4, 64).setValue(18);
    private final SliderSettings minecartCullDistance = new SliderSettings("accident.module.optimization.setting.minecartculldistance.name", "accident.module.optimization.setting.minecartculldistance.desc")
            .range(4, 64).setValue(18);
    private final SliderSettings droppedItemCullDistance = new SliderSettings("accident.module.optimization.setting.droppeditemculldistance.name", "accident.module.optimization.setting.droppeditemculldistance.desc")
            .range(4, 64).setValue(14);
    private final SliderSettings expOrbCullDistance = new SliderSettings("accident.module.optimization.setting.exporbculldistance.name", "accident.module.optimization.setting.exporbculldistance.desc")
            .range(4, 64).setValue(14);
    private final SliderSettings overlapCellSize = new SliderSettings("accident.module.optimization.setting.overlapcellsize.name", "accident.module.optimization.setting.overlapcellsize.desc")
            .range(0, 4).setValue(1);
    private final SliderSettings minKeepDistance = new SliderSettings("accident.module.optimization.setting.minkeepdistance.name", "accident.module.optimization.setting.minkeepdistance.desc")
            .range(0, 16).setValue(0);

    private final SliderSettings particlePerTick = new SliderSettings("accident.module.optimization.setting.particlepertick.name", "accident.module.optimization.setting.particlepertick.desc")
            .range(0, 3000).setValue(300);
    private final SliderSettings expParticlePerTick = new SliderSettings("accident.module.optimization.setting.expparticlepertick.name", "accident.module.optimization.setting.expparticlepertick.desc")
            .range(0, 1000).setValue(80);
    private final SliderSettings scanInterval = new SliderSettings("accident.module.optimization.setting.scaninterval.name", "accident.module.optimization.setting.scaninterval.desc")
            .range(1, 20).setValue(5);

    private final BooleanSetting enabled = new BooleanSetting("accident.module.optimization.setting.enabled.name", "accident.module.optimization.setting.enabled.desc").setValue(true);

    private final Map<Long, Integer> boatOverlapCounts = new HashMap<>();
    private final Map<Long, Integer> minecartOverlapCounts = new HashMap<>();
    private final Map<Long, Integer> expOrbOverlapCounts = new HashMap<>();
    private final Map<Item, Integer> droppedItemTypeCounts = new HashMap<>();
    private final Map<Long, Integer> droppedItemBlockTypeCounts = new HashMap<>();
    private int particleCounterThisTick;
    private int expParticleCounterThisTick;
    private int lastScanTick = -1;

    public Optimization() {
        super("accident.module.optimization.name", "accident.module.optimization.desc", ModuleCategory.MISC);
        settings(
                enabled, threshold, boatCullDistance, minecartCullDistance,
                droppedItemCullDistance, expOrbCullDistance, overlapCellSize,
                minKeepDistance, particlePerTick, expParticlePerTick, scanInterval
        );
    }

    @Override
    public boolean activate() {
        resetCounters();
        return false;
    }

    @Override
    public boolean deactivate() {
        resetCounters();
        return false;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!enabled.isValue()) return;
        if (MinecraftClient.getInstance().player == null) return;

        particleCounterThisTick = 0;
        expParticleCounterThisTick = 0;

        int age = MinecraftClient.getInstance().player.age;
        if (lastScanTick != -1 && age - lastScanTick < scanInterval.getInt()) return;

        lastScanTick = age;
        recalculateNearbyCounts();
    }

    public boolean shouldSkipEntity(Entity entity) {
        if (!enabled.isValue()) return false;
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || entity == null || entity == mc.player) return false;

        if (entity instanceof AbstractBoatEntity) {
            int overlapCount = getOverlapCount(entity, boatOverlapCounts);
            return shouldSkipByLoad(overlapCount, threshold.getInt(), boatCullDistance.getInt(), entity);
        }
        if (entity instanceof AbstractMinecartEntity) {
            int overlapCount = getOverlapCount(entity, minecartOverlapCounts);
            return shouldSkipByLoad(overlapCount, threshold.getInt(), minecartCullDistance.getInt(), entity);
        }
        if (entity instanceof ExperienceOrbEntity) {
            int overlapCount = getOverlapCount(entity, expOrbOverlapCounts);
            return shouldSkipByLoad(overlapCount, threshold.getInt(), expOrbCullDistance.getInt(), entity);
        }
        if (entity instanceof ItemEntity) {
            if (shouldSkipDenseItemGroup((ItemEntity) entity)) return true;
            int typeCount = getDroppedItemTypeCount((ItemEntity) entity);
            return shouldSkipByLoad(typeCount, threshold.getInt(), droppedItemCullDistance.getInt(), entity);
        }

        return false;
    }

    public boolean allowParticle() {
        if (!enabled.isValue()) return true;
        if (particlePerTick.getInt() <= 0) return false;

        if (particleCounterThisTick >= particlePerTick.getInt()) return false;
        particleCounterThisTick++;
        return true;
    }

    public boolean allowExpParticle(ParticleEffect effect) {
        if (!enabled.isValue()) return true;
        if (!isExpParticle(effect)) return true;
        if (expParticlePerTick.getInt() <= 0) return false;

        if (expParticleCounterThisTick >= expParticlePerTick.getInt()) return false;
        expParticleCounterThisTick++;
        return true;
    }

    public int getRenderedAmountForItem(ItemEntity itemEntity, int originalRenderedAmount) {
        if (!enabled.isValue() || itemEntity == null) return originalRenderedAmount;
        int groupedCount = droppedItemBlockTypeCounts.getOrDefault(toBlockItemKey(itemEntity), 0);
        if (groupedCount > 3) {
            return Math.min(originalRenderedAmount, 2);
        }
        return originalRenderedAmount;
    }


    private void resetCounters() {
        boatOverlapCounts.clear();
        minecartOverlapCounts.clear();
        expOrbOverlapCounts.clear();
        droppedItemTypeCounts.clear();
        droppedItemBlockTypeCounts.clear();
        particleCounterThisTick = 0;
        expParticleCounterThisTick = 0;
        lastScanTick = -1;
    }

    private boolean shouldSkipByLoad(int nearbyCount, int threshold, double keepDistance, Entity entity) {
        if (nearbyCount <= threshold) return false;

        var mc = MinecraftClient.getInstance();
        double distanceSq = mc.player.squaredDistanceTo(entity);
        double keepDistanceSq = keepDistance * keepDistance;
        if (distanceSq > keepDistanceSq) return true;

        double minKeepDistanceSq = minKeepDistance.getInt() * minKeepDistance.getInt();
        if (distanceSq <= minKeepDistanceSq) return false;

        double keepRatio = Math.max(0.05, Math.min(1.0, (double) threshold / (double) nearbyCount));
        double sample = stableSample01(entity.getId());
        return sample > keepRatio;
    }

    private void recalculateNearbyCounts() {
        boatOverlapCounts.clear();
        minecartOverlapCounts.clear();
        expOrbOverlapCounts.clear();
        droppedItemTypeCounts.clear();
        droppedItemBlockTypeCounts.clear();

        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof AbstractBoatEntity) {
                incrementCount(boatOverlapCounts, toOverlapKey(entity));
                continue;
            }
            if (entity instanceof AbstractMinecartEntity) {
                incrementCount(minecartOverlapCounts, toOverlapKey(entity));
                continue;
            }
            if (entity instanceof ExperienceOrbEntity) {
                incrementCount(expOrbOverlapCounts, toOverlapKey(entity));
                continue;
            }
            if (entity instanceof ItemEntity) {
                ItemEntity itemEntity = (ItemEntity) entity;
                Item item = itemEntity.getStack().getItem();
                droppedItemTypeCounts.put(item, droppedItemTypeCounts.getOrDefault(item, 0) + 1);
                incrementCount(droppedItemBlockTypeCounts, toBlockItemKey(itemEntity));
            }
        }
    }

    private int getOverlapCount(Entity entity, Map<Long, Integer> map) {
        return map.getOrDefault(toOverlapKey(entity), 0);
    }

    private int getDroppedItemTypeCount(ItemEntity itemEntity) {
        Item item = itemEntity.getStack().getItem();
        return droppedItemTypeCounts.getOrDefault(item, 0);
    }

    private void incrementCount(Map<Long, Integer> map, long key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    private long toOverlapKey(Entity entity) {
        double cell = Math.max(0.25, overlapCellSize.getInt());
        int cx = (int) Math.floor(entity.getX() / cell);
        int cy = (int) Math.floor(entity.getY() / cell);
        int cz = (int) Math.floor(entity.getZ() / cell);
        return hash3(cx, cy, cz);
    }

    private long hash3(int x, int y, int z) {
        long h = 1469598103934665603L;
        h ^= x;
        h *= 1099511628211L;
        h ^= y;
        h *= 1099511628211L;
        h ^= z;
        h *= 1099511628211L;
        return h;
    }

    private long toBlockItemKey(ItemEntity entity) {
        int cx = (int) Math.floor(entity.getX() / DROPPED_ITEM_GROUP_CELL);
        int cy = (int) Math.floor(entity.getY() / DROPPED_ITEM_GROUP_CELL);
        int cz = (int) Math.floor(entity.getZ() / DROPPED_ITEM_GROUP_CELL);
        long blockKey = hash3(cx, cy, cz);
        int itemId = Item.getRawId(entity.getStack().getItem());
        return blockKey * 31L + itemId;
    }

    private boolean isExpParticle(ParticleEffect effect) {
        return effect.getType() == ParticleTypes.ENCHANT
                || effect.getType() == ParticleTypes.ENTITY_EFFECT
                || effect.getType() == ParticleTypes.EFFECT;
    }

    private boolean shouldSkipDenseItemGroup(ItemEntity itemEntity) {
        int groupedCount = droppedItemBlockTypeCounts.getOrDefault(toBlockItemKey(itemEntity), 0);
        if (groupedCount <= 3) return false;

        var mc = MinecraftClient.getInstance();
        double distanceSq = mc.player.squaredDistanceTo(itemEntity);
        double minKeepDistanceSq = minKeepDistance.getInt() * minKeepDistance.getInt();
        if (distanceSq <= minKeepDistanceSq) return false;

        double keepRatio = Math.max(0.05, 2.0 / groupedCount);
        return stableSample01(itemEntity.getId()) > keepRatio;
    }

    private double stableSample01(int value) {
        int h = value;
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return (h & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }
}


