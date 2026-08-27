package accident.mixin;

import accident.modules.impl.render.Charms;
import accident.util.render.EntityRenderInterceptor;
import accident.util.render.shader.CharmsRenderer;
import accident.util.render.shader.GlassEntityRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.IMinecraft;
import accident.modules.impl.render.NoRender;
import accident.util.render.shader.SkyRenderer;

@Mixin(value = WorldRenderer.class, priority = 1100)
public class WorldRendererMixin implements IMinecraft {

    @Shadow
    @Final
    private EntityRenderManager entityRenderManager;
    @Shadow @Final private RenderDispatcher entityRenderDispatcher;
    @Shadow @Final private BufferBuilderStorage bufferBuilders;

    @org.spongepowered.asm.mixin.injection.Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
    private void onHasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        NoRender noRender = NoRender.getInstance();
        if (noRender == null || !noRender.isState()) return;

        Entity entity = camera.getFocusedEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return;

        if (noRender.modeSetting.isSelected("Bad Effects") && livingEntity.hasStatusEffect(StatusEffects.BLINDNESS)) {
            cir.setReturnValue(false);
        } else if (noRender.modeSetting.isSelected("Darkness") && livingEntity.hasStatusEffect(StatusEffects.DARKNESS)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void onRenderTail(
            ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci
    ) {
        SkyRenderer.getInstance().captureFrameMatrices(positionMatrix, projectionMatrix);
    }

    // рисуем небо здесь, чтобы оно было под остальным кадром, а не поверх готового
    @Inject(
            method = "method_62215(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/render/state/SkyRenderState;Lnet/minecraft/client/render/SkyRendering;)V",
            at = @At("TAIL")
    )
    private static void onSkyPassEnd(GpuBufferSlice fogBuffer, SkyRenderState skyRenderState, SkyRendering skyRendering, CallbackInfo ci) {
        SkyRenderer.getInstance().renderSkyBackground();
    }

    @Inject(
            method = "method_62214(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/render/state/WorldRenderState;Lnet/minecraft/util/profiler/Profiler;Lorg/joml/Matrix4f;Lnet/minecraft/client/util/Handle;Lnet/minecraft/client/util/Handle;ZLnet/minecraft/client/util/Handle;Lnet/minecraft/client/util/Handle;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterEntityFlush(
            GpuBufferSlice fogBuffer, WorldRenderState worldRenderState, Profiler profiler,
            Matrix4f matrix, Handle<?> handle1, Handle<?> handle2, boolean flag,
            Handle<?> handle3, Handle<?> handle4,
            CallbackInfo ci
    ) {
        EntityRenderInterceptor interceptor = EntityRenderInterceptor.getInstance();
        if (!interceptor.hasPending()) return;

        Charms charmsMod = Charms.getInstance();
        if (charmsMod == null || !charmsMod.isState()) {
            interceptor.clear();
            return;
        }

        // entityVC is empty at this point: fully flushed by the draw() at offset 571.
        VertexConsumerProvider.Immediate entityVC = bufferBuilders.getEntityVertexConsumers();

        try {
            // ── 1. Capture "before" ───────────────────────────────────────────
            // тут в буфере только чужая геометрия, target ещё не попал в entityVC
            //
            // если capture не удался - метод не прерываем: сущности ещё не отрисованы,
            // finally их всё равно очистит, так что просто едем дальше без эффекта
            //
            // setEnabled дергаем каждый кадр, т.к. renderer включается только в activate()
            boolean captured = false;

            if (charmsMod.isShaderStyle()) {
                GlassEntityRenderer glass = GlassEntityRenderer.getInstance();
                if (glass != null) {
                    glass.setEnabled(true);
                    glass.resetMapFlag();
                    glass.captureSceneBeforeHands();
                    captured = glass.isCapturing();
                }
            } else {
                CharmsRenderer charms = CharmsRenderer.getInstance();
                if (charms != null) {
                    charms.setEnabled(true);
                    charms.resetMapFlag();
                    charms.captureSceneBefore();
                    captured = charms.isCapturing();
                }
            }

            // ── 2. Add target entity commands to the queue ────────────────────
            // replayAllForMask временно глушит fire и leash - иначе их геометрия
            // (квады огня, линии поводка) лезет в маску как часть сущности
            interceptor.replayAllForMask(entityRenderManager);

            // ── 3. Flush command queue (shadows, fire, labels, leash, …) ─────
            // composite теперь целиком тут, в шагах 4-6 - в RenderDispatcherMixin TAIL больше нет
            entityRenderDispatcher.render();

            // ── 4. Flush target entity geometry from CPU buffers → GPU ────────
            // entityVC.draw() отправляет геометрию из CPU-буфера в текущий framebuffer
            entityVC.draw();

            // ── 5. Capture "after" and composite the effect ───────────────────
            // теперь в буфере и target, и остальные - разница глубин даёт маску только по target
            if (captured) {
                if (charmsMod.isShaderStyle()) {
                    GlassEntityRenderer glass = GlassEntityRenderer.getInstance();
                    if (glass != null) {
                        charmsMod.pushGlassSettings(glass);
                        glass.captureSceneAfterHands();
                        glass.renderGlassEffect();
                        glass.clearNeedsEffect();
                    }
                } else {
                    CharmsRenderer charms = CharmsRenderer.getInstance();
                    if (charms != null) {
                        charmsMod.pushSettings(charms);
                        charms.captureSceneAfter();
                        charms.renderCharmsEffect();
                        charms.clearNeedsEffect();
                    }
                }
            }

            // nametags рендерим после composite - иначе вокруг текста будет обводка от маски
            interceptor.replayAllForLabels(entityRenderManager);
            entityRenderDispatcher.render();
            entityVC.draw();

        } finally {
            interceptor.clear();
        }
    }
}