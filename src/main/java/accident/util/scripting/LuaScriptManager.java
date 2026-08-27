package accident.util.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;

public class LuaScriptManager {

    private static final String ROOT_DIR = "AccidentVisuals";
    private static final String SCRIPTS_DIR = "scripts";
    private static final long RELOAD_DEBOUNCE_MS = 400;

    private final List<ScriptInstance> scripts = new ArrayList<>();
    private final List<LuaScriptModule> scriptModules = new ArrayList<>();

    private WatchService watchService;
    private long pendingReloadAt = 0;

    public static Path getScriptsDirectory() {
        return Paths.get("").toAbsolutePath().resolve(ROOT_DIR).resolve(SCRIPTS_DIR);
    }

    public List<ScriptInstance> getScripts() {
        return scripts;
    }

    public void reload() {
        unregisterModules();
        scripts.clear();

        Path dir = getScriptsDirectory();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }

        File[] files = dir.toFile().listFiles((d, name) -> name.toLowerCase().endsWith(".lua"));
        if (files != null) {
            for (File file : files) {
                loadScript(file);
            }
        }

        watchDirectory(dir);
    }

    private void watchDirectory(Path dir) {
        if (watchService != null) return;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException ignored) {
        }
    }

    // вызывается каждый тик из LuaScripts.onTick, подхватывает изменения файлов в папке скриптов
    // без ручной перезагрузки. debounce нужен, т.к. редакторы при сохранении шлют несколько событий подряд
    public void pollFileChanges() {
        if (watchService == null) return;

        WatchKey key = watchService.poll();
        if (key == null) {
            if (pendingReloadAt != 0 && System.currentTimeMillis() >= pendingReloadAt) {
                pendingReloadAt = 0;
                reload();
            }
            return;
        }

        boolean relevant = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            Object ctx = event.context();
            if (ctx != null && ctx.toString().toLowerCase().endsWith(".lua")) relevant = true;
        }
        key.reset();

        if (relevant) pendingReloadAt = System.currentTimeMillis() + RELOAD_DEBOUNCE_MS;
    }

    private void loadScript(File file) {
        LuaScriptModule module = new LuaScriptModule(displayName(file));
        Globals globals = LuaApi.newSandboxedGlobals(module, file);
        ScriptInstance instance = new ScriptInstance(file, globals);
        module.setScript(instance);

        try {
            String source = Files.readString(file.toPath());
            LuaValue chunk = globals.load(source, file.getName());
            chunk.call();
        } catch (LuaError e) {
            instance.setLoadError(e.getMessage());
        } catch (Exception e) {
            instance.setLoadError(e.toString());
        }
        scripts.add(instance);

        if (instance.getLastError() == null) module.setState(true);
        accident.Initialization.getInstance().getManager().getModuleRepository().addDynamicModule(module);
        scriptModules.add(module);
    }

    private static String displayName(File file) {
        String name = file.getName();
        return name.toLowerCase().endsWith(".lua") ? name.substring(0, name.length() - 4) : name;
    }

    private void unregisterModules() {
        var repo = accident.Initialization.getInstance().getManager().getModuleRepository();
        for (LuaScriptModule module : scriptModules) {
            repo.removeDynamicModule(module);
        }
        scriptModules.clear();
    }

    public void clear() {
        unregisterModules();
        scripts.clear();
    }
}
