package accident.command.impl;

import accident.util.string.chat.ChatMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import accident.Initialization;
import accident.command.Command;
import accident.command.CommandManager;
import accident.command.helpers.Paginator;
import accident.command.helpers.TabCompleteHelper;
import accident.modules.module.ModuleRepository;
import accident.modules.module.ModuleStructure;
import accident.util.config.ConfigSystem;
import accident.util.config.impl.bind.BindConfig;
import accident.util.lang.LanguageManager;
import accident.util.string.KeyHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static accident.command.impl.HelpCommand.getLine;

public class BindCommand extends Command {

    public BindCommand() {
        super("bind", LanguageManager.get("accident.command.bind.desc"), "b");
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();
        ModuleRepository repository = getModuleRepository();

        if (repository == null) {
            logDirect(LanguageManager.get("accident.command.bind.repofound"), Formatting.RED);
            return;
        }

        String action = args.length > 0 ? args[0].toLowerCase(Locale.US) : "list";

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    logDirect(LanguageManager.get("accident.command.bind.usage_add"), Formatting.RED);
                    return;
                }

                String moduleName = args[1];
                String keyName = args[2];

                ModuleStructure module = findModule(repository, moduleName);
                if (module == null) {
                    logDirect(String.format(LanguageManager.get("accident.command.bind.modulenotfound"), moduleName), Formatting.RED);
                    return;
                }

                int key = KeyHelper.getKeyCode(keyName);
                if (key == -1) {
                    logDirect(String.format(LanguageManager.get("accident.command.bind.unknownkey"), keyName), Formatting.RED);
                    return;
                }

                if (module.isLocked()) {
                    logDirect(module.lockedMessage(), Formatting.RED);
                    return;
                }

                module.setKey(key);
                ConfigSystem.getInstance().save();

