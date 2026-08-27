package accident.command.impl;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import accident.command.Command;
import accident.command.CommandManager;
import accident.command.helpers.Paginator;
import accident.command.helpers.TabCompleteHelper;
import accident.util.lang.LanguageManager;
import accident.util.repository.macro.Macro;
import accident.util.repository.macro.MacroRepository;
import accident.util.string.KeyHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static accident.command.impl.HelpCommand.getLine;

public class MacroCommand extends Command {

    public MacroCommand() {
        super("macro", LanguageManager.get("accident.command.macro.desc"), "macros");
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();
        MacroRepository macroRepository = MacroRepository.getInstance();

        String action = args.length > 0 ? args[0].toLowerCase(Locale.US) : "list";

        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    logDirect(LanguageManager.get("accident.command.macro.usage_add"), Formatting.RED);
                    return;
                }

                String keyName = args[1];
                int key = KeyHelper.getKeyCode(keyName);

                if (key == -1) {
                    logDirect(String.format(LanguageManager.get("accident.command.macro.unknownkey"), keyName), Formatting.RED);
                    return;
                }

                String name = args[2];

                StringBuilder messageBuilder = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (i > 3) messageBuilder.append(" ");
                    messageBuilder.append(args[i]);
                }
                String message = messageBuilder.toString();

                if (macroRepository.hasMacro(name)) {
                    logDirect(String.format(LanguageManager.get("accident.command.macro.exists"), name), Formatting.RED);
                    return;
                }

                macroRepository.addMacroAndSave(name, message, key);

                logDirect(String.format(LanguageManager.get("accident.command.macro.added_full"),
                        name, KeyHelper.getKeyName(key).toLowerCase(), message), Formatting.GREEN);
            }
            case "remove", "del", "delete" -> {
                if (args.length < 2) {
                    logDirect(LanguageManager.get("accident.command.macro.usage_remove"), Formatting.RED);
                    return;
                }

                String name = args[1];

                if (!macroRepository.hasMacro(name)) {
                    logDirect(String.format(LanguageManager.get("accident.command.macro.notfound"), name), Formatting.RED);
                    return;
                }

                macroRepository.deleteMacroAndSave(name);
                logDirect(String.format(LanguageManager.get("accident.command.macro.removed"), name), Formatting.GREEN);
            }
            case "clear" -> {
                int count = macroRepository.size();
                macroRepository.clearListAndSave();
                logDirect(String.format(LanguageManager.get("accident.command.macro.allremoved"), count), Formatting.GREEN);
            }
            case "list" -> {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }

                List<Macro> macros = macroRepository.getMacroList();

                if (macros.isEmpty()) {
                    logDirect(LanguageManager.get("accident.command.macro.empty"), Formatting.RED);
                    return;
                }

                Paginator<Macro> paginator = new Paginator<>(macros);
                paginator.setPage(page);

                paginator.display(
                        () -> {
                            logDirectRaw(Text.literal(getLine()));
                            logDirect("§f§l" + LanguageManager.get("accident.command.macro.list_title") + " §7(" + macros.size() + ")");
                            logDirectRaw(Text.literal(getLine()));
                        },
                        macro -> {
                            String macroName = macro.name();
                            String keyName = KeyHelper.getKeyName(macro.key()).toLowerCase();
                            String message = macro.message();

                            MutableText component = Text.literal("  §e● §f" + macroName)
                                    .append(Text.literal(" §8[§7" + keyName + "§8]"))
                                    .append(Text.literal(" §8-> §7" + message));

                            MutableText hoverText = Text.literal("§7" + String.format(LanguageManager.get("accident.command.macro.clickremove"), macroName));
                            String removeCommand = manager.getPrefix() + "macro remove " + macroName;

                            component.setStyle(component.getStyle()
                                    .withHoverEvent(new HoverEvent.ShowText(hoverText))
                                    .withClickEvent(new ClickEvent.RunCommand(removeCommand)));

                            return component;
                        },
                        manager.getPrefix() + label + " list"
                );
            }
            default -> {
                logDirectRaw(Text.literal(getLine()));
                logDirect("§f§l" + LanguageManager.get("accident.command.macro.title"));
                logDirectRaw(Text.literal(getLine()));
                logDirect("§7> macro add <key> <name> <message> §8- §f" + LanguageManager.get("accident.command.macro.desc_add"));
                logDirect("§7> macro remove <name> §8- §f" + LanguageManager.get("accident.command.macro.desc_remove"));
                logDirect("§7> macro list §8- §f" + LanguageManager.get("accident.command.macro.desc_list"));
                logDirect("§7> macro clear §8- §f" + LanguageManager.get("accident.command.macro.desc_clear"));
                logDirectRaw(Text.literal(getLine()));
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return new TabCompleteHelper()
                    .append("add", "remove", "list", "clear")
                    .sortAlphabetically()
                    .filterPrefix(args[0])
                    .stream();
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (action.equals("add")) {
                return new TabCompleteHelper()
                        .append(KeyHelper.getAllKeyNames())
                        .filterPrefix(args[1])
                        .stream();
            }
            if (action.equals("remove") || action.equals("del") || action.equals("delete")) {
                return new TabCompleteHelper()
                        .append(MacroRepository.getInstance().getMacroNames().toArray(new String[0]))
                        .filterPrefix(args[1])
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return LanguageManager.get("accident.command.macro.short");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                LanguageManager.get("accident.command.macro.long"),
                LanguageManager.get("accident.command.usage"),
                "> macro add <key> <name> <message> - " + LanguageManager.get("accident.command.macro.short"),
                "> macro remove <name> - " + LanguageManager.get("accident.command.macro.short"),
                "> macro list - " + LanguageManager.get("accident.command.macro.short"),
                "> macro clear - " + LanguageManager.get("accident.command.macro.short")
        );
    }
}