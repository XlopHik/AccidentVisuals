package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;

import accident.util.Instance;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BetterChat extends ModuleStructure {

    public static BetterChat getInstance() {
        return Instance.get(BetterChat.class);
    }

    BooleanSetting antiSpam = new BooleanSetting("accident.module.betterchat.setting.antispam.name", "accident.module.betterchat.setting.antispam.desc")
            .setValue(true);

    BooleanSetting showTime = new BooleanSetting("accident.module.betterchat.setting.showtime.name", "accident.module.betterchat.setting.showtime.desc")
            .setValue(false);

    BooleanSetting copyMessages = new BooleanSetting("accident.module.betterchat.setting.copymessages.name", "accident.module.betterchat.setting.copymessages.desc")
            .setValue(true);

    public BetterChat() {
        super("accident.module.betterchat.name", "accident.module.betterchat.desc", ModuleCategory.RENDER);
        settings(antiSpam, showTime, copyMessages);
    }
}


