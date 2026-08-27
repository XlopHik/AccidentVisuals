package accident.util.scripting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

// свой json на скрипт, в scripts/data/<имя скрипта>.json - путь берётся из имени
// самого .lua файла, скрипт не может подсунуть свой путь и вылезти за пределы своего файла
final class LuaStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final JsonObject data;

    LuaStorage(File scriptFile) {
        File dataDir = new File(scriptFile.getParentFile(), "data");
        this.file = new File(dataDir, stripExtension(scriptFile.getName()) + ".json");
        this.data = load();
    }

    private static String stripExtension(String name) {
        return name.toLowerCase().endsWith(".lua") ? name.substring(0, name.length() - 4) : name;
    }

    private JsonObject load() {
        if (!file.exists()) return new JsonObject();
        try {
            String content = Files.readString(file.toPath());
            JsonElement parsed = JsonParser.parseString(content);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    JsonElement get(String key) {
        return data.get(key);
    }

    void setBoolean(String key, boolean value) { data.addProperty(key, value); save(); }
    void setNumber(String key, double value) { data.addProperty(key, value); save(); }
    void setString(String key, String value) { data.addProperty(key, value); save(); }
    void remove(String key) { data.remove(key); save(); }

    private void save() {
        try {
            File dir = file.getParentFile();
            if (!dir.exists()) Files.createDirectories(dir.toPath());
            Files.writeString(file.toPath(), GSON.toJson(data));
        } catch (IOException ignored) {
        }
    }
}
