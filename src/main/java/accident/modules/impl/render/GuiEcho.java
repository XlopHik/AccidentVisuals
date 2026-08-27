package accident.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import accident.events.api.EventHandler;
import accident.events.impl.SetScreenEvent;
import accident.events.impl.WorldRenderEvent;
import accident.modules.module.ModuleStructure;
import accident.modules.module.category.ModuleCategory;
import accident.modules.module.setting.implement.SliderSettings;
import accident.screens.clickgui.ClickGui;
import accident.util.Instance;
import accident.util.render.GuiCaptureTarget;
import accident.util.render.pipeline.GuiMaskPipeline;
import accident.util.render.сliemtpipeline.ClientPipelines;
import accident.util.render.texture.GpuTextureWrapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

// снимок ClickGUI в момент закрытия, подвешенный в мире там, куда смотрела камера
public class GuiEcho extends ModuleStructure {

    public static GuiEcho getInstance() {
        return Instance.get(GuiEcho.class);
    }

    private final SliderSettings duration = new SliderSettings("accident.module.guiecho.setting.duration.name", "accident.module.guiecho.setting.duration.desc")
            .range(500, 5000).setValue(2000);

    private final SliderSettings distance = new SliderSettings("accident.module.guiecho.setting.distance.name", "accident.module.guiecho.setting.distance.desc")
            .range(1.0f, 6.0f).setValue(2.5f);

    private final List<Ghost> ghosts = new ArrayList<>();
    private int ghostCounter = 0;

    // вытаскивает захваченные панели обратно из затемнения экрана ClickGUI (блюр его запекает).
    // подобрано на глаз - от одного затемнения было бы ~1.33, но блюр цепляет ещё и соседние
    // затемнённые пиксели, поэтому темнее и надо сильнее компенсировать
    private static final float GHOST_BRIGHTNESS = 1.55f;

    private SimpleFramebuffer guiLayer;
    private GuiMaskPipeline brightenPipeline;
    private int capturedWidth = 0, capturedHeight = 0;

    public GuiEcho() {
        super("accident.module.guiecho.name", "accident.module.guiecho.desc", ModuleCategory.RENDER);
        settings(duration, distance);
    }

    @Override
    public boolean deactivate() {
        clearGhosts();
        GuiCaptureTarget.end();
        if (guiLayer != null) { guiLayer.delete(); guiLayer = null; }
        if (brightenPipeline != null) { brightenPipeline.close(); brightenPipeline = null; }
        capturedWidth = 0;
        capturedHeight = 0;
        return false;
    }

    private boolean ensureGuiLayer(int width, int height) {
        if (width == capturedWidth && height == capturedHeight && guiLayer != null) return true;

        if (guiLayer != null) { guiLayer.delete(); guiLayer = null; }

        guiLayer = new SimpleFramebuffer("accident:gui_echo_layer", width, height, true);
        capturedWidth = width;
        capturedHeight = height;
        return guiLayer.getColorAttachmentView() != null;
    }

