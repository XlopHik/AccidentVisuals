package accident.mixin;

import accident.Initialization;
import accident.util.server.SyncManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

@Mixin(PlayerListHud.class)
public abstract class MixinPlayerListHud {

    @Unique
    private static final Identifier SYNC_ICON =
            Identifier.of("accident", "textures/gui/logo.png");

    @Unique
    private final Set<String> accidentIconUsers = new HashSet<>();

    @Unique
    private boolean accidentFilterApplied = false;

    @Unique
    private static SyncManager getSyncManager() {
        if (Initialization.getInstance() == null) return null;
        if (Initialization.getInstance().getManager() == null) return null;
        return Initialization.getInstance().getManager().getSyncManager();
    }

    // Применяем фильтр через рефлексию
    private void applyTextureFilter() {
        if (accidentFilterApplied) return;
        try {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(SYNC_ICON);

            // Используем рефлексию для доступа к protected полю sampler
            Field samplerField = AbstractTexture.class.getDeclaredField("sampler");
            samplerField.setAccessible(true);

            // Создаём новый сэмплер с LINEAR фильтрацией
            var newSampler = RenderSystem.getSamplerCache().get(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR,
                    FilterMode.LINEAR,
                    false
            );

            samplerField.set(texture, newSampler);
            accidentFilterApplied = true;
        } catch (Exception ignored) {}
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(DrawContext context, int scaledWindowWidth,
                              Scoreboard scoreboard, ScoreboardObjective objective,
                              CallbackInfo ci) {
        applyTextureFilter();

        accidentIconUsers.clear();
        SyncManager sm = getSyncManager();
        if (sm == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;

        for (PlayerListEntry entry : mc.player.networkHandler.getListedPlayerListEntries()) {
            if (entry.getProfile() != null && entry.getProfile().name() != null) {
                String name = entry.getProfile().name();
                if (sm.isUser(name)) {
                    accidentIconUsers.add(name);
                }
            }
        }
    }

    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Math;min(II)I",
                    ordinal = 0),
            index = 0
    )
    private int expandRowWidth(int value) {
        if (!accidentIconUsers.isEmpty()) {
            return value + 10;
        }
        return value;
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V")
    )
    private void wrapNickDraw(DrawContext context, TextRenderer renderer,
                              Text text, int x, int y, int color,
                              Operation<Void> original) {
        if (!accidentIconUsers.isEmpty()) {
            String plain = text.getString();
            for (String name : accidentIconUsers) {
                if (plain.contains(name)) {
                    // Рисуем иконку 16x16 (увеличили размер)
                    context.drawTexture(
                            RenderPipelines.GUI_TEXTURED,
                            SYNC_ICON,
                            x - 2, y - 4,
                            0, 0,
                            12, 12,     // рисуем 12x12 (масштабируем)
                            16, 16      // реальный размер текстуры 16x16
                    );
                    original.call(context, renderer, text, x + 12, y, color);
                    return;
                }
            }
        }
        original.call(context, renderer, text, x, y, color);
    }
}