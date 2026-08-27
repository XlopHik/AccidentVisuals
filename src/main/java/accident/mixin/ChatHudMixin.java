package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.render.BetterChat;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow private List<ChatHudLine> messages;
    @Shadow protected abstract void refresh();

    private static final Pattern COUNTER_PATTERN = Pattern.compile(" §7\\(x\\d+\\)$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^§d\\[\\d{2}:\\d{2}:\\d{2}\\] §r");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s.,!?:;'\"()\\-\\[\\]/\\\\]");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        BetterChat module = BetterChat.getInstance();
        if (module == null || !module.isState()) return;

        boolean antiSpam = module.getAntiSpam().isValue();
        boolean showTime = module.getShowTime().isValue();

        if (antiSpam && !messages.isEmpty()) {
            String[] current = extractSenderAndMessage(message.getString());
            String currentSender = current[0];
            String currentContent = current[1];

            if (!currentContent.isEmpty()) {
                ChatHudLine lastLine = messages.get(0);
                String[] last = extractSenderAndMessage(lastLine.content().getString());

                if (last[0].equals(currentSender) && last[1].equals(currentContent)) {
                    ci.cancel();

                    int newCount = extractCounter(lastLine.content().getString()) + 1;
                    messages.remove(0);

                    MutableText newText;
                    if (showTime) {
                        String timePrefix = "§d[" + LocalTime.now().format(TIME_FORMATTER) + "] §r";
                        newText = Text.literal(timePrefix).append(message).append(Text.literal(" §7(x" + newCount + ")"));
                    } else {
                        newText = Text.empty().append(message).append(Text.literal(" §7(x" + newCount + ")"));
                    }

                    int tick = MinecraftClient.getInstance().inGameHud.getTicks();
                    messages.add(0, new ChatHudLine(tick, newText, null, indicator));
                    refresh();
                    return;
                }
            }
        }

        if (showTime) {
            ci.cancel();
            String timePrefix = "§d[" + LocalTime.now().format(TIME_FORMATTER) + "] §r";
            MutableText newText = Text.literal(timePrefix).append(message);
            int tick = MinecraftClient.getInstance().inGameHud.getTicks();
            messages.add(0, new ChatHudLine(tick, newText, null, indicator));
            refresh();
        }
    }

    private String[] extractSenderAndMessage(String text) {
        String result = removeTimeAndCounter(text);
        result = result.replaceAll("§.", "");

        int separatorIndex = result.lastIndexOf(": ");
        if (separatorIndex != -1) {
            String sender = result.substring(0, separatorIndex);
            String messageContent = result.substring(separatorIndex + 2).trim();

            String normalizedSender = SPECIAL_CHARS_PATTERN.matcher(sender)
                    .replaceAll("").replaceAll("\\s+", " ").trim();

            return new String[]{ normalizedSender, messageContent };
        }

        // Системное сообщение без разделителя
        String normalized = SPECIAL_CHARS_PATTERN.matcher(result)
                .replaceAll("").replaceAll("\\s+", " ").trim();
        return new String[]{ "", normalized };
    }

    private String removeTimeAndCounter(String text) {
        String result = TIME_PATTERN.matcher(text).replaceAll("");
        result = COUNTER_PATTERN.matcher(result).replaceAll("");
        return result;
    }

    private int extractCounter(String text) {
        var matcher = Pattern.compile("\\(x(\\d+)\\)$").matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }
}