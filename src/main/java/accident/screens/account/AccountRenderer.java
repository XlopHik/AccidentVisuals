package accident.screens.account;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.render.shader.Scissor;

import java.util.List;

public class AccountRenderer {

    private static final float OUTLINE_THICKNESS = 1f;

    /**
     * Верхняя панель: поле ввода и кнопка добавления
     */
    public void renderTopBar(float x, float y, float width, float height, float contentAlpha,
                             String nicknameText, boolean nicknameFieldFocused,
                             float scaledMouseX, float scaledMouseY, long currentTime) {
        int bgAlpha = (int) (contentAlpha * 60);
        int bgColor = (bgAlpha << 24) | 0x0F121F;

        Render2D.blur(x, y, width, height, 10f, 4f, bgColor);
        Render2D.outline(x, y, width, height, OUTLINE_THICKNESS, withAlpha(0x252a36, (int)(contentAlpha * 100)), 6);

        float fieldHeight = 16f;
        float addButtonSize = 16f;
        float buttonGap = 4f;
        float fieldY = y + (height - fieldHeight) / 2f;
        float fieldX = x + 5;
        float fieldWidth = width - 10 - addButtonSize - buttonGap;

        renderNicknameField(fieldX, fieldY, fieldWidth, fieldHeight, contentAlpha, nicknameText, nicknameFieldFocused, currentTime);

        float addButtonX = fieldX + fieldWidth + buttonGap;
        boolean addButtonHovered = isMouseOver(scaledMouseX, scaledMouseY, addButtonX, fieldY, addButtonSize, addButtonSize);
        renderAddButton(addButtonX, fieldY, addButtonSize, contentAlpha, addButtonHovered, (int)(contentAlpha * 255));
    }

    /**
     * Левая панель: кнопки Random, Clear и отображение активного аккаунта
     */
    public void renderLeftSidebar(float x, float y, float width, float height, float contentAlpha,
                                  String activeAccountName, String activeAccountDate, Identifier activeAccountSkin,
                                  float scaledMouseX, float scaledMouseY, long currentTime) {
        int bgAlpha = (int) (contentAlpha * 60);
        int bgColor = (bgAlpha << 24) | 0x0F121F;

        Render2D.blur(x, y, width, height, 10f, 4f, bgColor);
        Render2D.outline(x, y, width, height, OUTLINE_THICKNESS, withAlpha(0x252a36, (int)(contentAlpha * 100)), 6);

        float btnWidth = width - 10;
        float btnHeight = 18f;
        float btnX = x + 5;
        int titleAlpha = (int) (contentAlpha * 255);

        // Кнопка Random
        float randomBtnY = y + 5;
        boolean randomHovered = isMouseOver(scaledMouseX, scaledMouseY, btnX, randomBtnY, btnWidth, btnHeight);
        renderRandomButton(btnX, randomBtnY, btnWidth, btnHeight, contentAlpha, randomHovered, titleAlpha);

        // Кнопка Clear All
        float clearBtnY = randomBtnY + btnHeight + 4f;
        boolean clearHovered = isMouseOver(scaledMouseX, scaledMouseY, btnX, clearBtnY, btnWidth, btnHeight);
        renderClearAllButton(btnX, clearBtnY, btnWidth, btnHeight, contentAlpha, clearHovered, titleAlpha);

        // Отображение активного аккаунта
        float activeY = clearBtnY + btnHeight + 8f;
        float activeHeight = height - (activeY - y) - 5;

        if (!activeAccountName.isEmpty() && activeHeight > 40) {
            float faceSize = 24f;
            float faceX = btnX + (btnWidth - faceSize) / 2f;
            float faceY = activeY + 5;

            Identifier skinTexture = SkinManager.getSkin(activeAccountName);
            drawPlayerFace(skinTexture, faceX, faceY, faceSize, withAlpha(0xFFFFFF, titleAlpha));

            float nameY = faceY + faceSize + 6;
            float dateY = nameY + 10;

            Fonts.TEST.drawCentered(activeAccountName, btnX + btnWidth / 2f, nameY, 6f, withAlpha(0xFFFFFF, titleAlpha));
            Fonts.TEST.drawCentered(activeAccountDate, btnX + btnWidth / 2f, dateY, 4.5f, withAlpha(0x808890, titleAlpha));
        } else {
            Fonts.REGULARNEW.drawCentered("No account", btnX + btnWidth / 2f, activeY + activeHeight / 2f, 5f, withAlpha(0x606878, (int)(contentAlpha * 155)));
        }
    }

