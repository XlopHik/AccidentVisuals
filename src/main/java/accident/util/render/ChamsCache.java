package accident.util.render;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import java.util.Map;
import java.util.WeakHashMap;

public final class ChamsCache {
    public static final Map<LivingEntityRenderState, Integer> STATE_CACHE = new WeakHashMap<>();
    public static final Map<LivingEntityRenderState, Identifier> SKIN_CACHE = new WeakHashMap<>();

    private ChamsCache() {}
}