package accident.modules.impl.misc;

import org.lwjgl.glfw.GLFW;
import accident.events.api.EventHandler;
import accident.events.api.EventManager;
import accident.events.impl.DrawEvent;
import accident.events.impl.KeyEvent;
import accident.events.impl.TickEvent;
import accident.events.impl.WorldRenderEvent;
import accident.util.scripting.EntityDeathTracker;
import accident.util.scripting.LuaApi;
import accident.util.scripting.LuaGuiScreen;
import accident.util.scripting.LuaScriptManager;
import accident.util.scripting.ScriptInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.List;

// рантайм для луа-скриптов, всегда включён - каждый скрипт сам регистрирует
// свою запись в ClickGUI (LuaScriptModule) и включается/выключается там,
// отдельный тумблер тут был бы просто лишним и путал бы, если панель пустая
public class LuaScripts {

    private static LuaScripts instance;

    public static LuaScripts getInstance() {
        if (instance == null) instance = new LuaScripts();
        return instance;
    }

    private final LuaScriptManager manager = new LuaScriptManager();
    private static final int OPEN_GUI_KEY = GLFW.GLFW_KEY_RIGHT_BRACKET;

    private LuaScripts() {
        EventManager.register(this);
        manager.reload();
    }

    public void reload() {
        manager.reload();
    }

    public List<ScriptInstance> getScripts() {
        return manager.getScripts();
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof LuaGuiScreen) {
            if (e.isKeyDown(OPEN_GUI_KEY, true)) mc.setScreen(null);
            return;
        }
        if (mc.currentScreen == null && e.isKeyDown(OPEN_GUI_KEY)) {
            mc.setScreen(new LuaGuiScreen());
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        manager.pollFileChanges();

        List<Entity> deaths = EntityDeathTracker.pollDeaths();

        for (ScriptInstance script : manager.getScripts()) {
            script.callIfDefined("onTick");
            for (Entity died : deaths) {
                script.callIfDefined("onEntityDeath", LuaApi.buildEntityEntry(died));
            }
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent event) {
        for (ScriptInstance script : manager.getScripts()) {
            script.callIfDefined("onRender3D");
        }
    }

    @EventHandler
    public void onDraw(DrawEvent event) {
        for (ScriptInstance script : manager.getScripts()) {
            script.callIfDefined("onRender2D");
        }
    }
}
