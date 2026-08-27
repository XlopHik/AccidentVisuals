package accident.mixin;

import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.Initialization;
import accident.modules.impl.render.ChunkSaver;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {

    @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
    private void onUnload(ChunkPos pos, CallbackInfo ci) {
        if (Initialization.getInstance() == null) return;

        ChunkSaver saver = ChunkSaver.getInstance();
        if (!saver.isEnabled()) return;

        ClientChunkManager self = (ClientChunkManager) (Object) this;
        WorldChunk chunk = self.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (chunk != null) {
            saver.saveChunk(chunk);
        }

        ci.cancel();
    }

    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void onLoadChunkFromPacket(int x, int z, PacketByteBuf buf,
                                       Map<Heightmap.Type, long[]> heightmaps,
                                       Consumer<ChunkData.BlockEntityVisitor> consumer,
                                       CallbackInfoReturnable<WorldChunk> cir) {
        if (Initialization.getInstance() == null) return;

        ChunkSaver saver = ChunkSaver.getInstance();
        if (saver.isEnabled()) {
            saver.removeSavedChunk(x, z);
        }
    }
}