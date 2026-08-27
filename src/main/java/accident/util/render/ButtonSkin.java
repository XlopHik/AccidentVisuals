package accident.util.render;

import accident.util.render.font.Fonts;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

// очередь на кадр для кастомных кнопок из PressableWidgetMixin.
// флашится в GuiRendererMixin в конце GuiRenderer.render() - раньше нельзя,
// иначе Render2D рисует поверх ещё не отправленного на GPU кадра и всё ломается
public final class ButtonSkin {

    public record Entry(float x, float y, float w, float h, float radius,
                         int bodyColor, int borderColor,
                         String label, int textColor, float textSize) {
    }

    private static final List<Entry> QUEUE = new ArrayList<>();

    private ButtonSkin() {
    }

    public static void add(Entry entry) {
        QUEUE.add(entry);
    }

    public static void flush() {
        if (QUEUE.isEmpty()) return;

        Render2D.beginOverlay();
        for (Entry e : QUEUE) {
            Render2D.rect(e.x(), e.y(), e.w(), e.h(), e.bodyColor(), e.radius());
            Render2D.outline(e.x(), e.y(), e.w(), e.h(), 1f, e.borderColor(), e.radius());

            float cx = e.x() + e.w() / 2f;
            float cy = e.y() + e.h() / 2f - Fonts.TEST.getHeight(e.textSize()) / 2f;
            Fonts.TEST.drawCentered(e.label(), cx, cy, e.textSize(), e.textColor());
        }
        Render2D.endOverlay();

        QUEUE.clear();
    }
}
