package accident.mixin;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import accident.util.render.RevealTintHolder;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateRevealMixin implements RevealTintHolder {

    @Unique
    private int accident$revealTint = RevealTintHolder.NO_TINT;

    @Override
    public int accident$getRevealTint() {
        return accident$revealTint;
    }

    @Override
    public void accident$setRevealTint(int tint) {
        this.accident$revealTint = tint;
    }
}
