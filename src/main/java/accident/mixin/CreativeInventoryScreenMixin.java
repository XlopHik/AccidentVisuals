package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.modules.impl.misc.SmoothAnimations;
import accident.modules.impl.render.InventoryAnim;
import accident.util.iface.IAnimatedScreen;

@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin extends HandledScreen<CreativeInventoryScreen.CreativeScreenHandler> {

    protected CreativeInventoryScreenMixin() { super(null, null, null); }

    @Shadow private static ItemGroup selectedTab;
    @Shadow private float scrollPosition;

    @Unique private float creative$smoothPos = -1f;
    @Unique private float creative$targetPos  = 0f;

    // Before vanilla mouseScrolled: restore actual target so the new delta is computed correctly
    @Inject(method = "mouseScrolled", at = @At("HEAD"))
    private void creative$preScroll(double mx, double my, double hScroll, double vScroll,
                                    CallbackInfoReturnable<Boolean> cir) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.inventorySmoothness.getValue() == 0) return;
        if (creative$smoothPos >= 0f) scrollPosition = creative$targetPos;
    }

    // After vanilla mouseScrolled: capture new target, restore smooth display position
    @Inject(method = "mouseScrolled", at = @At("TAIL"))
    private void creative$postScroll(double mx, double my, double hScroll, double vScroll,
                                     CallbackInfoReturnable<Boolean> cir) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.inventorySmoothness.getValue() == 0) return;
        creative$targetPos = scrollPosition;
        if (creative$smoothPos >= 0f) scrollPosition = creative$smoothPos;
    }

    // Scrollbar drag: snap immediately (no animation feels better while dragging)
    @Inject(method = "mouseDragged", at = @At("TAIL"))
    private void creative$onDrag(Click click, double deltaX, double deltaY,
                                 CallbackInfoReturnable<Boolean> cir) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.inventorySmoothness.getValue() == 0) return;
        creative$smoothPos = scrollPosition;
        creative$targetPos = scrollPosition;
    }

    // Each frame: apply exponential smoothing and update inventory display
    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void creative$smoothDraw(DrawContext ctx, float delta, int mouseX, int mouseY,
                                     CallbackInfo ci) {
        SmoothAnimations sa = SmoothAnimations.getInstance();
        if (sa == null || !sa.isEnabled() || sa.inventorySmoothness.getValue() == 0) return;

        if (creative$smoothPos < 0f) {
            creative$smoothPos = scrollPosition;
            creative$targetPos = scrollPosition;
            return;
        }

        creative$smoothPos = SmoothAnimations.smooth(
                creative$smoothPos, creative$targetPos,
                SmoothAnimations.factor(sa.inventorySmoothness));

        if (Math.abs(creative$smoothPos - creative$targetPos) < 0.0001f) {
            creative$smoothPos = creative$targetPos;
        }

        scrollPosition = creative$smoothPos;
        handler.scrollItems(creative$smoothPos);
    }

    // Tab change: reset so animation doesn't bleed across tabs
    @Inject(method = "setSelectedTab", at = @At("TAIL"))
    private void creative$onTabChange(ItemGroup group, CallbackInfo ci) {
        creative$smoothPos = 0f;
        creative$targetPos = 0f;
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V"
            )
    )
    private void redirectDrawEntity(DrawContext ctx, int x1, int y1, int x2, int y2,
                                    int size, float scale, float mouseX, float mouseY,
                                    LivingEntity entity) {
        if (!InventoryAnim.isActive(this)) {
            InventoryScreen.drawEntity(ctx, x1, y1, x2, y2, size, scale, mouseX, mouseY, entity);
            return;
        }

        IAnimatedScreen anim = (IAnimatedScreen) this;
        float progress = anim.accident$getAnimProgress();
        boolean closing = anim.accident$isAnimClosing();

        if (progress >= 0.999f && !closing) {
            InventoryScreen.drawEntity(ctx, x1, y1, x2, y2, size, scale, mouseX, mouseY, entity);
            return;
        }

        float v = accident$applyEasing(progress, closing);
        if (v < 0.001f) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        float cx = sw / 2f;
        float cy = sh / 2f;

        int[] t = accident$transformBounds(x1, y1, x2, y2, v, cx, cy, sw, sh, closing);
        InventoryScreen.drawEntity(ctx, t[0], t[1], t[2], t[3], size, scale, mouseX, mouseY, entity);
    }

    @Unique
    private static int[] accident$transformBounds(int x1, int y1, int x2, int y2,
                                                  float v, float cx, float cy,
                                                  int sw, int sh, boolean closing) {
        return switch (InventoryAnim.getAnimType()) {
            case SCALE, BOUNCE -> new int[]{
                    Math.round(cx + (x1 - cx) * v), Math.round(cy + (y1 - cy) * v),
                    Math.round(cx + (x2 - cx) * v), Math.round(cy + (y2 - cy) * v)
            };
            case SLIDE_UP -> {
                int off = Math.round(sh * (1f - v));
                yield new int[]{x1, y1 + off, x2, y2 + off};
            }
            case SLIDE_DOWN -> {
                int off = Math.round(sh * (1f - v));
                yield new int[]{x1, y1 - off, x2, y2 - off};
            }
            case SLIDE_LEFT -> {
                int off = Math.round(sw * (1f - v));
                yield new int[]{x1 - off, y1, x2 - off, y2};
            }
            case SLIDE_RIGHT -> {
                int off = Math.round(sw * (1f - v));
                yield new int[]{x1 + off, y1, x2 + off, y2};
            }
            case FLIP -> new int[]{
                    Math.round(cx + (x1 - cx) * v), y1,
                    Math.round(cx + (x2 - cx) * v), y2
            };
            case WARP -> {
                float s = closing ? v : (1.15f - 0.15f * v);
                yield new int[]{
                        Math.round(cx + (x1 - cx) * s), Math.round(cy + (y1 - cy) * s),
                        Math.round(cx + (x2 - cx) * s), Math.round(cy + (y2 - cy) * s)
                };
            }
            case GLITCH -> {
                float shake = (1f - v) * 35f * (float) Math.sin(v * 28f);
                float s = 0.88f + 0.12f * v;
                yield new int[]{
                        Math.round(cx + (x1 - cx) * s + shake), Math.round(cy + (y1 - cy) * s),
                        Math.round(cx + (x2 - cx) * s + shake), Math.round(cy + (y2 - cy) * s)
                };
            }
            default -> new int[]{x1, y1, x2, y2};
        };
    }

    @Unique
    private static float accident$applyEasing(float raw, boolean closing) {
        if (closing) return accident$easeOutCubic(raw);
        return switch (InventoryAnim.getAnimType()) {
            case BOUNCE -> Math.max(0.001f, accident$easeOutBack(raw));
            default -> accident$easeOutCubic(raw);
        };
    }

    @Unique
    private static float accident$easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return 1f - (float) Math.pow(1f - t, 3);
    }

    @Unique
    private static float accident$easeOutBack(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }
}