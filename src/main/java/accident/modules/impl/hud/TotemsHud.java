package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import accident.client.draggables.AbstractHudElement;
import accident.util.math.MathUtils;
import accident.util.render.font.Fonts;

public class TotemsHud extends AbstractHudElement {
    private float angle, prevAngle;

    public TotemsHud() {
        super("TotemsHud", 100, 100, 24, 24, true);
    }

    @Override
    public void tick() {
        prevAngle = angle;
        if (angle > 0) angle--;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0 || mc.player == null) return;

        Item totemItem = Items.TOTEM_OF_UNDYING;
        int count = getItemCount(totemItem);

        if (count <= 0) return;

        ItemStack displayStack = mc.player.getOffHandStack();
        if (!isTotem(displayStack)) {
            displayStack = mc.player.getMainHandStack();
        }
        if (!isTotem(displayStack)) {
            displayStack = new ItemStack(totemItem);
        }

        float xPos = mc.getWindow().getScaledWidth() / 2f;
        float yPos = mc.getWindow().getScaledHeight() / 2f;

        float delta = mc.getRenderTickCounter().getTickProgress(false);
        float renderAngle = prevAngle + (angle - prevAngle) * delta;

        float scaleFactor = 1f;
        if (mc.player.getActiveItem().isOf(totemItem)) {
            scaleFactor = MathUtils.clamp(1f - (float) mc.player.getItemUseTime() / 40f, 0.01f, 1f);
        }

        org.joml.Matrix3x2fStack matrixStack = context.getMatrices();

        matrixStack.pushMatrix();

        matrixStack.translate(xPos, yPos);
        matrixStack.rotate((float) Math.toRadians(-renderAngle));
        matrixStack.translate(-xPos, -yPos);

        matrixStack.translate(xPos + 20, yPos - 6);
        matrixStack.translate(8f, 8f);
        matrixStack.rotate((float) Math.toRadians(renderAngle * 3f));
        matrixStack.scale(scaleFactor, scaleFactor);
        matrixStack.translate(-8f, -8f);

        context.drawItem(displayStack, 0, 0);

        matrixStack.popMatrix();

        String countText = String.valueOf(count);
        float fontSize = 7f;
        float centerX = xPos + 28f;
        float textWidth = Fonts.BOLD.getWidth(countText, fontSize);

        float textX = centerX - (textWidth / 2f);
        float textY = yPos + 8f;

        Fonts.BOLD.draw(countText, textX + 10.5f, textY - 7.5f, fontSize, 0xFF000000);
        Fonts.BOLD.draw(countText, textX + 10f, textY - 8f, fontSize, 0xFFFFFFFF);
    }

    private boolean isTotem(ItemStack stack) {
        return stack.isOf(Items.TOTEM_OF_UNDYING);
    }

    public int getItemCount(Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public void onUse() {
        this.angle = 15f;
    }
}