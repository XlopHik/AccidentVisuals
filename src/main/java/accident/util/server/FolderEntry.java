package accident.util.server;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.text.Text;

// заголовок папки в списке серверов, клик сворачивает/разворачивает, состояние хранится в ServerMeta
public class FolderEntry extends MultiplayerServerListWidget.Entry {

    private final String folder;
    private final int serverCount;
    private final Runnable onToggle;

    public FolderEntry(String folder, int serverCount, Runnable onToggle) {
        this.folder = folder;
        this.serverCount = serverCount;
        this.onToggle = onToggle;
    }

    public String folder() {
        return folder;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        boolean collapsed = ServerMeta.isCollapsed(folder);
        MinecraftClient client = MinecraftClient.getInstance();

        context.fill(x, y + 2, x + width, y + height - 2, hovered ? 0x33FFFFFF : 0x22FFFFFF);

        String arrow = collapsed ? "▶" : "▼";
        String label = arrow + "  " + folder + "  (" + serverCount + ")";
        context.drawTextWithShadow(client.textRenderer, Text.literal(label),
                x + 6, y + (height - 8) / 2, 0xFFB9BEFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            ServerMeta.toggleCollapsed(folder);
            onToggle.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean isOfSameType(MultiplayerServerListWidget.Entry other) {
        return other instanceof FolderEntry entry && entry.folder.equals(folder);
    }

    @Override
    public void connect() {
        // A heading is not something you can join.
    }

    @Override
    public Text getNarration() {
        return Text.literal(folder);
    }
}