    /**
     * Правая панель: список аккаунтов
     */
    public void renderRightList(float x, float y, float width, float height, float contentAlpha,
                                List<AccountEntry> accounts, float scrollOffset,
                                float scaledMouseX, float scaledMouseY, float scale, int guiScale) {
        int bgAlpha = (int) (contentAlpha * 60);
        int bgColor = (bgAlpha << 24) | 0x0F121F;

        // Используем обычный rect вместо blur — blur не работает для больших панелей
        Render2D.rect(x, y, width, height, bgColor, 6);
        Render2D.outline(x, y, width, height, OUTLINE_THICKNESS, withAlpha(0x252a36, (int)(contentAlpha * 100)), 6);

        float listX = x + 5;
        float listY = y + 5;
        float listWidth = width - 10;
        float listHeight = height - 10;

        float cardWidth = (listWidth - 5) / 2f;
        float cardHeight = 40;
        float cardGap = 5;

        // карточка на краю рисуется целиком, без scissor последний ряд вылезет за панель
        Scissor.enableForClickGuiOverlay(listX, listY, listWidth, listHeight);
        try {
            for (int i = 0; i < accounts.size(); i++) {
                AccountEntry account = accounts.get(i);

                int col = i % 2;
                int row = i / 2;

                float cardX = listX + col * (cardWidth + cardGap);
                float cardY = listY + row * (cardHeight + cardGap) - scrollOffset;

                if (cardY + cardHeight < listY || cardY > listY + listHeight) {
                    continue;
                }

                renderAccountCard(cardX, cardY, cardWidth, cardHeight, account, contentAlpha,
                        scaledMouseX, scaledMouseY, listY, listHeight);
            }
        } finally {
            Scissor.disable();
        }

        if (accounts.isEmpty()) {
            Fonts.REGULARNEW.drawCentered("No accounts added", x + width / 2f, y + height / 2f, 6f, withAlpha(0x606878, (int)(contentAlpha * 155)));
        }
    }

    // --- Вспомогательные методы рендеринга ---

    private void renderNicknameField(float x, float y, float width, float height, float contentAlpha,
                                     String nicknameText, boolean focused, long currentTime) {
        int titleAlpha = (int) (contentAlpha * 255);
        int titleTextAlpha = (int) (contentAlpha * 155);

        int fieldBgAlpha = (int) (contentAlpha * 60);
        int fieldBgColor = (fieldBgAlpha << 24) | 0x0F121F;
        Render2D.blur(x, y, width, height, 10f, 4f, fieldBgColor);

        int fieldOutlineAlpha = focused ? (int) (contentAlpha * 180) : (int) (contentAlpha * 80);
        int fieldOutlineColor = focused ? withAlpha(0x3a4a5a, fieldOutlineAlpha) : withAlpha(0x252a36, fieldOutlineAlpha);
        Render2D.outline(x, y, width, height, 0.5f, fieldOutlineColor, 3);

        String displayText = nicknameText.isEmpty() && !focused ? "Enter nick..." : nicknameText;
        int textColor = nicknameText.isEmpty() && !focused ? withAlpha(0x606878, titleTextAlpha) : withAlpha(0xd0d4dc, titleAlpha);
        Fonts.TEST.draw(displayText, x + 4, y + 4.5f, 5.5f, textColor);

        if (focused && (currentTime / 500) % 2 == 0) {
            float cursorX = x + 4 + Fonts.TEST.getWidth(nicknameText, 5.5f);
            Render2D.rect(cursorX, y + 3, 0.5f, height - 6, withAlpha(0xd0d4dc, titleAlpha), 0);
        }
    }