    // вызывается из ClickGui.renderGui перед отрисовкой панелей - направляет
    // все 2D-пайплайны в прозрачный буфер вместо мира
    public void beginGuiCapture() {
        if (!isState()) return;
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;
        if (!ensureGuiLayer(fb.textureWidth, fb.textureHeight)) return;

        // очистка в 0 делает нетронутые GUI пиксели по-настоящему прозрачными
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "accident:gui_echo_clear",
                guiLayer.getColorAttachmentView(), OptionalInt.of(0),
                guiLayer.getDepthAttachmentView(), OptionalDouble.of(1.0))) {
            // clear only
        }

        GuiCaptureTarget.begin(guiLayer);
    }

    // вызывается после отрисовки панелей: снимаем редирект и блитим захваченный
    // слой обратно на реальный фреймбуфер, чтобы игрок всё так же видел GUI
    public void endGuiCapture() {
        if (!GuiCaptureTarget.isActive()) return;
        GuiCaptureTarget.end();

        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || guiLayer == null) return;
        // аргумент drawBlit - это НАЗНАЧЕНИЕ, а получатель - источник (не наоборот),
        // перепутаешь - молча скопирует мир в слой и на экран ничего не попадёт
        guiLayer.drawBlit(fb.getColorAttachmentView());
    }

    @EventHandler
    public void onSetScreen(SetScreenEvent event) {
        if (!isState()) return;
        // событие в HEAD у setScreen, поле ещё не переприсвоено - mc.currentScreen тут
        // всё ещё закрывающийся экран
        if (mc.currentScreen instanceof ClickGui && !(event.getScreen() instanceof ClickGui)) {
            captureGhost();
        }
    }

    private void captureGhost() {
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return;
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;
        if (guiLayer == null || guiLayer.getColorAttachment() == null) return;

        int width = fb.textureWidth;
        int height = fb.textureHeight;

        GpuTexture texture = RenderSystem.getDevice().createTexture(
                () -> "accident:gui_echo_" + ghostCounter,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1
        );
        GpuTextureView textureView = RenderSystem.getDevice().createTextureView(texture);

        // слой уже содержит ровно GUI на прозрачном фоне - этот проход только
        // осветляет, компенсируя запечённое затемнение экрана
        if (brightenPipeline == null) brightenPipeline = new GuiMaskPipeline();
        brightenPipeline.render(textureView, guiLayer.getColorAttachmentView(), GHOST_BRIGHTNESS);
        GpuTextureWrapper wrapper = new GpuTextureWrapper(texture, textureView);

        Identifier id = Identifier.of("accident", "dynamic/gui_echo_" + (ghostCounter++));
        mc.getTextureManager().registerTexture(id, wrapper);

        // углы квада - фиксированные мировые точки, считаются один раз тут же от
        // позиции и поворота камеры и потом не обновляются, как настоящее фото на стене
        Quaternionf rotation = new Quaternionf(camera.getRotation());
        Vector3f forward = rotation.transform(new Vector3f(0, 0, -1));
        Vector3f right   = rotation.transform(new Vector3f(1, 0, 0));
        Vector3f up      = rotation.transform(new Vector3f(0, 1, 0));

        float dist = distance.getValue();
        float fovDeg = mc.options.getFov().getValue().floatValue();
        float halfHeight = dist * (float) Math.tan(Math.toRadians(fovDeg / 2.0));
        float aspect = (float) width / (float) height;
        float halfWidth = halfHeight * aspect;

        Vec3d camPos = camera.getCameraPos();
        Vec3d center = camPos.add(forward.x * dist, forward.y * dist, forward.z * dist);

        Vec3d rightVec = new Vec3d(right.x * halfWidth, right.y * halfWidth, right.z * halfWidth);
        Vec3d upVec = new Vec3d(up.x * halfHeight, up.y * halfHeight, up.z * halfHeight);

        // center/right/up храним отдельно, а не как готовые 4 угла - так квад
        // может каждый кадр сжиматься к центру, а не просто держать один размер
        ghosts.add(new Ghost(id, center, rightVec, upVec, System.currentTimeMillis()));
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (ghosts.isEmpty() || mc.world == null) return;

        long maxTime = (long) duration.getValue();
        long now = System.currentTimeMillis();

        Iterator<Ghost> it = ghosts.iterator();
        while (it.hasNext()) {
            Ghost ghost = it.next();
            if (now - ghost.spawnTime > maxTime) {
                disposeGhost(ghost);
                it.remove();
            }
        }
        if (ghosts.isEmpty()) return;

        MatrixStack matrices = e.getStack();
        VertexConsumerProvider.Immediate immediate = (VertexConsumerProvider.Immediate) e.getVertexConsumers();
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        for (Ghost ghost : ghosts) {
            renderGhost(matrices, immediate, ghost, cam, now, maxTime);
        }

        immediate.draw();
    }

    private void renderGhost(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Ghost ghost, Vec3d cam, long now, long maxTime) {
        float t = (now - ghost.spawnTime) / (float) maxTime;

        float fadeInEnd = 0.05f;
        float fadeOutStart = 0.7f;
        float alpha;
        if (t < fadeInEnd) {
            alpha = t / fadeInEnd;
        } else if (t > fadeOutStart) {
            alpha = 1f - (t - fadeOutStart) / (1f - fadeOutStart);
        } else {
            alpha = 1f;
        }
        alpha = Math.max(0f, Math.min(1f, alpha));
        int a = (int) (alpha * 255);

        // сжимается к своему центру всё время жизни - с ускорением, чтобы выглядело
        // как улетание вдаль, а не просто затухающая картинка
        float shrinkT = Math.max(0f, Math.min(1f, t));
        float scale = 1f - (shrinkT * shrinkT * shrinkT) * 0.9f;

        Vec3d topLeft = ghost.center.subtract(ghost.rightVec.multiply(scale)).add(ghost.upVec.multiply(scale));
        Vec3d topRight = ghost.center.add(ghost.rightVec.multiply(scale)).add(ghost.upVec.multiply(scale));
        Vec3d bottomRight = ghost.center.add(ghost.rightVec.multiply(scale)).subtract(ghost.upVec.multiply(scale));
        Vec3d bottomLeft = ghost.center.subtract(ghost.rightVec.multiply(scale)).subtract(ghost.upVec.multiply(scale));

        VertexConsumer buffer = immediate.getBuffer(ClientPipelines.GUI_ECHO_LAYER.apply(ghost.textureId));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // копия фреймбуфера идёт снизу вверх (конвенция OpenGL), а тут top/bottom
        // названы по мировому "верху" - переворот V возвращает картинку правильной стороной
        buffer.vertex(matrix, (float) (topLeft.x - cam.x), (float) (topLeft.y - cam.y), (float) (topLeft.z - cam.z))
                .texture(0f, 1f).color(255, 255, 255, a);
        buffer.vertex(matrix, (float) (topRight.x - cam.x), (float) (topRight.y - cam.y), (float) (topRight.z - cam.z))
                .texture(1f, 1f).color(255, 255, 255, a);
        buffer.vertex(matrix, (float) (bottomRight.x - cam.x), (float) (bottomRight.y - cam.y), (float) (bottomRight.z - cam.z))
                .texture(1f, 0f).color(255, 255, 255, a);
        buffer.vertex(matrix, (float) (bottomLeft.x - cam.x), (float) (bottomLeft.y - cam.y), (float) (bottomLeft.z - cam.z))
                .texture(0f, 0f).color(255, 255, 255, a);
    }

    private void disposeGhost(Ghost ghost) {
        mc.getTextureManager().destroyTexture(ghost.textureId);
    }

    private void clearGhosts() {
        for (Ghost ghost : ghosts) disposeGhost(ghost);
        ghosts.clear();
    }

    private record Ghost(Identifier textureId,
                         Vec3d center, Vec3d rightVec, Vec3d upVec, long spawnTime) {}
}
