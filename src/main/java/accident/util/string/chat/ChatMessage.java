package accident.util.string.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.lang.LanguageManager;
import accident.util.render.ThemeGradient;
import accident.util.string.chat.helper.TextHelper;

public class ChatMessage {
    public static MutableText brandmessage() {
        return (MutableText) TextHelper.applyPredefinedGradient("Accident Client", "swamp_green", true);
    }

    public static void brandmessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();

            Text prefix = ThemeGradient.applyThemeGradient("Accident Client -> ", theme, true);

            Text formattedMessage = prefix.copy().append(Text.literal(message).withColor(0xFFFFFF));

            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void totemPopMessage(String playerName, boolean enchanted) {
        if (MinecraftClient.getInstance().player != null) {
            String enchantedStatus = enchanted ? "Yes" : "No";
            String fullText = String.format(LanguageManager.get("accident.chat.totem"), playerName, enchantedStatus);

            Text formattedMessage = TextHelper.applyPredefinedGradient(fullText, "purple_red_fade", false);

        }
    }


    public static void ircmessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = TextHelper.applyPredefinedGradient("[IRC] ", "black_light_purple", true);
            Text formattedMessage = prefix.copy().append(Text.literal(message));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void ircmessageWithGreen(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = TextHelper.applyPredefinedGradient("[IRC] ", "black_light_purple", true);
            Text formattedMessage = prefix.copy().append(Text.literal(message).setStyle(Style.EMPTY.withColor(Formatting.GREEN)));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void ircmessageWithRed(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = TextHelper.applyPredefinedGradient("[IRC] ", "black_light_purple", true);
            Text formattedMessage = prefix.copy().append(Text.literal(message).setStyle(Style.EMPTY.withColor(Formatting.RED)));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }
}