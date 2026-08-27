package accident.screens.menu;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import accident.Initialization;
import accident.util.lang.LanguageManager;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;

import java.awt.Color;

public class LanguageScreen extends Screen {

    private static final Identifier BACKGROUND_TEXTURE = Identifier.of("accident", "textures/menu/back.png");
    private static final float FIXED_GUI_SCALE = 2.0f;

    private int lastWindowWidth = 0, lastWindowHeight = 0;
    private boolean initialized = false;
    private float ruHoverProgress = 0f;
    private float enHoverProgress = 0f;
    private float buttonHoverProgress = 0f;

    public LanguageScreen() {
        super(Text.literal("Language Selection"));
    }

    @Override
    protected void init() {
        initialized = false;
    }

    private int getFixedScaledWidth() {
        return (int) Math.ceil((double) client.getWindow().getFramebufferWidth() / FIXED_GUI_SCALE);
    }

    private int getFixedScaledHeight() {
        return (int) Math.ceil((double) client.getWindow().getFramebufferHeight() / FIXED_GUI_SCALE);
    }

    private float toFixedCoord(double coord) {
        return (float) (coord * (float) client.getWindow().getScaleFactor() / FIXED_GUI_SCALE);
    }

    private boolean isMouseOver(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float deltaTime = Math.min(delta, 0.05f);
        if (!initialized) {
            initialized = true;
        }

        int fixedWidth = getFixedScaledWidth();
        int fixedHeight = getFixedScaledHeight();

        float scaledMouseX = toFixedCoord(mouseX);
        float scaledMouseY = toFixedCoord(mouseY);

        float centerX = fixedWidth / 2f;
        float centerY = fixedHeight / 2f;

        Initialization.getInstance().getManager().getRenderCore().getTexturePipeline()
                .drawTexture(BACKGROUND_TEXTURE, 0, 0, fixedWidth, fixedHeight,
                        0, 0, 1, 1, new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}, new float[]{0, 0, 0, 0}, 1f);

        Render2D.beginOverlay();

        float panelWidth = 300;
        float panelHeight = 220;
        float panelX = centerX - panelWidth / 2f;
        float panelY = centerY - panelHeight / 2f;

        Render2D.blur(panelX, panelY, panelWidth, panelHeight, 12f, 5f, new Color(10, 12, 20, 30).getRGB());
        Render2D.rect(panelX, panelY, panelWidth, panelHeight, new Color(20, 25, 35, 60).getRGB(), 12);

        String title = LanguageManager.get("accident.language.title");
        float titleWidth = Fonts.TEST.getWidth(title, 14f);
        Fonts.TEST.draw(title, centerX - titleWidth / 2f, panelY + 20, 14f, -1);

        float btnWidth = 100;
        float btnHeight = 40;
        float btnSpacing = 20;
        float btnY = panelY + 60;

        float ruX = centerX - btnWidth - btnSpacing / 2f;
        float enX = centerX + btnSpacing / 2f;

        float lerpSpeed = 1f - (float) Math.pow(0.001f, deltaTime);
        ruHoverProgress = MathHelper.lerp(lerpSpeed, ruHoverProgress,
                isMouseOver(scaledMouseX, scaledMouseY, ruX, btnY, btnWidth, btnHeight) ? 1f : 0f);
        enHoverProgress = MathHelper.lerp(lerpSpeed, enHoverProgress,
                isMouseOver(scaledMouseX, scaledMouseY, enX, btnY, btnWidth, btnHeight) ? 1f : 0f);

        drawLangButton(ruX, btnY, btnWidth, btnHeight, "RU", "Русский", ruHoverProgress);
        drawLangButton(enX, btnY, btnWidth, btnHeight, "EN", "English", enHoverProgress);

        String confirmText = LanguageManager.get("accident.language.confirm");
        float confirmWidth = 120;
        float confirmHeight = 28;
        float confirmX = centerX - confirmWidth / 2f;
        float confirmY = panelY + panelHeight - confirmHeight - 20;

