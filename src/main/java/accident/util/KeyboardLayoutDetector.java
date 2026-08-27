package accident.util;

import org.lwjgl.glfw.GLFW;

public class KeyboardLayoutDetector {

    private static volatile String lastResult = "ENG";
    private static volatile long lastCheckTime = 0;
    private static final long CACHE_MS = 300;

    public static String getCurrentInputLanguage() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CACHE_MS) {
            return lastResult;
        }
        lastCheckTime = now;

        try {
            String keyName = GLFW.glfwGetKeyName(GLFW.GLFW_KEY_Q, 0);
            if (keyName != null && !keyName.isEmpty()) {
                char c = keyName.charAt(0);
                if (c >= '\u0410' && c <= '\u044F') {
                    lastResult = "RUS";
                } else {
                    lastResult = "ENG";
                }
            }
        } catch (Throwable ignored) {
        }

        return lastResult;
    }
}
