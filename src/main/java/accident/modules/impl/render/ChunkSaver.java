package accident.modules.impl.render;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;

import accident.util.Instance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ChunkSaver extends ModuleStructure {

    public static ChunkSaver getInstance() {
        return Instance.get(ChunkSaver.class);
    }

    private final Map<Long, WorldChunk> savedChunks = new ConcurrentHashMap<>();

    public ChunkSaver() {
        super("accident.module.chunksaver.name", "accident.module.chunksaver.desc", ModuleCategory.RENDER);
    }

    @Override
    public boolean deactivate() {
        savedChunks.clear();
        return true;
    }

    public void saveChunk(WorldChunk chunk) {
        if (!isEnabled()) return;
        savedChunks.put(chunk.getPos().toLong(), chunk);
    }

    public WorldChunk getSavedChunk(int x, int z) {
        return savedChunks.get(ChunkPos.toLong(x, z));
    }

    public boolean hasSavedChunk(int x, int z) {
        return savedChunks.containsKey(ChunkPos.toLong(x, z));
    }

    public void removeSavedChunk(int x, int z) {
        savedChunks.remove(ChunkPos.toLong(x, z));
    }

    public void clearSavedChunks() {
        savedChunks.clear();
    }

    public int getSavedChunkCount() {
        return savedChunks.size();
    }
}


