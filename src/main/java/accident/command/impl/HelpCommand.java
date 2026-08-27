package accident.command.impl;

import net.minecraft.client.MinecraftClient;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HelpCommand extends Command {

    public HelpCommand() {
        super("help", LanguageManager.get("accident.command.help.desc"));
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();

        if (args.length == 0 || isInteger(args[0])) {
            int page = 1;
            if (args.length > 0 && isInteger(args[0])) {
                page = Integer.parseInt(args[0]);
            }

            List<Command> commands = manager.getCommands().stream()
                    .filter(cmd -> !cmd.hiddenFromHelp())
                    .collect(Collectors.toList());

            Paginator<Command> paginator = new Paginator<>(commands);
            paginator.setPage(page);

            paginator.display(
                    () -> {
                        logDirectRaw(Text.literal(getLine()));
                        logDirect("§f§l" + LanguageManager.get("accident.command.help.title"));
                        logDirectRaw(Text.literal(getLine()));
                    },
                    command -> {
                        String name = command.getName();
                        String fullName = manager.getPrefix() + name;

                        MutableText shortDescComponent = Text.literal(" §8- §7" + command.getShortDesc());

                        MutableText hoverComponent = Text.literal("");
                        hoverComponent.setStyle(hoverComponent.getStyle().withColor(Formatting.GRAY));
                        hoverComponent.append(Text.literal(fullName).formatted(Formatting.WHITE));
                        hoverComponent.append("\n§7" + command.getShortDesc());
                        hoverComponent.append("\n\n§8" + LanguageManager.get("accident.command.help.clickhelp"));

                        String clickCommand = manager.getPrefix() + String.format("%s %s", label, name);

                        MutableText component = Text.literal("§f" + fullName);
                        component.append(shortDescComponent);
                        component.setStyle(component.getStyle()
                                .withHoverEvent(new HoverEvent.ShowText(hoverComponent))
                                .withClickEvent(new ClickEvent.RunCommand(clickCommand)));

                        return component;
                    },
                    manager.getPrefix() + label
            );
        } else {
            String commandName = args[0].toLowerCase();
            Command command = manager.getCommand(commandName);

            if (command == null) {
                logDirect(String.format(LanguageManager.get("accident.command.help.notfound"), commandName), Formatting.RED);
                return;
            }

            logDirectRaw(Text.literal(getLine()));
            logDirect("§f§l" + command.getName().toUpperCase());
            logDirectRaw(Text.literal(getLine()));

            List<String> desc = command.getLongDesc();
            boolean firstLine = true;

            for (String line : desc) {
                if (line.isEmpty()) {
                    continue;
                }
                logDirect("§7" + line);
                if (firstLine) {
                    logDirectRaw(Text.literal(getLine()));
                    firstLine = false;
                }
            }

            logDirectRaw(Text.literal(getLine()));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return new TabCompleteHelper()
                    .filterPrefix(args[0])
                    .addCommands(CommandManager.getInstance())
                    .stream();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return LanguageManager.get("accident.command.help.short");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                LanguageManager.get("accident.command.help.long"),
                LanguageManager.get("accident.command.usage"),
                "> " + LanguageManager.get("accident.command.help.usage1"),
                "> " + LanguageManager.get("accident.command.help.usage2")
        );
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String getLine() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) {
            return "§8§m                    ";
        }

        int chatWidth = mc.options.getChatWidth().getValue().intValue();
        int scaledWidth = (int) (chatWidth * 280 + 40);

        int dashWidth = mc.textRenderer.getWidth("-");
        if (dashWidth <= 0) {
            dashWidth = 4;
        }

        int dashCount = (scaledWidth / dashWidth) - 2;
        dashCount = Math.max(10, Math.min(dashCount, 80));

        StringBuilder sb = new StringBuilder("§8§m");
        for (int i = 0; i < dashCount; i++) {
            sb.append("-");
        }

        return sb.toString();
    }
}