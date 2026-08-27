package accident.modules.impl.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import accident.client.draggables.AbstractHudElement;
import accident.util.animations.Animation;
import accident.util.animations.Direction;
import accident.util.animations.OutBack;
import accident.util.lang.LanguageManager;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class Notifications extends AbstractHudElement {

    private static final int FORCED_GUI_SCALE = 2;
    private static Notifications instance;

    public static Notifications getInstance() {
        return instance;
    }

    private final List<Notification> list = new CopyOnWriteArrayList<>();
    private static final float NOTIFICATION_HEIGHT = 16f;
    private static final float NOTIFICATION_GAP = 3f;

    private long lastNotificationChange = 0;
    private String currentRandomNotification = "Module Toggled";
    private static final List<String> RANDOM_NOTIFICATIONS = List.of("accident.notification.example");

    public Notifications() {
        super("Notifications", 0, 0, 110, 16, false);
        instance = this;
    }

    private int getCurrentGuiScale() {
        int scale = mc.options.getGuiScale().getValue();
        if (scale == 0) {
            scale = mc.getWindow().calculateScaleFactor(0, mc.forcesUnicodeFont());
        }
        return scale;
    }

    private float getScaleFactor() {
        return (float) getCurrentGuiScale() / (float) FORCED_GUI_SCALE;
    }

    private float getVirtualWidth() {
        return mc.getWindow().getFramebufferWidth() / (float) FORCED_GUI_SCALE;
    }

    private float getVirtualHeight() {
        return mc.getWindow().getFramebufferHeight() / (float) FORCED_GUI_SCALE;
    }

    @Override
    public boolean visible() {
        return !list.isEmpty();
    }

    @Override
    public void tick() {
        list.forEach(notif -> {
            if (System.currentTimeMillis() > notif.removeTime) {
                notif.anim.setDirection(Direction.BACKWARDS);
            }
        });
        list.removeIf(notif -> notif.anim.isFinished(Direction.BACKWARDS));

        if (isChat(mc.currentScreen)) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastNotificationChange >= 1500) {
                currentRandomNotification = RANDOM_NOTIFICATIONS.get(
                        new Random().nextInt(RANDOM_NOTIFICATIONS.size())
                );
                lastNotificationChange = currentTime;
            }

            boolean hasExampleNotification = list.stream()
                    .anyMatch(n -> n.text.contains(LanguageManager.get("accident.notification.example")) || n.text.contains("Module Toggled"));

            if (!hasExampleNotification) {
                addNotification("§f" + LanguageManager.get("accident.notification.example"), 99999999, true);
            }
        } else {
            list.removeIf(notif -> notif.text.contains(LanguageManager.get("accident.notification.example")));
        }

        updatePosition();
    }

    private void updatePosition() {
        if (mc.getWindow() == null) return;
        float virtualWidth = getVirtualWidth();
        float virtualHeight = getVirtualHeight();
        float crosshairX = virtualWidth / 2f;
        float crosshairY = virtualHeight / 2f;
        this.setX((int) (crosshairX - 60));
        this.setY((int) (crosshairY + 100));
    }

    public void addNotification(String text, long duration, boolean enabled) {
        addNotification(text, ItemStack.EMPTY, duration, enabled);
    }

    public void addNotification(String text, long duration) {
        addNotification(text, ItemStack.EMPTY, duration, true);
    }

    public void addNotification(String text, ItemStack stack, long duration) {
        addNotification(text, stack, duration, true);
    }

    public void addNotification(String text, ItemStack stack, long duration, boolean enabled) {
        Animation anim = new OutBack().setMs(700).setValue(1);
        anim.setDirection(Direction.FORWARDS);
        long currentTime = System.currentTimeMillis();

        Notification notification = new Notification(
                text,
                stack,
                anim,
                currentTime,
                currentTime + duration,
                enabled
        );

        float lastY = list.isEmpty() ? 0 : list.get(list.size() - 1).targetY + NOTIFICATION_HEIGHT + NOTIFICATION_GAP;
        notification.currentY = lastY;
        notification.targetY = lastY;
        notification.velocityY = 0;

        list.add(notification);
        while (list.size() > 5) {
            Notification oldest = list.stream()
                    .min(Comparator.comparingLong(n -> n.startTime))
                    .orElse(null);
            if (oldest == null) break;
            oldest.anim.setDirection(Direction.BACKWARDS);
            if (oldest.anim.isFinished(Direction.BACKWARDS) || list.size() > 7) {
                list.remove(oldest);
            } else {
                oldest.removeTime = 0;
                break;
            }
        }
        list.sort(Comparator.comparingDouble(notif -> -notif.removeTime));
        updateTargetPositions();
    }

    private void updateTargetPositions() {
        float offsetY = 0;
        for (int i = 0; i < list.size(); i++) {
            Notification notif = list.get(i);
            float anim = notif.anim.getOutput().floatValue();
            notif.targetY = offsetY;
            offsetY += (NOTIFICATION_HEIGHT + NOTIFICATION_GAP) * anim;
        }
    }

    private int clampAlpha(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int clampAlpha(float value) {
        return Math.max(0, Math.min(255, (int) value));
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;

        float alphaFactor = alpha / 255.0f;
        updatePosition();
        updateTargetPositions();

        float deltaTime = 0.016f;
        for (Notification notification : list) {
            float diff = notification.targetY - notification.currentY;
            notification.velocityY += (diff * 180f - notification.velocityY * 12f) * deltaTime;
            notification.currentY += notification.velocityY * deltaTime;
        }

        for (Notification notification : list) {
            float anim = notification.anim.getOutput().floatValue();
            if (anim <= 0.01f) continue;
            anim = Math.max(0f, Math.min(1f, anim));

            float textWidth = Fonts.TEST.getWidth(notification.text, 6);
            float width = textWidth + 30;
            float startX = this.getX() + (120 - width) / 2;
            float startY = this.getY() + notification.currentY;

            int finalAlpha = (int) (255 * anim * alphaFactor);
            int bgAlpha = (int) (225 * anim * alphaFactor);

            int bgColor = ((int)(anim * alphaFactor * 30) << 24) | 0x141623;
            Render2D.blur(startX, startY, width, NOTIFICATION_HEIGHT, 10f, 4f, bgColor);

            Render2D.outline(startX, startY, width, NOTIFICATION_HEIGHT, 0.5f, ((int)(60 * anim * alphaFactor) << 24) | 0x555555, 4);

            float iconSize = 9f;
            float iconX = startX + 7.5f;
            float iconY = startY + (NOTIFICATION_HEIGHT - iconSize) / 2f;

            if (notification.stack != null && !notification.stack.isEmpty()) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate((float)Math.floor(iconX), (float)Math.floor(iconY));
                context.getMatrices().scale(0.6f, 0.6f);
                context.drawItem(notification.stack, 0, 0);
                context.getMatrices().popMatrix();
            } else if (Fonts.ICONKIHUD != null) {
                if (notification.text.contains(LanguageManager.get("accident.notification.example")) || notification.text.contains("Module Toggled")) {
                    Fonts.GUI_ICONS.draw("C", iconX, startY + 4f, 8,
                            new Color(255, 255, 255, (int)(155 * anim * alphaFactor)).getRGB());
                } else {
                    String icon = notification.enabled ? "M" : "N";
                    Fonts.ICONKIHUD.draw(icon, iconX - 2f, startY + 3f, 11,
                            new Color(255, 255, 255, (int)(155 * anim * alphaFactor)).getRGB());
                }
            } else {
                Fonts.GUI_ICONS.draw("C", iconX, startY + 4f, 8,
                        new Color(255, 255, 255, (int)(155 * anim * alphaFactor)).getRGB());
            }

            Fonts.TEST.draw(notification.text, iconX + 13f, startY + 4.5f, 6,
                    new Color(255, 255, 255, bgAlpha).getRGB());
        }
    }

    public static class Notification {
        String text;
        Animation anim;
        ItemStack stack;
        long startTime;
        long removeTime;
        boolean enabled;

        float currentY;
        float targetY;
        float velocityY;

        Notification(String text, ItemStack stack, Animation anim, long startTime, long removeTime, boolean enabled) {
            this.text = text;
            this.anim = anim;
            this.startTime = startTime;
            this.stack = stack != null ? stack.copy() : ItemStack.EMPTY;
            this.removeTime = removeTime;
            this.enabled = enabled;
            this.currentY = 0;
            this.targetY = 0;
            this.velocityY = 0;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > removeTime;
        }
    }
}