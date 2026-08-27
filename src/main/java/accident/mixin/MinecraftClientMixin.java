package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.session.Session;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.Initialization;
import accident.events.api.EventManager;
import accident.events.impl.GameLeftEvent;
import accident.events.impl.HotBarUpdateEvent;
import accident.events.impl.SetScreenEvent;
import accident.modules.impl.hud.Hud;
import accident.screens.clickgui.ClickGui;
import accident.screens.menu.MainMenuScreen;
import accident.util.config.ConfigSystem;
import accident.util.render.font.FontRenderer;
import accident.util.session.SessionChanger;
import accident.util.window.WindowStyle;

import static accident.IMinecraft.mc;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Shadow
    @Final
    public GameRenderer gameRenderer;

    @Unique
    private int titleTick = 0;

    @Shadow
    public ClientWorld world;

    private static boolean fontsInitialized = false;

    @Shadow
    @Mutable
    private Session session;

    private void setSession(Session newSession) {
        this.session = newSession;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        new Initialization().init();
        SessionChanger.setSessionSetter(this::setSession);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        ConfigSystem configSystem = ConfigSystem.getInstance();
        if (configSystem != null) {
            configSystem.shutdown();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof ProgressScreen) return;
        if (!fontsInitialized && screen != null) {
            try {
                FontRenderer fontRenderer = Initialization.getInstance().getManager().getRenderCore().getFontRenderer();
                if (fontRenderer != null && !fontRenderer.isInitialized()) {
                    fontRenderer.initialize();
                    fontsInitialized = true;
                }
            } catch (Exception ignored) {}
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void redirectTitleScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof ProgressScreen) return;
        if (screen instanceof TitleScreen && !(screen instanceof MainMenuScreen)) {
            ci.cancel();
            mc.setScreen(new MainMenuScreen());
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    private void onDisconnect(Screen screen, boolean transferring, CallbackInfo info) {
        if (world != null) {
            EventManager.callEvent(GameLeftEvent.get());
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (Initialization.getInstance() == null || Initialization.getInstance().getManager() == null) return;

        if (Initialization.getInstance().getManager().getSyncManager() != null) {
            Initialization.getInstance().getManager().getSyncManager().onUpdate();
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        MinecraftClient client = (MinecraftClient)(Object)this;
        titleTick++;

        String base = "AccidentVisuals - best visuals!    ";
        int offset = (titleTick / 3) % base.length();
        String rotated = (base + base).substring(offset, offset + base.length());
        client.getWindow().setTitle(rotated);

        Hud hud = Hud.getInstance();
        if (hud != null && hud.isState()) {
            if (Initialization.getInstance().getManager().getHudManager() != null) {
                Initialization.getInstance().getManager().getHudManager().tick();
            }
        }
    }

    @Inject(method = "setScreen", at = @At(value = "HEAD"), cancellable = true)
    public void setScreenHook(Screen screen, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;

        if (client.currentScreen instanceof ClickGui clickGui) {
            if (clickGui.isClosing() && screen == null) {
                ci.cancel();
                return;
            }
        }

        if (screen instanceof ProgressScreen) return;

        SetScreenEvent event = new SetScreenEvent(screen);
        EventManager.callEvent(event);

        Initialization instance = Initialization.getInstance();

        Screen eventScreen = event.getScreen();
        if (screen != eventScreen) {
            mc.setScreen(eventScreen);
            ci.cancel();
        }
    }

    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void getWindowTitle(CallbackInfoReturnable<String> cir) {
        String base = "AccidentVisuals - best visuals!    ";
        int offset = (titleTick / 3) % base.length();
        cir.setReturnValue((base + base).substring(offset, offset + base.length()));
    }

    @Inject(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getInventory()Lnet/minecraft/entity/player/PlayerInventory;"), cancellable = true)
    public void handleInputEventsHook(CallbackInfo ci) {
        HotBarUpdateEvent event = new HotBarUpdateEvent();
        EventManager.callEvent(event);
        if (event.isCancelled()) ci.cancel();
    }

    @Inject(method = "onResolutionChanged", at = @At("TAIL"))
    private void applyDarkMode(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        WindowStyle.setDarkMode(client.getWindow().getHandle());
    }
}