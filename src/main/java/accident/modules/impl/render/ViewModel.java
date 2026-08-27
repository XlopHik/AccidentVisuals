package accident.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.Hand;
import accident.events.api.EventHandler;
import accident.events.impl.HandOffsetEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SliderSettings;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ViewModel extends ModuleStructure {

    SliderSettings mainHandXSetting = new SliderSettings("accident.module.viewmodel.setting.mainhandx.name", "accident.module.viewmodel.setting.mainhandx.desc")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings mainHandYSetting = new SliderSettings("accident.module.viewmodel.setting.mainhandy.name", "accident.module.viewmodel.setting.mainhandy.desc")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings mainHandZSetting = new SliderSettings("accident.module.viewmodel.setting.mainhandz.name", "accident.module.viewmodel.setting.mainhandz.desc")
            .setValue(0.0F).range(-2.5F, 2.5F);

    SliderSettings offHandXSetting = new SliderSettings("accident.module.viewmodel.setting.offhandx.name", "accident.module.viewmodel.setting.offhandx.desc")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings offHandYSetting = new SliderSettings("accident.module.viewmodel.setting.offhandy.name", "accident.module.viewmodel.setting.offhandy.desc")
            .setValue(0.0F).range(-1.0F, 1.0F);

    SliderSettings offHandZSetting = new SliderSettings("accident.module.viewmodel.setting.offhandz.name", "accident.module.viewmodel.setting.offhandz.desc")
            .setValue(0.0F).range(-2.5F, 2.5F);

    public ViewModel() {
        super("accident.module.viewmodel.name", "accident.module.viewmodel.desc", ModuleCategory.RENDER);
        settings(mainHandXSetting, mainHandYSetting, mainHandZSetting,
                offHandXSetting, offHandYSetting, offHandZSetting);
    }

    @EventHandler
    public void onHandOffset(HandOffsetEvent e) {
        Hand hand = e.getHand();
        if (hand.equals(Hand.MAIN_HAND) && e.getStack().getItem() instanceof CrossbowItem) return;

        MatrixStack matrix = e.getMatrices();

        if (hand.equals(Hand.MAIN_HAND)) {
            matrix.translate(mainHandXSetting.getValue(), mainHandYSetting.getValue(), mainHandZSetting.getValue());
        } else {
            matrix.translate(offHandXSetting.getValue(), offHandYSetting.getValue(), offHandZSetting.getValue());
        }
    }
}


