package accident.screens.clickgui.dropdown;

import org.lwjgl.glfw.GLFW;
import java.util.Locale;

public class ClientColumn {

    private String searchQuery = "";
    private boolean searchFocused = false;
    private float cursorBlink = 0f;
    private long lastUpdate = System.currentTimeMillis();

    public ClientColumn() { }

    public String getQuery() { return searchQuery; }
    public boolean isFocused() { return searchFocused; }

    public void setQuery(String q) { this.searchQuery = q; }
    public void setFocused(boolean f) { this.searchFocused = f; }

    public void clear() {
        searchQuery = "";
        searchFocused = false;
    }

    public boolean onCharTyped(char chr) {
        if (!searchFocused) return false;

        if (chr == '\b' && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }

        if (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_') {
            searchQuery += chr;
            return true;
        }

        return false;
    }

    public boolean onKeyPressed(int key) {
        if (!searchFocused) return false;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            clear();
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }

        return false;
    }

    public float getCursorAlpha() {
        long now = System.currentTimeMillis();
        float dt = (now - lastUpdate) / 1000f;
        lastUpdate = now;
        cursorBlink += dt * 2.5f;
        if (cursorBlink > 1f) cursorBlink -= 1f;
        return (float)(Math.sin(cursorBlink * Math.PI * 2) * 0.5 + 0.5);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                float colX, float colY,
                                float searchBarW, float searchBarH) {
        if (button == 0 &&
                mouseX >= colX && mouseX <= colX + searchBarW &&
                mouseY >= colY && mouseY <= colY + searchBarH) {

            searchFocused = !searchFocused;
            if (!searchFocused) {
                clear();
            }
            return true;
        }
        return false;
    }

    public static String getLanguageShort() {
        return accident.util.KeyboardLayoutDetector.getCurrentInputLanguage();
    }


    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                float colX, float colY) { return false; }

    public void mouseReleased(double mouseX, double mouseY, int button,
                              float colX, float colY) { }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY,
                                float colX, float colY) { return false; }

    public boolean isHovered(double mouseX, double mouseY, float colX, float colY) { return false; }

    public void scroll(double amount) { }

    public boolean keyPressed(int key, int scanCode, int modifiers,
                              float colX, float colY) { return false; }

    public boolean charTyped(char chr, int modifiers, float colX, float colY) { return false; }
}