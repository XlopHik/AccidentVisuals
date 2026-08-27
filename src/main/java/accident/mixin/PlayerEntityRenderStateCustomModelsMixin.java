package accident.mixin;

import accident.util.custommodels.ICustomPlayerModelState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateCustomModelsMixin implements ICustomPlayerModelState {
    @Unique
    private boolean accident$hasCustomModel;
    @Unique
    private String accident$customModel = "";

    @Override
    public boolean accident$hasCustomModel() {
        return accident$hasCustomModel;
    }

    @Override
    public String accident$getCustomModel() {
        return accident$customModel;
    }

    @Override
    public void accident$setCustomModel(boolean enabled, String model) {
        accident$hasCustomModel = enabled;
        accident$customModel = model == null ? "" : model;
    }
}
