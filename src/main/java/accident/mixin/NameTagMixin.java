package accident.mixin;

import net.minecraft.client.render.command.LabelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import accident.modules.impl.render.NameTags;

@Mixin(LabelCommandRenderer.Commands.class)
public class NameTagMixin {

    @ModifyArg(
            method = "add",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$LabelCommand;<init>(Lorg/joml/Matrix4f;FFLnet/minecraft/text/Text;IIID)V"
            ),
            index = 6 // 7-й параметр (backgroundColor)
    )
    private int modifyBackground(int backgroundColor) {
        NameTags module = NameTags.getInstance();
        if (module != null && module.isState() && module.noBackground.isValue()) {
            return 0; // 0 в ARGB означает полностью прозрачный цвет (альфа-канал = 0)
        }
        return backgroundColor;
    }
}