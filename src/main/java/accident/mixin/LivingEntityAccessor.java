package accident.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    int getLastAttackedTicks();
}

