package accident.screens.menu;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import accident.Initialization;
import accident.screens.account.AccountEntry;
import accident.screens.account.AccountRenderer;
import accident.util.config.impl.account.AccountConfig;
import accident.util.lang.LanguageManager;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.session.SessionChanger;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

public class MainMenuScreen extends Screen {

    private static final Identifier BACKGROUND_TEXTURE = Identifier.of("accident", "textures/menu/back.png");
    private static final float FIXED_GUI_SCALE = 2.0f;
    private static final int PARTICLE_COUNT = 50;
    private static final float PARTICLE_FADE_THRESHOLD = 0.8f;

    private static class Particle {
        float x, y, vx, vy, size, alpha, lifetime, maxLifetime;
        boolean isDead = false;

        Particle(float x, float y) {
            this.x = x; this.y = y;
            Random rand = new Random();
            this.vx = (rand.nextFloat() - 0.5f) * 0.2f;
            this.vy = (rand.nextFloat() - 0.5f) * 0.2f;
            this.size = 0.5f + rand.nextFloat();
            this.alpha = 0.2f + rand.nextFloat() * 0.3f;
            this.maxLifetime = 10f + rand.nextFloat() * 10f;
            this.lifetime = 0f;
        }

        void update(float delta, int width, int height) {
            lifetime += delta / 60f;
            if (lifetime > maxLifetime * PARTICLE_FADE_THRESHOLD) {
                float fadeProgress = (lifetime - maxLifetime * PARTICLE_FADE_THRESHOLD) / (maxLifetime * (1f - PARTICLE_FADE_THRESHOLD));
                alpha = Math.max(0, alpha * (1f - fadeProgress));
            }
            if (lifetime >= maxLifetime) { isDead = true; return; }
            x += vx * delta; y += vy * delta;
            if (x < -10 || x > width + 10 || y < -10 || y > height + 10) { isDead = true; }
        }
    }

    private final List<Particle> particles = new java.util.ArrayList<>();
    private boolean particlesInitialized = false;
    private int lastWindowWidth = 0, lastWindowHeight = 0;
    private View currentView = View.MAIN_MENU;
    private boolean initialized = false;
    private final float[] buttonHoverProgress = new float[6];
    private final AccountRenderer accountRenderer;
    private final AccountConfig accountConfig;
    private String nicknameText = "";
    private boolean nicknameFieldFocused = false;
    private float scrollOffset = 0f, targetScrollOffset = 0f;

    private enum View { MAIN_MENU, ALT_SCREEN }

    public MainMenuScreen() {
        super(Text.literal("Main Menu"));
        this.accountRenderer = new AccountRenderer();
        this.accountConfig = AccountConfig.getInstance();
        this.accountConfig.load();
    }

    @Override
    protected void init() {
        initialized = false;
        particlesInitialized = false;
    }

    private int getFixedScaledWidth() { return (int) Math.ceil((double) client.getWindow().getFramebufferWidth() / FIXED_GUI_SCALE); }
    private int getFixedScaledHeight() { return (int) Math.ceil((double) client.getWindow().getFramebufferHeight() / FIXED_GUI_SCALE); }
    private float toFixedCoord(double coord) { return (float) (coord * (float) client.getWindow().getScaleFactor() / FIXED_GUI_SCALE); }

