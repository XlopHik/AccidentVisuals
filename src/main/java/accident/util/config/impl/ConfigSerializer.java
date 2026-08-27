package accident.util.config.impl;

import com.google.gson.*;
import accident.Initialization;
import accident.client.draggables.Drag;
import accident.client.draggables.HudElement;
import accident.client.draggables.HudManager;
import accident.modules.module.ModuleRepository;
import accident.modules.module.ModuleStructure;
import accident.modules.module.setting.Setting;
import accident.modules.module.setting.implement.*;
import accident.screens.clickgui.dropdown.ThemesColumn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigSerializer {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public String serialize() {
        JsonObject root = new JsonObject();
        JsonObject modulesJson = new JsonObject();

        ModuleRepository repository = getModuleRepository();
        if (repository != null) {
            for (ModuleStructure module : repository.modules()) {
                JsonObject moduleJson = serializeModule(module);
                modulesJson.add(module.getConfigKey(), moduleJson);
            }
        }

        root.add("modules", modulesJson);
        root.add("hud_positions", serializeHudPositions());
        root.addProperty("version", "1.0");
        root.addProperty("timestamp", System.currentTimeMillis());
        root.addProperty("client", "Accident");
        root.addProperty("gui_theme", ThemesColumn.getCurrentGlobalTheme().name());

        return GSON.toJson(root);
    }

    private JsonObject serializeHudPositions() {
        JsonObject hudJson = new JsonObject();
        HudManager hudManager = getHudManager();

        if (hudManager != null) {
            for (HudElement element : hudManager.getElements()) {
                if (Drag.EXCLUDED_ELEMENTS.contains(element.getName())) {
                    continue;
                }
                JsonObject elementJson = new JsonObject();
                elementJson.addProperty("x", element.getX());
                elementJson.addProperty("y", element.getY());
                hudJson.add(element.getName(), elementJson);
            }
        }
        return hudJson;
    }

    private JsonObject serializeModule(ModuleStructure module) {
        JsonObject moduleJson = new JsonObject();
        moduleJson.addProperty("enabled", module.isState());
        moduleJson.addProperty("key", module.getKey());
        moduleJson.addProperty("type", module.getType());
        moduleJson.addProperty("favorite", module.isFavorite());

        JsonObject settingsJson = new JsonObject();
        for (Setting setting : module.settings()) {
            JsonElement element = serializeSetting(setting);
            if (element != null) {
                settingsJson.add(setting.getConfigKey(), element);
            }
        }
        moduleJson.add("settings", settingsJson);

        return moduleJson;
    }

    private JsonElement serializeSetting(Setting setting) {
        if (setting instanceof BooleanSetting boolSetting) {
            return new JsonPrimitive(boolSetting.isValue());
        }
        if (setting instanceof SliderSettings sliderSetting) {
            return new JsonPrimitive(sliderSetting.getValue());
        }
        if (setting instanceof BindSetting bindSetting) {
            JsonObject bindJson = new JsonObject();
            bindJson.addProperty("key", bindSetting.getKey());
            bindJson.addProperty("type", bindSetting.getType());
            return bindJson;
        }
        if (setting instanceof TextSetting textSetting) {
            return new JsonPrimitive(textSetting.getText() != null ? textSetting.getText() : "");
        }
        if (setting instanceof SelectSetting selectSetting) {
            return new JsonPrimitive(selectSetting.getSelected());
        }
        if (setting instanceof ColorSetting colorSetting) {
            JsonObject colorJson = new JsonObject();
            colorJson.addProperty("hue", colorSetting.getHue());
            colorJson.addProperty("saturation", colorSetting.getSaturation());
            colorJson.addProperty("brightness", colorSetting.getBrightness());
            colorJson.addProperty("alpha", colorSetting.getAlpha());
            return colorJson;
        }
        if (setting instanceof MultiSelectSetting multiSetting) {
            JsonArray array = new JsonArray();
            for (String value : multiSetting.getSelected()) {
                array.add(value);
            }
            return array;
        }
        if (setting instanceof GroupSetting groupSetting) {
            JsonObject groupJson = new JsonObject();
            groupJson.addProperty("value", groupSetting.isValue());
            JsonObject subSettingsJson = new JsonObject();
            for (Setting subSetting : groupSetting.getSubSettings()) {
                JsonElement element = serializeSetting(subSetting);
                if (element != null) {
                    subSettingsJson.add(subSetting.getName(), element);
                }
            }
            groupJson.add("subSettings", subSettingsJson);
            return groupJson;
        }
        return null;
    }

    public void deserialize(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("modules")) {
                JsonObject modulesJson = root.getAsJsonObject("modules");
                ModuleRepository repository = getModuleRepository();
                if (repository != null) {
                    for (ModuleStructure module : repository.modules()) {
                        if (modulesJson.has(module.getConfigKey())) {
                            deserializeModule(module, modulesJson.getAsJsonObject(module.getConfigKey()));
                        }
                    }
                }
            }

            if (root.has("gui_theme")) {
                try {
                    String themeName = root.get("gui_theme").getAsString();
                    ThemesColumn.Theme theme = ThemesColumn.Theme.valueOf(themeName);
                    ThemesColumn.updateGlobalTheme(theme);
                } catch (IllegalArgumentException e) {
                    ThemesColumn.updateGlobalTheme(ThemesColumn.Theme.DEFAULT);
                }
            }

            if (root.has("hud_positions")) {
                deserializeHudPositions(root.getAsJsonObject("hud_positions"));
            } else {
                migrateHudFromOldConfig();
            }
        } catch (JsonSyntaxException e) {
        }
    }

    private void migrateHudFromOldConfig() {
        Path oldPath = Paths.get("AccidentVisuals", "configs", "draggables.json");
        if (!Files.exists(oldPath)) return;

        try {
            String oldJson = Files.readString(oldPath, StandardCharsets.UTF_8);
            if (oldJson == null || oldJson.trim().isEmpty()) return;

            JsonObject oldRoot = JsonParser.parseString(oldJson).getAsJsonObject();
            deserializeHudPositions(oldRoot);
        } catch (Exception ignored) {}
    }

    private void deserializeHudPositions(JsonObject hudJson) {
        HudManager hudManager = getHudManager();
        if (hudManager == null) return;

        for (HudElement element : hudManager.getElements()) {
            if (Drag.EXCLUDED_ELEMENTS.contains(element.getName())) {
                continue;
            }

            String name = element.getName();
            if (hudJson.has(name)) {
                JsonObject elementJson = hudJson.getAsJsonObject(name);
                if (elementJson.has("x")) element.setX(elementJson.get("x").getAsInt());
                if (elementJson.has("y")) element.setY(elementJson.get("y").getAsInt());
            }
        }
    }


    private void deserializeModule(ModuleStructure module, JsonObject moduleJson) {
        if (moduleJson.has("enabled")) {
            boolean enabled = moduleJson.get("enabled").getAsBoolean();
            if (enabled) {
                module.setState(true);
            }
        }
        if (moduleJson.has("key")) {
            module.setKey(moduleJson.get("key").getAsInt());
        }
        if (moduleJson.has("type")) {
            module.setType(moduleJson.get("type").getAsInt());
        }
        if (moduleJson.has("favorite")) {
            module.setFavorite(moduleJson.get("favorite").getAsBoolean());
        }
        if (moduleJson.has("settings")) {
            JsonObject settingsJson = moduleJson.getAsJsonObject("settings");
            for (Setting setting : module.settings()) {
                if (settingsJson.has(setting.getConfigKey())) {
                    deserializeSetting(setting, settingsJson.get(setting.getConfigKey()));
                }
            }
        }
    }

    private void deserializeSetting(Setting setting, JsonElement element) {
        try {
            if (setting instanceof BooleanSetting boolSetting) {
                boolSetting.setValue(element.getAsBoolean());
            } else if (setting instanceof SliderSettings sliderSetting) {
                sliderSetting.setValue((float) element.getAsDouble());
            } else if (setting instanceof BindSetting bindSetting) {
                if (element.isJsonObject()) {
                    JsonObject bindJson = element.getAsJsonObject();
                    if (bindJson.has("key")) {
                        bindSetting.setKey(bindJson.get("key").getAsInt());
                    }
                    if (bindJson.has("type")) {
                        bindSetting.setType(bindJson.get("type").getAsInt());
                    }
                } else {
                    bindSetting.setKey(element.getAsInt());
                }
            } else if (setting instanceof TextSetting textSetting) {
                textSetting.setText(element.getAsString());
            } else if (setting instanceof SelectSetting selectSetting) {
                selectSetting.setSelected(element.getAsString());
            } else if (setting instanceof ColorSetting colorSetting) {
                if (element.isJsonObject()) {
                    JsonObject colorJson = element.getAsJsonObject();
                    if (colorJson.has("hue")) {
                        colorSetting.setHue(colorJson.get("hue").getAsFloat());
                    }
                    if (colorJson.has("saturation")) {
                        colorSetting.setSaturation(colorJson.get("saturation").getAsFloat());
                    }
                    if (colorJson.has("brightness")) {
                        colorSetting.setBrightness(colorJson.get("brightness").getAsFloat());
                    }
                    if (colorJson.has("alpha")) {
                        colorSetting.setAlpha(colorJson.get("alpha").getAsFloat());
                    }
                } else {
                    colorSetting.setColor(element.getAsInt());
                }
            } else if (setting instanceof MultiSelectSetting multiSetting) {
                if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    List<String> selected = new ArrayList<>();
                    for (JsonElement e : array) {
                        selected.add(e.getAsString());
                    }
                    multiSetting.setSelected(selected);
                }
            } else if (setting instanceof GroupSetting groupSetting) {
                if (element.isJsonObject()) {
                    JsonObject groupJson = element.getAsJsonObject();
                    if (groupJson.has("value")) {
                        groupSetting.setValue(groupJson.get("value").getAsBoolean());
                    }
                    if (groupJson.has("subSettings")) {
                        JsonObject subSettingsJson = groupJson.getAsJsonObject("subSettings");
                        for (Setting subSetting : groupSetting.getSubSettings()) {
                            if (subSettingsJson.has(subSetting.getConfigKey())) {
                                deserializeSetting(subSetting, subSettingsJson.get(subSetting.getConfigKey()));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private ModuleRepository getModuleRepository() {
        Initialization instance = Initialization.getInstance();
        if (instance != null && instance.getManager() != null) {
            return instance.getManager().getModuleRepository();
        }
        return null;
    }

    private HudManager getHudManager() {
        Initialization instance = Initialization.getInstance();
        if (instance != null && instance.getManager() != null) {
            return instance.getManager().getHudManager();
        }
        return null;
    }
}