                    logDirect(String.format(LanguageManager.get("accident.command.bind.binded"), module.getName(), KeyHelper.getKeyName(key).toLowerCase()), Formatting.GREEN);
            }
            case "remove", "del", "delete" -> {
                if (args.length < 2) {
                    logDirect(LanguageManager.get("accident.command.bind.usage_remove"), Formatting.RED);
                    return;
                }

                String moduleName = args[1];
                ModuleStructure module = findModule(repository, moduleName);

                if (module == null) {
                    logDirect(String.format(LanguageManager.get("accident.command.bind.modulenotfound"), moduleName), Formatting.RED);
                    return;
                }

                module.setKey(GLFW.GLFW_KEY_UNKNOWN);
                ConfigSystem.getInstance().save();

                logDirect(String.format(LanguageManager.get("accident.command.bind.removed"), module.getName()), Formatting.GREEN);
            }
            case "clear" -> {
                int count = 0;
                for (ModuleStructure module : repository.modules()) {
                    if (module.getKey() != GLFW.GLFW_KEY_UNKNOWN) {
                        module.setKey(GLFW.GLFW_KEY_UNKNOWN);
                        count++;
                    }
                }
                ConfigSystem.getInstance().save();
                logDirect(String.format(LanguageManager.get("accident.command.bind.allremoved"), count), Formatting.GREEN);
            }
            case "set" -> {
                if (args.length < 3) {
                    logDirect(LanguageManager.get("accident.command.bind.usage_set"), Formatting.RED);
                    logDirect(LanguageManager.get("accident.command.bind.available_targets"), Formatting.RED);
                    return;
                }

                String target = args[1].toLowerCase(Locale.US);
                String keyName = args[2];

                int key = KeyHelper.getKeyCode(keyName);
                if (key == -1) {
                    logDirect(String.format(LanguageManager.get("accident.command.bind.unknownkey"), keyName), Formatting.RED);
                    return;
                }

                if (target.equals("Bind")) {
                    BindConfig.getInstance().setKeyAndSave(key);
                    logDirect(String.format(LanguageManager.get("accident.command.bind.bindkeychanged"), KeyHelper.getKeyName(key).toLowerCase()), Formatting.GREEN);
                } else {
                    logDirect(String.format(LanguageManager.get("accident.command.bind.unknowntarget"), target), Formatting.RED);
                }
            }
            case "list" -> {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }

                List<ModuleStructure> boundModules = repository.modules().stream()
                        .filter(m -> m.getKey() != GLFW.GLFW_KEY_UNKNOWN && m.getKey() != -1)
                        .collect(Collectors.toList());

                if (boundModules.isEmpty()) {
                    logDirect(LanguageManager.get("accident.command.bind.nobinds"), Formatting.RED);
                    return;
                }

                Paginator<ModuleStructure> paginator = new Paginator<>(boundModules);
                paginator.setPage(page);

                paginator.display(
                        () -> {
                            logDirectRaw(Text.literal(getLine()));
                            logDirect("§f§l" + LanguageManager.get("accident.command.bind.list_title") + " §7(" + boundModules.size() + ")");
                            logDirectRaw(Text.literal(getLine()));
                        },
                        module -> {
                            String name = module.getName();
                            String keyName = KeyHelper.getKeyName(module.getKey()).toLowerCase();

                            MutableText component = Text.literal("  §b● §f" + name)
                                    .append(Text.literal(" §8[§7" + keyName + "§8]"));

                            MutableText hoverText = Text.literal("§7" + LanguageManager.get("accident.command.bind.clickremove") + " §f" + name);
                            String removeCommand = manager.getPrefix() + "bind remove " + name;

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
                logDirect("§f§l" + LanguageManager.get("accident.command.bind.title"));
                logDirectRaw(Text.literal(getLine()));
                logDirect("§7> bind add <module> <key> §8- §f" + LanguageManager.get("accident.command.bind.desc_add"));
                logDirect("§7> bind remove <module> §8- §f" + LanguageManager.get("accident.command.bind.desc_remove"));
                logDirect("§7> bind list §8- §f" + LanguageManager.get("accident.command.bind.desc_list"));
                logDirect("§7> bind clear §8- §f" + LanguageManager.get("accident.command.bind.desc_clear"));
                logDirect("§7> bind set Bind <key> §8- §f" + LanguageManager.get("accident.command.bind.desc_set"));
                logDirectRaw(Text.literal(getLine()));
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        ModuleRepository repository = getModuleRepository();

        if (args.length == 1) {
            return new TabCompleteHelper()
                    .append("add", "remove", "list", "clear", "set")
                    .sortAlphabetically()
                    .filterPrefix(args[0])
                    .stream();
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (action.equals("add")) {
                return new TabCompleteHelper()
                        .append(getModuleNames(repository))
                        .filterPrefix(args[1])
                        .stream();
            }
            if (action.equals("remove") || action.equals("del") || action.equals("delete")) {
                return new TabCompleteHelper()
                        .append(getBoundModuleNames(repository))
                        .filterPrefix(args[1])
                        .stream();
            }
            if (action.equals("set")) {
                return new TabCompleteHelper()
                        .append("Bind")
                        .filterPrefix(args[1])
                        .stream();
            }
        }
        if (args.length == 3) {
            String action = args[0].toLowerCase();
            if (action.equals("add") || action.equals("set")) {
                return new TabCompleteHelper()
                        .append(KeyHelper.getAllKeyNames())
                        .filterPrefix(args[2])
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return LanguageManager.get("accident.command.bind.short");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                LanguageManager.get("accident.command.bind.long"),
                LanguageManager.get("accident.command.usage"),
                "> bind add <module> <key> - " + LanguageManager.get("accident.command.bind.short"),
                "> bind remove <module> - " + LanguageManager.get("accident.command.bind.short"),
                "> bind list - " + LanguageManager.get("accident.command.bind.short"),
                "> bind clear - " + LanguageManager.get("accident.command.bind.short"),
                "> bind set Bind <key> - " + LanguageManager.get("accident.command.bind.short")
        );
    }

    private ModuleRepository getModuleRepository() {
        Initialization instance = Initialization.getInstance();
        if (instance != null && instance.getManager() != null) {
            return instance.getManager().getModuleRepository();
        }
        return null;
    }

    private ModuleStructure findModule(ModuleRepository repository, String name) {
        return repository.modules().stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private String[] getModuleNames(ModuleRepository repository) {
        if (repository == null) return new String[0];
        return repository.modules().stream()
                .map(ModuleStructure::getName)
                .toArray(String[]::new);
    }

    private String[] getBoundModuleNames(ModuleRepository repository) {
        if (repository == null) return new String[0];
        return repository.modules().stream()
                .filter(m -> m.getKey() != GLFW.GLFW_KEY_UNKNOWN && m.getKey() != -1)
                .map(ModuleStructure::getName)
                .toArray(String[]::new);
    }
}