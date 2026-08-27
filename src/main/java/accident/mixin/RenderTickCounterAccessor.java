package accident.mixin;

import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Добавь в fabricmc.mixin.json в секцию "client":
 * "RenderTickCounterAccessor"
 */
@Mixin(RenderTickCounter.Dynamic.class)
public interface RenderTickCounterAccessor {

    @Accessor("lastTimeMillis")
    long getLastTimeMillis();

    @Accessor("lastTimeMillis")
    void setLastTimeMillis(long value);
}