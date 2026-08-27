package accident.util.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

// один общий DynamicTransforms на все 2D-пассы мода вместо аллокации на каждый (ring buffer майна блокировался
// под Sodium, когда каждый пасс брал свою аллокацию - ~500 раз за кадр с открытым меню).
// шейдеры мода эти значения всё равно не читают, блок нужен только потому что его требует pipeline snippet
public final class Gui2DUniforms {

    private Gui2DUniforms() {}

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0f, 0f, 0f);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static GpuBufferSlice cached;

    /** Marks the allocation stale - call once per frame, before any 2D drawing. */
    public static void invalidate() {
        cached = null;
    }

    public static GpuBufferSlice slice() {
        if (cached == null) {
            cached = RenderSystem.getDynamicUniforms().write(
                    RenderSystem.getModelViewMatrix(),
                    COLOR_MODULATOR,
                    MODEL_OFFSET,
                    TEXTURE_MATRIX);
        }
        return cached;
    }
}