    private void renderAddButton(float x, float y, float size, float contentAlpha, boolean hovered, int titleAlpha) {
        int btnAlpha = hovered ? (int) (contentAlpha * 80) : (int) (contentAlpha * 60);
        int btnColor = (btnAlpha << 24) | 0x141623;

        Render2D.blur(x, y, size, size, 10f, 4f, btnColor);
        Render2D.outline(x, y, size, size, 0.5f, withAlpha(0x252a36, (int) (contentAlpha * 100)), 3);

        float plusCenterX = x + size / 2f;
        float plusCenterY = y + size / 2f;
        float plusSize = 5;
        float plusThickness = 1.2f;

        Render2D.rect(plusCenterX - plusSize / 2f, plusCenterY - plusThickness / 2f, plusSize, plusThickness, withAlpha(0xFFFFFF, titleAlpha), 0.5f);
        Render2D.rect(plusCenterX - plusThickness / 2f, plusCenterY - plusSize / 2f, plusThickness, plusSize, withAlpha(0xFFFFFF, titleAlpha), 0.5f);
    }

    private void renderRandomButton(float x, float y, float width, float height, float contentAlpha, boolean hovered, int titleAlpha) {
        int btnAlpha = hovered ? (int) (contentAlpha * 80) : (int) (contentAlpha * 60);
        int btnColor = (btnAlpha << 24) | 0x141623;

        Render2D.blur(x, y, width, height, 10f, 4f, btnColor);

        int outlineColor = hovered ? withAlpha(0x3a4a5a, (int) (contentAlpha * 150)) : withAlpha(0x252a36, (int) (contentAlpha * 100));
        Render2D.outline(x, y, width, height, 0.5f, outlineColor, 3);

        int textColor = hovered ? withAlpha(0xFFFFFF, titleAlpha) : withAlpha(0xd0d8e4, titleAlpha);
        Fonts.TEST.draw("Random", x + 6, y + 5f, 5.5f, textColor);
        Fonts.ICONS.draw("R", x + width - 15, y + 3.5f, 10f, textColor);
    }

    private void renderClearAllButton(float x, float y, float width, float height, float contentAlpha, boolean hovered, int titleAlpha) {
        int btnAlpha = hovered ? (int) (contentAlpha * 80) : (int) (contentAlpha * 60);
        int btnColor = hovered ? ((btnAlpha << 24) | 0x231416) : ((btnAlpha << 24) | 0x141623);

        Render2D.blur(x, y, width, height, 10f, 4f, btnColor);

        int outlineColor = hovered ? withAlpha(0x5a3a3a, (int) (contentAlpha * 150)) : withAlpha(0x352a2a, (int) (contentAlpha * 100));
        Render2D.outline(x, y, width, height, 0.5f, outlineColor, 3);

        int textColor = hovered ? withAlpha(0xff8080, titleAlpha) : withAlpha(0xd0a0a0, titleAlpha);
        Fonts.TEST.draw("Clear All", x + 6, y + 5f, 5.5f, textColor);
        Fonts.GUI_ICONS.draw("O", x + width - 13, y + 2.5f, 11f, textColor);
    }

