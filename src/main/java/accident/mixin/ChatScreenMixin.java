package accident.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import accident.Initialization;
import accident.client.draggables.Drag;
import accident.modules.impl.render.BetterChat;
import accident.util.render.Render2D;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow protected net.minecraft.client.gui.widget.TextFieldWidget chatField;

    private static final Pattern TIME_PREFIX   = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}] (.*)$", Pattern.DOTALL);
    private static final Pattern REPEAT_SUFFIX = Pattern.compile("^(.*) \\(x\\d+\\)$", Pattern.DOTALL);

    private String selectedText = null;
    private int    selectedLine = -1;

    @Unique private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Unique private long   accident$smoothTextChangeTime = 0;
    @Unique private String accident$smoothLastText = "";
    @Unique private boolean accident$smoothAnimating = false;
    @Unique private int    accident$smoothRevealStart = 0;
    @Unique private String accident$smoothSavedText = null;

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (Initialization.getInstance() != null
                && Initialization.getInstance().getManager() != null
                && Initialization.getInstance().getManager().getHudManager() != null) {
            if (Initialization.getInstance().getManager().getHudManager()
                    .mouseClicked((int) click.x(), (int) click.y(), click.button())) {
                cir.setReturnValue(true);
                return;
            }
        }

        Drag.onMouseClick(click);
        if (Drag.isDragging()) {
            selectedText = null;
            selectedLine = -1;
            cir.setReturnValue(true);
            return;
        }

        BetterChat module = BetterChat.getInstance();
        if (module != null && module.isState() && module.getCopyMessages().isValue() && click.button() == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            ChatHud chatHud = mc.inGameHud.getChatHud();
            int windowHeight = mc.getWindow().getScaledHeight();

            DrawnTextConsumer.ClickHandler clickHandler = new DrawnTextConsumer.ClickHandler(
                    mc.textRenderer, (int) click.x(), (int) click.y()
            ).insert(true);
            chatHud.render(clickHandler, windowHeight, mc.inGameHud.getTicks(), true);
            Style style = clickHandler.getStyle();
            if (style != null && style.getClickEvent() != null) {
                selectedText = null;
                selectedLine = -1;
                return;
            }

            double scale = (Double) mc.options.getChatScale().getValue();
            int chatWidth = (int) (ChatHud.getWidth((Double) mc.options.getChatWidth().getValue()) * scale);
            int left = (int) (4 * scale);
            if (click.x() < left || click.x() > left + chatWidth) {
                return;
            }

            int visLineIndex = getVisLineIndexAt(mc, (int) click.y(), windowHeight);
            if (visLineIndex >= 0) {
                List<ChatHudLine.Visible> visibles = ((ChatHudAccessor) chatHud).getVisibleMessages();
                if (visLineIndex < visibles.size()) {
                    String text = orderedTextToString(visibles.get(visLineIndex).content());
                    selectedText = cleanText(text);
                    selectedLine = visLineIndex;
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyInput;)Z", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        BetterChat module = BetterChat.getInstance();
        if (module != null && module.isState() && module.getCopyMessages().isValue() && selectedText != null) {
            if (input.key() == GLFW.GLFW_KEY_C && isCtrlHeld()) {
                mc.keyboard.setClipboard(selectedText);
                mc.inGameHud.setOverlayMessage(Text.literal("§7Скопировано: §f" + selectedText), false);
                cir.setReturnValue(true);
                return;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                selectedText = null;
                selectedLine = -1;
            }
        }
    }


    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void onRenderTail(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (accident$smoothSavedText != null && chatField != null) {
            chatField.setText(accident$smoothSavedText);
            accident$smoothSavedText = null;
        }

        BetterChat module = BetterChat.getInstance();
        if (module != null && module.isState() && module.getCopyMessages().isValue() && selectedLine >= 0) {
            ChatHud chatHud = mc.inGameHud.getChatHud();
            int windowHeight = mc.getWindow().getScaledHeight();
            List<ChatHudLine.Visible> visibles = ((ChatHudAccessor) chatHud).getVisibleMessages();

            double scale = (Double) mc.options.getChatScale().getValue();
            double lineSpacing = (Double) mc.options.getChatLineSpacing().getValue();
            int lineHeight = (int) (9.0 * (lineSpacing + 1.0) * scale);
            int chatWidth = (int) (ChatHud.getWidth((Double) mc.options.getChatWidth().getValue()) * scale);
            int left = (int) (4 * scale);

            if (selectedLine < visibles.size()) {
                int top = (windowHeight - 40) - (selectedLine + 1) * lineHeight;
                Render2D.rect(left, top, chatWidth, lineHeight, 0x80FFFFFF, 2f);
            }
        }

        Drag.onDraw(context, mouseX, mouseY, deltaTicks, true);
    }

    @Override
    public boolean mouseReleased(Click click) {
        Drag.onMouseRelease(click);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public void removed() {
        Drag.resetDragging();
        super.removed();
    }

    @Override
    public void close() {
        Drag.resetDragging();
        super.close();
    }

    private int getVisLineIndexAt(MinecraftClient mc, int mouseY, int windowHeight) {
        double scale = (Double) mc.options.getChatScale().getValue();
        double lineSpacing = (Double) mc.options.getChatLineSpacing().getValue();
        int lineHeight = (int) (9.0 * (lineSpacing + 1.0) * scale);
        if (mouseY > windowHeight - 40) return -1;
        return ((windowHeight - 40) - mouseY) / lineHeight;
    }

    private boolean isCtrlHeld() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private String orderedTextToString(OrderedText orderedText) {
        StringBuilder sb = new StringBuilder();
        orderedText.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    private String cleanText(String text) {
        Matcher tm = TIME_PREFIX.matcher(text);
        if (tm.matches()) text = tm.group(1);
        Matcher rm = REPEAT_SUFFIX.matcher(text);
        if (rm.matches()) text = rm.group(1);
        return text;
    }
}
