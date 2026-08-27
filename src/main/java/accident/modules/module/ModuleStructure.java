package accident.modules.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import accident.IMinecraft;
import accident.Initialization;
import accident.events.api.EventManager;
import accident.events.impl.ModuleToggleEvent;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.SettingRepository;
import accident.modules.impl.hud.Hud;
import accident.modules.impl.hud.Notifications;
import accident.util.animations.Animation;
import accident.util.animations.Decelerate;
import accident.util.animations.Direction;
import accident.util.lang.LanguageManager;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModuleStructure extends SettingRepository implements IMinecraft {
    String configKey;
    String name;
    String description;
    ModuleCategory category;
    Animation animation = new Decelerate().setMs(175).setValue(1);

    public ModuleStructure(String configKey, ModuleCategory category) {
        this.configKey = configKey;
        this.name = LanguageManager.get(configKey);
        this.category = category;
        this.description = "";
    }

    public void bindKey(int key) {
        // заблокированному модулю бинд не нужен - он всё равно не включится
        if (isLocked() && key != GLFW.GLFW_KEY_UNKNOWN) return;
        this.key = key;
    }

    // true, если модуль сейчас нельзя включить (клик гуи рисует его серым и не даёт нажать)
    public boolean isLocked() {
        return false;
    }

    // текст, который показывается при попытке включить заблокированный модуль
    public String lockedMessage() {
        return "";
    }

    @accident.events.api.EventHandler
    private void onKey(accident.events.impl.KeyEvent e) {
        if (isLocked()) return;
        if (e.isKeyDown(key) && key != GLFW.GLFW_KEY_UNKNOWN && key != -1) {
            long now = System.currentTimeMillis();
            if (accident.screens.clickgui.BindHelper.justBoundKey != key
                    || (now - accident.screens.clickgui.BindHelper.justBoundTime) > 1500) {
                switchState();
            }
        }
    }

    public ModuleStructure(String configKey, String descriptionKey, ModuleCategory category) {
        this.configKey = configKey;
        this.name = LanguageManager.get(configKey);
        this.description = LanguageManager.get(descriptionKey);
        this.category = category;
    }

    @NonFinal
    int key = GLFW.GLFW_KEY_UNKNOWN, type = 1;

    @NonFinal
    public boolean state;

    @Setter
    @NonFinal
    public boolean favorite;

    public void switchState() {
        setState(!state);
    }

    public void setState(boolean state) {
        // выключение работает всегда, даже если модуль заблокирован
        if (state && isLocked()) return;

        animation.setDirection(state ? Direction.FORWARDS : Direction.BACKWARDS);
        if (state != this.state) {
            this.state = state;
            handleStateChange();
        }
    }

    @NonFinal
    public boolean expanded;

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isEnabled() {
        return state;
    }

    public boolean isDisabled() {
        return !state;
    }

    public void switchFavorite() {
        setFavorite(!favorite);
    }

    private void handleStateChange() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null && mc.world != null) {
            Hud hud = Hud.getInstance();
            Notifications notifications = Notifications.getInstance();

            if (hud != null && hud.isState() && notifications != null) {
                var modules = accident.Initialization.getInstance().getManager().getModuleRepository().modules();
                boolean notifsEnabled = false;
                for (var m : modules) {
                    if (m instanceof accident.modules.impl.hud.Notifications n) {
                        notifsEnabled = n.isEnabled();
                        break;
                    }
                }
                if (notifsEnabled) {
                    if (state) {
                        notifications.addNotification(String.format(LanguageManager.get("accident.notification.enabled"), name), 2000, true);
                    } else {
                        notifications.addNotification(String.format(LanguageManager.get("accident.notification.disabled"), name), 2000, false);
                    }
                }
            }

            if (state) {
                activate();
            } else {
                deactivate();
            }
        }
        toggleSilent(state);

        ModuleToggleEvent event = new ModuleToggleEvent(this, state);
        EventManager.callEvent(event);
    }

    private void toggleSilent(boolean activate) {
        var eventManager = Initialization.getInstance().getManager().getEventManager();
        if (activate) {
            EventManager.register(this);
        } else {
            EventManager.unregister(this);
        }
    }

    public boolean activate() { return false; }
    public boolean deactivate() { return false; }
}
