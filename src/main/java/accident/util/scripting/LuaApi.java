package accident.util.scripting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Globals;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;
import accident.Initialization;
import accident.modules.module.ModuleStructure;
import accident.modules.module.setting.Setting;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SelectSetting;
import accident.modules.module.setting.implement.SliderSettings;
import accident.util.Instance;
import accident.util.math.Projection;
import accident.util.render.Render2D;
import accident.util.render.Render3D;
import accident.util.render.font.Fonts;

import java.io.File;

// песочница для скриптов - специально не грузим JseIoLib/JseOsLib/LuajavaLib
// (как в JsePlatform.standardGlobals()), иначе скрипт получит доступ к файлам
// и через luajava сможет дёрнуть любой Java-класс рефлексией, вплоть до Runtime.exec
public final class LuaApi {

    private LuaApi() {}

    public static Globals newSandboxedGlobals(LuaScriptModule ownModule, File scriptFile) {
        Globals globals = new Globals();
        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        globals.load(new StringLib());
        globals.load(new TableLib());
        globals.load(new JseMathLib());
        LoadStateInstall(globals);

        installRenderTable(globals);
        installPlayerTable(globals);
        installWorldTable(globals);
        installEntitiesTable(globals);
        installMouseTable(globals);
        installKeyboardTable(globals);
        installModulesTable(globals);
        installGuiTable(globals);
        installModuleSelfTable(globals, ownModule);
        installStorageTable(globals, scriptFile);

        return globals;
    }

