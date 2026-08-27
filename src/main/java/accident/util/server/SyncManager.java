package accident.util.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SyncManager {
    private static SyncManager INSTANCE;

    private final String API_URL = "http://185.56.162.100:10694/v1/users";
    private final HttpClient client = HttpClient.newHttpClient();
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private List<String> onlineUsers = new CopyOnWriteArrayList<>();
    private long lastUpdate = 0;

    public SyncManager() {}

    public static SyncManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SyncManager();
        }
        return INSTANCE;
    }

    public void onUpdate() {
        if (System.currentTimeMillis() - lastUpdate > 60000) {
            updateData();
            lastUpdate = System.currentTimeMillis();
        }
    }

    private void updateData() {
        new Thread(() -> {
            try {
                pingSelf();
                this.onlineUsers = fetchOnlinePlayers();
            } catch (Exception e) {
                System.err.println("[Sync] Ошибка связи с сервером: " + e.getMessage());
            }
        }).start();
    }

    private void pingSelf() throws Exception {
        if (mc.getSession() == null) return;
        String name = mc.getSession().getUsername();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/online?name=" + name))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private List<String> fetchOnlinePlayers() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/online"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();

        List<String> names = new ArrayList<>();
        for (JsonElement element : array) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    public boolean isUser(String name) {
        return onlineUsers.contains(name);
    }

    public List<String> getOnlineUsers() {
        return onlineUsers;
    }
}