    private void renderAccountCard(float x, float y, float width, float height, AccountEntry account,
                                   float contentAlpha, float mouseX, float mouseY,
                                   float listY, float listHeight) {
        int titleAlpha = (int) (contentAlpha * 255);

        boolean cardHovered = isMouseOver(mouseX, mouseY, x, y, width, height)
                && mouseY >= listY && mouseY <= listY + listHeight;

        int cardAlpha = cardHovered ? (int) (contentAlpha * 100) : (int) (contentAlpha * 120);
        int cardColor = (cardAlpha << 24) | 0x141623;

        // Используем обычный rect вместо blur для карточек внутри scissor-области
        Render2D.rect(x, y, width, height, cardColor, 4);

        int cardOutlineColor = withAlpha(0x252a36, (int) (contentAlpha * 80));
        Render2D.outline(x, y, width, height, 0.5f, cardOutlineColor, 4);

        float faceX = x + 7;
        float faceY = y + 7;
        float faceSize = 25;

        Identifier skinTexture = SkinManager.getSkin(account.getName());
        drawPlayerFace(skinTexture, faceX, faceY, faceSize, withAlpha(0xFFFFFF, titleAlpha));

        float textX = faceX + faceSize + 5;
        float nameY = faceY + 2;
        float dateY = nameY + 9;

        String displayName = account.getName();
        float maxNameWidth = width - faceSize - 45;
        if (Fonts.TEST.getWidth(displayName, 7f) > maxNameWidth) {
            while (Fonts.TEST.getWidth(displayName + "...", 7f) > maxNameWidth && displayName.length() > 3) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName += "...";
        }

        Fonts.TEST.draw(displayName, textX, nameY, 7f, withAlpha(0xFFFFFF, titleAlpha));
        Fonts.TEST.draw(account.getDate(), textX, dateY, 6f, withAlpha(0x707888, titleAlpha));

        float buttonSize = 12;
        float buttonYPos = y + height - buttonSize - 5;
        float pinButtonX = x + width - buttonSize * 2 - 8;
        float deleteButtonX = x + width - buttonSize - 5;

        boolean pinHovered = isMouseOver(mouseX, mouseY, pinButtonX, buttonYPos, buttonSize, buttonSize)
                && mouseY >= listY && mouseY <= listY + listHeight;
        boolean deleteHovered = isMouseOver(mouseX, mouseY, deleteButtonX, buttonYPos, buttonSize, buttonSize)
                && mouseY >= listY && mouseY <= listY + listHeight;

        // Pin button
        int pinBtnAlpha = pinHovered ? (int) (contentAlpha * 100) : (int) (contentAlpha * 70);
        int pinBtnColor = account.isPinned() ? ((pinBtnAlpha << 24) | 0x232014) : ((pinBtnAlpha << 24) | 0x141623);

        Render2D.rect(pinButtonX, buttonYPos, buttonSize, buttonSize, pinBtnColor, 3);

        int pinOutlineColor = account.isPinned() ? withAlpha(0xd4a017, (int) (contentAlpha * 180)) : withAlpha(0x353a46, (int) (contentAlpha * 100));
        Render2D.outline(pinButtonX, buttonYPos, buttonSize, buttonSize, 0.5f, pinOutlineColor, 3);

        int pinIconColor = account.isPinned() ? withAlpha(0xffd700, titleAlpha) : withAlpha(0xc0c8d4, titleAlpha);
        Fonts.MAINMENUSCREEN.drawCentered("c", pinButtonX + buttonSize / 2f, buttonYPos + 1.5f, 9f, pinIconColor);

        // Delete button
        int delBtnAlpha = deleteHovered ? (int) (contentAlpha * 100) : (int) (contentAlpha * 70);
        int delBtnColor = deleteHovered ? ((delBtnAlpha << 24) | 0x231416) : ((delBtnAlpha << 24) | 0x141623);

        Render2D.rect(deleteButtonX, buttonYPos, buttonSize, buttonSize, delBtnColor, 3);
        Render2D.outline(deleteButtonX, buttonYPos, buttonSize, buttonSize, 0.5f, withAlpha(0x353a46, (int) (contentAlpha * 100)), 3);

        int delIconColor = deleteHovered ? withAlpha(0xff8080, titleAlpha) : withAlpha(0xc0c8d4, titleAlpha);
        Fonts.GUI_ICONS.drawCentered("O", deleteButtonX + buttonSize / 2f, buttonYPos + 0.5f, 11f, delIconColor);
    }

    public void drawPlayerFace(Identifier skin, float x, float y, float size, int color) {
        float u0 = 8f / 64f;
        float v0 = 8f / 64f;
        float u1 = 16f / 64f;
        float v1 = 16f / 64f;

        Render2D.texture(skin, x, y, size, size, u0, v0, u1, v1, color, 0, 3f);

        float hatScale = 1.12f;
        float hatSize = size * hatScale;
        float hatOffset = (hatSize - size) / 2f;

        float hatU0 = 40f / 64f;
        float hatV0 = 8f / 64f;
        float hatU1 = 48f / 64f;
        float hatV1 = 16f / 64f;

        Render2D.texture(skin, x - hatOffset, y - hatOffset, hatSize, hatSize, hatU0, hatV0, hatU1, hatV1, color, 0f, 3f);
    }

    public boolean isMouseOver(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (MathHelper.clamp(alpha, 0, 255) << 24);
    }
}