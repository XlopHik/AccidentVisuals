package accident.util.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import accident.util.discord.DiscordEventHandlers;
import accident.util.discord.DiscordRPC;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class DiscordAvatarManager {

    private static final String APP_ID = "1289903287751147562";

    public static Identifier discordAvatarId = null;
    private static boolean isFetching = false;

    public static void init() {
        if (isFetching || discordAvatarId != null) return;
        isFetching = true;

        new Thread(() -> {
            try {
                DiscordEventHandlers handlers = new DiscordEventHandlers();

                handlers.ready = (user) -> {
                    if (user.avatar != null && !user.avatar.isEmpty()) {
                        loadAvatar(user.userId, user.avatar);
                    } else {
                    }
                };

                DiscordRPC.INSTANCE.Discord_Initialize(APP_ID, handlers, true, "");

                for (int i = 0; i < 30; i++) {
                    DiscordRPC.INSTANCE.Discord_RunCallbacks();
                    if (discordAvatarId != null) break;
                    Thread.sleep(500);
                }


            } catch (Exception e) {
            } finally {
                isFetching = false;
            }
        }, "Discord-Avatar-Fetcher").start();
    }

    public static void loadAvatar(String userId, String avatarHash) {
        String url = String.format("https://cdn.discordapp.com/avatars/%s/%s.png?size=128", userId, avatarHash);

        CompletableFuture.runAsync(() -> {
            try (InputStream is = new URL(url).openStream()) {
                NativeImage image = NativeImage.read(is);

                MinecraftClient.getInstance().execute(() -> {
                    Supplier<String> nameSupplier = () -> "discord_avatar_" + userId;

                    NativeImageBackedTexture texture = new NativeImageBackedTexture(nameSupplier, image);
                    discordAvatarId = Identifier.of("accident", "dynamic/discord_avatar");

                    MinecraftClient.getInstance().getTextureManager().registerTexture(discordAvatarId, texture);

                });
            } catch (Exception e) {
            }
        });
    }
}