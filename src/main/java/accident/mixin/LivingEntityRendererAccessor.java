package accident.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Мост в пакете миксинов, нужный для регистрации рендера косметики. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Invoker("addFeature")
    boolean accident$invokeAddFeature(FeatureRenderer<S, M> feature);
}
