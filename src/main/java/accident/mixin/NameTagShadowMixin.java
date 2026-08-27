package accident.mixin;

import accident.Initialization;
import accident.util.server.SyncManager;
import accident.modules.impl.render.NameTags;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.LabelCommandRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LabelCommandRenderer.class)
public class NameTagShadowMixin {

    @Unique
    private static final Identifier SYNC_ICON =
            Identifier.of("accident", "textures/gui/logo.png");

    @Unique
    private static SyncManager getSyncManager() {
        if (Initialization.getInstance() == null) return null;
        if (Initialization.getInstance().getManager() == null) return null;
        return Initialization.getInstance().getManager().getSyncManager();
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V"
            )
    )
    private void wrapNameTagDraw(TextRenderer renderer, Text text, float x, float y, int color, boolean shadow,
                                 Matrix4f matrix, VertexConsumerProvider provider, TextRenderer.TextLayerType layerType,
                                 int backgroundColor, int light, Operation<Void> original) {

        // 1. Логика тени из твоего модуля
        NameTags module = NameTags.getInstance();
        boolean shouldShadow = (module != null && module.isState() && module.shadow.isValue());

        String plainText = text.getString();

        // Временное тестовое условие: если ник равен "TEST" или содержит его
        boolean isTestTarget = plainText.contains("TEST") || plainText.equalsIgnoreCase("TEST");

        // 2. Логика проверки пользователя (добавили || isTestTarget)
        SyncManager sm = getSyncManager();
        if (isTestTarget || (sm != null && (sm.isUser(plainText) || checkContainsUser(sm, plainText)))) {

            float iconSize = 10.0f;
            float iconX = x - iconSize - 2.0f;
            float iconY = y - 1.0f;

            RenderLayer renderLayer;
            if (layerType == TextRenderer.TextLayerType.SEE_THROUGH) {
                renderLayer = RenderLayers.textSeeThrough(SYNC_ICON);
            } else {
                renderLayer = RenderLayers.text(SYNC_ICON);
            }

            VertexConsumer buffer = provider.getBuffer(renderLayer);

            // Рисуем иконку
            drawVertexIcon(buffer, matrix, iconX, iconY, iconSize, light);

            // Отрисовка текста
            original.call(renderer, text, x, y, color, shouldShadow, matrix, provider, layerType, backgroundColor, light);
            return;
        }

        original.call(renderer, text, x, y, color, shouldShadow, matrix, provider, layerType, backgroundColor, light);
    }

    @Unique
    private boolean checkContainsUser(SyncManager sm, String text) {
        for (String word : text.split(" ")) {
            String clean = word.replaceAll("[^a-zA-Z0-9_]", "");
            if (sm.isUser(clean)) return true;
        }
        return false;
    }

    @Unique
    private void drawVertexIcon(VertexConsumer buffer, Matrix4f matrix, float x, float y, float size, int light) {
        buffer.vertex(matrix, x, y + size, 0.0f).color(255, 255, 255, 255).texture(0.0f, 1.0f).light(light);
        buffer.vertex(matrix, x + size, y + size, 0.0f).color(255, 255, 255, 255).texture(1.0f, 1.0f).light(light);
        buffer.vertex(matrix, x + size, y, 0.0f).color(255, 255, 255, 255).texture(1.0f, 0.0f).light(light);
        buffer.vertex(matrix, x, y, 0.0f).color(255, 255, 255, 255).texture(0.0f, 0.0f).light(light);
    }
}