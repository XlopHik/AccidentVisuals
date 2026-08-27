package accident.modules.impl.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import accident.events.api.EventHandler;
import accident.events.impl.DrawEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SelectSetting;

import accident.util.render.Render2D;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class Hotbar extends ModuleStructure {

    private static Hotbar instance;

    private final SelectSetting leftHandMode = new SelectSetting("accident.module.hotbar.setting.lefthandmode.name", "accident.module.hotbar.setting.lefthandmode.desc")
            .value("Слитно", "Раздельно")
            .selected("Слитно");

    public final BooleanSetting customHealthFood = new BooleanSetting("accident.module.hotbar.setting.customhealthfood.name", "accident.module.hotbar.setting.customhealthfood.desc").setValue(true);

    private static final int ICON_SIZE  = 7;
    private static final int ICON_GAP   = 1;
    private static final int ICON_COUNT = 10;
    private static final int ICON_STEP  = ICON_SIZE + ICON_GAP;
    private static final int ROW_STEP   = ICON_SIZE + 2;

    private static final int EMPTY_BG   = 0xFF080808;

    private final List<Particle> hpParticles  = new ArrayList<>();
    private final List<Particle> absParticles = new ArrayList<>();
    private final List<Particle> foodParticles= new ArrayList<>();
    private final List<Particle> armorParticles= new ArrayList<>();
    private final Random rng = new Random();

    private float lastHealth     = -1f;
    private float lastAbsorption = -1f;
    private int   lastFood       = -1;
    private int   lastArmor      = -1;

    public Hotbar() {
        super("accident.module.hotbar.name", "accident.module.hotbar.desc", ModuleCategory.RENDER);
        settings(leftHandMode, customHealthFood);
        instance = this;
    }

    public static Hotbar getInstance() {
        return instance;
    }

    @EventHandler
    public void onDraw(DrawEvent event) {
        if (mc.player == null) return;

        DrawContext context = event.getDrawContext();
        PlayerEntity player = mc.player;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int cx = sw / 2;

        if (player.getOffHandStack().isEmpty()) {
            drawHotbarBase(context, cx - 90, sh - 25, 180, 20);
        } else if (leftHandMode.getSelected().equals("Слитно")) {
            drawHotbarBase(context, cx - 111, sh - 25, 201, 20);
            Render2D.rect(cx - 109 + 18, sh - 23, 0.5f, 15,
                    new Color(0x44FFFFFF, true).getRGB(), 0);
        } else {
            drawHotbarBase(context, cx - 90, sh - 25, 180, 20);
            drawHotbarBase(context, (int)(cx - 112.5f), sh - 25, 20, 20);
        }

        int selectedX = (int)(cx - 88 + player.getInventory().getSelectedSlot() * 19.8f);
        Render2D.rect(selectedX, sh - 24, 17, 17, new Color(0x890C0C0C, true).getRGB(), 5);

        renderItems(context, player);
        renderXpLevel(context);

        if (customHealthFood.isValue()) {
            renderHealthFood(player, cx, sh);
        }
    }

    private void renderHealthFood(PlayerEntity player, int cx, int sh) {
        if (player.isCreative()) return;

        float maxHp      = (float) player.getAttributeValue(EntityAttributes.MAX_HEALTH);
        float realHealth = Math.min(player.getHealth(), maxHp);
        float absorption = player.getAbsorptionAmount();
        int   realFood   = player.getHungerManager().getFoodLevel();
        int   armor      = player.getArmor();

        final float PER_ICON = 2.0f;

        int hpIcons    = (int) Math.ceil(realHealth / PER_ICON);
        int maxHpIcons = (int) Math.ceil(maxHp / PER_ICON);
        int absIcons   = absorption > 0 ? (int) Math.ceil(absorption / PER_ICON) : 0;
        int foodIcons  = (int) Math.ceil(realFood / PER_ICON);
        int armorIcons = (int) Math.ceil(armor / PER_ICON);

        int baseRowY = sh - 42;

        int displayHpRows = Math.max(1, (int) Math.ceil(maxHpIcons / 10.0));
        int currentHpRows = Math.max(1, (int) Math.ceil(hpIcons / 10.0));

        int topVisibleHpY = baseRowY - (currentHpRows - 1) * ROW_STEP;
        int rowAbs        = (absorption > 0) ? topVisibleHpY - ROW_STEP : topVisibleHpY;
        int rowArmor      = (absorption > 0) ? rowAbs - ROW_STEP : topVisibleHpY - ROW_STEP;

        if (lastHealth >= 0 && realHealth < lastHealth) {
            int prev = (int) Math.ceil(lastHealth / PER_ICON);
            for (int i = (int) Math.ceil(realHealth / PER_ICON); i < prev; i++) {
                int row = baseRowY - (i / 10) * ROW_STEP;
                spawnParticles(hpParticles, getHpIconX(cx, i % 10), row, 220, 50, 50);
            }
        }
        if (lastAbsorption >= 0 && absorption < lastAbsorption && lastAbsorption > 0) {
            int prev = (int) Math.ceil(lastAbsorption / PER_ICON);
            for (int i = (int) Math.ceil(absorption / PER_ICON); i < prev && i < 10; i++)
                spawnParticles(absParticles, getHpIconX(cx, i), rowAbs, 255, 200, 0);
        }
        if (lastFood >= 0 && realFood < lastFood) {
            int prev = (int) Math.ceil(lastFood / PER_ICON);
            for (int i = (int) Math.ceil(realFood / PER_ICON); i < prev && i < 10; i++) {
                int foodIdx = ICON_COUNT - 1 - i;
                spawnParticles(foodParticles, getFoodIconX(cx, foodIdx), baseRowY, 210, 150, 30);
            }
        }
        if (lastArmor >= 0 && armor < lastArmor && lastArmor > 0) {
            int prev = (int) Math.ceil(lastArmor / PER_ICON);
            for (int i = (int) Math.ceil(armor / PER_ICON); i < prev && i < 10; i++)
                spawnParticles(armorParticles, getHpIconX(cx, i), rowArmor, 150, 200, 255);
        }

        lastHealth     = realHealth;
        lastAbsorption = absorption;
        lastFood       = realFood;
        lastArmor      = armor;

        tickParticles(hpParticles);
        tickParticles(absParticles);
        tickParticles(foodParticles);
        tickParticles(armorParticles);

        boolean poison = player.hasStatusEffect(StatusEffects.POISON);
        boolean wither = player.hasStatusEffect(StatusEffects.WITHER);
        boolean hunger = player.hasStatusEffect(StatusEffects.HUNGER);
        float hpRatio = realHealth / maxHp;

        for (int row = 0; row < displayHpRows; row++) {
            int rowY = baseRowY - row * ROW_STEP;
            int startGlobal = row * 10;

            for (int i = 0; i < ICON_COUNT; i++) {
                int globalIdx = startGlobal + i;
                if (globalIdx >= maxHpIcons) continue;

                float fill = Math.max(0, Math.min(1f, (realHealth / PER_ICON) - globalIdx));
                int x = (int) getHpIconX(cx, i);

                Render2D.rect(x, rowY, ICON_SIZE, ICON_SIZE, EMPTY_BG, 2);

                if (fill > 0) {
                    int fw = Math.max(1, (int)(ICON_SIZE * fill));
                    Render2D.rect(x, rowY, fw, ICON_SIZE, getHpColor(poison, wither), 2);
                }
                if (hpRatio <= 0.2f && fill > 0) {
                    float f = (float)(0.3 + 0.3 * Math.sin(System.currentTimeMillis() / 150.0 + globalIdx));
                    Render2D.rect(x, rowY, ICON_SIZE, ICON_SIZE, ((int)(f * 150) << 24) | 0xFF2020, 2);
                }
            }
        }

        if (absIcons > 0) {
            for (int i = 0; i < ICON_COUNT; i++) {
                float fill = Math.max(0, Math.min(1f, (absorption / PER_ICON) - i));
                int x = (int) getHpIconX(cx, i);

                if (fill > 0) {
                    int fw = Math.max(1, (int)(ICON_SIZE * fill));
                    Render2D.rect(x, rowAbs, fw, ICON_SIZE, 0xDDFFCC00, 2);
                }
            }
        }

        if (armorIcons > 0) {
            for (int i = 0; i < ICON_COUNT; i++) {
                float fill = Math.max(0, Math.min(1f, (armor / PER_ICON) - i));
                int x = (int) getHpIconX(cx, i);

                Render2D.rect(x, rowArmor, ICON_SIZE, ICON_SIZE, EMPTY_BG, 2);
                if (fill > 0) {
                    int fw = Math.max(1, (int)(ICON_SIZE * fill));
                    Render2D.rect(x, rowArmor, fw, ICON_SIZE, 0xDD7AADCC, 2);
                }
            }
        }

        for (int i = 0; i < ICON_COUNT; i++) {
            int rightIdx = ICON_COUNT - 1 - i;
            float fill = Math.max(0, Math.min(1f, foodIcons - rightIdx));
            int x = (int) getFoodIconX(cx, i);

            Render2D.rect(x, baseRowY, ICON_SIZE, ICON_SIZE, EMPTY_BG, 2);
            if (fill > 0) {
                int fw = Math.max(1, (int)(ICON_SIZE * fill));
                Render2D.rect(x, baseRowY, fw, ICON_SIZE, getFoodColor(hunger, realFood), 2);
            }
        }

        renderParticles(hpParticles);
        renderParticles(absParticles);
        renderParticles(foodParticles);
        renderParticles(armorParticles);
    }

    private float getHpIconX(int cx, int i) {
        return cx - 90 + i * ICON_STEP;
    }

    private float getFoodIconX(int cx, int i) {
        int totalW = ICON_COUNT * ICON_STEP - ICON_GAP;
        return cx + 90 - totalW + i * ICON_STEP;
    }

    private int getHpColor(boolean poison, boolean wither) {
        if (wither) return 0xDD7A3030;
        if (poison) return 0xDD6AAF30;
        return 0xDDE03838;
    }

    private int getFoodColor(boolean hunger, int food) {
        if (hunger)      return 0xDD9B6E30;
        if (food > 14)   return 0xDDD4A840;
        if (food > 7)    return 0xDDB87830;
        return 0xDDA04020;
    }

    private void spawnParticles(List<Particle> list, float ix, float iy, int r, int g, int b) {
        int count = 6 + rng.nextInt(5);
        for (int i = 0; i < count; i++) {
            float px = ix + rng.nextFloat() * ICON_SIZE;
            float py = iy + rng.nextFloat() * ICON_SIZE;
            float vx = (rng.nextFloat() - 0.5f) * 2.2f;
            float vy = -(rng.nextFloat() * 1.8f + 0.2f);
            float sz = rng.nextFloat() * 2f + 0.8f;
            list.add(new Particle(px, py, vx, vy, sz, new Color(r, g, b, 230).getRGB(), 60 + rng.nextInt(40)));
        }
    }

    private void tickParticles(List<Particle> list) {
        Iterator<Particle> it = list.iterator();
        while (it.hasNext()) { Particle p = it.next(); p.tick(); if (p.dead()) it.remove(); }
    }

    private void renderParticles(List<Particle> list) {
        for (Particle p : list) {
            float t   = p.life / (float) p.maxLife;
            int alpha = (int)(t * ((p.color >> 24) & 0xFF));
            int col   = (alpha << 24) | (p.color & 0x00FFFFFF);
            float s   = p.size * t;
            if (s < 0.5f) continue;
            Render2D.rect((int)(p.x - s / 2), (int)(p.y - s / 2), (int) s, (int) s, col, 1);
        }
    }

    private void drawHotbarBase(DrawContext context, int x, int y, int width, int height) {
        int alpha   = 70;
        int bgColor = ((alpha * 30) << 24) | 0x141623;
        Render2D.blur((float)x, (float)y, (float)width, (float)height, 10f, 5f, bgColor);
        Render2D.outline(x, y, width, height, 0.35f,
                (155 << 24) | (90 << 16) | (90 << 8) | 90, 5);
    }

    private void renderItems(DrawContext context, PlayerEntity player) {
        int sh  = mc.getWindow().getScaledHeight();
        int cx  = mc.getWindow().getScaledWidth() / 2;
        int itemY = sh - 16 - 3;

        if (!player.getOffHandStack().isEmpty()) {
            if (leftHandMode.getSelected().equals("Слитно")) {
                renderItem(context, player.getOffHandStack(), cx - 109, itemY - 5);
            } else {
                renderItem(context, player.getOffHandStack(), cx - 111, itemY - 5);
            }
        }

        for (int i = 0; i < 9; i++) {
            int slotX  = cx - 90 + i * 20 + 2;
            boolean sel = i == player.getInventory().getSelectedSlot();
            renderItem(context, player.getInventory().getStack(i), slotX, sel ? itemY - 7 : itemY - 5);
        }
    }

    private void renderItem(DrawContext context, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        var m = context.getMatrices();
        m.pushMatrix();
        m.translate(x + 8, y + 12);
        m.scale(0.9f, 0.9f);
        m.translate(-(x + 8), -(y + 12));
        context.drawItem(stack, x, y);
        context.drawStackOverlay(mc.textRenderer, stack, x, y);
        m.popMatrix();
    }

    private void renderXpLevel(DrawContext context) {
        if (mc.player == null || mc.player.experienceLevel <= 0) return;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        String lvl = String.valueOf(mc.player.experienceLevel);
        context.drawText(mc.textRenderer, lvl,
                (sw - mc.textRenderer.getWidth(lvl)) / 2, sh - 35, 0xFF80FF80, true);
    }

    private static class Particle {
        float x, y, vx, vy, size;
        int color, life, maxLife;

        Particle(float x, float y, float vx, float vy, float size, int color, int life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.size = size; this.color = color; this.life = this.maxLife = life;
        }

        void tick() { x += vx; y += vy; vy += 0.03f; vx *= 0.96f; life--; }
        boolean dead() { return life <= 0; }
    }
}


