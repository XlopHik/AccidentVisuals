package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import accident.util.server.ServerArrangement;
import accident.util.server.ServerMeta;

@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryMixin {

    @Shadow
    public abstract ServerInfo getServer();

    // ванильные стрелки меняют позиции в списке детей виджета, а с папками это уже не сохранённый порядок -
    // вместо этого просто двигаем сервер на шаг по отображаемому порядку
    @Inject(method = "swapEntries", at = @At("HEAD"), cancellable = true)
    private void accident$moveOneStep(int from, int to, CallbackInfo ci) {
        ci.cancel();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof MultiplayerScreen screen) {
            ServerArrangement.step(screen.getServerList(), getServer(), to < from ? -1 : 1);
        }
    }

    /** A bar down the left edge so a pinned server reads as pinned. */
    @Inject(method = "render", at = @At("TAIL"))
    private void accident$drawPin(DrawContext context, int mouseX, int mouseY,
                                  boolean hovered, float tickDelta, CallbackInfo ci) {
        ServerInfo server = getServer();
        if (server == null || !ServerMeta.isPinned(server.address)) return;

        MultiplayerServerListWidget.ServerEntry self =
                (MultiplayerServerListWidget.ServerEntry) (Object) this;

        context.fill(self.getX() - 4, self.getY() + 1,
                self.getX() - 1, self.getY() + self.getHeight() - 1, 0xFFB9BEFF);
    }
}
