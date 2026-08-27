package accident.screens.keybinds;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class CustomKeyBindListWidget extends ElementListWidget<CustomKeyBindListWidget.BaseEntry> {

    private static final int ENTRY_HEIGHT = 20;

    private final CustomKeybindsScreen parent;

    public CustomKeyBindListWidget(CustomKeybindsScreen parent, MinecraftClient client, int listWidth, int top, int bottom) {
        super(client, listWidth, bottom - top, top, ENTRY_HEIGHT);
        this.parent = parent;

        KeyBinding[] sorted = ArrayUtils.clone(client.options.allKeys);
        Arrays.sort(sorted);

        KeyBinding.Category currentCategory = null;
        for (KeyBinding kb : sorted) {
            KeyBinding.Category cat = kb.getCategory();
            if (!cat.equals(currentCategory)) {
                currentCategory = cat;
                addEntry(new CategoryEntry(cat));
            }
            addEntry(new KeyBindEntry(kb, Text.translatable(kb.getId()), parent));
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 6;
    }

    @Override
    protected int getScrollbarX() {
        return this.getX() + this.width - 4;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // запоминаем до того, как элементы встанут в очередь на отрисовку - нужно для обрезки при скролле
        KeybindListRenderer.setViewport(getX(), getY(), getWidth(), getHeight());
        super.renderWidget(context, mouseX, mouseY, deltaTicks);
    }

    public void update() {
        KeyBinding.updateKeysByCode();
        children().forEach(BaseEntry::update);
    }


    public abstract static class BaseEntry extends ElementListWidget.Entry<BaseEntry> {
        public abstract void update();
    }


    public static class CategoryEntry extends BaseEntry {

        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();

        public CategoryEntry(KeyBinding.Category category) {
            this.label = category.getLabel().copy().formatted(Formatting.YELLOW, Formatting.BOLD);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            KeybindListRenderer.queueHeader(getX(), getY(), getWidth(), getHeight(), label.getString());
        }

        @Override
        public List<? extends Element> children() { return List.of(); }

        @Override
        public List<? extends Selectable> selectableChildren() { return List.of(); }

        @Override
        public void update() {}
    }


    public static class KeyBindEntry extends BaseEntry {

        private static final int COLOR_FREE     = 0xFFAAAAAA;
        private static final int COLOR_OCCUPIED = 0xFFFF8C00;
        private static final int COLOR_CONFLICT = 0xFFCC2200;
        private static final int COLOR_SELECTED_BG = 0xFF003366;

        private final KeyBinding binding;
        private final Text bindingName;
        private final CustomKeybindsScreen parent;
        private final MinecraftClient client = MinecraftClient.getInstance();

        private boolean duplicate = false;
        private Text keyLabel = Text.empty();

        public KeyBindEntry(KeyBinding binding, Text bindingName, CustomKeybindsScreen parent) {
            this.binding  = binding;
            this.bindingName = bindingName;
            this.parent   = parent;
            update();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean isSelected = parent.selectedKeyBinding == binding;
            boolean isWaiting  = parent.waitingForInput && isSelected;

            int rowFill = isSelected
                    ? new java.awt.Color(110, 116, 227, 90).getRGB()
                    : hovered ? new java.awt.Color(255, 255, 255, 18).getRGB() : 0;

            int badgeFill, badgeBorder, keyColour;
            String keyText;

            if (isWaiting) {
                badgeFill   = new java.awt.Color(110, 116, 227, 235).getRGB();
                badgeBorder = new java.awt.Color(200, 205, 255, 235).getRGB();
                keyColour   = 0xFFFFFFFF;
                keyText     = "...";
            } else if (duplicate) {
                badgeFill   = new java.awt.Color(225, 60, 80, 220).getRGB();
                badgeBorder = new java.awt.Color(255, 150, 165, 220).getRGB();
                keyColour   = 0xFFFFFFFF;
                keyText     = keyLabel.getString();
            } else if (binding.isUnbound()) {
                badgeFill   = new java.awt.Color(30, 32, 38, 190).getRGB();
                badgeBorder = new java.awt.Color(255, 255, 255, 24).getRGB();
                keyColour   = new java.awt.Color(120, 124, 135, 235).getRGB();
                keyText     = "—";
            } else {
                badgeFill   = new java.awt.Color(110, 116, 227, 150).getRGB();
                badgeBorder = new java.awt.Color(150, 158, 255, 170).getRGB();
                keyColour   = 0xFFF2F3FF;
                keyText     = keyLabel.getString();
            }

            KeybindListRenderer.queueRow(
                    getX(), getY(), getWidth(), getHeight(),
                    bindingName.getString(), keyText,
                    rowFill, badgeFill, badgeBorder,
                    isSelected ? 0xFFFFFFFF : new java.awt.Color(198, 202, 212, 240).getRGB(),
                    keyColour);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
            if (click.button() == 0) {
                parent.startWaitingFor(binding);
                return true;
            }
            return false;
        }

        @Override
        public List<? extends Element> children() { return List.of(); }

        @Override
        public List<? extends Selectable> selectableChildren() { return List.of(); }

        @Override
        public void update() {
            this.keyLabel  = binding.getBoundKeyLocalizedText();
            this.duplicate = false;

            if (!binding.isUnbound()) {
                for (KeyBinding other : MinecraftClient.getInstance().options.allKeys) {
                    if (other != binding && binding.equals(other)) {
                        duplicate = true;
                        break;
                    }
                }
            }
        }

        public KeyBinding getBinding() { return binding; }
    }
}