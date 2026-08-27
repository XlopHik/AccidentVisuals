package accident.util.inventory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.util.Identifier;
import accident.mixin.ClientWorldAccessor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class InventoryUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private InventoryUtils() {}

    public static void click(int slot, int button, SlotActionType type) {
        if (mc.player == null || mc.interactionManager == null || slot == -1) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, button, type, mc.player);
    }
}