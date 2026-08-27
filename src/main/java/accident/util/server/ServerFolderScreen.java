package accident.util.server;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

// запрашивает имя папки - либо для сервера, либо переименование существующей; пустое имя разгруппирует
public class ServerFolderScreen extends Screen {

    private final Screen parent;
    private final String subject;
    private final String address;
    private final String folder;

    private TextFieldWidget field;

    private ServerFolderScreen(Screen parent, String subject, String address, String folder) {
        super(Text.literal("Папка сервера"));
        this.parent = parent;
        this.subject = subject;
        this.address = address;
        this.folder = folder;
    }

    public static ServerFolderScreen forServer(Screen parent, String serverName, String address) {
        return new ServerFolderScreen(parent, serverName, address, null);
    }

    public static ServerFolderScreen forFolder(Screen parent, String folder) {
        return new ServerFolderScreen(parent, folder, null, folder);
    }

    @Override
    protected void init() {
        int centreX = width / 2;
        int centreY = height / 2;

        field = new TextFieldWidget(textRenderer, centreX - 100, centreY - 10, 200, 20,
                Text.literal("Папка"));
        field.setMaxLength(48);
        field.setText(address != null ? ServerMeta.folderOf(address) : folder);
        addDrawableChild(field);
        setInitialFocus(field);

        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), button -> {
            if (address != null) {
                ServerMeta.setFolder(address, field.getText());
            } else {
                ServerArrangement.renameFolder(folder, field.getText());
            }
            ServerArrangement.refresh();
            close();
        }).dimensions(centreX - 100, centreY + 20, 96, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), button -> close())
                .dimensions(centreX + 4, centreY + 20, 96, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        String heading = address != null ? "Папка для " + subject : "Переименовать папку " + subject;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(heading),
                width / 2, height / 2 - 40, 0xFFFFFFFF);

        String hint = address != null
                ? "Пустое поле уберёт сервер из папки"
                : "Пустое поле распустит папку";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(hint),
                width / 2, height / 2 + 46, 0xFF9AA0B4);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
