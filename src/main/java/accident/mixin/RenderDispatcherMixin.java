package accident.mixin;

import net.minecraft.client.render.command.RenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.render.GlassChams;
import accident.util.render.shader.GlassEntityRenderer;

@Mixin(RenderDispatcher.class)
public class RenderDispatcherMixin {
}