        boolean confirmHover = isMouseOver(scaledMouseX, scaledMouseY, confirmX, confirmY, confirmWidth, confirmHeight);
        buttonHoverProgress = MathHelper.lerp(lerpSpeed, buttonHoverProgress, confirmHover ? 1f : 0f);

        int brightness = (int) (buttonHoverProgress * 15);
        Color confirmBg = new Color(
                Math.min(255, 30 + brightness),
                Math.min(255, 35 + brightness),
                Math.min(255, 45 + brightness),
                150
        );
        Render2D.rect(confirmX, confirmY, confirmWidth, confirmHeight, confirmBg.getRGB(), 8);
        Render2D.outline(confirmX, confirmY, confirmWidth, confirmHeight, 1f, new Color(50, 55, 65, 80).getRGB(), 8);

        float textWidth = Fonts.TEST.getWidth(confirmText, 9f);
        Fonts.TEST.draw(confirmText, centerX - textWidth / 2f, confirmY + (confirmHeight / 2f) - 4, 9f,
                new Color(160, 160, 160).getRGB());

        Render2D.endOverlay();
    }

    private void drawLangButton(float x, float y, float width, float height, String code, String name, float hover) {
        int brightness = (int) (hover * 20);

        String currentLang = LanguageManager.getInstance().getCurrentLanguage();
        boolean isSelected = (code.equals("RU") && currentLang.equals("ru")) ||
                (code.equals("EN") && currentLang.equals("en"));

        Color baseColor = isSelected ? new Color(40, 80, 120) : new Color(30, 35, 45);
        Color bgColor = new Color(
                Math.min(255, baseColor.getRed() + brightness),
                Math.min(255, baseColor.getGreen() + brightness),
                Math.min(255, baseColor.getBlue() + brightness),
                150
        );

        Render2D.rect(x, y, width, height, bgColor.getRGB(), 8);
        Render2D.outline(x, y, width, height, 1f, new Color(50, 55, 65, 80).getRGB(), 8);

        if (hover > 0.01f) {
            int borderAlpha = (int) (hover * 100);
            Render2D.outline(x, y, width, height, 1f, new Color(100, 110, 130, borderAlpha).getRGB(), 8);
        }

        float codeWidth = Fonts.TEST.getWidth(code, 12f);
        float nameWidth = Fonts.TEST.getWidth(name, 8f);

        Fonts.TEST.draw(code, x + (width - codeWidth) / 2f, y + 10, 12f, new Color(255, 255, 255).getRGB());
        Fonts.TEST.draw(name, x + (width - nameWidth) / 2f, y + 26, 8f, new Color(160, 160, 160).getRGB());
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        float scaledMouseX = toFixedCoord(click.x());
        float scaledMouseY = toFixedCoord(click.y());
        int fixedWidth = getFixedScaledWidth();
        int fixedHeight = getFixedScaledHeight();
        float centerX = fixedWidth / 2f;
        float centerY = fixedHeight / 2f;

        float panelWidth = 300;
        float panelHeight = 220;
        float panelY = centerY - panelHeight / 2f;

        float btnWidth = 100;
        float btnHeight = 40;
        float btnSpacing = 20;
        float btnY = panelY + 60;
        float ruX = centerX - btnWidth - btnSpacing / 2f;
        float enX = centerX + btnSpacing / 2f;

        if (isMouseOver(scaledMouseX, scaledMouseY, ruX, btnY, btnWidth, btnHeight)) {
            LanguageManager.getInstance().setLanguage("ru");
            return true;
        }
        if (isMouseOver(scaledMouseX, scaledMouseY, enX, btnY, btnWidth, btnHeight)) {
            LanguageManager.getInstance().setLanguage("en");
            return true;
        }

        float confirmWidth = 120;
        float confirmHeight = 28;
        float confirmX = centerX - confirmWidth / 2f;
        float confirmY = panelY + panelHeight - confirmHeight - 20;

        if (isMouseOver(scaledMouseX, scaledMouseY, confirmX, confirmY, confirmWidth, confirmHeight)) {
            this.client.setScreen(new MainMenuScreen());
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }
}
