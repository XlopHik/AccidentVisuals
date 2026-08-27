package accident.screens.keybinds;

import net.minecraft.client.gui.DrawContext;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import java.awt.Color;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KeyboardRenderer {

    private static final int KEY_HEIGHT      = 16;
    private static final int KEY_GAP         = 2;
    private static final int UNIT            = KEY_HEIGHT + KEY_GAP;
    private static final int CORNER_RADIUS   = 2;

    private static final int COLOR_KEY_FREE      = 0xFFCCCCCC;
    private static final int COLOR_KEY_OCCUPIED  = 0xFFFF8C00;
    private static final int COLOR_KEY_CONFLICT  = 0xFFCC2200;
    private static final int COLOR_KEY_SELECTED  = 0xFF00AAFF;
    private static final int COLOR_KEY_BG        = 0xFF1A1A1A;
    private static final int COLOR_KEY_BORDER    = 0xFF444444;
    private static final int COLOR_KB_BG         = 0xFF111111;
    private static final int COLOR_TEXT          = 0xFF000000;
    private static final int COLOR_TEXT_BRIGHT   = 0xFFEEEEEE;

    public record KeyCell(String label, int keyCode, int col, int row, float widthUnits) {}

    private static final KeyCell[] LAYOUT = buildLayout();

    private static KeyCell[] buildLayout() {
        final int ESC=256, F1=290,F2=291,F3=292,F4=293,F5=294,F6=295,F7=296,F8=297,F9=298,F10=299,F11=300,F12=301;
        final int GRAVE=96, K1=49,K2=50,K3=51,K4=52,K5=53,K6=54,K7=55,K8=56,K9=57,K0=48,MINUS=45,EQUAL=61,BACKSPACE=259;
        final int TAB=258,Q=81,W=87,E=69,R=82,T=84,Y=89,U=85,I=73,O=79,P=80,LBRACKET=91,RBRACKET=93,BACKSLASH=92;
        final int CAPS=280,A=65,S=83,D=68,F=70,G=71,H=72,J=74,K=75,L=76,SEMICOLON=59,APOSTROPHE=39,ENTER=257;
        final int LSHIFT=340,Z=90,X=88,C=67,V=86,B=66,N=78,M=77,COMMA=44,PERIOD=46,SLASH=47,RSHIFT=344;
        final int LCTRL=341,LALT=342,SPACE=32,RALT=346,RCTRL=345;
        final int INS=260,HOME=268,PGUP=266,DEL=261,END=269,PGDN=267;
        final int UP=265,DOWN=264,LEFT=263,RIGHT=262;
        final int NUMLOCK=282,NUMDIV=331,NUMMUL=332,NUMSUB=333;
        final int NUM7=327,NUM8=328,NUM9=329,NUMADD=334;
        final int NUM4=324,NUM5=325,NUM6=326;
        final int NUM1=321,NUM2=322,NUM3=323,NUMENTER=335;
        final int NUM0=320,NUMDOT=330;



        return new KeyCell[]{
                new KeyCell("Esc",  ESC,   0,  0, 1.0f),
                new KeyCell("F1",   F1,    2,  0, 1.0f),
                new KeyCell("F2",   F2,    3,  0, 1.0f),
                new KeyCell("F3",   F3,    4,  0, 1.0f),
                new KeyCell("F4",   F4,    5,  0, 1.0f),
                new KeyCell("F5",   F5,    6,  0, 1.0f),
                new KeyCell("F6",   F6,    7,  0, 1.0f),
                new KeyCell("F7",   F7,    8,  0, 1.0f),
                new KeyCell("F8",   F8,    9,  0, 1.0f),
                new KeyCell("F9",   F9,    10, 0, 1.0f),
                new KeyCell("F10",  F10,   11, 0, 1.0f),
                new KeyCell("F11",  F11,   12, 0, 1.0f),
                new KeyCell("F12",  F12,   13, 0, 1.0f),

                new KeyCell("`",    GRAVE,     0,  1, 1.0f),
                new KeyCell("1",    K1,        1,  1, 1.0f),
                new KeyCell("2",    K2,        2,  1, 1.0f),
                new KeyCell("3",    K3,        3,  1, 1.0f),
                new KeyCell("4",    K4,        4,  1, 1.0f),
                new KeyCell("5",    K5,        5,  1, 1.0f),
                new KeyCell("6",    K6,        6,  1, 1.0f),
                new KeyCell("7",    K7,        7,  1, 1.0f),
                new KeyCell("8",    K8,        8,  1, 1.0f),
                new KeyCell("9",    K9,        9,  1, 1.0f),
                new KeyCell("0",    K0,        10, 1, 1.0f),
                new KeyCell("-",    MINUS,     11, 1, 1.0f),
                new KeyCell("=",    EQUAL,     12, 1, 1.0f),
                new KeyCell("BS",   BACKSPACE, 13, 1, 2.0f),

                new KeyCell("Tab",  TAB,       0,  2, 1.5f),
                new KeyCell("Q",    Q,         2,  2, 1.0f),
                new KeyCell("W",    W,         3,  2, 1.0f),
                new KeyCell("E",    E,         4,  2, 1.0f),
                new KeyCell("R",    R,         5,  2, 1.0f),
                new KeyCell("T",    T,         6,  2, 1.0f),
                new KeyCell("Y",    Y,         7,  2, 1.0f),
                new KeyCell("U",    U,         8,  2, 1.0f),
                new KeyCell("I",    I,         9,  2, 1.0f),
                new KeyCell("O",    O,         10, 2, 1.0f),
                new KeyCell("P",    P,         11, 2, 1.0f),
                new KeyCell("[",    LBRACKET,  12, 2, 1.0f),
                new KeyCell("]",    RBRACKET,  13, 2, 1.0f),
                new KeyCell("\\",   BACKSLASH, 14, 2, 1.5f),

                new KeyCell("Caps", CAPS,      0,  3, 1.75f),
                new KeyCell("A",    A,         2,  3, 1.0f),
                new KeyCell("S",    S,         3,  3, 1.0f),
                new KeyCell("D",    D,         4,  3, 1.0f),
                new KeyCell("F",    F,         5,  3, 1.0f),
                new KeyCell("G",    G,         6,  3, 1.0f),
                new KeyCell("H",    H,         7,  3, 1.0f),
                new KeyCell("J",    J,         8,  3, 1.0f),
                new KeyCell("K",    K,         9,  3, 1.0f),
                new KeyCell("L",    L,         10, 3, 1.0f),
                new KeyCell(";",    SEMICOLON, 11, 3, 1.0f),
                new KeyCell("'",    APOSTROPHE,12, 3, 1.0f),
                new KeyCell("Enter",ENTER,     13, 3, 2.25f),

                new KeyCell("Shift",LSHIFT,    0,  4, 2.25f),
                new KeyCell("Z",    Z,         2,  4, 1.0f),
                new KeyCell("X",    X,         3,  4, 1.0f),
                new KeyCell("C",    C,         4,  4, 1.0f),
                new KeyCell("V",    V,         5,  4, 1.0f),
                new KeyCell("B",    B,         6,  4, 1.0f),
                new KeyCell("N",    N,         7,  4, 1.0f),
                new KeyCell("M",    M,         8,  4, 1.0f),
                new KeyCell(",",    COMMA,     9,  4, 1.0f),
                new KeyCell(".",    PERIOD,    10, 4, 1.0f),
                new KeyCell("/",    SLASH,     11, 4, 1.0f),
                new KeyCell("Shift",RSHIFT,    12, 4, 3.0f),

                new KeyCell("Ctrl", LCTRL,     0,  5, 1.5f),
                new KeyCell("Alt",  LALT,      2,  5, 1.5f),
                new KeyCell("Space",SPACE,     4,  5, 6.0f),
                new KeyCell("Alt",  RALT,      10, 5, 1.5f),
                new KeyCell("Ctrl", RCTRL,     12, 5, 1.5f),

                new KeyCell("Ins",  INS,       16, 1, 1.0f),
                new KeyCell("Home", HOME,      17, 1, 1.0f),
                new KeyCell("PgUp", PGUP,      18, 1, 1.0f),
                new KeyCell("Del",  DEL,       16, 2, 1.0f),
                new KeyCell("End",  END,       17, 2, 1.0f),
                new KeyCell("PgDn", PGDN,      18, 2, 1.0f),
                new KeyCell("↑",    UP,        17, 4, 1.0f),
                new KeyCell("←",    LEFT,      16, 5, 1.0f),
                new KeyCell("↓",    DOWN,      17, 5, 1.0f),
                new KeyCell("→",    RIGHT,     18, 5, 1.0f),

                new KeyCell("NLk",  NUMLOCK,   20, 1, 1.0f),
                new KeyCell("/",    NUMDIV,    21, 1, 1.0f),
                new KeyCell("*",    NUMMUL,    22, 1, 1.0f),
                new KeyCell("-",    NUMSUB,    23, 1, 1.0f),
                new KeyCell("7",    NUM7,      20, 2, 1.0f),
                new KeyCell("8",    NUM8,      21, 2, 1.0f),
                new KeyCell("9",    NUM9,      22, 2, 1.0f),
                new KeyCell("+",    NUMADD,    23, 2, 1.0f),
                new KeyCell("4",    NUM4,      20, 3, 1.0f),
                new KeyCell("5",    NUM5,      21, 3, 1.0f),
                new KeyCell("6",    NUM6,      22, 3, 1.0f),
                new KeyCell("1",    NUM1,      20, 4, 1.0f),
                new KeyCell("2",    NUM2,      21, 4, 1.0f),
                new KeyCell("3",    NUM3,      22, 4, 1.0f),
                new KeyCell("Ent",  NUMENTER,  23, 4, 1.0f),
                new KeyCell("0",    NUM0,      20, 5, 2.0f),
                new KeyCell(".",    NUMDOT,    22, 5, 1.0f),
        };
    }


    public static int getWidth(float scale) {
        return Math.round((24 * UNIT + 8) * scale);
    }

    public static int getHeight(float scale) {
        // подогнано под то, что реально рисует render() - раньше не хватало одной строки, и легенда лезла на нижний ряд клавиш
        return Math.round((7 * UNIT + 12) * scale);
    }

    // рисуется через Render2D, а не DrawContext - чтобы были скруглённые клавиши, блюр фона и общий шрифт, как в остальном интерфейсе
    // координаты приходят ванильные (в них же потом hit-test), а у Render2D своё фиксированное пространство - поэтому конвертация тут, в последний момент
    private record Pending(int originX, int originY, Set<Integer> occupied,
                          Set<Integer> conflict, int selectedCode, float scale) {}

    private static Pending pending;

    private record Tooltip(float mouseX, float mouseY, java.util.List<String> lines) {}

    private static Tooltip tooltip;
    private static boolean wantLegend;

    /** Queued alongside the keyboard so it lands under it, not behind it. */
    public static void queueLegend() {
        wantLegend = true;
    }

    // тултип кладётся в очередь последним, чтобы рисовался поверх клавиш
    public static void queueTooltip(float mouseX, float mouseY, java.util.List<String> lines) {
        tooltip = lines.isEmpty() ? null : new Tooltip(mouseX, mouseY, lines);
    }

    // клавиатура не рисуется сразу, а копится в очередь - DrawContext тут отложенный, а Render2D рисует сразу, так что она бы оказалась под фоном экрана
    // флашится из хвоста GuiRenderer, там же где скины кнопок и слайдеров
    public static void render(
            DrawContext context,
            int originX,
            int originY,
            Set<Integer> occupiedKeys,
            Set<Integer> conflictKeys,
            int selectedCode,
            float scale
    ) {
        pending = new Pending(originX, originY, occupiedKeys, conflictKeys, selectedCode, scale);
    }

    public static void flush() {
        if (pending == null) return;

        int originX = pending.originX();
        int originY = pending.originY();
        Set<Integer> occupiedKeys = pending.occupied();
        Set<Integer> conflictKeys = pending.conflict();
        int selectedCode = pending.selectedCode();
        float scale = pending.scale();
        pending = null;

        float k = 1f / Render2D.getScaleMultiplier();

        int pad = 6;
        float kbWidth  = (24 * UNIT + pad * 2) * scale;
        float kbHeight = (7 * UNIT + pad * 2) * scale;

        Render2D.beginOverlay();

        float bx = originX * k, by = originY * k;
        float bw = kbWidth * k, bh = kbHeight * k;
        float panelRadius = 8f * k;

        Render2D.blur(bx, by, bw, bh, 12f, panelRadius, new Color(15, 17, 31, 130).getRGB());
        Render2D.outline(bx, by, bw, bh, 1f * k, new Color(255, 255, 255, 22).getRGB(), panelRadius);

        for (KeyCell cell : LAYOUT) {
            float pixelX = originX + pad + cell.col() * UNIT * scale;
            float pixelY = originY + pad + rowYOffset(cell.row(), scale);
            float pixelW = cell.widthUnits() * UNIT * scale - KEY_GAP * scale;
            float pixelH = KEY_HEIGHT * scale;

            if (cell.label().equals("+") || (cell.label().equals("Ent") && cell.col() == 23)) {
                pixelH = (KEY_HEIGHT * 2 + KEY_GAP) * scale;
            }

            float x = pixelX * k, y = pixelY * k;
            float w = pixelW * k, h = pixelH * k;
            float radius = Math.max(1.5f, 3.5f * k);

            boolean conflict = conflictKeys.contains(cell.keyCode());
            boolean selected = selectedCode == cell.keyCode();
            boolean occupied = occupiedKeys.contains(cell.keyCode());

            int fill;
            int border;
            int text;

            if (selected) {
                fill = new Color(110, 116, 227, 235).getRGB();
                border = new Color(200, 205, 255, 235).getRGB();
                text = 0xFFFFFFFF;
            } else if (conflict) {
                fill = new Color(225, 60, 80, 220).getRGB();
                border = new Color(255, 150, 165, 220).getRGB();
                text = 0xFFFFFFFF;
            } else if (occupied) {
                fill = new Color(110, 116, 227, 150).getRGB();
                border = new Color(150, 158, 255, 170).getRGB();
                text = 0xFFF2F3FF;
            } else {
                fill = new Color(30, 32, 38, 190).getRGB();
                border = new Color(255, 255, 255, 28).getRGB();
                text = new Color(196, 199, 208, 235).getRGB();
            }

            Render2D.rect(x, y, w, h, fill, radius);
            Render2D.outline(x, y, w, h, Math.max(0.6f, 0.8f * k), border, radius);

            // у выбранной клавиши мягкое свечение по краю - так её видно сразу на такой плотной раскладке
            if (selected) {
                Render2D.outline(x - 1.5f * k, y - 1.5f * k, w + 3f * k, h + 3f * k,
                        1f * k, new Color(140, 148, 255, 90).getRGB(), radius + 1.5f * k);
            }

            String label = cell.label();

            // размер шрифта считаем от высоты одной строки, а не клавиши - иначе на двухрядных клавишах "Ent"/"+" не влезали и пропадали
            float rowHeight = KEY_HEIGHT * scale * k;
            float fontSize = Math.max(4.5f, rowHeight * 0.45f);

            // в жирном шрифте нет ` = [ ] * + и стрелок - такие клавиши рисовались пустыми
            // проверяем у атласа наличие глифов напрямую: ширина текста это не покажет, у отсутствующего глифа тоже есть advance width
            accident.util.render.font.Font font = hasAllGlyphs(Fonts.BOLD, label) ? Fonts.BOLD : Fonts.TEST;
            float labelWidth = font.getWidth(label, fontSize);

            // что всё равно не влезает (Home, PgUp, PgDn на одной клетке) - просто ужимаем шрифт, а не пропускаем
            float room = w - 2.5f * k;
            if (labelWidth > room && labelWidth > 0.01f) {
                fontSize *= room / labelWidth;
            }

            if (fontSize >= 2.5f) {
                font.drawCentered(label, x + w / 2f,
                        y + (h - font.getHeight(fontSize)) / 2f, fontSize, text);
            }
        }

        if (wantLegend) {
            drawLegend(originX * k, (originY + kbHeight) * k + 8f * k, k);
            wantLegend = false;
        }

        if (tooltip != null) {
            drawTooltip(k);
            tooltip = null;
        }

        Render2D.endOverlay();
    }

    private static boolean hasAllGlyphs(accident.util.render.font.Font font, String text) {
        var atlas = accident.Initialization.getInstance().getManager()
                .getRenderCore().getFontRenderer().getFont(font.getName());
        if (atlas == null) return false;

        atlas.ensureLoaded();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (codePoint != ' ' && !atlas.hasGlyph(codePoint)) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    /** The colour key, sitting under the keyboard. */
    private static void drawLegend(float x, float y, float k) {
        record Item(int colour, String label) {}
        Item[] items = {
                new Item(new Color(30, 32, 38, 190).getRGB(), "Свободна"),
                new Item(new Color(110, 116, 227, 200).getRGB(), "Занята"),
                new Item(new Color(225, 60, 80, 220).getRGB(), "Конфликт"),
        };

        float swatch = 7f * k;
        float gap = 4f * k;
        float fontSize = Math.max(5f, 6.5f * k);
        float cursorX = x;

        for (Item item : items) {
            Render2D.rect(cursorX, y, swatch, swatch, item.colour(), 2f * k);
            Render2D.outline(cursorX, y, swatch, swatch, Math.max(0.6f, 0.8f * k),
                    new Color(255, 255, 255, 40).getRGB(), 2f * k);

            float textX = cursorX + swatch + gap;
            Fonts.TEST.draw(item.label(), textX,
                    y + (swatch - Fonts.TEST.getHeight(fontSize)) / 2f,
                    fontSize, new Color(190, 194, 205, 235).getRGB());

            cursorX = textX + Fonts.TEST.getWidth(item.label(), fontSize) + 12f * k;
        }
    }

    private static void drawTooltip(float k) {
        float fontSize = Math.max(5f, 6.5f * k);
        float lineStep = Fonts.TEST.getHeight(fontSize) + 3f * k;
        float padX = 6f * k, padY = 5f * k;

        float widest = 0f;
        for (String line : tooltip.lines()) {
            widest = Math.max(widest, Fonts.TEST.getWidth(line, fontSize));
        }

        float w = widest + padX * 2f;
        float h = tooltip.lines().size() * lineStep - 3f * k + padY * 2f;

        float x = tooltip.mouseX() * k + 10f * k;
        float y = tooltip.mouseY() * k + 10f * k;

        // тултип не вылезает за окно, если клавиша у правого/нижнего края
        x = Math.min(x, Render2D.getFixedScaledWidth() - w - 4f);
        y = Math.min(y, Render2D.getFixedScaledHeight() - h - 4f);

        Render2D.blur(x, y, w, h, 12f, 5f * k, new Color(15, 17, 31, 170).getRGB());
        Render2D.outline(x, y, w, h, Math.max(0.6f, 0.9f * k),
                new Color(110, 116, 227, 150).getRGB(), 5f * k);

        float lineY = y + padY;
        for (String line : tooltip.lines()) {
            Fonts.TEST.draw(line, x + padX, lineY, fontSize,
                    new Color(235, 237, 245, 240).getRGB());
            lineY += lineStep;
        }
    }

    public static int getKeyCodeAt(
            int mouseX, int mouseY,
            int originX, int originY,
            float scale
    ) {
        int pad = 6;
        for (KeyCell cell : LAYOUT) {
            int pixelX = originX + pad + Math.round(cell.col() * UNIT * scale);
            int pixelY = originY + pad + rowYOffset(cell.row(), scale);
            int pixelW = Math.round(cell.widthUnits() * UNIT * scale) - Math.round(KEY_GAP * scale);
            int pixelH = Math.round(KEY_HEIGHT * scale);

            if (cell.label().equals("+") || (cell.label().equals("Ent") && cell.col() == 23)) {
                pixelH = Math.round((KEY_HEIGHT * 2 + KEY_GAP) * scale);
            }

            if (mouseX >= pixelX && mouseX < pixelX + pixelW
                    && mouseY >= pixelY && mouseY < pixelY + pixelH) {
                return cell.keyCode();
            }
        }
        return -1;
    }


    private static int rowYOffset(int row, float scale) {
        int extraGap = (row >= 1) ? Math.round(4 * scale) : 0;
        return Math.round(row * UNIT * scale) + extraGap;
    }

    private static int getFillColor(int keyCode, Set<Integer> occupied, Set<Integer> conflict, int selected) {
        if (keyCode == selected)  return COLOR_KEY_SELECTED;
        if (conflict.contains(keyCode)) return COLOR_KEY_CONFLICT;
        if (occupied.contains(keyCode)) return COLOR_KEY_OCCUPIED;
        return COLOR_KEY_BG;
    }

    private static int getBorderColor(int keyCode, Set<Integer> conflict, int selected) {
        if (keyCode == selected)        return 0xFF66DDFF;
        if (conflict.contains(keyCode)) return 0xFFFF4422;
        return COLOR_KEY_BORDER;
    }

    private static boolean isLightColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b = argb          & 0xFF;
        return (r * 299 + g * 587 + b * 114) > 180_000;
    }

    public static Map<Integer, Integer> buildKeyUsageMap(KeyBinding[] allKeys) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (KeyBinding kb : allKeys) {
            if (!kb.isUnbound()) {
                InputUtil.Key key = ((accident.mixin.KeyBindingAccessor) kb).getBoundKey();
                if (key.getCategory() == InputUtil.Type.KEYSYM) {
                    counts.merge(key.getCode(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }
}