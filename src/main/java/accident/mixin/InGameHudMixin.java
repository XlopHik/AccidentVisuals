package accident.mixin;

import accident.modules.impl.misc.SmoothAnimations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.IMinecraft;
import accident.Initialization;
import accident.events.api.EventManager;
import accident.events.impl.DrawEvent;
import accident.events.impl.HotbarItemRenderEvent;
import accident.modules.impl.render.Crosshair;
import accident.modules.impl.render.Hotbar;
import accident.modules.impl.hud.Hud;
import accident.modules.impl.render.NoRender;
import accident.screens.clickgui.ClickGui;
import accident.util.render.Render2D;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin implements IMinecraft {

    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private int richCurrentHotbarIndex = 0;

    @Unique private float tabSlideProgress = 0f;
    @Unique private boolean tabPressedThisFrame = false;
    @Unique private static final float TAB_SLIDE_OFFSET = -200f;

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbarStart(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        richCurrentHotbarIndex = 0;

        Hotbar hotbar = Initialization.getInstance().getManager().getModuleRepository().modules().stream()
                .filter(m -> m instanceof Hotbar && m.isState())
                .map(m -> (Hotbar) m)
                .findFirst()
                .orElse(null);

        if (hotbar != null && hotbar.isState()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void onRenderHealthBar(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        Hotbar hotbar = Hotbar.getInstance();
        if (hotbar != null && hotbar.isState() && hotbar.customHealthFood.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void onRenderFood(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        Hotbar hotbar = Hotbar.getInstance();
        if (hotbar != null && hotbar.isState() && hotbar.customHealthFood.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void onRenderArmor(DrawContext context, PlayerEntity player, int y, int i, int healthBarLines, int x, CallbackInfo ci) {
        Hotbar hotbar = Hotbar.getInstance();
        if (hotbar != null && hotbar.isState() && hotbar.customHealthFood.isValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void cancelVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Crosshair crosshair = Initialization.getInstance().getManager().getModuleRepository().modules().stream()
                .filter(m -> m instanceof Crosshair && m.isState())
                .map(m -> (Crosshair) m)
                .findFirst()
                .orElse(null);
        if (crosshair != null) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "renderHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V"
            )
    )
    private void onRenderHotbarItem(InGameHud instance, DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, Operation<Void> original) {
        int hotbarIndex = richCurrentHotbarIndex;

        if (richCurrentHotbarIndex < 9) {
            richCurrentHotbarIndex++;
        }

        HotbarItemRenderEvent event = new HotbarItemRenderEvent(stack, hotbarIndex);
        EventManager.callEvent(event);
        original.call(instance, context, x, y, tickCounter, player, event.getStack(), seed);
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderNauseaOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isState() && noRender.modeSetting.isSelected("Nausea")) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isState() && noRender.modeSetting.isSelected("Scoreboard")) {
            ci.cancel();
            return;
        }

        Hud hud = Hud.getInstance();
        boolean scoreBoardEnabled = false;
        var modules = accident.Initialization.getInstance().getManager().getModuleRepository().modules();
        for (var m : modules) {
            if (m instanceof accident.modules.impl.hud.ScoreBoard sb) {
                scoreBoardEnabled = sb.isEnabled();
                break;
            }
        }
        if (hud != null && hud.isState() && scoreBoardEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true)
    private void onRenderBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isState() && noRender.modeSetting.isSelected("BossBar")) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "renderPlayerList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/PlayerListHud;setVisible(Z)V"
            )
    )
    private void onSetVisible(PlayerListHud playerListHud, boolean visible, Operation<Void> original) {
        if (!visible && tabSlideProgress > 0.001f) {
            return;
        }
        original.call(playerListHud, visible);
    }

    @WrapOperation(
            method = "renderPlayerList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/PlayerListHud;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"
            )
    )
    private void onRenderPlayerList(PlayerListHud playerListHud, DrawContext context, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective objective, Operation<Void> original) {
        tabPressedThisFrame = true;

        float dt = SmoothAnimations.getDt();
        float smoothness = SmoothAnimations.factor(SmoothAnimations.getInstance().tabListSmoothness);
        if (smoothness <= 0f) {
            tabSlideProgress = 1f;
        } else {
            tabSlideProgress = (float) ((tabSlideProgress - 1f) * Math.pow(smoothness, dt) + 1f);
        }
        float eased = 1f - (float) Math.pow(1.0 - Math.min(tabSlideProgress, 1f), 3.0);
        float offset = TAB_SLIDE_OFFSET * (1f - eased);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0f, offset);
        original.call(playerListHud, context, scaledWidth, scoreboard, objective);
        context.getMatrices().popMatrix();
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderCustomHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (this.client.options.hudHidden) return;
        if (client.world == null || client.player == null) return;
        if (client.getOverlay() != null) return;

        Screen screen = client.currentScreen;
        if (isLoadingScreen(screen)) return;

        context.createNewRootLayer();
        Render2D.beginOverlay();
        accident.util.render.DrawTimes.frame();
        context.getMatrices().pushMatrix();

        DrawEvent event = new DrawEvent(context, drawEngine, tickCounter.getTickProgress(false));
        EventManager.callEvent(event);

        context.getMatrices().popMatrix();

        if (shouldRenderHud(screen)) {
            int mouseX    = (int) client.mouse.getScaledX(client.getWindow());
            int mouseY    = (int) client.mouse.getScaledY(client.getWindow());
            float tickDelta = tickCounter.getTickProgress(false);

            Hud hud = Hud.getInstance();
            if (hud != null && hud.isState() && Initialization.getInstance() != null
                    && Initialization.getInstance().getManager() != null
                    && Initialization.getInstance().getManager().getHudManager() != null) {
                Initialization.getInstance().getManager().getHudManager().render(context, tickDelta, mouseX, mouseY);
            }
        }

        Render2D.endOverlay();
    }

    @Unique
    private boolean shouldRenderHud(Screen screen) {
        if (screen == null) return true;
        if (screen instanceof ClickGui) return false;
        if (screen instanceof ChatScreen) return false;
        if (isLoadingScreen(screen)) return false;
        return true;
    }

    @Unique
    private boolean isLoadingScreen(Screen screen) {
        if (screen == null) return false;
        String className = screen.getClass().getSimpleName().toLowerCase();
        String fullName  = screen.getClass().getName().toLowerCase();
        if (className.contains("loading"))     return true;
        if (className.contains("progress"))    return true;
        if (className.contains("connecting"))  return true;
        if (className.contains("downloading")) return true;
        if (className.contains("terrain"))     return true;
        if (className.contains("generating"))  return true;
        if (className.contains("saving"))      return true;
        if (className.contains("reload"))      return true;
        if (className.contains("resource"))    return true;
        if (className.contains("pack"))        return true;
        if (fullName.contains("mojang"))       return true;
        return false;
    }
}