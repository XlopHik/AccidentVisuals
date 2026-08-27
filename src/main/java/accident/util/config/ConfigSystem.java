package accident.util.config;

import accident.util.config.impl.ConfigFileHandler;
import accident.util.config.impl.ConfigPath;
import accident.util.config.impl.ConfigSerializer;
import accident.util.config.impl.autosaver.ConfigAutoSaver;
import accident.util.config.impl.consolelogger.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


public class ConfigSystem {

    private static ConfigSystem instance;

    private final ConfigSerializer serializer;
    private final ConfigFileHandler fileHandler;
    private final ConfigAutoSaver autoSaver;
    private final AtomicBoolean initialized;
    private final AtomicBoolean saving;

    public ConfigSystem() {
        instance = this;
        this.serializer = new ConfigSerializer();
        this.fileHandler = new ConfigFileHandler();
        this.autoSaver = new ConfigAutoSaver(this::save);
        this.initialized = new AtomicBoolean(false);
        this.saving = new AtomicBoolean(false);
    }

    public static ConfigSystem getInstance() {
        return instance;
    }

    public void init() {
        if (initialized.compareAndSet(false, true)) {
            ConfigPath.init();
            fileHandler.createDirectories();
            load();
            autoSaver.start();
            registerShutdownHook();
            Logger.success("AutoConfiguration: System initialized!");
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("AutoConfiguration: Shutdown detected, saving...");
            shutdown();
        }, "Accident-ConfigShutdown"));
    }

    public void save() {
        if (!initialized.get()) {
            return;
        }
        if (!saving.compareAndSet(false, true)) {
            return;
        }
        try {
            String data = serializer.serialize();
            boolean success = fileHandler.write(data);
            if (success) {
                Logger.success("AutoConfiguration: autoconfig.json saved successfully!");
            } else {
                Logger.error("AutoConfiguration: autoconfig.json save failed!");
            }
        } catch (Exception e) {
            Logger.error("AutoConfiguration: Save error! " + e.getMessage());
        } finally {
            saving.set(false);
        }
    }

    public CompletableFuture<Void> saveAsync() {
        return CompletableFuture.runAsync(this::save);
    }

    public void load() {
        if (!fileHandler.exists()) {
            Logger.info("AutoConfiguration: No config found, creating new...");
            save();
            return;
        }
        try {
            String data = fileHandler.read();
            if (data != null && !data.isEmpty()) {
                serializer.deserialize(data);
                Logger.success("AutoConfiguration: autoconfig.json loaded successfully!");
            }
        } catch (Exception e) {
            Logger.error("AutoConfiguration: Load error! " + e.getMessage());
        }
    }

    public void shutdown() {
        if (!initialized.get()) {
            return;
        }
        autoSaver.shutdown();
        save();
        Logger.success("AutoConfiguration: Shutdown complete!");
    }

    public void reload() {
        load();
        Logger.success("AutoConfiguration: Config reloaded!");
    }

    /** Загрузка профиля из файла в папке configs (имя без .json). */
    public boolean loadNamedConfig(String name) {
        if (!initialized.get() || name == null || name.isEmpty()) {
            return false;
        }
        if (name.equalsIgnoreCase("autoconfig")) {
            return false;
        }
        try {
            Path p = safeProfilePath(name);
            if (p == null || !Files.exists(p)) {
                return false;
            }
            String data = Files.readString(p, StandardCharsets.UTF_8);
            if (data == null || data.isEmpty()) {
                return false;
            }
            serializer.deserialize(data);
            Logger.success("AutoConfiguration: loaded profile \"" + name + "\"");
            return true;
        } catch (Exception e) {
            Logger.error("AutoConfiguration: loadNamedConfig failed: " + e.getMessage());
            return false;
        }
    }

    /** Сохранить текущее состояние клиента в configs/&lt;name&gt;.json (как Create/Save в Celestial GuiConfig). */
    public boolean saveNamedConfig(String name) {
        if (!initialized.get() || name == null || name.isEmpty()) {
            return false;
        }
        String n = sanitizeProfileName(name);
        if (n == null || n.equalsIgnoreCase("autoconfig")) {
            return false;
        }
        try {
            Path p = ConfigPath.getConfigDirectory().resolve(n + ".json");
            String data = serializer.serialize();
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, data, StandardCharsets.UTF_8);
            Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            Logger.success("AutoConfiguration: saved profile \"" + n + "\"");
            return true;
        } catch (Exception e) {
            Logger.error("AutoConfiguration: saveNamedConfig failed: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteNamedConfig(String name) {
        if (!initialized.get() || name == null || name.isEmpty()) {
            return false;
        }
        String n = sanitizeProfileName(name);
        if (n == null || n.equalsIgnoreCase("autoconfig")) {
            return false;
        }
        try {
            Path p = ConfigPath.getConfigDirectory().resolve(n + ".json");
            if (!Files.exists(p)) {
                return false;
            }
            Files.delete(p);
            Logger.success("AutoConfiguration: deleted profile \"" + n + "\"");
            return true;
        } catch (Exception e) {
            Logger.error("AutoConfiguration: deleteNamedConfig failed: " + e.getMessage());
            return false;
        }
    }

    private static Path safeProfilePath(String name) {
        String n = sanitizeProfileName(name);
        if (n == null || n.equalsIgnoreCase("autoconfig")) {
            return null;
        }
        return ConfigPath.getConfigDirectory().resolve(n + ".json");
    }

    private static String sanitizeProfileName(String name) {
        if (name == null) {
            return null;
        }
        String t = name.trim().replace(" ", "");
        if (t.isEmpty()) {
            return null;
        }
        if (t.contains("..") || t.contains("/") || t.contains("\\")) {
            return null;
        }
        return t;
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public boolean isSaving() {
        return saving.get();
    }

    public ConfigAutoSaver getAutoSaver() {
        return autoSaver;
    }
}