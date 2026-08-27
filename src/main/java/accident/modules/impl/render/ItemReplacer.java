package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SelectSetting;


import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ItemReplacer extends ModuleStructure {

    private static ItemReplacer instance;

    public static ItemReplacer getInstance() { return instance; }

    public final SelectSetting model = new SelectSetting("accident.module.itemreplacer.setting.model.name", "accident.module.itemreplacer.setting.model.desc")
            .value(
                    "Abominable Blade", "Abominable Great Saber", "Abominable Scythe",
                    "Acidic Cleaver", "Amethyst Shuriken", "Ancient Royal Great Sword",
                    "Aquatic Sacred Blade", "Arcanethyst", "Ashura's Blade",
                    "Awakened Lichblade", "Blood Edge", "Bloody Death", "Bramblethorn",
                    "Brimstone Claymore", "Carian Knight's Sword", "Chrono Blade",
                    "Corrupted Mythic Blade", "Creation Splitter", "Crescent Rose",
                    "Cyber Katana", "Cyber Mantis Blade", "Cyber Sword",
                    "Cybernetic Chainsaw Blade", "Cybernetic Katana", "Cybernetic Knife",
                    "Dainsleif", "Dark Blade", "Dark Cleaver", "Death Knight's Dagger",
                    "Death Knight's Sword", "Demigod's Unholy Blade", "Demigod's Unholy Halberd",
                    "Demon Lord's Great Axe", "Demon Lord's Sword", "Demonic Blade",
                    "Demonic Cleaver", "Divine Axe Rhitta", "Divine Justice", "Divine Punisher",
                    "Divine Reaper", "Dragon Slaying blade", "Edge Of The Astral Plane",
                    "Emberblade", "Enigma", "Epic Sword", "Estoc", "Fallen God's Spear",
                    "Fallen God's Sword", "Floral Longsword", "Floral Sabre",
                    "Forest Guardian's Glaive", "Frost Axe", "Frost Blade", "Frost Scythe",
                    "Hearthflame", "Hero Sword", "Holy Moonlight Sword", "Hornet's Needle",
                    "Icewhisper", "Jade Halberd", "Katana", "Legendary Sword", "Longsword",
                    "Magi Scythe", "Masamune", "Mjolnir", "Molten Blade", "Molten Sword",
                    "Muramasa", "Mystical Spellblade", "Mythic Blade", "Ocean's Rage",
                    "Partisan", "Pharaoh's Treasure", "Pheonix Grace", "Plague Longsword",
                    "Power Fuse Hammer", "Power Fuse Sword", "Requiem of the Ninth Abyss",
                    "Ribbon Cleaver", "Righteous Relic", "Rivers Of Blood", "Royal Chakram",
                    "Royal Rapier", "Sabre", "Scissor Blade", "Sculk Cleaver", "Sculk Scythe",
                    "Sculk Sword", "Sentinel's Will", "Silverine Blade", "Soul Claws",
                    "Soul Collector", "Soul Devourer", "Soul Edge", "Soul Harvester",
                    "Soul Stealer", "Soulrender", "Star's Edge", "Steel Sword", "Stop Sign",
                    "Storm Bringer", "Storm's Edge", "Sunbreak", "Tengen's Blade", "Terra Blade",
                    "Thousand Demon Daggers", "Thunder Bringer", "Thunderbrand", "True Excalibur",
                    "Vampiric Needle", "Wakizashi", "Watcher Claymore", "Watching Warglaive",
                    "Waxweaver", "Whisperwind", "Wickpiercer", "Wraith Scythe", "Yoru"
            )
            .selected("Katana");

    public ItemReplacer() {
        super("accident.module.itemreplacer.name", "accident.module.itemreplacer.desc", ModuleCategory.MISC);
        instance = this;
        settings(model);
    }

    public ItemStack getRenderStack(ItemStack stack) {
        if (!isEnabled() || stack.isEmpty() || !stack.contains(DataComponentTypes.WEAPON)) {
            return stack;
        }
        ItemStack copy = stack.copy();
        copy.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                List.of(),
                List.of(),
                List.of(model.getSelected()),
                List.of()
        ));
        return copy;
    }
}


