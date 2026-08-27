package accident.screens.keybinds;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;

public class KeybindsScreenManager {

    private KeybindsScreenManager() {}

    public static void open(Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        GameOptions opts = client.options;
        client.setScreen(new CustomKeybindsScreen(parent, opts));
    }
}