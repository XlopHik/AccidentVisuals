package accident.util.render;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import accident.screens.clickgui.dropdown.ThemesColumn;

public class ThemeGradient {

    public static Text applyThemeGradient(String text, ThemesColumn.Theme theme, boolean bold) {
        if (text == null || text.isEmpty()) return Text.literal("");

        MutableText result = Text.literal("");
        int[] palette = theme.palette;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == ' ') {
                MutableText space = Text.literal(" ");
                if (bold) space.setStyle(space.getStyle().withFormatting(Formatting.BOLD));
                result.append(space);
                continue;
            }

            float progress = text.length() > 1 ? (float) i / (text.length() - 1) : 0f;
            int color = getInterpolatedColor(palette, progress);

            MutableText letter = Text.literal(String.valueOf(c));
            letter.setStyle(letter.getStyle()
                    .withColor(TextColor.fromRgb(color & 0xFFFFFF)));
            if (bold) {
                letter.setStyle(letter.getStyle().withFormatting(Formatting.BOLD));
            }
            result.append(letter);
        }

        return result;
    }

    private static int getInterpolatedColor(int[] palette, float progress) {
        if (palette == null || palette.length == 0) return 0xFFFFFF;
        if (palette.length == 1) return palette[0];

        float indexFloat = progress * (palette.length - 1);
        int idx1 = (int) indexFloat;
        int idx2 = Math.min(idx1 + 1, palette.length - 1);
        float frac = indexFloat - idx1;

        return interpolateColor(palette[idx1], palette[idx2], frac);
    }

    private static int interpolateColor(int color1, int color2, float factor) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        return ((int) (r1 + factor * (r2 - r1)) << 16)
                | ((int) (g1 + factor * (g2 - g1)) << 8)
                | (int) (b1 + factor * (b2 - b1));
    }
}