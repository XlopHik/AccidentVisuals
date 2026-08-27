package accident.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import accident.modules.module.setting.implement.MultiSelectSetting;
import accident.util.interfaces.AbstractSettingComponent;
import accident.util.render.Render2D;
import accident.util.render.shader.Scissor;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class MultiSelectComponent extends AbstractSettingComponent {
    private final MultiSelectSetting multiSelectSetting;
    private float hoverAnimation = 0f;

    private final Map<String, Float> optionHoverAnimations = new HashMap<>();
    private final Map<String, Float> checkAnimations = new HashMap<>();

    // Индивидуальные состояния умного скролла для каждой опции мультиселекта
    private final Map<String, Float> optionScrollOffsets = new HashMap<>();
    private final Map<String, Boolean> optionScrollingLeft = new HashMap<>();
    private final Map<String, Long> optionPauseEndTime = new HashMap<>();

    private long lastUpdateTime = System.currentTimeMillis();

    // Единые константы оформления и таймингов пауз
    private static final float ANIMATION_SPEED = 8f;
    private static final float BUTTON_HEIGHT = 10f;
    private static final float BUTTON_SPACING = 3f;
    private static final float TEXT_SCROLL_SPEED = 20f;
    private static final long PAUSE_DURATION = 2000;
    private static final float TEXT_PADDING = 1.5f; // Уменьшенный отступ для максимальной вместимости

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting);
        this.multiSelectSetting = setting;
        for (String option : setting.getList()) {
            checkAnimations.put(option, setting.isSelected(option) ? 1f : 0f);
            optionHoverAnimations.put(option, 0f);
            optionScrollOffsets.put(option, 0f);
            optionScrollingLeft.put(option, true);
            optionPauseEndTime.put(option, 0L);
        }
    }

    private float getDeltaTime() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000f, 0.1f);
        lastUpdateTime = currentTime;
        return deltaTime;
    }

    private float lerp(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(speed, 1f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = getDeltaTime();

        boolean mainHovered = isHover(mouseX, mouseY);
        hoverAnimation = lerp(hoverAnimation, mainHovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);

        // Рендер названия настройки
        Fonts.TEST.draw(multiSelectSetting.getName(), x + 4f, y + 4f, 6,
                applyAlpha(new Color(215, 215, 225, 220)).getRGB());

        renderModeButtons(context, mouseX, mouseY, deltaTime);
    }

    private void renderModeButtons(DrawContext context, int mouseX, int mouseY, float deltaTime) {
        List<String> options = multiSelectSetting.getList();

        // Сетка выравнивания под 2 колонки
        float buttonWidth = (width - 8f) / 2f - BUTTON_SPACING;
        float startX = x + 4f;
        float startY = y + 13f;

        int row = 0, col = 0;

        for (String option : options) {
            float buttonX = startX + col * (buttonWidth + BUTTON_SPACING);
            float buttonY = startY + row * (BUTTON_HEIGHT + BUTTON_SPACING);

            boolean optionHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                    mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;

            // Анимация наведения на под-опцию
            float hoverAnim = optionHoverAnimations.getOrDefault(option, 0f);
            hoverAnim = lerp(hoverAnim, optionHovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);
            optionHoverAnimations.put(option, hoverAnim);

            // Анимация чекбокса (выбора)
            boolean isSelected = multiSelectSetting.isSelected(option);
            float checkAnim = checkAnimations.getOrDefault(option, 0f);
            checkAnim = lerp(checkAnim, isSelected ? 1f : 0f, deltaTime * 10f);
            checkAnimations.put(option, checkAnim);

            // Рендер подложки плашки
            int bgAlpha = isSelected ? (60 + (int) (hoverAnim * 30)) : (25 + (int) (hoverAnim * 20));
            Color bgColor = isSelected ? new Color(115, 135, 205, bgAlpha) : new Color(45, 45, 50, bgAlpha);
            Render2D.rect(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, applyAlpha(bgColor).getRGB(), 2.5f);

            // Рендер контура плашки
            int outlineAlpha = isSelected ? (100 + (int) (hoverAnim * 50)) : (40 + (int) (hoverAnim * 30));
            Color outlineColor = isSelected ? new Color(130, 155, 225, outlineAlpha) : new Color(80, 80, 85, outlineAlpha);
            Render2D.outline(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, 0.5f, applyAlpha(outlineColor).getRGB(), 2.5f);

            // Отрисовка умной текстовой начинки
            renderSmartText(option, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, isSelected, optionHovered, deltaTime);

            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
    }

    private void renderSmartText(String text, float btnX, float btnY, float btnWidth, float btnHeight,
                                 boolean isSelected, boolean isHovered, float deltaTime) {
        float textY = btnY + btnHeight / 2f - 2.5f;
        float maxAvailableWidth = btnWidth - (TEXT_PADDING * 2f);
        float textWidth = Fonts.TEST.getWidth(text, 5);

        int textAlpha = isSelected ? 245 : 175;
        int grayVal = isSelected ? 240 : 165;
        int color = applyAlpha(new Color(grayVal, grayVal, grayVal + 10, textAlpha)).getRGB();

        // Если при новом микро-отступе в 1.5 пикселя название влезло — просто центрируем
        if (textWidth <= maxAvailableWidth) {
            float centerX = btnX + (btnWidth - textWidth) / 2f;
            Fonts.TEST.draw(text, centerX, textY, 5, color);
            return;
        }

        // --- ЛОГИКА ТАЙМИНГОВ И ДВИЖЕНИЯ СКРОЛЛА ---
        float maxScroll = textWidth - maxAvailableWidth;
        float currentOffset = optionScrollOffsets.getOrDefault(text, 0f);
        boolean scrollingLeft = optionScrollingLeft.getOrDefault(text, true);
        long pauseEndTime = optionPauseEndTime.getOrDefault(text, 0L);
        long currentTime = System.currentTimeMillis();

        if (isHovered) {
            if (currentTime < pauseEndTime) {
                // Ждём на паузе (2 секунды в начале или конце пути)
            } else {
                if (scrollingLeft) {
                    currentOffset += deltaTime * TEXT_SCROLL_SPEED;
                    if (currentOffset >= maxScroll) {
                        currentOffset = maxScroll;
                        scrollingLeft = false;
                        pauseEndTime = currentTime + PAUSE_DURATION;
                    }
                } else {
                    currentOffset -= deltaTime * TEXT_SCROLL_SPEED;
                    if (currentOffset <= 0f) {
                        currentOffset = 0f;
                        scrollingLeft = true;
                        pauseEndTime = currentTime + PAUSE_DURATION;
                    }
                }
            }
        } else {
            // Плавное возвращение к левому краю, если убрали мышку
            currentOffset = lerp(currentOffset, 0f, deltaTime * 8f);
            scrollingLeft = true;
            pauseEndTime = 0L;
        }

        optionScrollOffsets.put(text, currentOffset);
        optionScrollingLeft.put(text, scrollingLeft);
        optionPauseEndTime.put(text, pauseEndTime);

        // Обрезаем текст ровно по границам расширенной области
        Scissor.enable(btnX + TEXT_PADDING, btnY, maxAvailableWidth, btnHeight, 2);

        // Отрисовка
        Fonts.TEST.draw(text, btnX + TEXT_PADDING - currentOffset, textY, 5, color);

        Scissor.disable();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        List<String> options = multiSelectSetting.getList();
        float buttonWidth = (width - 8f) / 2f - BUTTON_SPACING;
        float startX = x + 4f;
        float startY = y + 13f;

        int row = 0, col = 0;

        for (String option : options) {
            float buttonX = startX + col * (buttonWidth + BUTTON_SPACING);
            float buttonY = startY + row * (BUTTON_HEIGHT + BUTTON_SPACING);

            if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                    mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT) {

                var selected = multiSelectSetting.getSelected();
                if (selected.contains(option)) {
                    selected.remove(option);
                } else {
                    selected.add(option);
                }
                return true;
            }
            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
        return false;
    }

    @Override public void tick() {}

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getTotalHeight();
    }

    public float getTotalHeight() {
        float baseHeight = 18f;
        List<String> options = multiSelectSetting.getList();
        int rows = (options.size() + 1) / 2;
        return baseHeight + (rows * (BUTTON_HEIGHT + BUTTON_SPACING));
    }
}