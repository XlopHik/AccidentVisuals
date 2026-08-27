package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.modules.impl.render.InventoryAnim;
import accident.util.iface.IAnimatedScreen;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends HandledScreen<PlayerScreenHandler> {

    protected InventoryScreenMixin() {
        super(null, null, null);
    }


    @Inject(method = "init", at = @At("TAIL"))
    private void addDropAllButton(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        InventoryScreen screen = (InventoryScreen) (Object) this;
        int bx = screen.width / 2 - 40;
        int by = screen.height / 2 - 120;
    }


    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void onDrawBackground(DrawContext ctx, float deltaTicks, int mouseX, int mouseY, CallbackInfo ci) {
        if (!InventoryAnim.isActive(this)) return;

        IAnimatedScreen anim = (IAnimatedScreen) this;
        float progress = anim.accident$getAnimProgress();
        boolean closing = anim.accident$isAnimClosing();

        if (progress >= 0.999f && !closing) return;

        float v = accident$applyEasing(progress, closing);

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND_TEXTURE,
                x, y, 0.0F, 0.0F,
                backgroundWidth, backgroundHeight,
                256, 256
        );

        if (v >= 0.001f) {
            int sw = ctx.getScaledWindowWidth();
            int sh = ctx.getScaledWindowHeight();
            float cx = sw / 2f;
            float cy = sh / 2f;

            int x1 = x + 26;
            int y1 = y + 8;
            int x2 = x + 75;
            int y2 = y + 78;

            int[] t = accident$transformBounds(x1, y1, x2, y2, v, cx, cy, sw, sh, closing);

            InventoryScreen.drawEntity(ctx, t[0], t[1], t[2], t[3],
                    30, 0.0625F, mouseX, mouseY, MinecraftClient.getInstance().player);
        }

        ci.cancel();
    }

    @Unique
    private static int[] accident$transformBounds(int x1, int y1, int x2, int y2,
                                              float v, float cx, float cy,
                                              int sw, int sh, boolean closing) {
        return switch (InventoryAnim.getAnimType()) {
            case SCALE, BOUNCE -> new int[]{
                    Math.round(cx + (x1 - cx) * v),
                    Math.round(cy + (y1 - cy) * v),
                    Math.round(cx + (x2 - cx) * v),
                    Math.round(cy + (y2 - cy) * v)
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
                    Math.round(cx + (x1 - cx) * v),
                    y1,
                    Math.round(cx + (x2 - cx) * v),
                    y2
            };
            case WARP -> {
                float scale = closing ? v : (1.15f - 0.15f * v);
                yield new int[]{
                        Math.round(cx + (x1 - cx) * scale),
                        Math.round(cy + (y1 - cy) * scale),
                        Math.round(cx + (x2 - cx) * scale),
                        Math.round(cy + (y2 - cy) * scale)
                };
            }
            case GLITCH -> {
                float shake = (1f - v) * 35f * (float) Math.sin(v * 28f);
                float scale = 0.88f + 0.12f * v;
                yield new int[]{
                        Math.round(cx + (x1 - cx) * scale + shake),
                        Math.round(cy + (y1 - cy) * scale),
                        Math.round(cx + (x2 - cx) * scale + shake),
                        Math.round(cy + (y2 - cy) * scale)
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
            default     -> accident$easeOutCubic(raw);
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
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }
}