package accident.client.draggables;

import net.minecraft.client.gui.DrawContext;
import accident.events.impl.PacketEvent;
import accident.modules.impl.hud.*;
import accident.modules.module.ModuleStructure;
import accident.Initialization;

import java.util.ArrayList;
import java.util.List;

public class HudManager {

    private final List<HudElement> elements = new ArrayList<>();
    private boolean initialized = false;

    public HudManager() {
    }

    public void initElements() {
        if (initialized) return;

        var modules = Initialization.getInstance().getManager().getModuleRepository().modules();
        for (ModuleStructure m : modules) {
            if (m instanceof HudElement he) {
                elements.add(he);
            }
        }

        initialized = true;
    }

    public void register(HudElement element) {
        elements.add(element);
    }

    public void onPacket(PacketEvent e) {
        for (HudElement element : elements) {
            element.onPacket(e);
        }
    }

    public void render(DrawContext context, float tickDelta, int mouseX, int mouseY) {
        for (HudElement element : elements) {
            if (element.isEnabled()) {
                element.render(context, tickDelta);
            }
        }
    }

    public void tick() {
        for (HudElement element : elements) {
            if (element.isEnabled()) {
                element.tick();
            }
        }
    }

    public HudElement getElementAt(double mouseX, double mouseY) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (element.isEnabled() && element.visible()) {
                if (mouseX >= element.getX() && mouseX <= element.getX() + element.getWidth() &&
                        mouseY >= element.getY() && mouseY <= element.getY() + element.getHeight()) {
                    return element;
                }
            }
        }
        return null;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (HudElement element : elements) {
            if (element.isEnabled()) {
                if (element.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<HudElement> getElements() {
        return elements;
    }

    public List<HudElement> getEnabledElements() {
        List<HudElement> enabled = new ArrayList<>();
        for (HudElement element : elements) {
            if (element.isEnabled()) {
                enabled.add(element);
            }
        }
        return enabled;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
