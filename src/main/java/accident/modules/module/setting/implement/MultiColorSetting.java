package accident.modules.module.setting.implement;

import accident.modules.module.setting.Setting;
import lombok.Getter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Getter
public class MultiColorSetting extends Setting {

    private final List<ColorSetting> colors = new ArrayList<>();
    private int colorCount;
    private final int minColors;
    private final int maxColors;

    public MultiColorSetting(String name, String description, int defaultCount) {
        super(name, description);
        this.colorCount = Math.max(1, Math.min(9, defaultCount));
        this.minColors = 1;
        this.maxColors = 9;
        rebuildColors();
    }

    private void rebuildColors() {
        int[] defaults = {
                0xFF_FF0000, 0xFF_FF7700, 0xFF_FFFF00, 0xFF_00FF00,
                0xFF_00FFFF, 0xFF_0000FF, 0xFF_8800FF, 0xFF_FF00FF, 0xFF_FFFFFF
        };
        while (colors.size() < maxColors) {
            int i = colors.size();
            ColorSetting c = new ColorSetting("Color" + (i + 1), "");
            c.setColor(defaults[i % defaults.length]);
            colors.add(c);
        }
    }

    public void setColorCount(int count) {
        this.colorCount = Math.max(minColors, Math.min(maxColors, count));
    }

    public List<ColorSetting> getActiveColors() {
        return colors.subList(0, colorCount);
    }

    public int[] getActiveColorInts() {
        int[] result = new int[colorCount];
        for (int i = 0; i < colorCount; i++) {
            result[i] = colors.get(i).getColor();
        }
        return result;
    }

    public MultiColorSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }
}