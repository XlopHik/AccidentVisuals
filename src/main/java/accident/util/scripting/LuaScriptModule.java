package accident.util.scripting;

import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;

// сторона скрипта в ClickGUI - даёт ему запись в Lua-панели как у обычного модуля
// (чекбокс + настройки из module.addBoolean/addSlider/...), тумблер просто
// включает/выключает ScriptInstance.
//
// создаётся раньше своего ScriptInstance (Globals скрипта нужен этот модуль,
// чтобы установить таблицу `module` до запуска кода скрипта) - сам script
// прикручивается через setScript() перед тем как чанк реально запустится
public class LuaScriptModule extends ModuleStructure {

    private ScriptInstance script;

    public LuaScriptModule(String displayName) {
        super(displayName, ModuleCategory.LUA);
    }

    void setScript(ScriptInstance script) {
        this.script = script;
    }

    public ScriptInstance getScript() {
        return script;
    }

    @Override
    public boolean activate() {
        if (script != null) script.setEnabled(true);
        return false;
    }

    @Override
    public boolean deactivate() {
        if (script != null) script.setEnabled(false);
        return false;
    }
}
