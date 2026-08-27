package accident.modules.impl.misc;

   
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import accident.events.api.EventHandler;
import accident.events.impl.ClickSlotEvent;
import accident.events.impl.HandledScreenEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.inventory.InventoryUtils;
import accident.util.string.PlayerInteractionHelper;
import accident.util.timer.StopWatch;

import java.util.stream.Stream;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ItemScroller extends ModuleStructure {
    StopWatch stopWatch = new StopWatch();

    SliderSettings scrollerSetting = new SliderSettings("accident.module.itemscroller.setting.scrollersetting.name", "accident.module.itemscroller.setting.scrollersetting.desc")
            .setValue(50).range(0, 200);

    public ItemScroller() {
        super("accident.module.itemscroller.name", "accident.module.itemscroller.desc", ModuleCategory.MISC);
        settings(scrollerSetting);
    }

    @EventHandler
      
    public void onHandledScreen(HandledScreenEvent e) {
        if (mc.player == null) return;

        if (mc.currentScreen instanceof CreativeInventoryScreen) {
            return;
        }


        Slot hoverSlot = e.getSlotHover();
        SlotActionType actionType = getActionType();

        if (PlayerInteractionHelper.isKey(mc.options.sneakKey)
                && !PlayerInteractionHelper.isKey(mc.options.sprintKey)
                && hoverSlot != null
                && hoverSlot.hasStack()
                && actionType != null
                && stopWatch.every(scrollerSetting.getValue())) {

            InventoryUtils.click(
                    hoverSlot.id,
                    actionType.equals(SlotActionType.THROW) ? 1 : 0,
                    actionType
            );
        }
    }

     
    private SlotActionType getActionType() {
        return PlayerInteractionHelper.isKey(mc.options.dropKey)
                ? SlotActionType.THROW
                : PlayerInteractionHelper.isKey(mc.options.attackKey)
                ? SlotActionType.QUICK_MOVE
                : null;
    }

    @EventHandler
      
    public void onClickSlot(ClickSlotEvent e) {
        if (mc.player == null) return;

        if (mc.currentScreen instanceof CreativeInventoryScreen) {
            return;
        }

        int slotId = e.getSlotId();
        if (slotId < 0 || slotId >= mc.player.currentScreenHandler.slots.size()) return;

        Slot slot = mc.player.currentScreenHandler.getSlot(slotId);
        Item item = slot.getStack().getItem();

        if (item != null
                && PlayerInteractionHelper.isKey(mc.options.sneakKey)
                && PlayerInteractionHelper.isKey(mc.options.sprintKey)
                && stopWatch.every(50)) {

            processSlotClick(slot, item, e);
        }
    }

      
    private void processSlotClick(Slot slot, Item item, ClickSlotEvent e) {
        getSlots()
                .filter(s -> s.getStack().getItem().equals(item) && s.inventory.equals(slot.inventory))
                .forEach(s -> InventoryUtils.click(s.id, 1, e.getActionType()));
    }

    private Stream<Slot> getSlots() {
        return mc.player.currentScreenHandler.slots.stream();
    }
}


