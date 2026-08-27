package accident.util.config.impl.drag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import accident.Initialization;
import accident.client.draggables.HudElement;
import accident.client.draggables.HudManager;
import accident.util.config.impl.consolelogger.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DragConfig {
    private static DragConfig instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;

    private DragConfig() {
        Path configDir = Paths.get("AccidentVisuals", "configs");
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {}
        configPath = configDir.resolve("draggables.json");
    }

    public static DragConfig getInstance() {
        if (instance == null) {
            instance = new DragConfig();
        }
        return instance;
    }

    public void save() {
        try {
            HudManager hudManager = getHudManager();
            if (hudManager == null || !hudManager.isInitialized()) return;

            JsonObject root = new JsonObject();
            for (HudElement element : hudManager.getElements()) {
                JsonObject elementJson = new JsonObject();
                elementJson.addProperty("x", element.getX());
                elementJson.addProperty("y", element.getY());
                elementJson.addProperty("width", element.getWidth());
                elementJson.addProperty("height", element.getHeight());
                root.add(element.getName(), elementJson);
            }
            Files.writeString(configPath, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
        }
    }

    public void load() {
        try {
            if (!Files.exists(configPath)) {
                return;
            }

            HudManager hudManager = getHudManager();
            if (hudManager == null) {
                return;
            }

            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                return;
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            for (HudElement element : hudManager.getElements()) {
                if (root.has(element.getName())) {
                    JsonObject elementJson = root.getAsJsonObject(element.getName());
                    if (elementJson.has("x")) {
                        element.setX(elementJson.get("x").getAsInt());
                    }
                    if (elementJson.has("y")) {
                        element.setY(elementJson.get("y").getAsInt());
                    }
                    if (elementJson.has("width")) {
                        element.setWidth(elementJson.get("width").getAsInt());
                    }
                    if (elementJson.has("height")) {
                        element.setHeight(elementJson.get("height").getAsInt());
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private HudManager getHudManager() {
        if (Initialization.getInstance() == null) return null;
        if (Initialization.getInstance().getManager() == null) return null;
        return Initialization.getInstance().getManager().getHudManager();
    }
}