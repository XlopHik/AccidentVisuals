package accident.mixin;

import accident.modules.impl.render.ItemReplacer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.jspecify.annotations.Nullable;

@Mixin(ItemModelManager.class)
public abstract class MixinItemRenderer {

    @ModifyVariable(
            method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;Lnet/minecraft/util/HeldItemContext;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack hookModelUpdate(ItemStack stack) {
        return ItemReplacer.getInstance() != null
                ? ItemReplacer.getInstance().getRenderStack(stack)
                : stack;
    }
}