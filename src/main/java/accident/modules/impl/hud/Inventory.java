package accident.modules.impl.hud;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import accident.client.draggables.AbstractHudElement;
import accident.util.animations.Direction;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.render.item.ItemRender;
import accident.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.List;

public class Inventory extends AbstractHudElement {

    private static final int SLOT_SIZE = 16;
    private static final int SLOTS_PER_ROW = 9;
    private static final int INVENTORY_ROWS = 3;
    private static final float ITEM_SCALE = 0.65f;
    private static final float FONT_SIZE = 7f;

    private int filledSlots = 0;
    private long lastTimeNs = 0L;

    public Inventory() {
        super("Inventory", 20, 60, 160, 60, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    private float computeDtSeconds() {
        long now = System.nanoTime();
        if (lastTimeNs == 0L) {
            lastTimeNs = now;
            return 1f / 60f;
        }
        long d = now - lastTimeNs;
        lastTimeNs = now;
        double dt = Math.min(Math.max(d / 1_000_000_000.0, 0.0), 0.1);
        return (float) dt;
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            filledSlots = 0;
            stopAnimation();
            return;
        }

        filledSlots = 0;
        for (int i = 9; i < 36; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                filledSlots++;
            }
        }

        if (filledSlots > 0 || isChat(mc.currentScreen)) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0 || mc.player == null) return;

        float alphaFactor = alpha / 255.0f;
        float x = getRenderX();
        float y = getRenderY();

        float padding = 8;
        float slotGap = 2;

        float slotsWidth = SLOTS_PER_ROW * SLOT_SIZE + (SLOTS_PER_ROW - 1) * slotGap;
        float slotsHeight = INVENTORY_ROWS * SLOT_SIZE + (INVENTORY_ROWS - 1) * slotGap;

        float contentWidth = slotsWidth + padding * 2;
        float contentHeight = slotsHeight + padding * 2;

        setWidth((int) contentWidth);
        setHeight((int) contentHeight);

        int glassAlpha = (int)(alphaFactor * 60);
        int bgColor = (glassAlpha << 24) | 0x0F121F;
        Render2D.blur(x, y, contentWidth, contentHeight, 10f, 4f, bgColor);

        float slotsStartX = x + padding;
        float slotsStartY = y + padding;

        List<CountLabel> countLabels = new ArrayList<>();

        for (int row = 0; row < INVENTORY_ROWS; row++) {
            for (int col = 0; col < SLOTS_PER_ROW; col++) {
                int slotIndex = 9 + row * SLOTS_PER_ROW + col;
                float slotX = slotsStartX + col * (SLOT_SIZE + slotGap);
                float slotY = slotsStartY + row * (SLOT_SIZE + slotGap);

                ItemStack stack = mc.player.getInventory().getStack(slotIndex);

                int slotBgAlpha = (int)(alphaFactor * 30);
                int slotBgColor = (slotBgAlpha << 24) | 0x000000;
                Render2D.rect(slotX, slotY, SLOT_SIZE, SLOT_SIZE, slotBgColor, 3);

                if (!stack.isEmpty()) {
                    float scaledSize = 15 * ITEM_SCALE;
                    float offset = (SLOT_SIZE - scaledSize) / 2f;

                    float itemX = slotX + offset;
                    float itemY = slotY + offset;

                    GlStateManager._enableBlend();
                    GlStateManager._blendFuncSeparate(
                            GlConst.GL_SRC_ALPHA,
                            GlConst.GL_ONE_MINUS_SRC_ALPHA,
                            GlConst.GL_ONE,
                            GlConst.GL_ZERO);

                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate((float)Math.floor(itemX), (float)Math.floor(itemY));
                    context.getMatrices().scale(ITEM_SCALE, ITEM_SCALE);

                    context.drawItem(stack, 0, 0);
                    context.drawStackOverlay(mc.textRenderer, stack, 0, 0);

                    context.getMatrices().popMatrix();

                    if (stack.getCount() > 1) {
                        countLabels.add(new CountLabel(slotX, slotY, stack.getCount()));
                    }
                }
            }
        }

        for (CountLabel label : countLabels) {
            String countText = String.valueOf(label.count);
        }
    }

    private record CountLabel(float slotX, float slotY, int count) {}
}