package accident.command.impl;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import accident.command.Command;
import accident.command.CommandManager;
import accident.util.config.impl.prefix.PrefixConfig;
import accident.util.lang.LanguageManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static accident.command.impl.HelpCommand.getLine;

public class PrefixCommand extends Command {

    public PrefixCommand() {
        super("prefix", LanguageManager.get("accident.command.prefix.desc"));
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();

        if (args.length == 0) {
            logDirectRaw(Text.literal(getLine()));
            logDirect("§f§l" + LanguageManager.get("accident.command.prefix.title"));
            logDirectRaw(Text.literal(getLine()));
            logDirect("§7" + String.format(LanguageManager.get("accident.command.prefix.current_prefix"), manager.getPrefix()));
            logDirect("§7> prefix set <symbol> §8- §f" + LanguageManager.get("accident.command.prefix.desc_set"));
            logDirectRaw(Text.literal(getLine()));
            return;
        }

        String action = args[0].toLowerCase();

        if (action.equals("set")) {
            if (args.length < 2) {
                logDirect(LanguageManager.get("accident.command.prefix.usage"), Formatting.RED);
                return;
            }

            String newPrefix = args[1];

            if (newPrefix.length() > 3) {
                logDirect(LanguageManager.get("accident.command.prefix.toolong"), Formatting.RED);
                return;
            }

            if (newPrefix.contains(" ")) {
                logDirect(LanguageManager.get("accident.command.prefix.nospaces"), Formatting.RED);
                return;
            }

            PrefixConfig.getInstance().setPrefixAndSave(newPrefix);
            logDirect(String.format(LanguageManager.get("accident.command.prefix.changed_full"), newPrefix), Formatting.GREEN);
            logDirect(String.format(LanguageManager.get("accident.command.prefix.nowuse_full"), newPrefix), Formatting.GREEN);
        } else {
            logDirect(LanguageManager.get("accident.command.prefix.usage"), Formatting.RED);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return Stream.of("set").filter(s -> s.startsWith(args[0].toLowerCase()));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Stream.of(".", "!", "$", "#", "-", "/").filter(s -> s.startsWith(args[1]));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return LanguageManager.get("accident.command.prefix.short");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                LanguageManager.get("accident.command.prefix.long"),
                LanguageManager.get("accident.command.usage"),
                "> prefix - " + LanguageManager.get("accident.command.prefix.current"),
                "> prefix set <symbol> - " + LanguageManager.get("accident.command.prefix.set")
        );
    }
}