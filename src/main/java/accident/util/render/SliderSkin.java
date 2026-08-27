package accident.util.render;

import accident.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;

// то же самое, что ButtonSkin, но для слайдеров
public final class SliderSkin {

    public record Entry(float x, float y, float w, float h, float radius, double value,
                         int bodyColor, int fillColor, int handleColor, int borderColor,
                         String label, int textColor, float textSize) {
    }

    private static final List<Entry> QUEUE = new ArrayList<>();

    private SliderSkin() {
    }

    public static void add(Entry entry) {
        QUEUE.add(entry);
    }

    public static void flush() {
        if (QUEUE.isEmpty()) return;

        Render2D.beginOverlay();
        for (Entry e : QUEUE) {
            Render2D.rect(e.x(), e.y(), e.w(), e.h(), e.bodyColor(), e.radius());

            float fillW = (float) (e.value() * (e.w() - 2));
            if (fillW > 0.5f) {
                Render2D.rect(e.x() + 1, e.y() + 1, fillW, e.h() - 2, e.fillColor(), Math.max(0f, e.radius() - 1));
            }

            float handleW = Math.min(4f, e.w() * 0.06f);
            float handleX = e.x() + (float) (e.value() * (e.w() - handleW));
            Render2D.rect(handleX, e.y(), handleW, e.h(), e.handleColor(), Math.max(0f, e.radius() - 1));

            Render2D.outline(e.x(), e.y(), e.w(), e.h(), 1f, e.borderColor(), e.radius());

            float cx = e.x() + e.w() / 2f;
            float cy = e.y() + e.h() / 2f - Fonts.TEST.getHeight(e.textSize()) / 2f;
            Fonts.TEST.drawCentered(e.label(), cx, cy, e.textSize(), e.textColor());
        }
        Render2D.endOverlay();

        QUEUE.clear();
    }
}
