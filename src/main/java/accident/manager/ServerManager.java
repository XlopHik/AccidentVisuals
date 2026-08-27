package accident.manager;

import lombok.Getter;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import accident.Initialization;
import accident.events.api.EventHandler;
import accident.events.api.EventManager;
import accident.events.impl.TickEvent;
import accident.modules.module.ModuleStructure;

import java.util.Locale;

import static accident.IMinecraft.mc;

public class ServerManager {
    @Getter
    private static ServerManager instance;

    public ServerManager() {
        instance = this;
        EventManager.register(this);
    }

    // выключает модули, ставшие недоступными - иначе server-only модуль продолжит работать после дисконнекта
    @EventHandler
    private void onTick(TickEvent event) {
        Initialization init = Initialization.getInstance();
        if (init == null || init.getManager() == null) return;

        var repository = init.getManager().getModuleRepository();
        if (repository == null) return;

        for (ModuleStructure module : repository.modules()) {
            if (module.isState() && module.isLocked()) module.setState(false);
        }
    }

    public static float getTps() {
        float tps = 20.0f;
        return Math.round(tps * 100.0f) / 100.0f;
    }

    public static final String HOLY_WORLD = "HolyWorld";

    // кэшируем на секунду - иначе детект дёргается каждый кадр, пока лейбл на экране
    private static final long DETECT_INTERVAL_MS = 1000L;

    private static volatile String detected = "";
    private static long lastDetectMs = 0L;

    /** Empty when in singleplayer or on a server the client does not recognise. */
    public static String getServerName() {
        long now = System.currentTimeMillis();
        if (now - lastDetectMs >= DETECT_INTERVAL_MS) {
            lastDetectMs = now;
            detected = detect();
        }
        return detected;
    }

    public static boolean isHolyWorld() {
        return HOLY_WORLD.equals(getServerName());
    }

    private static String detect() {
        if (mc.player == null || mc.world == null) return "";
        if (mc.isInSingleplayer() || mc.isIntegratedServerRunning()) return "";

        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return "";

        String brand = normalize(handler.getBrand());

        ServerInfo info = handler.getServerInfo();
        String address = normalize(info != null ? info.address : null);
        if (address.isEmpty() && mc.getCurrentServerEntry() != null) {
            address = normalize(mc.getCurrentServerEntry().address);
        }

        if (matches(brand, "holyworld") || matches(address, "holyworld", "hollyworld", "playhw")) {
            return HOLY_WORLD;
        }

        return "";
    }

    private static boolean matches(String value, String... keywords) {
        if (value.isEmpty()) return false;
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }

    // сервера добавляют в brand цветовые коды и пробелы, поэтому перед сравнением убираем всё кроме букв и цифр
    private static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if (c == '§') {
                i++;
                continue;
            }
            if (Character.isLetterOrDigit(c)) out.append(c);
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
