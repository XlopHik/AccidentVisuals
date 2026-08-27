package accident.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.MultiColorSetting;
import accident.util.interfaces.AbstractSettingComponent;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MultiColorComponent extends AbstractSettingComponent {

    private final MultiColorSetting multiSetting;

    private boolean draggingCount = false;
    private float countSliderAnim = 0f;

    private int expandedIndex = -1;
    private final List<ColorComponent> colorComponents = new ArrayList<>();

    private float expandAnim = 0f;
    private float hoverAnim  = 0f;
    private long  lastUpdate = System.currentTimeMillis();

    private static final float PREVIEW_SIZE = 10f;
    private static final float PREVIEW_GAP  = 3f;
    private static final float SLIDER_H     = 2.5f;
    private static final float ANIM_SPEED   = 10f;

    private float colX       = 0f;
    private float colW       = 0f;
    private float colBottomY = Float.MAX_VALUE;

    public MultiColorComponent(MultiColorSetting setting) {
        super(setting);
        this.multiSetting = setting;
        rebuildComponents();
    }

    public void setColumnBounds(float colX, float colW, float colBottomY) {
        this.colX       = colX;
        this.colW       = colW;
        this.colBottomY = colBottomY;
    }

    private void rebuildComponents() {
        colorComponents.clear();
        for (ColorSetting cs : multiSetting.getColors())
            colorComponents.add(new ColorComponent(cs));
    }

    private float dt() {
        long now = System.currentTimeMillis();
        float d = Math.min((now - lastUpdate) / 1000f, 0.1f);
        lastUpdate = now;
        return d;
    }

    private float lerp(float a, float b, float s) {
        float diff = b - a;
        return Math.abs(diff) < 0.001f ? b : a + diff * Math.min(s, 1f);
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public float getTotalHeight() {
        float base     = height;
        float expanded = 0f;
        if (expandedIndex >= 0 && expandedIndex < colorComponents.size())
            expanded = colorComponents.get(expandedIndex).getTotalHeight() * expandAnim;
        return base + expanded;
    }

    private ColorComponent getActiveColorComponent() {
        if (expandedIndex >= 0 && expandedIndex < colorComponents.size()) {
            ColorComponent cc = colorComponents.get(expandedIndex);
            cc.position(colX + 2, y + height);
            cc.size(colW - 4, 16f);
            cc.forceExpanded();
            cc.setMaxBottomY(colBottomY);
            return cc;
        }
        return null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float dt = dt();
        hoverAnim  = lerp(hoverAnim,  isHover(mouseX, mouseY) ? 1f : 0f, dt * ANIM_SPEED);
        expandAnim = lerp(expandAnim, expandedIndex >= 0       ? 1f : 0f, dt * ANIM_SPEED);

        if (draggingCount) updateCountFromMouse(mouseX);

        int iconAlpha = clamp((int)(200 * alphaMultiplier));
        Fonts.GUI_ICONS.draw("R", x - 0.5f, y + height / 2 - 8.5f, 9, new Color(210, 210, 210, iconAlpha).getRGB());
        Fonts.BOLD.draw(multiSetting.getName(), x + 9.5f, y + height / 2 - 7.5f, 6, applyAlpha(new Color(210, 210, 220, 200)).getRGB());

        int   count         = multiSetting.getColorCount();
        float totalPreviewW = count * PREVIEW_SIZE + (count - 1) * PREVIEW_GAP;
        float previewStartX = x + width - totalPreviewW - 4;
        float previewY      = y + height / 2 - PREVIEW_SIZE / 2;

        for (int i = 0; i < count; i++) {
            ColorSetting cs = multiSetting.getColors().get(i);
            float px = previewStartX + i * (PREVIEW_SIZE + PREVIEW_GAP);
            if (expandedIndex == i)
                Render2D.outline(px - 1, previewY - 1, PREVIEW_SIZE + 2, PREVIEW_SIZE + 2, 0.5f, applyAlpha(new Color(180, 160, 255, 160)).getRGB(), 3f);
            Render2D.rect(px, previewY, PREVIEW_SIZE, PREVIEW_SIZE, applyAlpha(new Color(cs.getColor(), true)).getRGB(), 3f);
            Render2D.outline(px, previewY, PREVIEW_SIZE, PREVIEW_SIZE, 0.5f, applyAlpha(new Color(100, 100, 105, 80)).getRGB(), 3f);
        }

        float sliderY = y + height - 5f;
        float sliderW = width - 4;
        countSliderAnim = lerp(countSliderAnim, (float)(count - 1) / (multiSetting.getMaxColors() - 1), dt * ANIM_SPEED);

        Render2D.rect(x + 2, sliderY, sliderW, SLIDER_H, applyAlpha(new Color(60, 60, 65, 180)).getRGB(), 2f);
        float filled = sliderW * countSliderAnim;
        if (filled > 0) Render2D.rect(x + 2, sliderY, filled, SLIDER_H, applyAlpha(new Color(140, 120, 220, 200)).getRGB(), 2f);

        float knobSize = 5f;
        Render2D.rect(x + 2 + filled - knobSize / 2, sliderY + SLIDER_H / 2 - knobSize / 2, knobSize, knobSize,
                applyAlpha(new Color(200, 190, 255, 255)).getRGB(), knobSize / 2);

        if (expandedIndex >= 0 && expandedIndex < colorComponents.size() && expandAnim > 0.01f) {
            ColorComponent cc = getActiveColorComponent();
            if (cc != null) {
                final float fAlpha = alphaMultiplier * expandAnim;
                final DrawContext fCtx = context;
                final int fMx = mouseX, fMy = mouseY;
            }
        } else if (expandedIndex < 0) {
            for (ColorComponent cc : colorComponents) cc.setExpanded(false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int   count         = multiSetting.getColorCount();
            float totalPreviewW = count * PREVIEW_SIZE + (count - 1) * PREVIEW_GAP;
            float previewStartX = x + width - totalPreviewW - 4;
            float previewY      = y + height / 2 - PREVIEW_SIZE / 2;

            for (int i = 0; i < count; i++) {
                float px = previewStartX + i * (PREVIEW_SIZE + PREVIEW_GAP);
                if (mouseX >= px && mouseX <= px + PREVIEW_SIZE &&
                        mouseY >= previewY && mouseY <= previewY + PREVIEW_SIZE) {
                    expandedIndex = (expandedIndex == i) ? -1 : i;
                    return true;
                }
            }

            float sliderY = y + height - 5f;
            if (mouseX >= x + 2 && mouseX <= x + 2 + width - 4 &&
                    mouseY >= sliderY - 3 && mouseY <= sliderY + SLIDER_H + 3) {
                draggingCount = true;
                updateCountFromMouse((int) mouseX);
                return true;
            }

            ColorComponent cc = getActiveColorComponent();
            if (cc != null) {
                return cc.mouseClicked(mouseX, mouseY, button);
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) draggingCount = false;
        ColorComponent cc = getActiveColorComponent();
        if (cc != null) cc.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (button == 0 && draggingCount) { updateCountFromMouse((int) mouseX); return true; }
        ColorComponent cc = getActiveColorComponent();
        if (cc != null) return cc.mouseDragged(mouseX, mouseY, button, dX, dY);
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        ColorComponent cc = getActiveColorComponent();
        if (cc != null) return cc.keyPressed(key, scan, mods);
        return false;
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        ColorComponent cc = getActiveColorComponent();
        if (cc != null) return cc.charTyped(chr, mods);
        return false;
    }

    private void updateCountFromMouse(int mouseX) {
        float pct = Math.max(0f, Math.min(1f, (mouseX - x - 2) / (width - 4)));
        multiSetting.setColorCount(1 + Math.round(pct * (multiSetting.getMaxColors() - 1)));
    }

    @Override public void tick() {}

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getTotalHeight();
    }
}