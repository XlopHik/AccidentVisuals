package accident.manager;

import lombok.Getter;
import accident.client.draggables.HudManager;
import accident.command.CommandManager;
import accident.events.api.EventManager;
import accident.modules.module.*;
import accident.screens.clickgui.ClickGui;
import accident.util.config.ConfigSystem;
import accident.util.config.impl.bind.BindConfig;
import accident.util.config.impl.prefix.PrefixConfig;
import accident.util.config.impl.proxy.ProxyConfig;
import accident.util.modules.ModuleProvider;
import accident.util.modules.ModuleSwitcher;
import accident.util.render.shader.RenderCore;
import accident.util.render.shader.Scissor;
import accident.util.render.font.FontInitializer;
import accident.util.repository.macro.MacroRepository;
import accident.util.repository.way.WayRepository;
import accident.util.server.SyncManager;
import accident.util.tps.TPSCalculate;

@Getter
public class Manager {
    private EventManager eventManager;
    private RenderCore renderCore;
    private Scissor scissor;
    private ModuleProvider moduleProvider;
    private ModuleRepository moduleRepository;
    private ModuleSwitcher moduleSwitcher;
    private ClickGui clickgui;
    private ConfigSystem configSystem;
    private CommandManager commandManager;
    private TPSCalculate tpsCalculate;
    public static ServerManager serverManager;
    private HudManager hudManager = new HudManager();
    private SyncManager syncManager;

    public void init() {
        MacroRepository.getInstance().init();
        WayRepository.getInstance().init();
        PrefixConfig.getInstance().load();
        ProxyConfig.getInstance().load();
        BindConfig.getInstance();

        FontInitializer.register();

        tpsCalculate = new TPSCalculate();
        serverManager = new ServerManager();

        clickgui = new ClickGui();
        eventManager = new EventManager();
        tpsCalculate = new TPSCalculate();

        syncManager = new SyncManager();

        renderCore = new RenderCore();
        scissor = new Scissor();
        hudManager = new HudManager();
        moduleRepository = new ModuleRepository();
        moduleRepository.setup();
        hudManager.initElements();
        moduleProvider = new ModuleProvider(moduleRepository.modules());
        moduleSwitcher = new ModuleSwitcher(moduleRepository.modules(), eventManager);
        configSystem = new ConfigSystem();
        configSystem.init();
        commandManager = new CommandManager();
        commandManager.init();
    }
}