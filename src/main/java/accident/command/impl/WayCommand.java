package accident.command.impl;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Formatting;
import accident.IMinecraft;
import accident.command.Command;
import accident.command.CommandManager;
import accident.command.helpers.Paginator;
import accident.command.helpers.TabCompleteHelper;
import accident.util.lang.LanguageManager;
import accident.util.repository.way.Way;
import accident.util.repository.way.WayRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static accident.command.impl.HelpCommand.getLine;

public class WayCommand extends Command implements IMinecraft {

    public WayCommand() {
        super("way", LanguageManager.get("accident.command.way.desc"), "waypoint", "wp");
    }

    @Override
    public void execute(String label, String[] args) {
        CommandManager manager = CommandManager.getInstance();
        WayRepository repository = WayRepository.getInstance();

        if (mc.player == null) {
            logDirect(LanguageManager.get("accident.command.way.ingame"), Formatting.RED);
            return;
        }

        String action = args.length > 0 ? args[0].toLowerCase(Locale.US) : "list";

        switch (action) {
            case "add" -> {
                if (args.length < 2) {
                    logDirect(LanguageManager.get("accident.command.way.usage_add"), Formatting.RED);
                    return;
                }

                String name = args[1];
                BlockPos pos;

                if (args.length >= 5) {
                    try {
                        int x = Integer.parseInt(args[2]);
                        int y = Integer.parseInt(args[3]);
                        int z = Integer.parseInt(args[4]);
                        pos = new BlockPos(x, y, z);
                    } catch (NumberFormatException e) {
                    logDirect(LanguageManager.get("accident.command.way.coordserror"), Formatting.RED);
                        return;
                    }
                } else {
                    pos = mc.player.getBlockPos();
                }

                String server = repository.getCurrentServer();
                if (server.isEmpty()) {
                    logDirect(LanguageManager.get("accident.command.way.servererror"), Formatting.RED);
                    return;
                }

                if (repository.hasWay(name)) {
                    logDirect(String.format(LanguageManager.get("accident.command.way.exists"), name), Formatting.RED);
                    return;
                }

                repository.addWayAndSave(name, pos, server);

                logDirect(String.format(LanguageManager.get("accident.command.way.added_full"),
                        name, pos.getX(), pos.getY(), pos.getZ()), Formatting.GREEN);
            }
            case "remove", "del", "delete" -> {
                if (args.length < 2) {
                    logDirect(LanguageManager.get("accident.command.way.usage_remove"), Formatting.RED);
                    return;
                }

                String name = args[1];

                if (!repository.hasWay(name)) {
                    logDirect(String.format(LanguageManager.get("accident.command.way.notfound"), name), Formatting.RED);
                    return;
                }

                repository.deleteWayAndSave(name);
                logDirect(String.format(LanguageManager.get("accident.command.way.removed"), name), Formatting.GREEN);
            }
            case "clear" -> {
                String server = repository.getCurrentServer();
                int count = 0;

                List<Way> toRemove = repository.getWayList().stream()
                        .filter(way -> way.server().equalsIgnoreCase(server))
                        .toList();

                for (Way way : toRemove) {
                    repository.deleteWay(way.name());
                    count++;
                }

                if (count > 0) {
                    accident.util.config.impl.way.WayConfig.getInstance().save();
                }

                logDirect(String.format(LanguageManager.get("accident.command.way.cleared"), count), Formatting.GREEN);
            }
            case "clearall" -> {
                int count = repository.size();
                repository.clearListAndSave();
                logDirect(String.format(LanguageManager.get("accident.command.way.allcleared"), count), Formatting.GREEN);
            }
            case "list" -> {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }

                String server = repository.getCurrentServer();
                List<Way> serverWays = repository.getWayList().stream()
                        .filter(way -> way.server().equalsIgnoreCase(server))
                        .toList();

                if (serverWays.isEmpty()) {
                    logDirect(String.format(LanguageManager.get("accident.command.way.nopoints")));
                    return;
                }

                Paginator<Way> paginator = new Paginator<>(serverWays);
                paginator.setPage(page);

                paginator.display(
                        () -> {
                            logDirectRaw(Text.literal(getLine()));
                            logDirect("§f§l" + LanguageManager.get("accident.command.way.list_title") + " §7(" + serverWays.size() + ")");
                            logDirectRaw(Text.literal(getLine()));
                        },
                        way -> {
                            String wayName = way.name();
                            BlockPos pos = way.pos();
                            double distance = mc.player.getEntityPos().distanceTo(pos.toCenterPos());

                            MutableText component = Text.literal("  §d● §f" + wayName)
                                    .append(Text.literal(String.format(" §8[§7%d %d %d§8]",
                                            pos.getX(), pos.getY(), pos.getZ())))
                                    .append(Text.literal(String.format(" §8(§7%.1fm§8)", distance)));

                            MutableText hoverText = Text.literal("§7" + LanguageManager.get("accident.command.way.clickremove"));
                            String removeCommand = manager.getPrefix() + "way remove " + wayName;

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
                logDirect("§f§l" + LanguageManager.get("accident.command.way.title"));
                logDirectRaw(Text.literal(getLine()));
                logDirect("§7> way add <name> [x y z] §8- §f" + LanguageManager.get("accident.command.way.desc_add"));
                logDirect("§7> way remove <name> §8- §f" + LanguageManager.get("accident.command.way.desc_remove"));
                logDirect("§7> way list §8- §f" + LanguageManager.get("accident.command.way.desc_list"));
                logDirect("§7> way clear §8- §f" + LanguageManager.get("accident.command.way.desc_clear"));
                logDirect("§7> way clearall §8- §f" + LanguageManager.get("accident.command.way.desc_clearall"));
                logDirectRaw(Text.literal(getLine()));
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        WayRepository repository = WayRepository.getInstance();

        if (args.length == 1) {
            return new TabCompleteHelper()
                    .append("add", "remove", "list", "clear", "clearall")
                    .sortAlphabetically()
                    .filterPrefix(args[0])
                    .stream();
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (action.equals("remove") || action.equals("del") || action.equals("delete")) {
                String server = repository.getCurrentServer();
                return new TabCompleteHelper()
                        .append(repository.getWayNamesForServer(server).toArray(new String[0]))
                        .filterPrefix(args[1])
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return LanguageManager.get("accident.command.way.short");
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                LanguageManager.get("accident.command.way.long1"),
                LanguageManager.get("accident.command.way.long2"),
                LanguageManager.get("accident.command.usage"),
                "> way add <name> [x y z] - " + LanguageManager.get("accident.command.way.short"),
                "> way remove <name> - " + LanguageManager.get("accident.command.way.short"),
                "> way list - " + LanguageManager.get("accident.command.way.short"),
                "> way clear - " + LanguageManager.get("accident.command.way.short"),
                "> way clearall - " + LanguageManager.get("accident.command.way.short")
        );
    }
}