package accident.modules.impl.misc;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import accident.events.api.EventHandler;
import accident.events.impl.PacketEvent;
import accident.events.impl.TotemPopEvent;
import accident.events.impl.TickEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.modules.impl.hud.Notifications;
import accident.util.color.GradientHelper;
import accident.util.lang.LanguageManager;
import accident.util.string.chat.ChatMessage;
import accident.util.timer.TimerUtil;

import java.util.List;
import java.util.Set;

public final class Notify extends ModuleStructure {
    private static Notify INSTANCE;

    public final SelectSetting mode = new SelectSetting("accident.module.notify.setting.mode.name", "accident.module.notify.setting.mode.desc")
            .value("Notification", "Chat");
    public final BooleanSetting potions = new BooleanSetting("accident.module.notify.setting.potions.name", "accident.module.notify.setting.potions.desc")
            .setValue(false);
    public final BooleanSetting upItems = new BooleanSetting("accident.module.notify.setting.upitems.name", "accident.module.notify.setting.upitems.desc")
            .setValue(false);
    public final BooleanSetting totemPops = new BooleanSetting("accident.module.notify.setting.totempops.name", "accident.module.notify.setting.totempops.desc")
            .setValue(true);
    public final BooleanSetting durabilityAlert = new BooleanSetting("accident.module.notify.setting.durabilityalert.name", "accident.module.notify.setting.durabilityalert.desc")
            .setValue(true);
    public final SliderSettings durabilityPercent = new SliderSettings("accident.module.notify.setting.durabilitypercent.name", "accident.module.notify.setting.durabilitypercent.desc")
            .range(1f, 100f)
            .setValue(20f)
            .setInteger(true)
            .visible(() -> durabilityAlert.isValue());

    private final Set<Item> trackedItems = Set.of(
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.NETHERITE_SWORD, Items.ELYTRA, Items.TOTEM_OF_UNDYING
    );

    private final TimerUtil durabilityTimer = new TimerUtil();
    private static final int[] ARMOR_SLOTS = {36, 37, 38, 39};

    public Notify() {
        super("accident.module.notify.name", "accident.module.notify.desc", ModuleCategory.MISC);
        settings(mode, potions, upItems, totemPops, durabilityAlert, durabilityPercent);
        INSTANCE = this;
    }

    @Override
    public boolean activate() {
        durabilityTimer.reset();
        return super.activate();
    }

