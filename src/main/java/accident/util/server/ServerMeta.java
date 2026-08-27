package accident.util.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import accident.util.config.impl.ConfigPath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// какие сервера закреплены, в какой они папке, какие папки свёрнуты
// ключ - адрес, а не индекс в списке: ванильный список переупорядочивается, индекс бы постоянно съезжал не на тот сервер
public final class ServerMeta {

    private ServerMeta() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, Boolean> PINNED = new HashMap<>();
    private static final Map<String, String> FOLDERS = new HashMap<>();
    private static final Set<String> COLLAPSED = new HashSet<>();

    private static boolean loaded = false;

    private static Path file() {
        return ConfigPath.getConfigDirectory().resolve("servers.json");
    }

    private static final class Data {
        Map<String, Boolean> pinned = new HashMap<>();
        Map<String, String> folders = new HashMap<>();
        Set<String> collapsed = new HashSet<>();
    }

    public static void load() {
        if (loaded) return;
        loaded = true;

        try {
            Path path = file();
            if (!Files.exists(path)) return;

            String json = Files.readString(path, StandardCharsets.UTF_8);
            Data data = GSON.fromJson(json, new TypeToken<Data>() {}.getType());
            if (data == null) return;

            if (data.pinned != null) PINNED.putAll(data.pinned);
            if (data.folders != null) FOLDERS.putAll(data.folders);
            if (data.collapsed != null) COLLAPSED.addAll(data.collapsed);
        } catch (Exception ignored) {
            // битый файл - просто без пинов и папок, не страшно
        }
    }

    public static void save() {
        try {
            Data data = new Data();
            data.pinned = PINNED;
            data.folders = FOLDERS;
            data.collapsed = COLLAPSED;

            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static boolean isPinned(String address) {
        load();
        return address != null && Boolean.TRUE.equals(PINNED.get(address));
    }

    public static void togglePinned(String address) {
        if (address == null) return;
        load();

        if (isPinned(address)) {
            PINNED.remove(address);
        } else {
            PINNED.put(address, true);
        }
        save();
    }

    /** Takes a server out of the pinned block without touching anything else. */
    public static void togglePinnedOff(String address) {
        if (address == null) return;
        load();

        if (PINNED.remove(address) != null) save();
    }

    /** Empty string when the server sits outside any folder. */
    public static String folderOf(String address) {
        load();
        if (address == null) return "";
        String folder = FOLDERS.get(address);
        return folder == null ? "" : folder;
    }

    public static void setFolder(String address, String folder) {
        if (address == null) return;
        load();

        if (folder == null || folder.isBlank()) {
            FOLDERS.remove(address);
        } else {
            FOLDERS.put(address, folder.trim());
        }
        save();
    }

    /** Every server currently filed under the given folder. */
    public static java.util.List<String> addressesIn(String folder) {
        load();
        java.util.List<String> out = new java.util.ArrayList<>();
        if (folder == null || folder.isEmpty()) return out;

        for (Map.Entry<String, String> entry : FOLDERS.entrySet()) {
            if (folder.equals(entry.getValue())) out.add(entry.getKey());
        }
        return out;
    }

    public static boolean isCollapsed(String folder) {
        load();
        return COLLAPSED.contains(folder);
    }

    public static void toggleCollapsed(String folder) {
        load();
        if (!COLLAPSED.remove(folder)) COLLAPSED.add(folder);
        save();
    }
}
