package accident.mixin;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import accident.util.server.FolderEntry;
import accident.util.server.ServerArrangement;
import accident.util.server.ServerFolderScreen;
import accident.util.server.ServerMeta;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Mixin(MultiplayerServerListWidget.class)
public abstract class MultiplayerServerListWidgetMixin
        extends AlwaysSelectedEntryListWidget<MultiplayerServerListWidget.Entry>
        implements ServerArrangement.Refresher {

    /** Never runs - mixin drops it. Present only so javac accepts the superclass. */
    private MultiplayerServerListWidgetMixin(MinecraftClient client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    @Mutable
    @Final
    @Shadow
    static ThreadPoolExecutor SERVER_PINGER_THREAD_POOL;

    @Unique
    private static final int PINGER_THREAD_COUNT_OVERHEAD = 5;

    @Final
    @Shadow
    private List<MultiplayerServerListWidget.ServerEntry> servers;

    @Final
    @Shadow
    private MultiplayerScreen screen;

    @Unique
    private static boolean threadpoolInitialized = false;

    @Shadow
    protected abstract void updateEntries();

    @Unique
    private boolean accident$rebuilding = false;

    // состояние drag - нажатие превращается в drag только после реального сдвига курсора,
    // чтобы обычный клик всё ещё выбирал, а даблклик всё ещё подключал
    @Unique
    private MultiplayerServerListWidget.ServerEntry accident$pressed;
    @Unique
    private double accident$pressX;
    @Unique
    private double accident$pressY;
    @Unique
    private boolean accident$dragging;

    @Unique
    private static final int DROP_NONE = -1;
    @Unique
    private static final int DROP_BEFORE = 0;
    @Unique
    private static final int DROP_AFTER = 1;
    @Unique
    private static final int DROP_MERGE = 2;
    @Unique
    private static final int DROP_INTO_FOLDER = 3;
    @Unique
    private static final int DROP_END = 4;

    @Unique
    private int accident$dropMode = DROP_NONE;
    @Unique
    private MultiplayerServerListWidget.Entry accident$dropRow;

    @Override
    public void accident$rebuildServerList() {
        updateEntries();
    }

    // перестраивает список: сначала закреплённые, потом папки со сворачиваемыми заголовками, остальное (LAN и тд) - в конце
    @Inject(method = "updateEntries", at = @At("TAIL"))
    private void accident$applyPinsAndFolders(CallbackInfo ci) {
        if (accident$rebuilding) return;
        ServerArrangement.setActive(this);

        Map<ServerInfo, MultiplayerServerListWidget.ServerEntry> byServer = new IdentityHashMap<>();
        List<ServerInfo> infos = new ArrayList<>();
        for (MultiplayerServerListWidget.ServerEntry entry : servers) {
            byServer.put(entry.getServer(), entry);
            infos.add(entry.getServer());
        }

        boolean grouped = false;
        for (ServerInfo info : infos) {
            if (ServerMeta.isPinned(info.address) || !ServerMeta.folderOf(info.address).isEmpty()) {
                grouped = true;
                break;
            }
        }

        // Nothing configured yet - leave the list exactly as vanilla built it.
        if (!grouped) return;

        List<MultiplayerServerListWidget.Entry> extras = new ArrayList<>(children());
        extras.removeIf(entry -> entry instanceof MultiplayerServerListWidget.ServerEntry);

        List<ServerInfo> order = ServerArrangement.flatten(infos);
        MultiplayerServerListWidget.Entry selected = getSelectedOrNull();

        accident$rebuilding = true;
        try {
            clearEntries();

            String openFolder = null;
            boolean collapsed = false;

            for (ServerInfo info : order) {
                String folder = ServerArrangement.groupOf(info);

                if (!folder.equals(openFolder)) {
                    openFolder = folder;
                    collapsed = false;

                    if (!folder.isEmpty()) {
                        int count = 0;
                        for (ServerInfo other : order) {
                            if (folder.equals(ServerArrangement.groupOf(other))) count++;
                        }
                        addEntry(new FolderEntry(folder, count, this::updateEntries));
                        collapsed = ServerMeta.isCollapsed(folder);
                    }
                }

                if (collapsed) continue;

                MultiplayerServerListWidget.ServerEntry entry = byServer.get(info);
                if (entry != null) addEntry(entry);
            }

            for (MultiplayerServerListWidget.Entry entry : extras) addEntry(entry);
        } finally {
            accident$rebuilding = false;
        }

        if (selected != null && children().contains(selected)) setSelected(selected);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        accident$pressed = null;
        accident$dragging = false;

        MultiplayerServerListWidget.Entry entry = getEntryAtPosition(click.x(), click.y());

        if (click.button() == 1) {
            if (entry instanceof MultiplayerServerListWidget.ServerEntry server) {
                ServerInfo info = server.getServer();
                if ((click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.setScreen(ServerFolderScreen.forServer(mc.currentScreen, info.name, info.address));
                } else {
                    ServerMeta.togglePinned(info.address);
                    updateEntries();
                }
                return true;
            }

            if (entry instanceof FolderEntry folder) {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.setScreen(ServerFolderScreen.forFolder(mc.currentScreen, folder.folder()));
                return true;
            }
        }

        if (click.button() == 0 && entry instanceof MultiplayerServerListWidget.ServerEntry server) {
            // крайние 32px с каждой стороны - ванильные стрелки/кнопка join, drag начинается только с области имени
            if (click.x() >= getRowLeft() + 32 && click.x() <= getRowRight() - 32) {
                accident$pressed = server;
                accident$pressX = click.x();
                accident$pressY = click.y();
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (accident$pressed != null) {
            if (!accident$dragging
                    && Math.abs(click.x() - accident$pressX) + Math.abs(click.y() - accident$pressY) > 4.0) {
                accident$dragging = true;
                setDragging(false);
            }
            if (accident$dragging) return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (accident$dragging && accident$pressed != null) {
            accident$applyDrop(click.x(), click.y());
            accident$pressed = null;
            accident$dragging = false;
            return true;
        }

        accident$pressed = null;
        accident$dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        if (!accident$dragging || accident$pressed == null) return;

        accident$findDrop(mouseX, mouseY);

        int left = getRowLeft();
        int right = getRowRight();

        switch (accident$dropMode) {
            case DROP_MERGE, DROP_INTO_FOLDER -> {
                if (accident$dropRow == null) break;
                int y = accident$dropRow.getY();
                int height = accident$dropRow.getHeight();
                context.fill(left, y, right, y + height, 0x4066AAFF);
                accident$outline(context, left, y, right, y + height, 0xFF8FC2FF);
            }
            case DROP_BEFORE -> accident$drawLine(context, left, right, accident$dropRow.getY());
            case DROP_AFTER -> accident$drawLine(context, left, right,
                    accident$dropRow.getY() + accident$dropRow.getHeight());
            case DROP_END -> {
                List<MultiplayerServerListWidget.Entry> rows = children();
                int y = rows.isEmpty() ? getY()
                        : rows.get(rows.size() - 1).getY() + rows.get(rows.size() - 1).getHeight();
                accident$drawLine(context, left, right, y);
            }
            default -> {
            }
        }

        // A label under the cursor, so it is obvious which server is in hand.
        MinecraftClient mc = MinecraftClient.getInstance();
        String name = accident$pressed.getServer().name;
        int width = mc.textRenderer.getWidth(name);
        context.fill(mouseX + 6, mouseY - 7, mouseX + width + 14, mouseY + 6, 0xE0101018);
        accident$outline(context, mouseX + 6, mouseY - 7, mouseX + width + 14, mouseY + 6, 0x60FFFFFF);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(name), mouseX + 10, mouseY - 3, 0xFFFFFFFF);
    }

    @Unique
    private void accident$outline(DrawContext context, int left, int top, int right, int bottom, int color) {
        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom - 1, right, bottom, color);
        context.fill(left, top, left + 1, bottom, color);
        context.fill(right - 1, top, right, bottom, color);
    }

    @Unique
    private void accident$drawLine(DrawContext context, int left, int right, int y) {
        context.fill(left, y - 1, right, y + 1, 0xFF8FC2FF);
    }

    /** Works out what a release at this position would mean. */
    @Unique
    private void accident$findDrop(double x, double y) {
        accident$dropMode = DROP_NONE;
        accident$dropRow = null;

        if (!isMouseOver(x, y)) return;

        MultiplayerServerListWidget.Entry entry = getEntryAtPosition(x, y);
        if (entry == null) {
            accident$dropMode = DROP_END;
            return;
        }

        if (entry instanceof FolderEntry) {
            accident$dropRow = entry;
            accident$dropMode = DROP_INTO_FOLDER;
            return;
        }

        if (!(entry instanceof MultiplayerServerListWidget.ServerEntry)) return;
        if (entry == accident$pressed) return;

        accident$dropRow = entry;
        double rel = (y - entry.getY()) / Math.max(1.0, entry.getHeight());
        accident$dropMode = rel < 0.28 ? DROP_BEFORE : rel > 0.72 ? DROP_AFTER : DROP_MERGE;
    }

    @Unique
    private void accident$applyDrop(double x, double y) {
        accident$findDrop(x, y);
        if (accident$dropMode == DROP_NONE) return;

        ServerList list = screen.getServerList();
        ServerInfo dragged = accident$pressed.getServer();

        switch (accident$dropMode) {
            case DROP_END -> ServerArrangement.place(list, dragged, "", null, false);

            case DROP_INTO_FOLDER -> {
                String folder = ((FolderEntry) accident$dropRow).folder();
                ServerInfo first = null;
                for (ServerInfo info : ServerArrangement.flatten(ServerArrangement.snapshot(list))) {
                    if (folder.equals(ServerArrangement.groupOf(info)) && info != dragged) {
                        first = info;
                        break;
                    }
                }
                ServerArrangement.place(list, dragged, folder, first, false);
            }

            case DROP_BEFORE, DROP_AFTER -> {
                ServerInfo anchor = ((MultiplayerServerListWidget.ServerEntry) accident$dropRow).getServer();
                ServerArrangement.place(list, dragged, ServerArrangement.groupOf(anchor),
                        anchor, accident$dropMode == DROP_AFTER);
            }

            case DROP_MERGE -> {
                ServerInfo anchor = ((MultiplayerServerListWidget.ServerEntry) accident$dropRow).getServer();
                String folder = ServerArrangement.groupOf(anchor);

                if (folder.isEmpty()) {
                    // бросили один сервер на другой без папки - создаём папку из двух
                    folder = ServerArrangement.freeFolderName(ServerArrangement.snapshot(list));
                    ServerMeta.setFolder(anchor.address, folder);
                    ServerMeta.togglePinnedOff(anchor.address);
                }

                ServerArrangement.place(list, dragged, folder, anchor, true);
            }

            default -> {
            }
        }
    }

    @Inject(method = "updateEntries", at = @At("HEAD"))
    private void updateEntriesInject(CallbackInfo ci) {
        if (!threadpoolInitialized) {
            threadpoolInitialized = true;
            clearServerPingerThreadPool();
        }
        if (SERVER_PINGER_THREAD_POOL.getActiveCount() >= PINGER_THREAD_COUNT_OVERHEAD) {
            clearServerPingerThreadPool();
        }
    }

    @Unique
    private void clearServerPingerThreadPool() {
        SERVER_PINGER_THREAD_POOL.shutdownNow();
        SERVER_PINGER_THREAD_POOL = new ScheduledThreadPoolExecutor(
                servers.size() + PINGER_THREAD_COUNT_OVERHEAD,
                (new ThreadFactoryBuilder()).setNameFormat("Server Pinger #%d").setDaemon(true).build()
        );
    }
}