    private static void LoadStateInstall(Globals globals) {
        org.luaj.vm2.LoadState.install(globals);
        LuaC.install(globals);
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    // ---- render ------------------------------------------------------

    private static void installRenderTable(Globals globals) {
        LuaTable render = new LuaTable();

        render.set("rect", varArgFn(args -> {
            float x = (float) args.checkdouble(1), y = (float) args.checkdouble(2);
            float w = (float) args.checkdouble(3), h = (float) args.checkdouble(4);
            int color = (int) args.checklong(5);
            if (args.narg() >= 9) {
                Render2D.rect(x, y, w, h, color, (float) args.checkdouble(6), (float) args.checkdouble(7),
                        (float) args.checkdouble(8), (float) args.checkdouble(9));
            } else if (args.narg() >= 6) {
                Render2D.rect(x, y, w, h, color, (float) args.checkdouble(6));
            } else {
                Render2D.rect(x, y, w, h, color);
            }
            return LuaValue.NONE;
        }));

        render.set("gradientRect", varArgFn(args -> {
            float x = (float) args.checkdouble(1), y = (float) args.checkdouble(2);
            float w = (float) args.checkdouble(3), h = (float) args.checkdouble(4);
            int[] colors = {(int) args.checklong(5), (int) args.checklong(6),
                    (int) args.checklong(7), (int) args.checklong(8)};
            float radius = args.narg() >= 9 ? (float) args.checkdouble(9) : 0f;
            Render2D.gradientRect(x, y, w, h, colors, radius);
            return LuaValue.NONE;
        }));

        render.set("outline", varArgFn(args -> {
            float x = (float) args.checkdouble(1), y = (float) args.checkdouble(2);
            float w = (float) args.checkdouble(3), h = (float) args.checkdouble(4);
            float thickness = (float) args.checkdouble(5);
            int color = (int) args.checklong(6);
            if (args.narg() >= 10) {
                Render2D.outline(x, y, w, h, thickness, color, (float) args.checkdouble(7),
                        (float) args.checkdouble(8), (float) args.checkdouble(9), (float) args.checkdouble(10));
            } else if (args.narg() >= 7) {
                Render2D.outline(x, y, w, h, thickness, color, (float) args.checkdouble(7));
            } else {
                Render2D.outline(x, y, w, h, thickness, color);
            }
            return LuaValue.NONE;
        }));

        render.set("gradientOutline", varArgFn(args -> {
            float x = (float) args.checkdouble(1), y = (float) args.checkdouble(2);
            float w = (float) args.checkdouble(3), h = (float) args.checkdouble(4);
            float thickness = (float) args.checkdouble(5);
            int[] colors = {(int) args.checklong(6), (int) args.checklong(7),
                    (int) args.checklong(8), (int) args.checklong(9)};
            float radius = args.narg() >= 10 ? (float) args.checkdouble(10) : 0f;
            Render2D.gradientOutline(x, y, w, h, thickness, colors, radius);
            return LuaValue.NONE;
        }));

        render.set("blur", varArgFn(args -> {
            float x = (float) args.checkdouble(1), y = (float) args.checkdouble(2);
            float w = (float) args.checkdouble(3), h = (float) args.checkdouble(4);
            float blurRadius = (float) args.checkdouble(5);
            int tintColor = (int) args.checklong(6);
            if (args.narg() >= 10) {
                Render2D.blur(x, y, w, h, blurRadius, (float) args.checkdouble(7), (float) args.checkdouble(8),
                        (float) args.checkdouble(9), (float) args.checkdouble(10), tintColor);
            } else if (args.narg() >= 7) {
                Render2D.blur(x, y, w, h, blurRadius, (float) args.checkdouble(7), tintColor);
            } else {
                Render2D.blur(x, y, w, h, blurRadius, tintColor);
            }
            return LuaValue.NONE;
        }));

        render.set("glowOutline", varArgFn(args -> {
            float x = (float) args.checkdouble(1), y = (float) args.checkdouble(2);
            float w = (float) args.checkdouble(3), h = (float) args.checkdouble(4);
            float thickness = (float) args.checkdouble(5);
            int color = (int) args.checklong(6);
            float radius = (float) args.checkdouble(7);
            float progress = (float) args.checkdouble(8);
            float baseAlpha = args.narg() >= 9 ? (float) args.checkdouble(9) : 1.0f;
            Render2D.glowOutline(x, y, w, h, thickness, color, radius, progress, baseAlpha);
            return LuaValue.NONE;
        }));

        render.set("arc", varArgFn(args -> {
            Render2D.arc((float) args.checkdouble(1), (float) args.checkdouble(2), (float) args.checkdouble(3),
                    (float) args.checkdouble(4), (float) args.checkdouble(5), (float) args.checkdouble(6),
                    (int) args.checklong(7));
            return LuaValue.NONE;
        }));

        render.set("arcOutline", varArgFn(args -> {
            Render2D.arcOutline((float) args.checkdouble(1), (float) args.checkdouble(2), (float) args.checkdouble(3),
                    (float) args.checkdouble(4), (float) args.checkdouble(5), (float) args.checkdouble(6),
                    (float) args.checkdouble(7), (int) args.checklong(8), (int) args.checklong(9));
            return LuaValue.NONE;
        }));

        render.set("screenWidth", varArgFn(args -> LuaValue.valueOf(Render2D.getFixedScaledWidth())));
        render.set("screenHeight", varArgFn(args -> LuaValue.valueOf(Render2D.getFixedScaledHeight())));

        render.set("texture", varArgFn(args -> {
            String namespace = args.checkjstring(1);
            String path = args.checkjstring(2);
            Identifier id = Identifier.of(namespace, path);
            float x = (float) args.checkdouble(3), y = (float) args.checkdouble(4);
            float w = (float) args.checkdouble(5), h = (float) args.checkdouble(6);
            int color = (int) args.checklong(7);
            if (args.narg() >= 8) {
                Render2D.texture(id, x, y, w, h, (float) args.checkdouble(8), color);
            } else {
                Render2D.texture(id, x, y, w, h, color);
            }
            return LuaValue.NONE;
        }));

        render.set("text", varArgFn(args -> {
            String text = args.checkjstring(1);
            float x = (float) args.checkdouble(2);
            float y = (float) args.checkdouble(3);
            float size = (float) args.checkdouble(4);
            int color = (int) args.checklong(5);
            Fonts.TEST.draw(text, x, y, size, color);
            return LuaValue.NONE;
        }));

        render.set("textCentered", varArgFn(args -> {
            String text = args.checkjstring(1);
            float x = (float) args.checkdouble(2);
            float y = (float) args.checkdouble(3);
            float size = (float) args.checkdouble(4);
            int color = (int) args.checklong(5);
            Fonts.TEST.drawCentered(text, x, y, size, color);
            return LuaValue.NONE;
        }));

        render.set("textWidth", varArgFn(args -> {
            String text = args.checkjstring(1);
            float size = (float) args.checkdouble(2);
            return LuaValue.valueOf(Fonts.TEST.getWidth(text, size));
        }));

        render.set("textGlow", varArgFn(args -> {
            String text = args.checkjstring(1);
            float x = (float) args.checkdouble(2), y = (float) args.checkdouble(3);
            float size = (float) args.checkdouble(4);
            int color = (int) args.checklong(5);
            int glowColor = (int) args.checklong(6);
            float glowWidth = (float) args.checkdouble(7);
            Initialization.getInstance().getManager().getRenderCore().getFontRenderer()
                    .drawTextWithGlow("test", text, x, y, size, color, glowColor, glowWidth);
            return LuaValue.NONE;
        }));

        render.set("line3d", varArgFn(args -> {
            Vec3d start = new Vec3d(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
            Vec3d end = new Vec3d(args.checkdouble(4), args.checkdouble(5), args.checkdouble(6));
            int color = (int) args.checklong(7);
            float width = args.narg() >= 8 ? (float) args.checkdouble(8) : 1.5f;
            boolean depth = args.narg() >= 9 && args.checkboolean(9);
            Render3D.drawLine(start, end, color, width, depth);
            return LuaValue.NONE;
        }));

        render.set("box3d", varArgFn(args -> {
            Box box = new Box(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3),
                    args.checkdouble(4), args.checkdouble(5), args.checkdouble(6));
            int color = (int) args.checklong(7);
            float width = args.narg() >= 8 ? (float) args.checkdouble(8) : 1.5f;
            Render3D.drawBox(box, color, width);
            return LuaValue.NONE;
        }));

        render.set("worldToScreen", varArgFn(args -> {
            Vec3d world = new Vec3d(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
            Vec3d screen = Projection.worldSpaceToScreenSpace(world);
            LuaTable result = new LuaTable();
            result.set("x", screen.x);
            result.set("y", screen.y);
            result.set("visible", LuaValue.valueOf(screen.z >= 0.0 && screen.z <= 1.0));
            return result;
        }));

        globals.set("render", render);
    }

    // ---- player --------------------------------------------------------

    private static void installPlayerTable(Globals globals) {
        LuaTable player = new LuaTable();

        player.set("getPos", varArgFn(args -> {
            PlayerEntity p = mc().player;
            if (p == null) return LuaValue.NIL;
            LuaTable pos = new LuaTable();
            pos.set("x", p.getX());
            pos.set("y", p.getY());
            pos.set("z", p.getZ());
            return pos;
        }));

        player.set("getHealth", varArgFn(args -> {
            PlayerEntity p = mc().player;
            return p == null ? LuaValue.valueOf(0) : LuaValue.valueOf(p.getHealth());
        }));

        player.set("getMaxHealth", varArgFn(args -> {
            PlayerEntity p = mc().player;
            return p == null ? LuaValue.valueOf(0) : LuaValue.valueOf(p.getMaxHealth());
        }));

        player.set("getYaw", varArgFn(args -> {
            PlayerEntity p = mc().player;
            return p == null ? LuaValue.valueOf(0) : LuaValue.valueOf(p.getYaw());
        }));

        player.set("getPitch", varArgFn(args -> {
            PlayerEntity p = mc().player;
            return p == null ? LuaValue.valueOf(0) : LuaValue.valueOf(p.getPitch());
        }));

        player.set("getName", varArgFn(args -> {
            PlayerEntity p = mc().player;
            return p == null ? LuaValue.valueOf("") : LuaValue.valueOf(p.getName().getString());
        }));

        player.set("isValid", varArgFn(args -> LuaValue.valueOf(mc().player != null)));

        globals.set("player", player);
    }

    // ---- world -----------------------------------------------------------

    private static void installWorldTable(Globals globals) {
        LuaTable world = new LuaTable();

        world.set("getTime", varArgFn(args -> {
            var w = mc().world;
            return w == null ? LuaValue.valueOf(0) : LuaValue.valueOf(w.getTimeOfDay());
        }));

        world.set("isLoaded", varArgFn(args -> LuaValue.valueOf(mc().world != null)));

        globals.set("world", world);
    }

    // ---- entities --------------------------------------------------------

    private static void installEntitiesTable(Globals globals) {
        LuaTable entities = new LuaTable();

        entities.set("getAll", varArgFn(args -> entityList(false)));
        entities.set("getPlayers", varArgFn(args -> entityList(true)));

        // фильтр по line-of-sight тут специально пропускаем, но отдаём только bool -
        // чтобы скрипт мог отличить "умер/деспавнился" от "зашёл за стену", без вальхака
        entities.set("isAlive", varArgFn(args -> {
            int id = (int) args.checklong(1);
            var world = mc().world;
            if (world == null) return LuaValue.FALSE;
            for (Entity entity : world.getEntities()) {
                if (entity.getId() == id) return LuaValue.valueOf(!entity.isRemoved());
            }
            return LuaValue.FALSE;
        }));

        globals.set("entities", entities);
    }

    // скрипт видит только тех, кого реально видно (настоящий raycast, а не просто
    // "в конусе обзора") - позиции за стеной в Lua вообще не попадают, так что esp через wallhack не сделать
    private static LuaTable entityList(boolean playersOnly) {
        LuaTable result = new LuaTable();
        var world = mc().world;
        var self = mc().player;
        if (world == null || self == null) return result;

        int i = 1;
        for (Entity entity : world.getEntities()) {
            if (entity == self) continue;
            if (playersOnly && !(entity instanceof PlayerEntity)) continue;
            if (!hasLineOfSight(self, entity)) continue;

            result.set(i++, buildEntityEntry(entity));
        }
        return result;
    }

    // общее с EntityDeathTracker/LuaScripts - чтобы onEntityDeath отдавал ту же структуру, что и entities.getAll()
    public static LuaTable buildEntityEntry(Entity entity) {
        LuaTable entry = new LuaTable();
        entry.set("id", entity.getId());
        entry.set("name", entity.getName().getString());
        entry.set("x", entity.getX());
        entry.set("y", entity.getY());
        entry.set("z", entity.getZ());
        entry.set("type", entity.getType().toString());
        entry.set("isPlayer", LuaValue.valueOf(entity instanceof PlayerEntity));
        if (entity instanceof LivingEntity living) {
            entry.set("health", living.getHealth());
            entry.set("maxHealth", living.getMaxHealth());
        }
        return entry;
    }

    static boolean hasLineOfSight(PlayerEntity self, Entity target) {
        var world = mc().world;
        if (world == null) return false;

        Vec3d eyePos = self.getCameraPosVec(1.0f);
        Vec3d targetPos = target.getBoundingBox().getCenter();
        double distance = eyePos.distanceTo(targetPos);
        if (distance > 128.0) return false;

        RaycastContext context = new RaycastContext(eyePos, targetPos,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, self);
        HitResult hit = world.raycast(context);
        if (hit.getType() == HitResult.Type.MISS) return true;
        return eyePos.distanceTo(hit.getPos()) >= distance - 0.3;
    }

    // ---- modules -----------------------------------------------------

    // доступ к своим модулям/настройкам - тумблер модуля или ползунок не заставит
    // игрока что-то делать в мире, так что это не то же самое, что attack()/setYaw()
    private static void installModulesTable(Globals globals) {
        LuaTable modules = new LuaTable();

        modules.set("getAll", varArgFn(args -> {
            LuaTable result = new LuaTable();
            int i = 1;
            for (ModuleStructure m : allModules()) {
                LuaTable entry = new LuaTable();
                entry.set("name", m.getName());
                entry.set("category", m.getCategory().getReadableName());
                entry.set("enabled", LuaValue.valueOf(m.isState()));
                result.set(i++, entry);
            }
            return result;
        }));

        modules.set("isEnabled", varArgFn(args -> {
            ModuleStructure m = findModule(args.checkjstring(1));
            return m == null ? LuaValue.FALSE : LuaValue.valueOf(m.isState());
        }));

        modules.set("setEnabled", varArgFn(args -> {
            ModuleStructure m = findModule(args.checkjstring(1));
            if (m != null) m.setState(args.checkboolean(2));
            return LuaValue.NONE;
        }));

        modules.set("getSettings", varArgFn(args -> {
            ModuleStructure m = findModule(args.checkjstring(1));
            LuaTable result = new LuaTable();
            if (m == null) return result;

            int i = 1;
            for (Setting setting : m.settings()) {
                if (!setting.isVisible()) continue;
                result.set(i++, describeSetting(setting));
            }
            return result;
        }));

        modules.set("setSetting", varArgFn(args -> {
            ModuleStructure m = findModule(args.checkjstring(1));
            if (m == null) return LuaValue.FALSE;
            Setting setting = m.get(args.checkjstring(2));
            if (setting == null) return LuaValue.FALSE;
            return LuaValue.valueOf(applySettingValue(setting, args.arg(3)));
        }));

        globals.set("modules", modules);
    }

    private static java.util.List<ModuleStructure> allModules() {
        return Initialization.getInstance().getManager().getModuleRepository().allModules();
    }

    private static ModuleStructure findModule(String name) {
        for (ModuleStructure m : allModules()) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    private static LuaTable describeSetting(Setting setting) {
        LuaTable t = new LuaTable();
        t.set("name", setting.getName());
        t.set("key", setting.getConfigKey());

        if (setting instanceof BooleanSetting b) {
            t.set("type", "boolean");
            t.set("value", LuaValue.valueOf(b.isValue()));
        } else if (setting instanceof SliderSettings s) {
            t.set("type", "slider");
            t.set("value", s.getValue());
            t.set("min", s.getMin());
            t.set("max", s.getMax());
        } else if (setting instanceof ColorSetting c) {
            t.set("type", "color");
            t.set("value", c.getColor());
        } else if (setting instanceof SelectSetting sel) {
            t.set("type", "select");
            t.set("value", sel.getSelected());
            LuaTable options = new LuaTable();
            int oi = 1;
            for (String opt : sel.getList()) options.set(oi++, opt);
            t.set("options", options);
        } else {
            t.set("type", "unknown");
        }
        return t;
    }

    private static boolean applySettingValue(Setting setting, LuaValue value) {
        if (setting instanceof BooleanSetting b) {
            b.setValue(value.toboolean());
            return true;
        }
        if (setting instanceof SliderSettings s) {
            s.setValue((float) value.todouble());
            return true;
        }
        if (setting instanceof ColorSetting c) {
            c.setColor((int) value.tolong());
            return true;
        }
        if (setting instanceof SelectSetting sel) {
            sel.setSelected(value.tojstring());
            return true;
        }
        return false;
    }

    // ---- gui -------------------------------------------------------------

    // позиция мыши имеет смысл только когда открыт реальный Screen (курсор освобождён из игры) -
    // isScreenOpen() позволяет скрипту так же проверять это перед своим drag/click, как это делает HUD
    private static void installGuiTable(Globals globals) {
        LuaTable gui = new LuaTable();

        gui.set("isScreenOpen", varArgFn(args -> LuaValue.valueOf(mc().currentScreen != null)));
        gui.set("isOpen", varArgFn(args -> LuaValue.valueOf(mc().currentScreen instanceof LuaGuiScreen)));

        gui.set("open", varArgFn(args -> {
            if (mc().currentScreen == null) mc().setScreen(new LuaGuiScreen());
            return LuaValue.NONE;
        }));

        gui.set("close", varArgFn(args -> {
            if (mc().currentScreen instanceof LuaGuiScreen) mc().setScreen(null);
            return LuaValue.NONE;
        }));

        globals.set("gui", gui);
    }

    // ---- module (this script's own entry in the Lua ClickGUI panel) ----

    // скрипт может добавить себе настоящие настройки (те же классы, что у обычных модулей),
    // и они сами отрисуются в ClickGUI, а скрипт просто читает их текущее значение
    private static void installModuleSelfTable(Globals globals, LuaScriptModule ownModule) {
        LuaTable module = new LuaTable();

        module.set("addBoolean", varArgFn(args -> {
            String key = args.checkjstring(1);
            boolean def = args.checkboolean(2);
            ownModule.settings(new BooleanSetting(key, "").setValue(def));
            return LuaValue.NONE;
        }));

        module.set("addSlider", varArgFn(args -> {
            String key = args.checkjstring(1);
            float min = (float) args.checkdouble(2);
            float max = (float) args.checkdouble(3);
            float def = (float) args.checkdouble(4);
            SliderSettings setting = new SliderSettings(key, "").range(min, max);
            setting.setValue(def);
            ownModule.settings(setting);
            return LuaValue.NONE;
        }));

        module.set("addColor", varArgFn(args -> {
            String key = args.checkjstring(1);
            int def = (int) args.checklong(2);
            ownModule.settings(new ColorSetting(key, "").value(def));
            return LuaValue.NONE;
        }));

        module.set("addSelect", varArgFn(args -> {
            String key = args.checkjstring(1);
            LuaTable options = args.checktable(2);
            String[] values = new String[options.length()];
            for (int i = 1; i <= options.length(); i++) values[i - 1] = options.get(i).tojstring();
            ownModule.settings(new SelectSetting(key, "").value(values));
            return LuaValue.NONE;
        }));

        module.set("get", varArgFn(args -> {
            String key = args.checkjstring(1);
            Setting setting = ownModule.get(key);
            if (setting == null) return LuaValue.NIL;
            if (setting instanceof BooleanSetting b) return LuaValue.valueOf(b.isValue());
            if (setting instanceof SliderSettings s) return LuaValue.valueOf(s.getValue());
            if (setting instanceof ColorSetting c) return LuaValue.valueOf(c.getColor());
            if (setting instanceof SelectSetting sel) return LuaValue.valueOf(sel.getSelected());
            return LuaValue.NIL;
        }));

        module.set("isEnabled", varArgFn(args -> LuaValue.valueOf(ownModule.isEnabled())));

        globals.set("module", module);
    }

    // ---- mouse ---------------------------------------------------------

    // отдаём только позицию и состояние кнопки - хватит на кликабельные кнопки/окна,
    // но это просто чтение ввода, а не управление игроком
    private static void installMouseTable(Globals globals) {
        LuaTable mouse = new LuaTable();

        mouse.set("getX", varArgFn(args -> LuaValue.valueOf(fixedMouseX())));
        mouse.set("getY", varArgFn(args -> LuaValue.valueOf(fixedMouseY())));

        mouse.set("isButtonDown", varArgFn(args -> {
            int button = (int) args.checklong(1);
            long handle = mc().getWindow().getHandle();
            boolean down = GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
            return LuaValue.valueOf(down);
        }));

        globals.set("mouse", mouse);
    }

    // ---- keyboard --------------------------------------------------------

    // та же логика, что и с мышью: чтение клавиш даёт скрипту хоткеи для своего UI,
    // но не даёт управлять игроком
    private static void installKeyboardTable(Globals globals) {
        LuaTable keyboard = new LuaTable();

        keyboard.set("isKeyDown", varArgFn(args -> {
            int key = (int) args.checklong(1);
            long handle = mc().getWindow().getHandle();
            return LuaValue.valueOf(GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS);
        }));

        globals.set("keyboard", keyboard);
    }

    // ---- storage ---------------------------------------------------------

    // хранилище ключ-значение, свой json на каждый скрипт - единственное исключение из
    // запрета на файловый доступ, и оно строго ограничено файлом самого скрипта
    private static void installStorageTable(Globals globals, File scriptFile) {
        LuaStorage storage = new LuaStorage(scriptFile);
        LuaTable table = new LuaTable();

        table.set("get", varArgFn(args -> {
            JsonElement el = storage.get(args.checkjstring(1));
            if (el == null || el.isJsonNull()) return LuaValue.NIL;
            JsonPrimitive prim = el.getAsJsonPrimitive();
            if (prim.isBoolean()) return LuaValue.valueOf(prim.getAsBoolean());
            if (prim.isNumber()) return LuaValue.valueOf(prim.getAsDouble());
            return LuaValue.valueOf(prim.getAsString());
        }));

        table.set("set", varArgFn(args -> {
            String key = args.checkjstring(1);
            LuaValue value = args.arg(2);
            if (value.isboolean()) storage.setBoolean(key, value.toboolean());
            else if (value.isnumber()) storage.setNumber(key, value.todouble());
            else storage.setString(key, value.tojstring());
            return LuaValue.NONE;
        }));

        table.set("remove", varArgFn(args -> {
            storage.remove(args.checkjstring(1));
            return LuaValue.NONE;
        }));

        globals.set("storage", table);
    }

    // берём Mouse.getScaledX/Y и пересчитываем пропорционально в масштаб Render2D -
    // раньше был свой велосипед с неверным scale factor, из-за чего ничего не кликалось
    private static float fixedMouseX() {
        var window = mc().getWindow();
        double scaledX = mc().mouse.getScaledX(window);
        double scaledWidth = window.getScaledWidth();
        if (scaledWidth <= 0) return 0f;
        return (float) (scaledX / scaledWidth * Render2D.getFixedScaledWidth());
    }

    private static float fixedMouseY() {
        var window = mc().getWindow();
        double scaledY = mc().mouse.getScaledY(window);
        double scaledHeight = window.getScaledHeight();
        if (scaledHeight <= 0) return 0f;
        return (float) (scaledY / scaledHeight * Render2D.getFixedScaledHeight());
    }

    // ---- helper: wrap a lambda taking Varargs as a Lua VarArgFunction -----

    private interface LuaFn {
        LuaValue call(org.luaj.vm2.Varargs args);
    }

    private static VarArgFunction varArgFn(LuaFn fn) {
        return new VarArgFunction() {
            @Override
            public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                return fn.call(args);
            }
        };
    }
}