    private void initParticles(int width, int height) {
        if (particlesInitialized) return;
        Random rand = new Random();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new Particle(rand.nextFloat() * width, rand.nextFloat() * height));
        }
        particlesInitialized = true;
    }

    private void drawBackground() {
        int screenWidth = getFixedScaledWidth();
        int screenHeight = getFixedScaledHeight();

        Initialization.getInstance().getManager().getRenderCore().getTexturePipeline()
                .drawTexture(BACKGROUND_TEXTURE, 0, 0, screenWidth, screenHeight,
                        0, 0, 1, 1, new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}, new float[]{0,0,0,0}, 1f);

        initParticles(screenWidth, screenHeight);
        for (Particle p : particles) {
            if (!p.isDead && p.alpha > 0.01f) {
                int alpha = (int) (p.alpha * 255);
                Render2D.rect(p.x, p.y, p.size, p.size, new Color(255, 255, 255, alpha).getRGB());
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!initialized) {
            Path langPath = Paths.get("AccidentVisuals", "configs", "language.json");
            if (!Files.exists(langPath)) {
                this.client.setScreen(new LanguageScreen());
                return;
            }
        }

        long currentTime = Util.getMeasuringTimeMs();
        long lastRenderTime = 0L;
        if (!initialized) {
            lastRenderTime = currentTime; initialized = true;
        }
        float deltaTime = Math.min(delta, 0.05f);
        lastRenderTime = currentTime;
        int fixedWidth = getFixedScaledWidth();
        int fixedHeight = getFixedScaledHeight();

        if (lastWindowWidth != fixedWidth || lastWindowHeight != fixedHeight) {
            particles.clear(); particlesInitialized = false;
            lastWindowWidth = fixedWidth; lastWindowHeight = fixedHeight;
        }

        for (Particle p : particles) { p.update(deltaTime * 60f, fixedWidth, fixedHeight); }
        particles.removeIf(p -> p.isDead);
        Random rand = new Random();
        while (particles.size() < PARTICLE_COUNT) {
            particles.add(new Particle(rand.nextFloat() * fixedWidth, rand.nextFloat() * fixedHeight));
        }

        float scaledMouseX = toFixedCoord(mouseX);
        float scaledMouseY = toFixedCoord(mouseY);

        updateButtonAnimations(deltaTime, scaledMouseX, scaledMouseY, fixedWidth, fixedHeight);

        drawBackground();

        Render2D.beginOverlay();

        // тот же island, что и в Mini-режиме HUD, рисует сам модуль, чтобы не разъезжались
        accident.modules.impl.hud.Media media = accident.util.Instance.get(accident.modules.impl.hud.Media.class);
        if (media != null) {
            media.renderInMenu(context, fixedWidth / 2f, 8f, 1f);
        }

        if (currentView == View.MAIN_MENU) {
            renderMainMenu(fixedWidth, fixedHeight, scaledMouseX, scaledMouseY);
        } else {
            renderAltScreen(fixedWidth, fixedHeight, scaledMouseX, scaledMouseY, currentTime);
        }
        Render2D.endOverlay();
    }

    private void renderMainMenu(int screenWidth, int screenHeight, float mouseX, float mouseY) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        float buttonWidth = 235;
        float buttonHeight = 29;
        float buttonSpacing = 5;
        float buttonStartY = centerY + 3;

        float paddingSide = 15;
        float paddingTop = 100;
        float paddingBottom = 30;
        float panelWidth = buttonWidth + paddingSide * 2;
        float buttonsTotalHeight = (buttonHeight * 4) + (buttonSpacing * 3);
        float panelHeight = buttonsTotalHeight + paddingTop + paddingBottom;
        float panelY = buttonStartY - paddingTop;

        float x = centerX - panelWidth / 2f;
        float y = panelY;
        float w = panelWidth;
        float h = panelHeight;

        Render2D.blur(x, y, w, h, 12f, 5f, new Color(10, 12, 20, 30).getRGB());

        Render2D.rect(x, y, w, h, new Color(20, 25, 35, 60).getRGB(), 12);

        float avatarSize = 20; float avatarX = 8; float avatarY = 10;
        Render2D.rect(avatarX, avatarY, avatarSize, avatarSize, new Color(40, 45, 55).getRGB(), avatarSize / 2f);

        // --- ЛОГОТИП (рисуем через шрифт вместо PNG) ---
        float logoHeight = 50f;
        float logoWidth = logoHeight * (1136f / 944f);

        // Рисуем символ 'A' из шрифта LOGO
        float logoX = centerX - logoWidth / 2f;
        float logoY = centerY - 90;
        Fonts.LOGO.draw("A", logoX + 7, logoY, logoHeight, 0xFFFFFFFF);

        Identifier avatarToDraw = accident.util.render.DiscordAvatarManager.discordAvatarId != null
                ? accident.util.render.DiscordAvatarManager.discordAvatarId
                : Identifier.of("accident", "user.png");

        Render2D.rect(avatarX, avatarY, avatarSize, avatarSize, new Color(40, 45, 55).getRGB(), avatarSize / 2f);

        Initialization.getInstance().getManager().getRenderCore().getTexturePipeline()
                .drawTexture(avatarToDraw, avatarX, avatarY, avatarSize, avatarSize,
                        0, 0, 1, 1,
                        new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF},
                        new float[]{avatarSize/2f, avatarSize/2f, avatarSize/2f, avatarSize/2f}, 1f);

        drawButton(centerX - buttonWidth / 2f, buttonStartY, buttonWidth, buttonHeight, "a", LanguageManager.get("accident.menu.singleplayer"), 0, mouseX, mouseY, new Color(30, 35, 45));
        drawButton(centerX - buttonWidth / 2f, buttonStartY + buttonHeight + buttonSpacing, buttonWidth, buttonHeight, "b", LanguageManager.get("accident.menu.multiplayer"), 1, mouseX, mouseY, new Color(30, 35, 45));

        float halfButtonWidth = (buttonWidth - buttonSpacing) / 2f;
        float halfButtonY = buttonStartY + (buttonHeight + buttonSpacing) * 2;
        drawButton(centerX - buttonWidth / 2f, halfButtonY, halfButtonWidth, buttonHeight, "s", LanguageManager.get("accident.menu.options"), 2, mouseX, mouseY, new Color(30, 35, 45));
        drawButton(centerX - buttonWidth / 2f + halfButtonWidth + buttonSpacing, halfButtonY, halfButtonWidth, buttonHeight, "x", LanguageManager.get("accident.menu.accounts"), 3, mouseX, mouseY, new Color(30, 35, 45));

        drawButton(centerX - buttonWidth / 2f, halfButtonY + buttonHeight + buttonSpacing, buttonWidth, buttonHeight, "i", LanguageManager.get("accident.menu.exit"), 4, mouseX, mouseY, new Color(40, 45, 59));

        String username = client.getSession().getUsername();
        Fonts.TEST.draw(LanguageManager.get("accident.menu.loggedinas"), avatarX + avatarSize + 5, avatarY, 7f, new Color(150, 150, 150).getRGB());
        Fonts.TEST.draw(username, avatarX + avatarSize + 5, avatarY + 9, 8f, new Color(255, 255, 255).getRGB());

        String greeting = LanguageManager.get("accident.menu.good" + getTimeOfDay());
        Fonts.TEST.draw(greeting, centerX - Fonts.TEST.getWidth(greeting, 14f)/2f, centerY - 38, 14f, -1);

        String welcomeText = LanguageManager.get("accident.menu.welcome") + " "; String clientName = "AccidentVisuals";
        float welcomeWidth = Fonts.TEST.getWidth(welcomeText, 9f) + Fonts.TEST.getWidth(clientName, 9f);
        Fonts.TEST.draw(welcomeText, centerX - welcomeWidth / 2f, centerY - 20, 9f, new Color(180, 180, 180).getRGB());
        Fonts.TEST.draw(clientName, centerX - welcomeWidth / 2f + Fonts.TEST.getWidth(welcomeText, 9f), centerY - 20, 9f, new Color(180, 180, 180).getRGB());
    }

    private String getTimeOfDay() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 5 && hour < 12) return "morning";
        if (hour >= 12 && hour < 17) return "afternoon";
        if (hour >= 17 && hour < 21) return "evening";
        return "night";
    }

    private void drawButton(float x, float y, float width, float height, String icon, String text, int index, float mouseX, float mouseY, Color baseColor) {
        float hoverProgress = buttonHoverProgress[index];
        int brightness = (int) (hoverProgress * 15);
        int opacity = 150;

        Color bgColor = new Color(
                Math.min(255, baseColor.getRed() + brightness),
                Math.min(255, baseColor.getGreen() + brightness),
                Math.min(255, baseColor.getBlue() + brightness),
                opacity
        );

        Render2D.rect(x, y, width, height, bgColor.getRGB(), 8);
        Render2D.outline(x, y, width, height, 1f, new Color(50, 55, 65, 80).getRGB(), 8);

        if (hoverProgress > 0.01f) {
            int borderAlpha = (int) (hoverProgress * 100);
            Render2D.outline(x, y, width, height, 1f, new Color(100, 110, 130, borderAlpha).getRGB(), 8);
        }

        int textColor = new Color(160, 160, 160).getRGB();
        float textSize = 9f;
        float iconSize = 10f;
        float spacing = 2f;

        float iconWidth = Fonts.MAINMENUSCREEN.getWidth(icon, iconSize);
        float textWidth = Fonts.TEST.getWidth(text, textSize);

        float totalContentWidth = iconWidth + spacing + textWidth;

        float contentX = x + (width - totalContentWidth) / 2f;
        float contentY = y + (height / 2f);

        Fonts.MAINMENUSCREEN.draw(icon, contentX, contentY - (Fonts.MAINMENUSCREEN.getHeight(iconSize) / 2f), iconSize, textColor);

        Fonts.TEST.draw(text, contentX + iconWidth + spacing, contentY - (Fonts.TEST.getHeight(textSize) / 2f), textSize, textColor);
    }

    private boolean isMouseOver(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void updateButtonAnimations(float deltaTime, float mouseX, float mouseY, int screenWidth, int screenHeight) {
        float lerpSpeed = 1f - (float) Math.pow(0.001f, deltaTime);
        if (currentView == View.MAIN_MENU) {
            float centerX = screenWidth / 2f;
            float centerY = screenHeight / 2f;
            float buttonWidth = 235;
            float buttonHeight = 29;
            float buttonSpacing = 5;
            float buttonStartY = centerY + 3;

            for (int i = 0; i < 2; i++) {
                float y = buttonStartY + i * (buttonHeight + buttonSpacing);
                buttonHoverProgress[i] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[i], isMouseOver(mouseX, mouseY, centerX - buttonWidth / 2f, y, buttonWidth, buttonHeight) ? 1f : 0f);
            }

            float halfButtonWidth = (buttonWidth - buttonSpacing) / 2f;
            float halfButtonY = buttonStartY + (buttonHeight + buttonSpacing) * 2;
            buttonHoverProgress[2] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[2], isMouseOver(mouseX, mouseY, centerX - buttonWidth / 2f, halfButtonY, halfButtonWidth, buttonHeight) ? 1f : 0f);
            buttonHoverProgress[3] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[3], isMouseOver(mouseX, mouseY, centerX - buttonWidth / 2f + halfButtonWidth + buttonSpacing, halfButtonY, halfButtonWidth, buttonHeight) ? 1f : 0f);

            float exitButtonY = halfButtonY + buttonHeight + buttonSpacing;
            buttonHoverProgress[4] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[4], isMouseOver(mouseX, mouseY, centerX - buttonWidth / 2f, exitButtonY, buttonWidth, buttonHeight) ? 1f : 0f);

        } else {
            for (int i = 0; i < 6; i++) {
                buttonHoverProgress[i] = MathHelper.lerp(lerpSpeed, buttonHoverProgress[i], 0f);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (currentView != View.MAIN_MENU || click.button() != 0) {
            return currentView == View.ALT_SCREEN ? handleAltScreenClick(toFixedCoord(click.x()), toFixedCoord(click.y()), click) : super.mouseClicked(click, doubled);
        }

        float scaledMouseX = toFixedCoord(click.x());
        float scaledMouseY = toFixedCoord(click.y());
        int fixedWidth = getFixedScaledWidth();
        int fixedHeight = getFixedScaledHeight();
        float centerX = fixedWidth / 2f;
        float centerY = fixedHeight / 2f;
        float buttonWidth = 235;
        float buttonHeight = 29;
        float buttonSpacing = 5;
        float buttonStartY = centerY + 3;

        for (int i = 0; i < 2; i++) {
            float y = buttonStartY + i * (buttonHeight + buttonSpacing);
            if (isMouseOver(scaledMouseX, scaledMouseY, centerX - buttonWidth / 2f, y, buttonWidth, buttonHeight)) {
                handleMainMenuButtonClick(i);
                return true;
            }
        }

        float halfButtonWidth = (buttonWidth - buttonSpacing) / 2f;
        float halfButtonY = buttonStartY + (buttonHeight + buttonSpacing) * 2;
        if (isMouseOver(scaledMouseX, scaledMouseY, centerX - buttonWidth / 2f, halfButtonY, halfButtonWidth, buttonHeight)) {
            handleMainMenuButtonClick(2);
            return true;
        }
        if (isMouseOver(scaledMouseX, scaledMouseY, centerX - buttonWidth / 2f + halfButtonWidth + buttonSpacing, halfButtonY, halfButtonWidth, buttonHeight)) {
            handleMainMenuButtonClick(3);
            return true;
        }

        float exitButtonY = halfButtonY + buttonHeight + buttonSpacing;
        if (isMouseOver(scaledMouseX, scaledMouseY, centerX - buttonWidth / 2f, exitButtonY, buttonWidth, buttonHeight)) {
            handleMainMenuButtonClick(4);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleMainMenuButtonClick(int index) {
        switch (index) {
            case 0 -> this.client.setScreen(new SelectWorldScreen(this));
            case 1 -> {
                Screen screen = this.client.options.skipMultiplayerWarning ? new MultiplayerScreen(this) : new MultiplayerWarningScreen(this);
                this.client.setScreen(screen);
            }
            case 2 -> this.client.setScreen(new OptionsScreen(this, this.client.options));
            case 3 -> currentView = View.ALT_SCREEN;
            case 4 -> this.client.scheduleStop();
        }
    }

    private void renderAltScreen(int screenWidth, int screenHeight, float mouseX, float mouseY, long currentTime) {
        float totalWidth = 405;
        float totalHeight = 210;

        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float startX = centerX - totalWidth / 2f;
        float startY = centerY - totalHeight / 2f;

        float contentAlpha = 1f;
        List<AccountEntry> sortedAccounts = accountConfig.getSortedAccounts();

        scrollOffset += (targetScrollOffset - scrollOffset) * 0.2f;
        if (Math.abs(targetScrollOffset - scrollOffset) < 0.1f) scrollOffset = targetScrollOffset;

        // Координаты для верхней панели
        float topBarHeight = 28f;

        // Координаты для правой панели
        float rightListX = centerX - totalWidth / 2f;
        float rightListY = startY + topBarHeight + 5;
        float rightListWidth = 405;
        float rightListHeight = totalHeight - topBarHeight - 10;

        // Координаты для левой панели
        float leftSidebarWidth = 100;
        float leftSidebarHeight = 120f;
        float rightListBottomY = rightListY + rightListHeight;
        float leftSidebarY = rightListBottomY - leftSidebarHeight;

        // === НОВЫЙ ПОРЯДОК РЕНДЕРИНГА ===

        // 1. СНАЧАЛА правая панель (с blur и scissor) — самая "грязная" с точки зрения OpenGL
        accountRenderer.renderRightList(rightListX, rightListY, rightListWidth, rightListHeight, contentAlpha,
                sortedAccounts, scrollOffset, mouseX, mouseY, 1f, (int) FIXED_GUI_SCALE);

        // 2. ПОТОМ левая панель (с blur)
        accountRenderer.renderLeftSidebar(startX - 105, leftSidebarY, leftSidebarWidth, leftSidebarHeight, contentAlpha,
                accountConfig.getActiveAccountName(),
                accountConfig.getActiveAccountDate(),
                accountConfig.getActiveAccountSkin(),
                mouseX, mouseY, currentTime);

        // 3. В САМОМ КОНЦЕ верхняя панель — чтобы текст рендерился в "чистом" состоянии
        accountRenderer.renderTopBar(startX, startY, totalWidth, topBarHeight, contentAlpha,
                nicknameText, nicknameFieldFocused, mouseX, mouseY, currentTime);
    }

    private boolean handleAltScreenClick(float mouseX, float mouseY, Click click) {
        int screenWidth = getFixedScaledWidth();
        int screenHeight = getFixedScaledHeight();

        float totalWidth = 405f;
        float totalHeight = 210f;
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float startX = centerX - totalWidth / 2f;
        float startY = centerY - totalHeight / 2f;

        float topBarHeight = 28f;

        // 1. Верхняя панель: поле ввода и кнопка добавления
        float fieldHeight = 16f;
        float addButtonSize = 16f;
        float buttonGap = 4f;
        float fieldY = startY + (topBarHeight - fieldHeight) / 2f;
        float fieldX = startX + 5f;
        float fieldWidth = totalWidth - 10f - addButtonSize - buttonGap;

        if (accountRenderer.isMouseOver(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight)) {
            nicknameFieldFocused = true;
            return true;
        }
        nicknameFieldFocused = false;

        float addButtonX = fieldX + fieldWidth + buttonGap;
        if (accountRenderer.isMouseOver(mouseX, mouseY, addButtonX, fieldY, addButtonSize, addButtonSize)) {
            if (!nicknameText.isEmpty()) { addAccount(nicknameText); nicknameText = ""; }
            return true;
        }

        // 2. Правый список (такие же координаты, как в renderAltScreen)
        float rightListX = startX;
        float rightListY = startY + topBarHeight + 5f;
        float rightListWidth = 405f;
        float rightListHeight = totalHeight - topBarHeight - 10f;

        // 3. Левый сайдбар (такие же координаты, как в renderAltScreen)
        float leftSidebarWidth = 100f;
        float leftSidebarHeight = 120f;
        float rightListBottomY = rightListY + rightListHeight;
        float leftSidebarY = rightListBottomY - leftSidebarHeight;
        float leftSidebarX = startX - 105f;

        float btnWidth = leftSidebarWidth - 10f;
        float btnHeight = 18f;
        float btnX = leftSidebarX + 5f;
        float randomBtnY = leftSidebarY + 5f;
        float clearBtnY = randomBtnY + btnHeight + 4f;

        if (accountRenderer.isMouseOver(mouseX, mouseY, btnX, randomBtnY, btnWidth, btnHeight)) {
            addAccount(generateRandomNickname()); nicknameText = "";
            return true;
        }
        if (accountRenderer.isMouseOver(mouseX, mouseY, btnX, clearBtnY, btnWidth, btnHeight)) {
            accountConfig.clearAllAccounts(); targetScrollOffset = 0f; scrollOffset = 0f;
            return true;
        }

        // 4. Карточки в правом списке
        float accountListX = rightListX + 5f;
        float accountListY = rightListY + 5f;
        float accountListWidth = rightListWidth - 10f;
        float accountListHeight = rightListHeight - 10f;

        if (!accountRenderer.isMouseOver(mouseX, mouseY, accountListX, accountListY, accountListWidth, accountListHeight)) return false;

        float cardWidth = (accountListWidth - 5f) / 2f;
        float cardHeight = 40f;
        float cardGap = 5f;
        List<AccountEntry> sortedAccounts = accountConfig.getSortedAccounts();

        for (int i = 0; i < sortedAccounts.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            float cardX = accountListX + col * (cardWidth + cardGap);
            float cardY = accountListY + row * (cardHeight + cardGap) - scrollOffset;

            if (cardY + cardHeight < accountListY - 10 || cardY > accountListY + accountListHeight + 10) continue;

            float btnSize = 12f;
            float buttonYPos = cardY + cardHeight - btnSize - 5f;
            float pinButtonX = cardX + cardWidth - btnSize * 2f - 8f;
            float deleteButtonX = cardX + cardWidth - btnSize - 5f;

            if (accountRenderer.isMouseOver(mouseX, mouseY, pinButtonX, buttonYPos, btnSize, btnSize)) {
                AccountEntry entry = sortedAccounts.get(i);
                entry.togglePinned();
                if (entry.isPinned()) setActiveAccount(entry);
                accountConfig.save();
                return true;
            }
            if (accountRenderer.isMouseOver(mouseX, mouseY, deleteButtonX, buttonYPos, btnSize, btnSize)) {
                accountConfig.removeAccountByIndex(i);
                return true;
            }
            if (accountRenderer.isMouseOver(mouseX, mouseY, cardX, cardY, cardWidth, cardHeight)) {
                setActiveAccount(sortedAccounts.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentView != View.ALT_SCREEN) return false;
        float scaledMouseX = toFixedCoord(mouseX); float scaledMouseY = toFixedCoord(mouseY);
        int screenWidth = getFixedScaledWidth(); int screenHeight = getFixedScaledHeight();
        // тот же прямоугольник, что в renderAltScreen и mouseClicked - раньше тут были
        // отдельные устаревшие числа, скролл проверялся по несуществующей панели
        float totalWidth = 405f; float totalHeight = 210f; float topBarHeight = 28f;
        float centerX = screenWidth / 2f; float centerY = screenHeight / 2f;
        float startY = centerY - totalHeight / 2f;

        float listX = (centerX - totalWidth / 2f) + 5f;
        float listY = startY + topBarHeight + 5f + 5f;
        float listWidth = totalWidth - 10f;
        float listHeight = totalHeight - topBarHeight - 10f - 10f;

        if (accountRenderer.isMouseOver(scaledMouseX, scaledMouseY, listX, listY, listWidth, listHeight)) {
            float cardHeight = 40f; float cardGap = 5f;
            int rows = (int) Math.ceil(accountConfig.getSortedAccounts().size() / 2.0);
            // последний gap не считаем - он не принадлежит ни одному ряду
            float contentHeight = rows <= 0 ? 0f : rows * (cardHeight + cardGap) - cardGap;
            float maxScroll = Math.max(0f, contentHeight - listHeight);

            targetScrollOffset -= (float) verticalAmount * 25f;
            targetScrollOffset = MathHelper.clamp(targetScrollOffset, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (currentView == View.ALT_SCREEN) {
            if (nicknameFieldFocused) {
                int keyCode = input.key();
                if (keyCode == 259 && !nicknameText.isEmpty()) { nicknameText = nicknameText.substring(0, nicknameText.length() - 1); return true; }
                if (keyCode == 256) { nicknameFieldFocused = false; return true; }
                if (keyCode == 257 || keyCode == 335) {
                    if (!nicknameText.isEmpty()) { addAccount(nicknameText); nicknameText = ""; }
                    nicknameFieldFocused = false; return true;
                }
            }
            if (input.key() == 256) { currentView = View.MAIN_MENU; accountConfig.save(); return true; }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (currentView == View.ALT_SCREEN && nicknameFieldFocused) {
            char c = (char)input.codepoint();
            if ((Character.isLetterOrDigit(c) || c == '_') && nicknameText.length() < 16) {
                nicknameText += c; return true;
            }
        }
        return super.charTyped(input);
    }

    private void setActiveAccount(AccountEntry account) {
        accountConfig.setActiveAccount(account.getName(), account.getDate(), account.getSkin());
        SessionChanger.changeUsername(account.getName());
    }

    private void addAccount(String nickname) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        AccountEntry entry = new AccountEntry(nickname, date, null);
        accountConfig.addAccount(entry);
        setActiveAccount(entry);
    }

    private String generateRandomNickname() {
        Random random = new Random();
        StringBuilder username = new StringBuilder();
        char[] vowels = {'a', 'e', 'i', 'o', 'u'};
        char[] consonants = {'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z'};
        String finalUsername = null;
        int attempts = 0; final int MAX_ATTEMPTS = 10;
        List<AccountEntry> existingAccounts = accountConfig.getAccounts();
        do {
            username.setLength(0);
            int length = 6 + random.nextInt(5);
            boolean startWithVowel = random.nextBoolean();
            for (int i = 0; i < length; i++) {
                username.append(i % 2 == (startWithVowel ? 0 : 1) ? vowels[random.nextInt(vowels.length)] : consonants[random.nextInt(consonants.length)]);
            }
            if (random.nextInt(100) < 30) { username.append(random.nextInt(100)); }
            String tempUsername = username.substring(0, 1).toUpperCase() + username.substring(1);
            attempts++;
            boolean exists = existingAccounts.stream().anyMatch(acc -> acc.getName().equalsIgnoreCase(tempUsername));
            if (!exists) { finalUsername = tempUsername; break; }
        } while (attempts < MAX_ATTEMPTS);
        if (finalUsername == null) { finalUsername = username.substring(0, 1).toUpperCase() + username.substring(1) + (System.currentTimeMillis() % 1000); }
        return finalUsername;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }
    @Override
    public boolean shouldCloseOnEsc() { return false; }
    @Override
    public boolean shouldPause() { return false; }
}
