package accident.screens.keybinds;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CustomKeybindsScreen extends Screen {

    private static final Text TITLE = Text.translatable("controls.keybinds.title");
    private static final int FOOTER_HEIGHT = 36;
    private static final int HEADER_HEIGHT = 32;

    private final Screen parent;
    private final GameOptions gameOptions;

    public @Nullable KeyBinding selectedKeyBinding = null;
    public boolean waitingForInput = false;
    public long lastKeyCodeUpdateTime = 0L;

    private CustomKeyBindListWidget bindList;
    private ButtonWidget resetAllButton;

    private Set<Integer> occupiedKeys = new HashSet<>();
    private Set<Integer> conflictKeys = new HashSet<>();
    private int hoveredKeyCode = -1;

    private int kbOriginX;
    private int kbOriginY;
    private float kbScale;

    public CustomKeybindsScreen(Screen parent, GameOptions gameOptions) {
        super(TITLE);
        this.parent = parent;
        this.gameOptions = gameOptions;
    }


    @Override
    protected void init() {
        int listWidth = (int) (this.width * 0.42);

        int listTop    = HEADER_HEIGHT;
        int listBottom = this.height - FOOTER_HEIGHT;

        this.bindList = addDrawableChild(new CustomKeyBindListWidget(
                this, this.client, listWidth, listTop, listBottom));
        this.bindList.setX(0);

        this.resetAllButton = addDrawableChild(
                ButtonWidget.builder(Text.translatable("controls.resetAll"), btn -> {
                    for (KeyBinding kb : gameOptions.allKeys) {
                        kb.setBoundKey(kb.getDefaultKey());
                    }
                    refresh();
                }).dimensions(this.width / 2 - 155, this.height - FOOTER_HEIGHT + 8, 150, 20).build()
        );

        addDrawableChild(
                ButtonWidget.builder(ScreenTexts.DONE, btn -> close())
                        .dimensions(this.width / 2 + 5, this.height - FOOTER_HEIGHT + 8, 150, 20)
                        .build()
        );

        computeKeyboardLayout();
        refreshKeyColors();
    }

    private void computeKeyboardLayout() {
        int rightPanelX = (int) (this.width * 0.44);
        int rightPanelW = this.width - rightPanelX - 8;
        int rightPanelH = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - 16;

        float scaleW = (float) rightPanelW  / KeyboardRenderer.getWidth(1.0f);
        float scaleH = (float) rightPanelH  / KeyboardRenderer.getHeight(1.0f);
        this.kbScale   = Math.min(scaleW, scaleH) * 0.95f;

        int kbW = KeyboardRenderer.getWidth(kbScale);
        int kbH = KeyboardRenderer.getHeight(kbScale);

        this.kbOriginX = rightPanelX + (rightPanelW - kbW) / 2;
        this.kbOriginY = HEADER_HEIGHT + (rightPanelH - kbH) / 2 + 8;
    }


    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        context.drawCenteredTextWithShadow(textRenderer, TITLE, this.width / 2, 10, 0xFFFFFFFF);

        int divX = (int) (this.width * 0.43);
        context.fill(divX, HEADER_HEIGHT, divX + 1, this.height - FOOTER_HEIGHT, 0xFF444444);

        renderLegend(context);

        hoveredKeyCode = KeyboardRenderer.getKeyCodeAt(mouseX, mouseY, kbOriginX, kbOriginY, kbScale);
        int selectedCode = getSelectedKeyCode();
        KeyboardRenderer.render(context, kbOriginX, kbOriginY, occupiedKeys, conflictKeys, selectedCode, kbScale);

        if (hoveredKeyCode != -1 && !waitingForInput) {
            renderKeyTooltip(context, mouseX, mouseY, hoveredKeyCode);
        }

        boolean anyNonDefault = false;
        for (KeyBinding kb : gameOptions.allKeys) {
            if (!kb.isDefault()) { anyNonDefault = true; break; }
        }
        resetAllButton.active = anyNonDefault;

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderLegend(DrawContext context) {
        // Queued rather than drawn: it belongs in the same deferred pass as the
        // keyboard, or it would be painted over by it.
        KeyboardRenderer.queueLegend();
    }

    private void renderKeyTooltip(DrawContext context, int mouseX, int mouseY, int keyCode) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (KeyBinding kb : gameOptions.allKeys) {
            if (kb.isUnbound()) continue;

            InputUtil.Key key = ((accident.mixin.KeyBindingAccessor) kb).getBoundKey();
            if (key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() == keyCode) {
                lines.add(Text.translatable(kb.getId()).getString());
            }
        }

        KeyboardRenderer.queueTooltip(mouseX, mouseY, lines);
    }


    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (waitingForInput && selectedKeyBinding != null) {
            selectedKeyBinding.setBoundKey(InputUtil.Type.MOUSE.createFromCode(click.button()));
            finishInput();
            return true;
        }

        if (hoveredKeyCode != -1 && selectedKeyBinding != null && waitingForInput) {
            selectedKeyBinding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(hoveredKeyCode));
            finishInput();
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (waitingForInput && selectedKeyBinding != null) {
            if (input.isEscape()) {
                selectedKeyBinding.setBoundKey(InputUtil.UNKNOWN_KEY);
            } else {
                selectedKeyBinding.setBoundKey(InputUtil.fromKeyCode(input));
            }
            finishInput();
            return true;
        }
        return super.keyPressed(input);
    }


    public void startWaitingFor(KeyBinding binding) {
        this.selectedKeyBinding = binding;
        this.waitingForInput    = true;
        bindList.update();
    }

    private void finishInput() {
        this.selectedKeyBinding = null;
        this.waitingForInput    = false;
        this.lastKeyCodeUpdateTime = Util.getMeasuringTimeMs();
        refresh();
    }

    private void refresh() {
        KeyBinding.updateKeysByCode();
        refreshKeyColors();
        bindList.update();
    }

    private void refreshKeyColors() {
        Map<Integer, Integer> usage = KeyboardRenderer.buildKeyUsageMap(gameOptions.allKeys);
        occupiedKeys.clear();
        conflictKeys.clear();
        for (Map.Entry<Integer, Integer> e : usage.entrySet()) {
            if (e.getValue() == 1) occupiedKeys.add(e.getKey());
            else                   conflictKeys.add(e.getKey());
        }
    }

    private int getSelectedKeyCode() {
        if (selectedKeyBinding == null || !waitingForInput) return -1;
        if (selectedKeyBinding.isUnbound()) return -1;
        InputUtil.Key k = ((accident.mixin.KeyBindingAccessor) selectedKeyBinding).getBoundKey();
        return (k.getCategory() == InputUtil.Type.KEYSYM) ? k.getCode() : -1;
    }

    @Override
    public void close() {
        client.options.write();
        client.setScreen(parent);
    }
}