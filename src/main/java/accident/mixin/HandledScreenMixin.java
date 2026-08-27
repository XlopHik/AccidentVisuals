package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.events.api.EventManager;
import accident.events.impl.HandledScreenEvent;
import accident.util.iface.IAnimatedScreen;
import accident.modules.impl.render.InventoryAnim;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> implements IAnimatedScreen {

    @Override public float accident$getAnimProgress() { return animProgress; }
    @Override public boolean accident$isAnimClosing() { return animClosing; }
    @Override public int accident$getX() { return x; }
    @Override public int accident$getY() { return y; }

    @Shadow public int backgroundWidth;
    @Shadow public int backgroundHeight;
    @Shadow @Nullable protected Slot focusedSlot;

    @Shadow protected int x;
    @Shadow protected int y;

    @Unique private float animProgress = 0f;
    @Unique private boolean animClosing = false;
    @Unique private boolean animBypassClose = false;
    @Unique private boolean animScheduledClose = false;
    @Unique private boolean animMainMatrixPushed = false;
    @Unique private boolean animBgMatrixPushed = false;
    @Unique private boolean animCursorHidden = false;

    @Unique private float fastLerp(float current, float target, float speed) {
        return current + (target - current) * Math.min(speed, 1f);
    }

    @Unique private float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return 1f - (float) Math.pow(1f - t, 3);
    }

    @Unique private float easeOutBack(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    @Unique private float applyEasing(float raw) {
        if (animClosing) return easeOutCubic(raw);
        return switch (InventoryAnim.getAnimType()) {
            case BOUNCE -> Math.max(0.001f, easeOutBack(raw));
            default     -> easeOutCubic(raw);
        };
    }

    @Unique private void applyTransform(DrawContext ctx, float v, int sw, int sh) {
        float cx = sw / 2f;
        float cy = sh / 2f;
        switch (InventoryAnim.getAnimType()) {
            case SCALE, BOUNCE -> {
                ctx.getMatrices().translate(cx, cy);
                ctx.getMatrices().scale(v, v);
                ctx.getMatrices().translate(-cx, -cy);
            }
            case SLIDE_UP    -> ctx.getMatrices().translate(0f,  sh * (1f - v));
            case SLIDE_DOWN  -> ctx.getMatrices().translate(0f, -sh * (1f - v));
            case SLIDE_LEFT  -> ctx.getMatrices().translate( sw * (1f - v), 0f);
            case SLIDE_RIGHT -> ctx.getMatrices().translate(-sw * (1f - v), 0f);
            case FLIP -> {
                ctx.getMatrices().translate(cx, cy);
                ctx.getMatrices().scale(v, 1f);
                ctx.getMatrices().translate(-cx, -cy);
            }
            case WARP -> {
                float scale = animClosing ? v : (1.15f - 0.15f * v);
                ctx.getMatrices().translate(cx, cy);
                ctx.getMatrices().scale(scale, scale);
                ctx.getMatrices().translate(-cx, -cy);
            }
            case GLITCH -> {
                float shake = (1f - v) * 35f * (float) Math.sin(v * 28f);
                float scale = 0.88f + 0.12f * v;
                ctx.getMatrices().translate(shake, 0f);
                ctx.getMatrices().translate(cx, cy);
                ctx.getMatrices().scale(scale, scale);
                ctx.getMatrices().translate(-cx, -cy);
            }
            default -> {}
        }
    }

    @Unique private boolean tickAnim(DrawContext ctx, CallbackInfo ci) {
        float speed = InventoryAnim.getSpeed();

        if (animClosing) {
            animProgress = fastLerp(animProgress, 0f, speed * 3f);

            if (animProgress < 0.05f && !animCursorHidden) {
                animCursorHidden = true;
                GLFW.glfwSetInputMode(
                        MinecraftClient.getInstance().getWindow().getHandle(),
                        GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN
                );
            }

            if (animProgress < 0.005f) {
                animScheduledClose = true;
                animBypassClose = true;
                GLFW.glfwSetInputMode(
                        MinecraftClient.getInstance().getWindow().getHandle(),
                        GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL
                );
                animCursorHidden = false;
                MinecraftClient.getInstance().execute(() -> {
                    try { ((HandledScreen<?>) (Object) this).close(); } catch (Exception ignored) {}
                });
                ci.cancel();
                return false;
            }
        } else {
            animProgress = Math.min(1f, fastLerp(animProgress, 1f, speed));
        }

        return true;
    }

    @Inject(
            method = "renderBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/Screen;renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterSuperRenderBackground(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        animBgMatrixPushed = false;
        if (!InventoryAnim.isActive(this)) return;
        if (animScheduledClose || (animProgress >= 0.999f && !animClosing)) return;

        ctx.getMatrices().pushMatrix();
        animBgMatrixPushed = true;
        applyTransform(ctx, Math.max(0.001f, applyEasing(animProgress)),
                ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
    }

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void onRenderBackgroundPost(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (animBgMatrixPushed) {
            ctx.getMatrices().popMatrix();
            animBgMatrixPushed = false;
        }
    }

    @Inject(method = "renderMain", at = @At("HEAD"), cancellable = true)
    private void onRenderMainPre(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        animMainMatrixPushed = false;
        if (!InventoryAnim.isActive(this)) return;
        if (animScheduledClose) { ci.cancel(); return; }
        if (animProgress >= 0.999f && !animClosing) return;

        if (!tickAnim(ctx, ci)) return;

        ctx.getMatrices().pushMatrix();
        animMainMatrixPushed = true;
        applyTransform(ctx, Math.max(0.001f, applyEasing(animProgress)),
                ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
    }

    @Inject(method = "renderMain", at = @At("TAIL"))
    private void onRenderMainPost(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (animMainMatrixPushed) {
            ctx.getMatrices().popMatrix();
            animMainMatrixPushed = false;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderPost(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        EventManager.callEvent(new HandledScreenEvent(ctx, focusedSlot, backgroundWidth, backgroundHeight));
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void onClose(CallbackInfo ci) {
        if (animBypassClose) return;
        if (!InventoryAnim.isActive(this)) return;
        if (animClosing || animScheduledClose) { ci.cancel(); return; }
        if (animProgress > 0.05f) {
            animClosing = true;
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        if (animCursorHidden) {
            GLFW.glfwSetInputMode(
                    MinecraftClient.getInstance().getWindow().getHandle(),
                    GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL
            );
            animCursorHidden = false;
        }
        animProgress = 0f;
        animClosing = false;
        animScheduledClose = false;
    }
}