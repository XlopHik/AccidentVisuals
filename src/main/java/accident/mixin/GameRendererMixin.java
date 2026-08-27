package accident.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.Initialization;
import accident.client.draggables.Drag;
import accident.events.api.EventManager;
import accident.events.impl.FovEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.impl.render.AspectRatio;
import accident.modules.impl.hud.Hud;
import accident.modules.impl.render.NoRender;
import accident.screens.clickgui.ClickGui;
import accident.util.render.Render3D;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private Camera camera;
    @Shadow @Final GuiRenderState guiState;
    @Shadow @Final private GuiRenderer guiRenderer;
    @Shadow @Final private FogRenderer fogRenderer;
    @Unique private final MatrixStack matrices = new MatrixStack();
    @Shadow protected abstract void bobView(MatrixStack matrices, float tickDelta);
    @Shadow protected abstract void tiltViewWhenHurt(MatrixStack matrices, float tickDelta);
    @Shadow public abstract float getFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "close", at = @At("RETURN"))
    private void onClose(CallbackInfo ci) {
        if (Initialization.getInstance() != null && Initialization.getInstance().getManager() != null && Initialization.getInstance().getManager().getRenderCore() != null) {
            Initialization.getInstance().getManager().getRenderCore().close();
        }
    }

    @ModifyExpressionValue(method = "getFov", at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I", remap = false))
    private int hookGetFov(int original) {
        FovEvent event = new FovEvent();
        EventManager.callEvent(event);
        if (event.isCancelled()) return event.getFov();
        return original;
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void onGetBasicProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        float customRatio = AspectRatio.getInstance().getRatio();

        if (customRatio > 0) {
            Matrix4f matrix4f = new Matrix4f();
            float fovRad = fovDegrees * ((float)Math.PI / 180F);

            GameRenderer renderer = (GameRenderer)(Object)this;
            float farPlane = renderer.getFarPlaneDistance();

            cir.setReturnValue(matrix4f.perspective(fovRad, customRatio, 0.05F, farPlane));
        }
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = {"ldc=hand"}))
    public void hookWorldRender(RenderTickCounter tickCounter, CallbackInfo ci,
                                @Local(ordinal = 0) Matrix4f projection,
                                @Local(ordinal = 1) Matrix4f view,
                                @Local(ordinal = 0) float tickDelta,
                                @Local MatrixStack matrixStack) {

        if (client.world == null || client.player == null) return;

        MatrixStack worldSpaceStack = new MatrixStack();
        worldSpaceStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        worldSpaceStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

        Render3D.lastProjMat.set(client.gameRenderer.getBasicProjectionMatrix(getFov(camera, tickDelta, true)));
        Render3D.lastModMat.set(RenderSystem.getModelViewMatrix());
        Render3D.lastWorldSpaceMatrix.set(worldSpaceStack.peek().getPositionMatrix());

        Render3D.setLastWorldSpaceEntry(matrixStack.peek());
        Render3D.setLastTickDelta(tickDelta);
        Render3D.setLastCameraPos(camera.getCameraPos());
        Render3D.setLastCameraRotation(new Quaternionf(camera.getRotation()));

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix().mul(view);

        matrices.push();
        tiltViewWhenHurt(matrices, camera.getLastTickProgress());
        if (client.options.getBobView().getValue()) {
            bobView(matrices, camera.getLastTickProgress());
        }
        modelViewStack.mul(matrices.peek().getPositionMatrix().invert(new Matrix4f()));
        matrices.pop();

        var vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();

        WorldRenderEvent event = new WorldRenderEvent(matrixStack, tickDelta, camera, vertexConsumers);
        EventManager.callEvent(event);
        Render3D.onWorldRender(event);

        modelViewStack.popMatrix();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isState() && noRender.modeSetting.isSelected("Damage")) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "renderWorld", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0))
    private float onNauseaDistortion(float original) {
        NoRender noRender = NoRender.getInstance();
        if (noRender != null && noRender.isState() && noRender.modeSetting.isSelected("Nausea")) {
            return 0.0F;
        }
        return original;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", shift = At.Shift.AFTER))
    private void afterGuiRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (client.world == null || client.player == null) return;
        if (isLoadingScreen(client.currentScreen)) return;
        if (client.getOverlay() != null) return;
        if (!shouldRenderOnTop(client.currentScreen)) return;

        guiState.clear();

        int mouseX = (int) client.mouse.getScaledX(client.getWindow());
        int mouseY = (int) client.mouse.getScaledY(client.getWindow());
        float tickDelta = tickCounter.getTickProgress(false);

        DrawContext context = new DrawContext(client, guiState, mouseX, mouseY);

        Hud hud = Hud.getInstance();
        if (hud != null && hud.isState()) {
            boolean isChatScreen = client.currentScreen instanceof ChatScreen;
            Drag.onDraw(context, mouseX, mouseY, tickDelta, isChatScreen);
        }

        if (client.currentScreen instanceof ClickGui clickGui) {
            // GuiEcho сам оборачивает захват внутри renderGui(), здесь ничего дополнительно не нужно
            clickGui.renderOverlay(context, tickCounter);
        }

        guiRenderer.render(fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
    }

    @Unique
    private boolean shouldRenderOnTop(Screen screen) {
        if (screen == null) return true;
        if (screen instanceof ClickGui) return true;
        if (screen instanceof ChatScreen) return true;
        return false;
    }

    @Unique
    private boolean isLoadingScreen(Screen screen) {
        if (screen == null) return false;
        String className = screen.getClass().getSimpleName().toLowerCase();
        String fullName = screen.getClass().getName().toLowerCase();
        if (className.contains("loading")) return true;
        if (className.contains("progress")) return true;
        if (className.contains("connecting")) return true;
        if (className.contains("downloading")) return true;
        if (className.contains("terrain")) return true;
        if (className.contains("generating")) return true;
        if (className.contains("saving")) return true;
        if (className.contains("reload")) return true;
        if (className.contains("resource")) return true;
        if (className.contains("pack")) return true;
        if (fullName.contains("mojang")) return true;
        return false;
    }
}