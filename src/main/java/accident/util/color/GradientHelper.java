package accident.util.color;

import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public class GradientHelper {

    public record GradientSegment(String text, int color) {}

    public static List<GradientSegment> extractSegments(Text node) {
        List<GradientSegment> raw = new ArrayList<>();
        if (node == null) return raw;
        extractRecursive(node, raw);

        List<GradientSegment> merged = new ArrayList<>();
        for (GradientSegment seg : raw) {
            if (merged.isEmpty() || merged.get(merged.size() - 1).color() != seg.color()) {
                merged.add(seg);
            } else {
                GradientSegment last = merged.get(merged.size() - 1);
                merged.set(merged.size() - 1, new GradientSegment(last.text() + seg.text(), last.color()));
            }
        }
        return merged;
    }

    private static void extractRecursive(Text node, List<GradientSegment> out) {
        List<Text> sibs = node.getSiblings();
        String full = node.getString();
        int sibLen = 0;
        for (Text s : sibs) sibLen += s.getString().length();

        if (full.length() > sibLen) {
            String local = full.substring(0, full.length() - sibLen);
            if (!local.isEmpty()) {
                var c = node.getStyle().getColor();
                int color = c != null ? (0xFF000000 | c.getRgb()) : 0xFFFFFFFF;
                out.add(new GradientSegment(local, color));
            }
        }

        for (Text s : sibs) {
            extractRecursive(s, out);
        }
    }
}