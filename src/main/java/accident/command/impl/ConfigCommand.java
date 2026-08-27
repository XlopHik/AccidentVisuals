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
import accident.util.config.ConfigSystem;
import accident.util.config.impl.ConfigPath;
import accident.util.lang.LanguageManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static accident.command.impl.HelpCommand.getLine;

public class ConfigCommand extends Command {

    public ConfigCommand() {
        super("config", LanguageManager.get("accident.command.config.desc"), "cfg");
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();

        String arg = args.length > 0 ? args[0].toLowerCase(Locale.US) : "list";

        switch (arg) {
            case "load" -> {
                if (args.length < 2) {
                    logDirect(LanguageManager.get("accident.command.config.usage_load"), Formatting.RED);
                    return;
                }
                String name = args[1];
                Path configDir = ConfigPath.getConfigDirectory();

                Path sourceConfig = configDir.resolve(name + ".json");
                Path targetConfig = ConfigPath.getConfigFile();

                if (Files.exists(sourceConfig)) {
                    try {
                        Files.copy(
                                sourceConfig,
                                targetConfig,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                        ConfigSystem.getInstance().load();
                    logDirect(String.format(LanguageManager.get("accident.command.config.loaded"), name));
                    } catch (Exception e) {
                        logDirect(String.format(LanguageManager.get("accident.command.config.loaderror"), e.getMessage()), Formatting.RED);
                        e.printStackTrace();
                    }
                } else {
                    logDirect(String.format(LanguageManager.get("accident.command.config.notfound"), name), Formatting.RED);
                }
            }
            case "save" -> {
                if (args.length < 2) {
                    ConfigSystem.getInstance().save();
                    logDirect(LanguageManager.get("accident.command.config.saved").formatted("config"));
                    return;
                }
                String name = args[1];
                try {
                    Path configDir = ConfigPath.getConfigDirectory();
                    Path newConfig = configDir.resolve(name + ".json");
                    ConfigSystem.getInstance().save();
                    Path currentConfig = ConfigPath.getConfigFile();
                    Files.copy(currentConfig, newConfig);
                    logDirect(String.format(LanguageManager.get("accident.command.config.saved"), name));
                } catch (Exception e) {
                    logDirect(String.format(LanguageManager.get("accident.command.config.saveerror"), e.getMessage()), Formatting.RED);
                }
            }
            case "list" -> {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }

                List<String> configs = getConfigs();

                if (configs.isEmpty()) {
                    logDirect(LanguageManager.get("accident.command.config.nofound"), Formatting.RED);
                    return;
                }

                Paginator<String> paginator = new Paginator<>(configs);
                paginator.setPage(page);

                paginator.display(
                        () -> {
                            logDirectRaw(Text.literal(getLine()));
                            logDirect("§f§l" + LanguageManager.get("accident.command.config.title"));
                            logDirectRaw(Text.literal(getLine()));
                        },
                        config -> {
                            MutableText namesComponent = Text.literal("  §b● §f" + config);

                            MutableText hoverText = Text.literal("§7" + String.format(LanguageManager.get("accident.command.config.clickload"), config));
                            String loadCommand = manager.getPrefix() + "config load " + config;

                            namesComponent.setStyle(namesComponent.getStyle()
                                    .withHoverEvent(new HoverEvent.ShowText(hoverText))
                                    .withClickEvent(new ClickEvent.RunCommand(loadCommand)));

                            return namesComponent;
                        },
                        manager.getPrefix() + label + " list"
                );
            }
            case "dir" -> {
                try {
                    Path configDir = ConfigPath.getConfigDirectory();
                    String os = System.getProperty("os.name").toLowerCase();

                    ProcessBuilder pb;
                    if (os.contains("win")) {
                        pb = new ProcessBuilder("explorer", configDir.toAbsolutePath().toString());
                    } else if (os.contains("mac")) {
                        pb = new ProcessBuilder("open", configDir.toAbsolutePath().toString());
                    } else {
                        pb = new ProcessBuilder("xdg-open", configDir.toAbsolutePath().toString());
                    }
                    pb.start();
                    logDirect(LanguageManager.get("accident.command.config.folderopened"));
                } catch (IOException e) {
                    logDirect(String.format(LanguageManager.get("accident.command.config.foldernotfound"), e.getMessage()), Formatting.RED);
                }
            }
            default -> {
                logDirectRaw(Text.literal(getLine()));
                logDirect("§f§l" + LanguageManager.get("accident.command.config.usage"));
                logDirectRaw(Text.literal(getLine()));
                logDirect("§7> config load <name> §8- §f" + LanguageManager.get("accident.command.config.load"));
                logDirect("§7> config save <name> §8- §f" + LanguageManager.get("accident.command.config.save"));
                logDirect("§7> config list §8- §f" + LanguageManager.get("accident.command.config.list"));
                logDirect("§7> config dir §8- §f" + LanguageManager.get("accident.command.config.dir"));
                logDirectRaw(Text.literal(getLine()));
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return new TabCompleteHelper()
                    .append("load", "save", "list", "dir")
                    .sortAlphabetically()
                    .filterPrefix(args[0])
                    .stream();
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (action.equals("load") || action.equals("save")) {
                return new TabCompleteHelper()
                        .append(getConfigs().toArray(new String[0]))
                        .filterPrefix(args[1])
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return LanguageManager.get("accident.command.config.short");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                LanguageManager.get("accident.command.config.long"),
                LanguageManager.get("accident.command.usage"),
                "> config load <name> - " + LanguageManager.get("accident.command.config.load"),
                "> config save <name> - " + LanguageManager.get("accident.command.config.save"),
                "> config list - " + LanguageManager.get("accident.command.config.list"),
                "> config dir - " + LanguageManager.get("accident.command.config.dir")
        );
    }

    public List<String> getConfigs() {
        List<String> configs = new ArrayList<>();
        try {
            Path configDir = ConfigPath.getConfigDirectory();
            if (Files.exists(configDir)) {
                Files.list(configDir)
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> {
                            String name = path.getFileName().toString();
                            configs.add(name.substring(0, name.length() - 5));
                        });
            }
        } catch (IOException ignored) {}
        return configs;
    }
}