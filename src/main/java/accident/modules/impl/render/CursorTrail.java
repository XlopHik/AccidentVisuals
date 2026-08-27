package accident.modules.impl.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import accident.events.api.EventHandler;
import accident.events.impl.DrawEvent; // Используем твое рабочее событие
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.ColorSetting;
import accident.modules.module.setting.implement.SliderSettings;

import accident.util.ColorUtil;
import accident.util.render.Render2D;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class CursorTrail extends ModuleStructure {

    private static final Identifier HEART_TEXTURE = Identifier.of("accident", "textures/world/heart.png");

    // --- ФИКСИРОВАННЫЕ НАСТРОЙКИ ---
    private final float particleSize = 26f;
    private final float density = 10f;

    // --- НАСТРОЙКИ В МЕНЮ ---
    private final ColorSetting color = new ColorSetting("accident.module.cursortrail.setting.color.name", "accident.module.cursortrail.setting.color.desc")
            // Ярко-розовый/красный с полной непрозрачностью для базы
            .setColor(new Color(0xFF, 0x50, 0x70, 255).getRGB());

    private final SliderSettings lifetime = new SliderSettings("accident.module.cursortrail.setting.lifetime.name", "accident.module.cursortrail.setting.lifetime.desc")
            .range(20, 1000).setValue(150);

    private final BooleanSetting sparkle = new BooleanSetting("accident.module.cursortrail.setting.sparkle.name", "accident.module.cursortrail.setting.sparkle.desc")
            .setValue(true);

    private final List<TrailParticle> particles = new ArrayList<>();
    private final Random random = new Random();

    private float lastMouseX = 0;
    private float lastMouseY = 0;
    private float accumulatedDistance = 0f;

    public CursorTrail() {
        super("accident.module.cursortrail.name", "accident.module.cursortrail.desc", ModuleCategory.RENDER);
        settings(color, lifetime, sparkle);
    }

    @Override
    public boolean deactivate() {
        particles.clear();
        accumulatedDistance = 0f;
        return super.deactivate();
    }

    @EventHandler
    public void onDraw(DrawEvent event) {
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            particles.clear();
            accumulatedDistance = 0f;
            return;
        }

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        float mouseX = (float) mc.mouse.getX() / mc.getWindow().getScaleFactor();
        float mouseY = (float) mc.mouse.getY() / mc.getWindow().getScaleFactor();

        mouseX = MathHelper.clamp(mouseX, 0, screenWidth);
        mouseY = MathHelper.clamp(mouseY, 0, screenHeight);

        float distance = (float) Math.hypot(mouseX - lastMouseX, mouseY - lastMouseY);

        accumulatedDistance += distance;

        int particlesToAdd = 0;
        if (accumulatedDistance >= density) {
            particlesToAdd = (int) (accumulatedDistance / density);
            accumulatedDistance -= particlesToAdd * density;
            particlesToAdd = Math.min(particlesToAdd, 100);
        }

        for (int i = 0; i < particlesToAdd; i++) {
            float t = (float) (i + 0.5f) / particlesToAdd;
            float spawnX = lastMouseX + (mouseX - lastMouseX) * t;
            float spawnY = lastMouseY + (mouseY - lastMouseY) * t;

            addParticle(spawnX, spawnY);
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        Iterator<TrailParticle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            TrailParticle particle = iterator.next();
            particle.update();
            if (particle.isDead()) {
                iterator.remove();
            } else {
                particle.render(event.getDrawContext());
            }
        }
    }

    private void addParticle(float x, float y) {
        int particleColor = color.getColor();

        if (sparkle.isValue()) {
            // При мерцании смешиваем с чисто белым для эффекта вспышки
            particleColor = ColorUtil.lerpColor(
                    color.getColor(),
                    0xFFFFFFFF,
                    random.nextFloat()
            );
        }

        float spread = particleSize * 0.3f;
        particles.add(new TrailParticle(
                x + (random.nextFloat() - 0.5f) * spread,
                y + (random.nextFloat() - 0.5f) * spread,
                particleColor,
                (int) lifetime.getValue(),
                particleSize
        ));
    }

    private class TrailParticle {
        private float x, y;
        private int color;
        private int maxLife;
        private int currentLife;
        private float size;

        private float rotation;
        private float rotationSpeed;

        public TrailParticle(float x, float y, int color, int maxLife, float size) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.maxLife = maxLife;
            this.currentLife = maxLife;
            this.size = size;

            this.rotation = random.nextFloat() * 360f;
            this.rotationSpeed = (random.nextFloat() - 0.5f) * 4f;
        }

        public void update() {
            currentLife--;
            rotation += rotationSpeed;
        }

        public boolean isDead() {
            return currentLife <= 0;
        }

        public void render(DrawContext context) {
            float lifeProgress = (float) currentLife / maxLife;

            // Размер остается 100% до последних 20% жизни
            float sizeMultiplier = lifeProgress > 0.2f ? 1.0f : (lifeProgress / 0.2f);
            float currentSize = size * sizeMultiplier;
            if (currentSize < 1f) return;

            // Держим альфу на максимуме до последних 5% жизни
            int baseAlpha = 255;
            float alphaMultiplier = lifeProgress > 0.05f ? 1.0f : (lifeProgress / 0.05f);
            int alpha = (int) (baseAlpha * alphaMultiplier);

            if (sparkle.isValue()) {
                float pulse = 0.9f + 0.1f * (float) Math.sin(currentLife * 0.4f);
                currentSize *= pulse;
            }

            // Основной цвет частицы
            int currentColor = (alpha << 24) | (color & 0x00FFFFFF);

            // Цвет для "ореола" (свечения): тот же оттенок, но с меньшей прозрачностью (около 30-40%)
            // Это создает эффект свечения, который визуально "приподнимает" частицу над темным фоном
            int glowAlpha = (int) (alpha * 0.4f);
            int glowColor = (glowAlpha << 24) | (color & 0x00FFFFFF);

            float half = currentSize / 2f;

            // 1. Рисуем свечение (чуть больше по размеру, рисуется первым, чтобы быть НА ЗАДНЕМ плане)
            Render2D.texture(
                    HEART_TEXTURE,
                    x - half * 1.4f,
                    y - half * 1.4f,
                    currentSize * 1.4f,
                    currentSize * 1.4f,
                    1f,
                    0f,
                    glowColor
            );

            // 2. Рисуем основное яркое сердечко поверх свечения
            Render2D.texture(
                    HEART_TEXTURE,
                    x - half,
                    y - half,
                    currentSize,
                    currentSize,
                    1f,
                    0f,
                    currentColor
            );
        }
    }
}
