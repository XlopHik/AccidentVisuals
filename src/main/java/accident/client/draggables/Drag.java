package accident.client.draggables;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import accident.Initialization;
import accident.modules.impl.hud.Hud;
import accident.util.ColorUtil;
import accident.util.animations.SweepAnim;
import accident.util.render.Render2D;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Drag {

    private static final float OUTLINE_OFFSET    = 3.0f;
    private static final float OUTLINE_THICKNESS = 1.0f;
    private static final int   OUTLINE_COLOR     = ColorUtil.rgba(255, 255, 255, 255);

    public static final Set<String> EXCLUDED_ELEMENTS =
            Set.of("Notifications", "Watermark", "Info", "ServerHelperHUD", "TotemsHud");

    private static final int   GRID_STEP      = 30;
    private static final float SNAP_THRESHOLD = 12f;
    private static final float SNAP_CENTER    = 10f;
    private static final float SNAP_ELEMENT   = 8f;
    private static final int   GUIDE_COLOR    = 0x70_6C8FFF;
    private static final int   LINE_COLOR     = 0x12_FFFFFF;
    private static final int   CENTER_COLOR   = 0x25_FFFFFF;

    private static HudElement draggingElement;
    private static int startX, startY;
    private static final Map<HudElement, SweepAnim> sweepAnimations = new HashMap<>();
    private static final Map<HudElement, Boolean>   wasHovered      = new HashMap<>();

    private static long lastDrawNs = 0L;

    private static boolean guideH = false;
    private static boolean guideV = false;
    private static float   guideHY, guideVX;

    public static void onDraw(DrawContext context, int mouseX, int mouseY, float delta, boolean isChatScreen) {
        HudManager hudManager = getHudManager();
        if (hudManager == null) return;

        Hud hud = Hud.getInstance();
        if (hud == null || !hud.isState()) return;

        if (!isChatScreen) {
            if (draggingElement != null) {
                draggingElement = null;
            }
            sweepAnimations.clear();
            wasHovered.clear();
            lastDrawNs = 0L;
        }

        guideH = false;
        guideV = false;

        if (isChatScreen && draggingElement != null) {
            int rawX = mouseX - startX;
            int rawY = mouseY - startY;

            if (hud.showGrid.isValue()) {
                int[] snapped = snap(rawX, rawY, hudManager);
                draggingElement.setX(snapped[0]);
                draggingElement.setY(snapped[1]);
            } else {
                draggingElement.setX(rawX);
                draggingElement.setY(rawY);
            }
        }

        long nowNs = System.nanoTime();
        float dt = (lastDrawNs == 0L)
                ? 1f / 60f
                : (float) Math.min((nowNs - lastDrawNs) / 1_000_000_000.0, 0.1);
        lastDrawNs = nowNs;

        for (HudElement element : hudManager.getEnabledElements()) {
            if (element instanceof AbstractHudElement ae) {
                if (isChatScreen && element == draggingElement) {
                    ae.updateJelly(dt);
                } else {
                    ae.snapRender();
                }
            }
        }

        if (isChatScreen && draggingElement != null && hud.showGrid.isValue()) {
            drawGrid(context);
        }

        hudManager.render(context, delta, mouseX, mouseY);

        if (isChatScreen && draggingElement != null && hud.showGrid.isValue()) {
            drawGuides(context);
        }

        if (isChatScreen) {
            for (HudElement element : hudManager.getEnabledElements()) {
                if (!element.visible()) {
                    sweepAnimations.remove(element);
                    wasHovered.remove(element);
                    continue;
                }
                if (EXCLUDED_ELEMENTS.contains(element.getName())) continue;
                // A pinned element cannot be moved, so a drag outline around
                // it would just be misleading.
                if (element instanceof AbstractHudElement ae && !ae.isDraggable()) continue;

                boolean isHovered       = isHovered(element, mouseX, mouseY);
                boolean prevHovered     = wasHovered.getOrDefault(element, false);
                float   rounding        = element.getRoundingRadius();
                float   offset          = OUTLINE_OFFSET;
                float   outlineX        = element.getX() - offset;
                float   outlineY        = element.getY() - offset;
                float   outlineWidth    = element.getWidth()  + offset * 2;
                float   outlineHeight   = element.getHeight() + offset * 2;
                float   outlineRounding = Math.max(0, rounding + offset);

                SweepAnim anim = sweepAnimations.computeIfAbsent(element, e -> new SweepAnim(0.05f));
                if (isHovered && !prevHovered) anim.start();
                else if (!isHovered && prevHovered) anim.reset();
                wasHovered.put(element, isHovered);
                anim.update();

                float progress = anim.getProgress();
                if (isHovered || anim.isActive()) {
                    Render2D.glowOutline(outlineX, outlineY, outlineWidth, outlineHeight,
                            OUTLINE_THICKNESS, OUTLINE_COLOR, outlineRounding, progress, 0.3f);
                }
                if (!isHovered && anim.isCompleted()) {
                    sweepAnimations.remove(element);
                    wasHovered.remove(element);
                }
            }
        }
    }


    private static int[] snap(int rawX, int rawY, HudManager hudManager) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int w  = draggingElement.getWidth();
        int h  = draggingElement.getHeight();

        int x = rawX;
        int y = rawY;

        int cx = sw / 2;
        int cy = sh / 2;

        if (Math.abs((x + w / 2f) - cx) < SNAP_CENTER) {
            x = cx - w / 2;
            guideV  = true;
            guideVX = cx;
        }
        if (Math.abs((y + h / 2f) - cy) < SNAP_CENTER) {
            y = cy - h / 2;
            guideH  = true;
            guideHY = cy;
        }

        for (HudElement other : hudManager.getEnabledElements()) {
            if (other == draggingElement || !other.visible()) continue;

            int ox = other.getX(), oy = other.getY();
            int ow = other.getWidth(), oh = other.getHeight();

            if (Math.abs(x - ox) < SNAP_ELEMENT)                         { x = ox;          guideV = true; guideVX = ox; }
            if (Math.abs((x + w) - (ox + ow)) < SNAP_ELEMENT)            { x = ox + ow - w; guideV = true; guideVX = ox + ow; }
            if (Math.abs(x - (ox + ow)) < SNAP_ELEMENT)                  { x = ox + ow;     guideV = true; guideVX = ox + ow; }
            if (Math.abs((x + w) - ox) < SNAP_ELEMENT)                   { x = ox - w;      guideV = true; guideVX = ox; }
            if (Math.abs((x + w / 2f) - (ox + ow / 2f)) < SNAP_ELEMENT) { x = ox + ow / 2 - w / 2; guideV = true; guideVX = ox + ow / 2f; }

            if (Math.abs(y - oy) < SNAP_ELEMENT)                         { y = oy;          guideH = true; guideHY = oy; }
            if (Math.abs((y + h) - (oy + oh)) < SNAP_ELEMENT)            { y = oy + oh - h; guideH = true; guideHY = oy + oh; }
            if (Math.abs(y - (oy + oh)) < SNAP_ELEMENT)                  { y = oy + oh;     guideH = true; guideHY = oy + oh; }
            if (Math.abs((y + h) - oy) < SNAP_ELEMENT)                   { y = oy - h;      guideH = true; guideHY = oy; }
            if (Math.abs((y + h / 2f) - (oy + oh / 2f)) < SNAP_ELEMENT) { y = oy + oh / 2 - h / 2; guideH = true; guideHY = oy + oh / 2f; }
        }

        int gridX = Math.round((float) x / GRID_STEP) * GRID_STEP;
        int gridY = Math.round((float) y / GRID_STEP) * GRID_STEP;
        if (!guideV && Math.abs(x - gridX) < SNAP_THRESHOLD) {
            x = gridX;
            guideV  = true;
            guideVX = gridX;
        }
        if (!guideH && Math.abs(y - gridY) < SNAP_THRESHOLD) {
            y = gridY;
            guideH  = true;
            guideHY = gridY;
        }

        return new int[]{x, y};
    }


    private static void drawGrid(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (int x = GRID_STEP; x < sw; x += GRID_STEP) {
            Render2D.rect(x - 0.25f, 0, 0.5f, sh, LINE_COLOR);
        }

        for (int y = GRID_STEP; y < sh; y += GRID_STEP) {
            Render2D.rect(0, y - 0.25f, sw, 0.5f, LINE_COLOR);
        }

        Render2D.rect(sw / 2f - 0.25f, 0, 0.5f, sh, CENTER_COLOR);
        Render2D.rect(0, sh / 2f - 0.25f, sw, 0.5f, CENTER_COLOR);
    }

    private static void drawGuides(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        if (guideH) {
            Render2D.rect(0, guideHY - 0.5f, sw, 1f, GUIDE_COLOR);
        }
        if (guideV) {
            Render2D.rect(guideVX - 0.5f, 0, 1f, sh, GUIDE_COLOR);
        }
    }


    public static void onMouseClick(Click click) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof ChatScreen)) return;

        if (click.button() == 0) {
            HudManager hudManager = getHudManager();
            if (hudManager == null) return;

            double mouseX = click.x();
            double mouseY = click.y();

            HudElement element = hudManager.getElementAt(mouseX, mouseY);
            if (element instanceof AbstractHudElement ae && ae.isDraggable()) {
                draggingElement = element;
                startX = (int) mouseX - element.getX();
                startY = (int) mouseY - element.getY();
            }
        }
    }

    public static void onMouseRelease(Click click) {
        if (click.button() == 0 && draggingElement != null) {
            draggingElement = null;
        }
    }

    public static void resetDragging() {
        if (draggingElement != null) {
            draggingElement = null;
        }
        sweepAnimations.clear();
        wasHovered.clear();
    }

    public static boolean isDragging() {
        return draggingElement != null;
    }

    private static boolean isHovered(HudElement element, double mouseX, double mouseY) {
        return mouseX >= element.getX() && mouseX <= element.getX() + element.getWidth()
                && mouseY >= element.getY() && mouseY <= element.getY() + element.getHeight();
    }

    private static HudManager getHudManager() {
        if (Initialization.getInstance() == null) return null;
        if (Initialization.getInstance().getManager() == null) return null;
        return Initialization.getInstance().getManager().getHudManager();
    }

    public static void tick() {
        HudManager hudManager = getHudManager();
        if (hudManager != null) hudManager.tick();
    }
}