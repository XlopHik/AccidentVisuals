package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import accident.client.draggables.AbstractHudElement;
import accident.modules.module.setting.implement.SelectSetting;
import accident.util.animations.Direction;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ArmorHud extends AbstractHudElement {

    public static final SelectSetting orientMode = new SelectSetting("accident.module.armorhud.setting.orient.name", "accident.module.armorhud.setting.orient.desc")
            .value("Horizontal", "Vertical")
            .selected("Horizontal");

    private static final int SLOT_SIZE = 16;
    private final float slotGap = 4f;

    public ArmorHud() {
        super("ArmorHud", 400, 400, 80, 24, true);
        settings(orientMode);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return mc.player != null && !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (mc.player != null) startAnimation();
        else stopAnimation();
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0 || mc.player == null) return;

        List<ItemStack> armor = getArmorStacks();
        boolean isVertical = orientMode.isSelected("Vertical");

        float drawX = getRenderX();
        float drawY = getRenderY();

        for (ItemStack stack : armor) {
            if (!stack.isEmpty()) {
                context.drawItem(stack, (int) drawX, (int) drawY);
                context.drawStackOverlay(mc.textRenderer, stack, (int) drawX, (int) drawY);
            }
            if (isVertical) drawY += SLOT_SIZE + slotGap;
            else drawX += SLOT_SIZE + slotGap;
        }

        if (isVertical) {
            setWidth(SLOT_SIZE);
            setHeight((int) (armor.size() * (SLOT_SIZE + slotGap) - slotGap));
        } else {
            setWidth((int) (armor.size() * (SLOT_SIZE + slotGap) - slotGap));
            setHeight(SLOT_SIZE);
        }
    }

    private List<ItemStack> getArmorStacks() {
        List<ItemStack> armor = new ArrayList<>();
        armor.add(mc.player.getEquippedStack(EquipmentSlot.HEAD));
        armor.add(mc.player.getEquippedStack(EquipmentSlot.CHEST));
        armor.add(mc.player.getEquippedStack(EquipmentSlot.LEGS));
        armor.add(mc.player.getEquippedStack(EquipmentSlot.FEET));
        return armor;
    }
}
