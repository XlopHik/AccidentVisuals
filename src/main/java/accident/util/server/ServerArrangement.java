package accident.util.server;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

// тут решается порядок серверов в списке и он же пишется обратно на диск
// порядок не хранится отдельно, а всегда выводится заново: сначала пины, потом папки подтягиваются к позиции первого своего сервера, остальное как было
public final class ServerArrangement {

    private ServerArrangement() {}

    /** Implemented by the server list widget so a change anywhere can refresh it. */
    public interface Refresher {
        void accident$rebuildServerList();
    }

    private static Refresher active;

    public static void setActive(Refresher refresher) {
        active = refresher;
    }

    public static void refresh() {
        if (active != null) active.accident$rebuildServerList();
    }

    public static List<ServerInfo> snapshot(ServerList list) {
        List<ServerInfo> all = new ArrayList<>();
        if (list == null) return all;
        for (int i = 0; i < list.size(); i++) all.add(list.get(i));
        return all;
    }

    /** Display order for the given servers. */
    public static List<ServerInfo> flatten(List<ServerInfo> all) {
        List<ServerInfo> pinned = new ArrayList<>();
        List<ServerInfo> rest = new ArrayList<>();

        for (ServerInfo server : all) {
            if (ServerMeta.isPinned(server.address)) pinned.add(server);
            else rest.add(server);
        }

        List<ServerInfo> out = new ArrayList<>(pinned);
        Set<ServerInfo> placed = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ServerInfo server : rest) {
            if (placed.contains(server)) continue;

            String folder = ServerMeta.folderOf(server.address);
            if (folder.isEmpty()) {
                placed.add(server);
                out.add(server);
                continue;
            }

            // First member of a folder decides where the whole folder sits.
            for (ServerInfo other : rest) {
                if (placed.contains(other)) continue;
                if (folder.equals(ServerMeta.folderOf(other.address))) {
                    placed.add(other);
                    out.add(other);
                }
            }
        }

        return out;
    }

    /** The folder a server is shown under - pinned servers ignore their folder. */
    public static String groupOf(ServerInfo server) {
        return ServerMeta.isPinned(server.address) ? "" : ServerMeta.folderOf(server.address);
    }

    private static void writeBack(ServerList list, List<ServerInfo> order) {
        if (list == null || order.size() != list.size()) return;
        for (int i = 0; i < order.size(); i++) list.set(i, order.get(i));
        list.saveFile();
    }

    // двигает сервер рядом с anchor и кладёт в нужную папку; anchor == null - в конец списка
    public static void place(ServerList list, ServerInfo dragged, String folder,
                             ServerInfo anchor, boolean after) {
        if (list == null || dragged == null) return;

        ServerMeta.setFolder(dragged.address, folder);

        List<ServerInfo> order = flatten(snapshot(list));
        order.remove(dragged);

        int at = order.size();
        if (anchor != null && anchor != dragged) {
            int index = order.indexOf(anchor);
            if (index >= 0) at = index + (after ? 1 : 0);
        }

        order.add(Math.min(at, order.size()), dragged);
        writeBack(list, order);
        refresh();
    }

    /** One step up or down in the displayed order, used by the vanilla arrows. */
    public static void step(ServerList list, ServerInfo server, int direction) {
        if (list == null || server == null) return;

        List<ServerInfo> order = flatten(snapshot(list));
        int from = order.indexOf(server);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= order.size()) return;

        ServerInfo neighbour = order.get(to);

        // в блок пинов случайно не попадёшь - шаг через границу просто ничего не делает
        if (ServerMeta.isPinned(neighbour.address) != ServerMeta.isPinned(server.address)) return;

        ServerMeta.setFolder(server.address, ServerMeta.folderOf(neighbour.address));

        order.set(from, neighbour);
        order.set(to, server);
        writeBack(list, order);
        refresh();
    }

    /** Renames a folder for every server in it. An empty name dissolves it. */
    public static void renameFolder(String from, String to) {
        for (String address : ServerMeta.addressesIn(from)) {
            ServerMeta.setFolder(address, to);
        }
        refresh();
    }

    /** A name no existing folder is using yet. */
    public static String freeFolderName(List<ServerInfo> all) {
        Set<String> used = new HashSet<>();
        for (ServerInfo server : all) {
            String folder = ServerMeta.folderOf(server.address);
            if (!folder.isEmpty()) used.add(folder);
        }

        for (int i = 1; ; i++) {
            String name = "Folder " + i;
            if (!used.contains(name)) return name;
        }
    }
}
