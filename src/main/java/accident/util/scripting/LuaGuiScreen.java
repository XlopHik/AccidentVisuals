package accident.util.scripting;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

// специально пустой Screen - нужен только чтобы освободить курсор (как при открытии чата/инвентаря),
// сам рендер и клики скрипт делает через render.*/mouse.* в onRender2D. shouldPause() false - мир не встаёт на паузу
public class LuaGuiScreen extends Screen {

    public LuaGuiScreen() {
        super(Text.literal("Lua GUI"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // пусто специально - скрипты рисуют через render.* в onRender2D, он и так вызывается каждый кадр
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
