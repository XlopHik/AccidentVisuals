package accident.util.repository.way;

import lombok.Getter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import accident.IMinecraft;
import accident.events.api.EventHandler;
import accident.events.api.EventManager;
import accident.events.impl.DrawEvent;
import accident.util.config.impl.way.WayConfig;
import accident.util.math.Projection;
import accident.util.render.Render2D;
import accident.util.render.font.Font;
import accident.util.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class WayRepository implements IMinecraft {
    private static WayRepository instance;
    private final List<Way> wayList = new ArrayList<>();
    private static final Identifier ARROW_TEXTURE = Identifier.of("accident", "textures/world/triangle2.png");

    public WayRepository() {
        instance = this;
    }

    public static WayRepository getInstance() {
        if (instance == null) {
            instance = new WayRepository();
        }
        return instance;
    }

    public void init() {
        EventManager.register(this);
        WayConfig.getInstance().load();
    }

    public boolean isEmpty() {
        return wayList.isEmpty();
    }

    public void addWay(String name, BlockPos pos, String server) {
        wayList.add(new Way(name, pos, server));
    }

    public void addWayAndSave(String name, BlockPos pos, String server) {
        addWay(name, pos, server);
        WayConfig.getInstance().save();
    }

    public boolean hasWay(String name) {
        return wayList.stream().anyMatch(way -> way.name().equalsIgnoreCase(name));
    }

    public Optional<Way> getWay(String name) {
        return wayList.stream()
                .filter(way -> way.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public void deleteWay(String name) {
        wayList.removeIf(way -> way.name().equalsIgnoreCase(name));
    }

    public void deleteWayAndSave(String name) {
        deleteWay(name);
        WayConfig.getInstance().save();
    }

    public void clearList() {
        wayList.clear();
    }

    public void clearListAndSave() {
        clearList();
        WayConfig.getInstance().save();
    }

    public int size() {
        return wayList.size();
    }

    public List<String> getWayNames() {
        return wayList.stream().map(Way::name).collect(Collectors.toList());
    }

    public List<String> getWayNamesForServer(String server) {
        return wayList.stream()
                .filter(way -> way.server().equalsIgnoreCase(server))
                .map(Way::name)
                .collect(Collectors.toList());
    }

    public void setWays(List<Way> ways) {
        wayList.clear();
        wayList.addAll(ways);
    }

    public String getCurrentServer() {
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) {
            return "";
        }
        return mc.getNetworkHandler().getServerInfo().address;
    }

    private boolean isInFrontOfCamera(Vec3d worldPos) {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return false;

        Vec3d cameraPos = camera.getCameraPos();
        Vec3d toPoint = worldPos.subtract(cameraPos);

        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3d lookDir = new Vec3d(lookX, lookY, lookZ);

        return lookDir.dotProduct(toPoint) > 0;
    }

    @EventHandler
    public void onRender2D(DrawEvent event) {
        if (isEmpty() || mc.player == null || mc.world == null) return;
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) return;

        String currentServer = getCurrentServer();
        DrawContext context = event.getDrawContext();
        Camera camera = mc.gameRenderer.getCamera();

        float screenWidth = mc.getWindow().getScaledWidth();
        float screenHeight = mc.getWindow().getScaledHeight();
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        for (Way way : wayList) {
            if (!way.server().equalsIgnoreCase(currentServer)) continue;

            Vec3d wayVec = way.pos().toCenterPos();
            boolean isForward = isInFrontOfCamera(wayVec);
            Vec3d screenPos = Projection.worldSpaceToScreenSpace(wayVec);

            boolean onScreen = screenPos.x > 0 && screenPos.x < screenWidth &&
                    screenPos.y > 0 && screenPos.y < screenHeight;

            if (isForward && onScreen) {
                double distance = mc.player.getEntityPos().distanceTo(wayVec);
                String fullText = way.name() + " - " + String.format("%.1f", distance) + "m";

                Font font = Fonts.TEST;
                float fontSize = 6f;
                float textWidth = font.getWidth(fullText, fontSize);
                float textHeight = font.getHeight(fontSize);
                float padding = 3f;

                float x = (float) screenPos.x - textWidth / 2;
                float y = (float) screenPos.y - textHeight / 2;

                Render2D.rect(x - padding, y - padding + 0.5f, textWidth + padding * 2, textHeight + padding * 2, 0xE0131315, 2f);
                font.drawCentered(fullText, (float) screenPos.x, y + 1f, fontSize, 0xFFFFFFFF);
            } else {
                double deltaX = wayVec.x - camera.getCameraPos().x;
                double deltaZ = wayVec.z - camera.getCameraPos().z;

                double yaw = Math.toRadians(camera.getYaw());
                double cos = Math.cos(yaw);
                double sin = Math.sin(yaw);

                double rotY = -(deltaZ * cos - deltaX * sin);
                double rotX = -(deltaX * cos + deltaZ * sin);

                float targetAngle = (float) (Math.atan2(rotY, rotX) * 180 / Math.PI);

                Way.animatedAngle = lerpAngle(Way.animatedAngle, targetAngle, 0.12f);

                Way.animatedRadius = MathHelper.lerp(0.1f, Way.animatedRadius, 45f);

                float arrowX = centerX + Way.animatedRadius * (float) Math.cos(Math.toRadians(Way.animatedAngle));
                float arrowY = centerY + Way.animatedRadius * (float) Math.sin(Math.toRadians(Way.animatedAngle));

                drawWayArrow(context, arrowX, arrowY, Way.animatedAngle, 0xCCFFFFFF);

                float textOffX = (Way.animatedRadius + 12f) * (float) Math.cos(Math.toRadians(Way.animatedAngle));
                float textOffY = (Way.animatedRadius + 12f) * (float) Math.sin(Math.toRadians(Way.animatedAngle));

                Fonts.TEST.drawCentered(way.name(), centerX + textOffX, centerY + textOffY - 1f, 5.5f, 0xCCFFFFFF);
            }
        }
    }

    private float lerpAngle(float start, float end, float pct) {
        float delta = ((end - start + 180) % 360 + 360) % 360 - 180;
        return start + delta * pct;
    }

    private void drawWayArrow(DrawContext context, float x, float y, float angle, int color) {
        float size = 16f;
        float halfSize = size / 2.0f;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().rotate((float) Math.toRadians(angle + 90));

        try {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    ARROW_TEXTURE,
                    (int) -halfSize, (int) -halfSize,
                    0f, 0f,
                    (int) size, (int) size,
                    (int) size, (int) size,
                    color
            );
        } catch (NoSuchMethodError e) {
            Render2D.rect(-halfSize, -halfSize, size, size, color, 1f);
        }

        context.getMatrices().popMatrix();
    }
}