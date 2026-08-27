package accident.modules.impl.render;

import accident.events.api.EventHandler;
import accident.events.impl.TickEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;


import accident.util.discord.DiscordEventHandlers;
import accident.util.discord.DiscordRPC;
import accident.util.discord.DiscordRichPresence;

public class DiscordRPCModule extends ModuleStructure {

    private static final String DISCORD_APP_ID = "1289903287751147562";
    private static final String BUILD_TYPE = "Release";
    private static final String LARGE_IMAGE_URL = "https://raw.githubusercontent.com/XlopHik/accidentgif/main/17в.gif";

    private static boolean started = false;
    private static boolean manuallyDisabled = false;
    private static Thread rpcThread;
    private static DiscordRichPresence presence = new DiscordRichPresence();
    private static boolean renderReady = false;

    public DiscordRPCModule() {
        super("accident.module.discordrpc.name", "accident.module.discordrpc.desc", ModuleCategory.RENDER);
    }

    @Override
    public boolean activate() {
        manuallyDisabled = false;
        startRPC();
        return super.activate();
    }

    @Override
    public boolean deactivate() {
        manuallyDisabled = true;
        stopRPC();
        super.deactivate();
        return false;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (isState() && !started && renderReady && !manuallyDisabled) {
            startRPC();
        }
    }

    public void startRPC() {
        if (started || manuallyDisabled) return;

        new Thread(() -> {
            try {
                waitForTessellator();
                initializeRPC();
            } catch (Exception e) {
                started = false;
            }
        }).start();
    }

    private void waitForTessellator() throws InterruptedException {
        int attempts = 0;
        while (attempts < 30) {
            try {
                net.minecraft.client.render.Tessellator.getInstance();
                renderReady = true;
                return;
            } catch (IllegalStateException e) {
                Thread.sleep(1000);
                attempts++;
            }
        }
    }

    private void initializeRPC() {
        if (manuallyDisabled || !isState()) {
            started = false;
            return;
        }

        started = true;

        try {
            DiscordEventHandlers handlers = new DiscordEventHandlers();

            handlers.ready = (user) -> {
                if (user.avatar != null && !user.avatar.isEmpty()) {
                    accident.util.render.DiscordAvatarManager.loadAvatar(user.userId, user.avatar);
                }
            };

            DiscordRPC.INSTANCE.Discord_Initialize(DISCORD_APP_ID, handlers, true, "");

            presence.startTimestamp = System.currentTimeMillis() / 1000L;

            try {
                presence.largeImageText = mc.getSession().getUsername();
            } catch (Exception e) {
                presence.largeImageText = "Accident";
            }

            DiscordRPC.INSTANCE.Discord_UpdatePresence(presence);

            rpcThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted() && isState() && !manuallyDisabled) {
                    try {
                        DiscordRPC.INSTANCE.Discord_RunCallbacks();

                        presence.details = "Build: " + BUILD_TYPE;
                        presence.state = "Best visuals";

                        try {
                            presence.largeImageText = mc.getSession().getUsername();
                        } catch (Exception e) {
                            presence.largeImageText = "Accident";
                        }

                        presence.smallImageText = "";
                        presence.smallImageKey = "";

                        presence.button_label_1 = "Discord";
                        presence.button_url_1 = "https://discord.gg/sNKPQn85y6";

                        presence.button_label_2 = "Telegram";
                        presence.button_url_2 = "https://t.me/accidentvisuals";

                        presence.largeImageKey = LARGE_IMAGE_URL;

                        DiscordRPC.INSTANCE.Discord_UpdatePresence(presence);
                        Thread.sleep(2000L);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (!isState() || manuallyDisabled) {
                    stopRPC();
                }
            }, "DiscordRPC-Thread");

            rpcThread.start();

        } catch (Exception e) {
            started = false;
        }
    }

    private void stopRPC() {
        started = false;
        if (rpcThread != null && !rpcThread.isInterrupted()) {
            rpcThread.interrupt();
            rpcThread = null;
        }
        try {
            DiscordRPC.INSTANCE.Discord_Shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