    private static void sendNotify(String content, ItemStack icon, int duration) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        mc.execute(() -> {
            if (INSTANCE.mode.isSelected("Notification")) {
                if (Notifications.getInstance() != null) {
                    Notifications.getInstance().addNotification(content, icon, duration);
                }
            } else if (INSTANCE.mode.isSelected("Chat")) {
                ChatMessage.brandmessage(content);
            }
        });
    }

    private static String getItemNameWithGradient(ItemStack stack) {
        if (stack.isEmpty()) return "Unknown";

        List<GradientHelper.GradientSegment> segments = GradientHelper.extractSegments(stack.getName());
        if (!segments.isEmpty()) {
            return segmentsToFormattedString(segments);
        }

        String rawName = stack.getName().getString().replaceAll("§[0-9a-fk-orx]", "");
        Formatting color = Formatting.WHITE;

        if (stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            int rgb = findTextColor(stack.getName());
            if (rgb != -1) color = getClosestFormatting(rgb);
        } else if (stack.getItem().toString().contains("netherite") ||
                stack.isOf(Items.ELYTRA) ||
                stack.isOf(Items.TOTEM_OF_UNDYING)) {
            color = Formatting.LIGHT_PURPLE;
        }

        return color + rawName;
    }

    private static String segmentsToFormattedString(List<GradientHelper.GradientSegment> segments) {
        if (segments == null || segments.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (GradientHelper.GradientSegment seg : segments) {
            int rgb = seg.color() & 0xFFFFFF;
            result.append("§#")
                    .append(String.format("%06X", rgb))
                    .append(seg.text());
        }
        return result.toString();
    }

    private static int getDurabilityPercent(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return 100;
        int max = stack.getMaxDamage();
        int current = max - stack.getDamage();
        return Math.max(0, Math.min(100, (int) (current * 100f / max)));
    }

    private static boolean hasLowDurability(PlayerEntity player, int threshold) {
        for (int slot : ARMOR_SLOTS) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isDamageable() && getDurabilityPercent(stack) < threshold) return true;
        }
        return false;
    }

    private static ItemStack getLowDurabilityItem(PlayerEntity player, int threshold) {
        for (int slot : ARMOR_SLOTS) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isDamageable() && getDurabilityPercent(stack) < threshold) return stack;
        }
        return ItemStack.EMPTY;
    }

    @EventHandler
    public void onReceivePacket(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;
        if (upItems.isValue() && event.getPacket() instanceof ItemPickupAnimationS2CPacket packet) {
            Entity collector = mc.world.getEntityById(packet.getCollectorEntityId());
            Entity pickedUpEntity = mc.world.getEntityById(packet.getEntityId());
            if (collector instanceof PlayerEntity player && player == mc.player && pickedUpEntity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getStack().copy();
                if (!stack.isEmpty() && (trackedItems.contains(stack.getItem()) || isDonateItem(stack))) {
                    onPlayerPickupItem(stack, isDonateItem(stack));
                }
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (durabilityAlert.isValue()) {
            int threshold = durabilityPercent.getInt();
            if (hasLowDurability(mc.player, threshold) && durabilityTimer.hasTimeElapsed(10000)) {
                ItemStack lowItem = getLowDurabilityItem(mc.player, threshold);
                if (!lowItem.isEmpty()) {
                    String itemName = getItemNameWithGradient(lowItem);
                    sendNotify(Formatting.RED + String.format(LanguageManager.get("accident.notification.durability"), itemName, getDurabilityPercent(lowItem)), lowItem, 4000);
                }
                durabilityTimer.reset();
            }
        }
    }

    @EventHandler
    public void onTotemPop(TotemPopEvent e) {
        if (mc.world == null || mc.player == null || !totemPops.isValue()) return;
        if (e.getEntity() == mc.player) {
            ChatMessage.totemPopMessage(mc.player.getName().getString(), false);
            sendNotify(Formatting.RED + LanguageManager.get("accident.notification.totem"), new ItemStack(Items.TOTEM_OF_UNDYING), 3000);
        }
    }

    public static void onPotionExpired(String effectName) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || !INSTANCE.potions.isValue()) return;
        sendNotify(Formatting.WHITE + String.format(LanguageManager.get("accident.notification.potion"), effectName), new ItemStack(Items.GLASS_BOTTLE), 3000);
    }

    public static void onPlayerPickupItem(ItemStack stack, boolean isDonate) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || !INSTANCE.upItems.isValue()) return;

        List<GradientHelper.GradientSegment> segments = GradientHelper.extractSegments(stack.getName());
        String formattedItemName;

        if (!segments.isEmpty()) {
            formattedItemName = segmentsToFormattedString(segments);
        } else {
            String rawItemName = stack.getName().getString().replaceAll("§[0-9a-fk-orx]", "");
            Formatting itemColor = Formatting.AQUA;

            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                itemColor = Formatting.RED;
            } else if (stack.isOf(Items.ELYTRA) || stack.getItem().toString().contains("netherite")) {
                itemColor = Formatting.LIGHT_PURPLE;
            } else if (isDonate) {
                int rgb = findTextColor(stack.getName());
                itemColor = (rgb != -1) ? getClosestFormatting(rgb) : Formatting.GOLD;
            }

            formattedItemName = itemColor + rawItemName;
        }

        String text = Formatting.WHITE + (isDonate ? String.format(LanguageManager.get("accident.notification.pickup_donate"), formattedItemName) : String.format(LanguageManager.get("accident.notification.pickup"), formattedItemName));
        sendNotify(text, stack, 4000);
    }

    private static Formatting getClosestFormatting(int rgbColor) {
        int r = (rgbColor >> 16) & 0xFF, g = (rgbColor >> 8) & 0xFF, b = rgbColor & 0xFF;
        Formatting closest = Formatting.WHITE;
        double minFound = Double.MAX_VALUE;
        for (Formatting f : Formatting.values()) {
            if (f.getColorValue() == null) continue;
            int c = f.getColorValue();
            double dist = Math.pow(r - ((c >> 16) & 0xFF), 2) + Math.pow(g - ((c >> 8) & 0xFF), 2) + Math.pow(b - (c & 0xFF), 2);
            if (dist < minFound) {
                minFound = dist;
                closest = f;
            }
        }
        return closest;
    }

    private static boolean isDonateItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.contains(DataComponentTypes.CUSTOM_NAME) && findTextColor(stack.getName()) != -1;
    }

    private static int findTextColor(Text text) {
        if (text == null) return -1;
        TextColor color = text.getStyle().getColor();
        if (color != null) return color.getRgb();
        for (Text sibling : text.getSiblings()) {
            int rgb = findTextColor(sibling);
            if (rgb != -1) return rgb;
        }
        return -1;
    }
}


