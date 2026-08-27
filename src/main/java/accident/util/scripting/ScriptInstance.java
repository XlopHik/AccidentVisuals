package accident.util.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.io.File;

// один загруженный .lua файл - если скрипт постоянно падает с ошибкой, отключаем его,
// чтобы не спамил в лог и не жрал CPU каждый тик
public class ScriptInstance {

    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    private final File file;
    private final Globals globals;
    private boolean enabled = true;
    private String lastError = null;
    private int consecutiveErrors = 0;

    public ScriptInstance(File file, Globals globals) {
        this.file = file;
        this.globals = globals;
    }

    public String getName() {
        return file.getName();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            consecutiveErrors = 0;
            lastError = null;
        }
    }

    public String getLastError() {
        return lastError;
    }

    public void setLoadError(String message) {
        this.enabled = false;
        this.lastError = message;
    }

    public void callIfDefined(String functionName) {
        if (!enabled) return;
        LuaValue fn = globals.get(functionName);
        if (!fn.isfunction()) return;

        try {
            fn.call();
            consecutiveErrors = 0;
        } catch (LuaError e) {
            onError(functionName, e.getMessage());
        } catch (Exception e) {
            onError(functionName, e.toString());
        }
    }

    public void callIfDefined(String functionName, LuaValue arg) {
        if (!enabled) return;
        LuaValue fn = globals.get(functionName);
        if (!fn.isfunction()) return;

        try {
            fn.call(arg);
            consecutiveErrors = 0;
        } catch (LuaError e) {
            onError(functionName, e.getMessage());
        } catch (Exception e) {
            onError(functionName, e.toString());
        }
    }

    private void onError(String functionName, String message) {
        lastError = functionName + ": " + message;
        consecutiveErrors++;
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            enabled = false;
        }
    }
}
