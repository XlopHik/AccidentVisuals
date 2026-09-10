package accident.modules.module;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import accident.modules.impl.misc.*;
import accident.modules.impl.movement.*;
import accident.modules.impl.render.*;
import accident.modules.impl.hud.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModuleRepository {
    List<ModuleStructure> moduleStructures = new ArrayList<>();
    List<ModuleStructure> hiddenModules = new ArrayList<>();
    Set<Class<? extends ModuleStructure>> registeredClasses = new HashSet<>();

    public void setup() {
        builder()
                .add(new Hud())
                .add(new HitModules())
                .add(new GuiEcho())
                .add(new BetterChat())
                .add(new Optimization())
                .add(new AspectRatio())
                .add(new KillEffect())
                .add(new LineGlyphs())
                .add(new NameTags())
                .add(new HitBoxes())
                .add(new CustomSky())
                .add(new ItemReplacer())
                .add(new CustomModels())
                .add(new FireFly())
                .add(new InventoryAnim())
                .add(new Trails())
                .add(new Wings())
                .add(new Crosshair())
                .add(new ClickGuiModule())
                .add(new SmallModel())
                .add(new JumpWave())
                .add(new Predictions())
                .add(new TntTimer())
                .add(new WorldParticles())
                .add(new Particles())
                .add(new GlassHands())
                .add(new DiscordRPCModule())
                .add(new FakePlayer())
                .add(new NoFog())
                .add(new Ambience())
                .add(new CursorTrail())
                .add(new ChinaHat())
                .add(new ClientSounds())
                .add(new Notify())
                .add(new TargetESP())
                .add(new BlockOverlay())
                .add(new JumpCircle())
                .add(new ChunkSaver())
                .add(new ItemScroller())
                .add(new GhostEffect())
                .add(new Hotbar())
                .add(new FreeLook())
                .add(new FullBright())
                .add(new CameraSettings())
                .add(new ItemPhysic())
                .add(new NoRender())
                .add(new ViewModel())
                .add(new SwingAnimation())
                .add(new SmoothAnimations())
                .add(new Charms())
                .add(new SeeInvisible())
                .add(new AutoSprint())
                .add(new Notifications())
                .add(new Watermark())
                .add(new TargetHud())
                .add(new ScoreBoard())
                .add(new Inventory())
                .add(new Potions())
                .add(new ArmorHud())
                .add(new TotemsHud())
                .add(new HotKeys())
                .add(new Info())
                .add(new Media())
                .add(new CoolDowns());
    }

    public ModuleBuilder builder() {
        return new ModuleBuilder(this);
    }

    // скриптовые модули (LuaScriptModule) - все один и тот же класс, по инстансу на .lua файл.
    // проверка уникальности в registerModule() рассчитана на один инстанс на класс и упадёт
    // на втором скрипте, поэтому у них свой отдельный путь регистрации
    public void addDynamicModule(ModuleStructure module) {
        moduleStructures.add(module);
    }

    public void removeDynamicModule(ModuleStructure module) {
        if (module.isState()) module.setState(false);
        moduleStructures.remove(module);
    }

    void registerModule(ModuleStructure module, boolean hidden) {
        Class<? extends ModuleStructure> clazz = module.getClass();
        if (registeredClasses.contains(clazz)) {
            throw new DuplicateModuleException(clazz.getSimpleName());
        }
        registeredClasses.add(clazz);
        if (hidden) {
            hiddenModules.add(module);
            module.setState(true);
        } else {
            moduleStructures.add(module);
        }
    }

    public List<ModuleStructure> modules() {
        return moduleStructures;
    }

    public List<ModuleStructure> hiddenModules() {
        return hiddenModules;
    }

    public List<ModuleStructure> allModules() {
        List<ModuleStructure> all = new ArrayList<>(moduleStructures);
        all.addAll(hiddenModules);
        return all;
    }
}
