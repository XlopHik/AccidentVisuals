package accident.screens.keybinds;

import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.render.shader.Scissor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

// рисует список бинды через свой рендерер, а не ванильными виджетами
// строки не рисуются сразу, а копятся в очередь и флашятся GuiRenderer'ом в конце кадра (как и клавиатура рядом) - иначе они окажутся под фоном экрана
// список скроллится, так что очередь клипается по вьюпорту, иначе строки за краем рисовались бы поверх хедера и кнопок
public final class KeybindListRenderer {

    private KeybindListRenderer() {}

    private record Row(float x, float y, float w, float h,
                       String name, String keyLabel,
                       int rowFill, int badgeFill, int badgeBorder,
                       int nameColour, int keyColour) {}

    private record Header(float x, float y, float w, float h, String label) {}

    private static final List<Row> ROWS = new ArrayList<>();
    private static final List<Header> HEADERS = new ArrayList<>();

    private static float viewX, viewY, viewW, viewH;
    private static boolean hasViewport;

    public static void setViewport(float x, float y, float w, float h) {
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
        hasViewport = true;
    }

    public static void queueRow(float x, float y, float w, float h,
                                String name, String keyLabel,
                                int rowFill, int badgeFill, int badgeBorder,
                                int nameColour, int keyColour) {
        ROWS.add(new Row(x, y, w, h, name, keyLabel,
                rowFill, badgeFill, badgeBorder, nameColour, keyColour));
    }

    public static void queueHeader(float x, float y, float w, float h, String label) {
        HEADERS.add(new Header(x, y, w, h, label));
    }

    public static void flush() {
        if (ROWS.isEmpty() && HEADERS.isEmpty()) {
            hasViewport = false;
            return;
        }

        float k = 1f / Render2D.getScaleMultiplier();

        Render2D.beginOverlay();
        if (hasViewport) {
            Scissor.enableForClickGuiOverlay(viewX * k, viewY * k, viewW * k, viewH * k);
        }

        for (Header header : HEADERS) {
            drawHeader(header, k);
        }
        for (Row row : ROWS) {
            drawRow(row, k);
        }

        if (hasViewport) Scissor.disable();
        Render2D.endOverlay();

        ROWS.clear();
        HEADERS.clear();
        hasViewport = false;
    }

    private static void drawHeader(Header header, float k) {
        float x = header.x() * k, y = header.y() * k;
        float w = header.w() * k, h = header.h() * k;

        float fontSize = Math.max(5.5f, 7.5f * k);
        float labelW = Fonts.BOLD.getWidth(header.label(), fontSize);
        float centreY = y + h / 2f;

        // линии по бокам от текста, а не под ним - иначе выглядит как зачёркнутое (как в ванили)
        float gap = labelW / 2f + 6f * k;
        int line = new Color(255, 255, 255, 28).getRGB();

        Render2D.rect(x + 4f * k, centreY - 0.5f * k, w / 2f - gap - 4f * k, 1f * k, line, 0f);
        Render2D.rect(x + w / 2f + gap, centreY - 0.5f * k, w / 2f - gap - 4f * k, 1f * k, line, 0f);

        Fonts.BOLD.drawCentered(header.label(), x + w / 2f,
                centreY - Fonts.BOLD.getHeight(fontSize) / 2f, fontSize,
                new Color(150, 158, 255, 240).getRGB());
    }

    private static void drawRow(Row row, float k) {
        float x = row.x() * k, y = row.y() * k;
        float w = row.w() * k, h = row.h() * k;

        if (((row.rowFill() >>> 24) & 0xFF) > 0) {
            Render2D.rect(x + 2f * k, y + 1f * k, w - 4f * k, h - 2f * k, row.rowFill(), 4f * k);
        }

        float fontSize = Math.max(5.5f, 7f * k);
        Fonts.TEST.draw(row.name(), x + 7f * k,
                y + (h - Fonts.TEST.getHeight(fontSize)) / 2f, fontSize, row.nameColour());

        // бейдж справа, ширина по тексту - чтобы длинное имя клавиши не обрезалось
        float badgeFont = Math.max(5f, 6.5f * k);
        float textW = Fonts.BOLD.getWidth(row.keyLabel(), badgeFont);
        float badgeW = Math.max(textW + 12f * k, 40f * k);
        float badgeH = h - 5f * k;
        float badgeX = x + w - badgeW - 7f * k;
        float badgeY = y + 2.5f * k;
        float radius = 4f * k;

        Render2D.rect(badgeX, badgeY, badgeW, badgeH, row.badgeFill(), radius);
        Render2D.outline(badgeX, badgeY, badgeW, badgeH, Math.max(0.6f, 0.8f * k),
                row.badgeBorder(), radius);

        Fonts.BOLD.drawCentered(row.keyLabel(), badgeX + badgeW / 2f,
                badgeY + (badgeH - Fonts.BOLD.getHeight(badgeFont)) / 2f,
                badgeFont, row.keyColour());
    }
}
