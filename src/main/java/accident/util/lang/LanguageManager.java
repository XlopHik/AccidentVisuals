package accident.util.lang;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {

    private static LanguageManager instance;
    private static final Gson gson = new Gson();
    private static final Path CONFIG_PATH = Paths.get("AccidentVisuals", "configs", "language.json");

    private String currentLanguage = "en";
    private final Map<String, String> translations = new HashMap<>();
    private boolean initialized = false;

    public static LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    public void init() {
        loadSavedLanguage();
        loadTranslations();
        initialized = true;
    }

    private void loadSavedLanguage() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("language")) {
                    currentLanguage = obj.get("language").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void setLanguage(String lang) {
        this.currentLanguage = lang;
        saveLanguage();
        translations.clear();
        loadTranslations();
    }

    private void saveLanguage() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("language", currentLanguage);
            Files.writeString(CONFIG_PATH, gson.toJson(obj), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private void loadTranslations() {
        String fileName = currentLanguage.equals("ru") ? "Ru_ru.json" : "En_en.json";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("assets/accident/lang/" + fileName)) {
            if (is != null) {
                InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                for (String key : obj.keySet()) {
                    translations.put(key, obj.get(key).getAsString());
                }
                reader.close();
            }
        } catch (Exception ignored) {
        }
    }

    public static String get(String key) {
        LanguageManager manager = getInstance();
        if (!manager.initialized) {
            manager.init();
        }
        return manager.translations.getOrDefault(key, key);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
