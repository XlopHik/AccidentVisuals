package accident.util.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

// позволяет 2D-пайплайнам рисовать не в основной фреймбуфер, а куда скажем,
// на время одного блока.
//
// раньше GuiEcho вычислял "что тут GUI" через диф кадра до/после отрисовки GUI -
// это не точно: если полупрозрачная панель совпадала по цвету с миром, разница
// выходила нулевой и получались дыры в панелях, плюс любой тинт экрана впечатывался в результат.
// теперь вместо вычисления просто захватываем: направляем пайплайны в прозрачный
// фреймбуфер, даём GUI отрисоваться как обычно - и то, что там осело, и есть GUI с реальной альфой.
//
// подменяется только *назначение* рендера. всё, что читает фреймбуфер как источник
// (BlurPipeline размывает мир за стеклом) - должно читать настоящий, иначе будет мылить пустоту.
public final class GuiCaptureTarget {

    private GuiCaptureTarget() {}

    private static Framebuffer override;

    public static void begin(Framebuffer target) {
        override = target;
    }

    public static void end() {
        override = null;
    }

    public static boolean isActive() {
        return override != null;
    }

    public static GpuTextureView colorView(MinecraftClient client) {
        if (override != null && override.getColorAttachmentView() != null) {
            return override.getColorAttachmentView();
        }
        return client.getFramebuffer().getColorAttachmentView();
    }

    public static GpuTextureView depthView(MinecraftClient client) {
        if (override != null && override.getDepthAttachmentView() != null) {
            return override.getDepthAttachmentView();
        }
        return client.getFramebuffer().getDepthAttachmentView();
    }
}
