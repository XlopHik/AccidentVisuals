package accident.screens.clickgui.impl.settingsrender;

import net.minecraft.client.gui.DrawContext;
import accident.modules.module.setting.implement.SelectSetting;
import accident.util.interfaces.AbstractSettingComponent;
import accident.util.render.Render2D;
import accident.util.render.shader.Scissor;
import accident.util.render.font.Fonts;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectComponent extends AbstractSettingComponent {

    private final SelectSetting selectSetting;
    private float hoverAnimation = 0f;

    private final Map<String, Float> optionHoverAnimations = new HashMap<>();
    private final Map<String, Float> selectAnimations = new HashMap<>();

    // Мапы индивидуальных состояний скролла для каждой отдельной опции
    private final Map<String, Float> optionScrollOffsets = new HashMap<>();
    private final Map<String, Boolean> optionScrollingLeft = new HashMap<>(); // true = влево, false = возвращается вправо
    private final Map<String, Long> optionPauseEndTime = new HashMap<>();    // Таймер удержания паузы

    private long lastUpdateTime = System.currentTimeMillis();

    // Настройки внешнего вида и таймингов анимации
    private static final float ANIMATION_SPEED = 8f;
    private static final float BUTTON_HEIGHT = 10f;
    private static final float BUTTON_SPACING = 3f;
    private static final float TEXT_SCROLL_SPEED = 20f; // Скорость бега строки (пикселей в секунду)
    private static final long PAUSE_DURATION = 2000;    // Время задержки в крайних точках (2 секунды)
    private static final float TEXT_PADDING = 5f;

    public SelectComponent(SelectSetting setting) {
        super(setting);
        this.selectSetting = setting;
        for (String option : setting.getList()) {
            selectAnimations.put(option, setting.isSelected(option) ? 1f : 0f);
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

        // Рендерим имя самой настройки сверху
        Fonts.TEST.draw(selectSetting.getName(), x + 4f, y + 4f, 6,
                applyAlpha(new Color(215, 215, 225, 220)).getRGB());

        renderOptionButtons(context, mouseX, mouseY, deltaTime);
    }

    private void renderOptionButtons(DrawContext context, int mouseX, int mouseY, float deltaTime) {
        List<String> options = selectSetting.getList();

        // Сетка на 2 колонки с фиксированными отступами
        float buttonWidth = (width - 8f) / 2f - BUTTON_SPACING;
        float startX = x + 4f;
        float startY = y + 13f;

        int row = 0, col = 0;

        for (String option : options) {
            float buttonX = startX + col * (buttonWidth + BUTTON_SPACING);
            float buttonY = startY + row * (BUTTON_HEIGHT + BUTTON_SPACING);

            boolean optionHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                    mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;

            // Сглаживание ховера плашки
            float hoverAnim = optionHoverAnimations.getOrDefault(option, 0f);
            hoverAnim = lerp(hoverAnim, optionHovered ? 1f : 0f, deltaTime * ANIMATION_SPEED);
            optionHoverAnimations.put(option, hoverAnim);

            // Сглаживание выбора элемента
            boolean isSelected = selectSetting.isSelected(option);
            float selectAnim = selectAnimations.getOrDefault(option, 0f);
            selectAnim = lerp(selectAnim, isSelected ? 1f : 0f, deltaTime * 10f);
            selectAnimations.put(option, selectAnim);

            // Отрисовка бэкграунда кнопок (Glassmorphism / Liquid Glass коэффициенты)
            int bgAlpha = isSelected ? (60 + (int)(hoverAnim * 30)) : (25 + (int)(hoverAnim * 20));
            Color bgColor = isSelected ? new Color(115, 135, 205, bgAlpha) : new Color(45, 45, 50, bgAlpha);
            Render2D.rect(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, applyAlpha(bgColor).getRGB(), 2.5f);

            // Отрисовка тонких контуров
            int outlineAlpha = isSelected ? (100 + (int)(hoverAnim * 50)) : (40 + (int)(hoverAnim * 30));
            Color outlineColor = isSelected ? new Color(130, 155, 225, outlineAlpha) : new Color(80, 80, 85, outlineAlpha);
            Render2D.outline(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, 0.5f, applyAlpha(outlineColor).getRGB(), 2.5f);

            // Отрисовка текста с задержками и скроллом
            renderSmartText(option, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, isSelected, optionHovered, deltaTime);

            col++;
            if (col >= 2) { col = 0; row++; }
        }
    }

    private void renderSmartText(String text, float btnX, float btnY, float btnWidth, float btnHeight,
                                 boolean isSelected, boolean isHovered, float deltaTime) {
        float textY = btnY + btnHeight / 2f - 2.5f;

        // Уменьшаем отступ до 1.5 пикселей, чтобы текст занимал максимум пространства кнопки
        float padding = 1.5f;
        float maxAvailableWidth = btnWidth - (padding * 2f);
        float textWidth = Fonts.TEST.getWidth(text, 5);

        int textAlpha = isSelected ? 245 : 175;
        int grayVal = isSelected ? 240 : 165;
        int color = applyAlpha(new Color(grayVal, grayVal, grayVal + 10, textAlpha)).getRGB();

        // Если текст полностью влезает в новые расширенные границы — центрируем его
        if (textWidth <= maxAvailableWidth) {
            float centerX = btnX + (btnWidth - textWidth) / 2f;
            Fonts.TEST.draw(text, centerX, textY, 5, color);
            return;
        }

        // --- ЛОГИКА СКРОЛЛА ДЛЯ ДЛИННОГО ТЕКСТА ---
        float maxScroll = textWidth - maxAvailableWidth;
        float currentOffset = optionScrollOffsets.getOrDefault(text, 0f);
        boolean scrollingLeft = optionScrollingLeft.getOrDefault(text, true);
        long pauseEndTime = optionPauseEndTime.getOrDefault(text, 0L);
        long currentTime = System.currentTimeMillis();

        if (isHovered) {
            if (currentTime < pauseEndTime) {
                // Удерживаем паузу
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
            // Мягкий возврат на исходную позицию при уходе курсора
            currentOffset = lerp(currentOffset, 0f, deltaTime * 8f);
            scrollingLeft = true;
            pauseEndTime = 0L;
        }

        optionScrollOffsets.put(text, currentOffset);
        optionScrollingLeft.put(text, scrollingLeft);
        optionPauseEndTime.put(text, pauseEndTime);

        // Сдвигаем scissor ближе к краям кнопки, чтобы текст не резался посередине
        Scissor.enable(btnX + padding, btnY, maxAvailableWidth, btnHeight, 2);

        // Отрисовка скроллящегося текста с учетом минимального отступа
        Fonts.TEST.draw(text, btnX + padding - currentOffset, textY, 5, color);

        Scissor.disable();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        List<String> options = selectSetting.getList();
        float buttonWidth = (width - 8f) / 2f - BUTTON_SPACING;
        float startX = x + 4f;
        float startY = y + 13f;

        int row = 0, col = 0;

        for (String option : options) {
            float buttonX = startX + col * (buttonWidth + BUTTON_SPACING);
            float buttonY = startY + row * (BUTTON_HEIGHT + BUTTON_SPACING);

            if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                    mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT) {
                selectSetting.setSelected(option);
                return true;
            }
            col++;
            if (col >= 2) { col = 0; row++; }
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
        List<String> options = selectSetting.getList();
        int rows = (options.size() + 1) / 2;
        return baseHeight + (rows * (BUTTON_HEIGHT + BUTTON_SPACING));
    }
}