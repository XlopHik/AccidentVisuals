package accident.modules.module.setting;

import accident.util.lang.LanguageManager;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Getter
public class Setting {
    private final String configKey;
    private final String name;
    private String description;

    @Setter
    private Supplier<Boolean> visible;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public Setting(String configKey) {
        this.configKey = configKey;
        this.name = LanguageManager.get(configKey);
        this.description = "";
    }

    public Setting(String configKey, String descriptionKey) {
        this.configKey = configKey;
        this.name = LanguageManager.get(configKey);
        this.description = LanguageManager.get(descriptionKey);
    }

    public boolean isVisible() { return visible == null || visible.get(); }

    public void onChange(Runnable listener) { changeListeners.add(listener); }

    protected void notifyChanged() { changeListeners.forEach(Runnable::run); }
}
