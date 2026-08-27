package accident.util.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import java.util.ArrayList;
import java.util.List;

// хранилище отложенных рендеров сущностей для двухпроходного Charms
// целевые сущности (игроки) отменяются в pushEntityRenders и складываются сюда,
// после первого прохода снимается "before", реплеятся отложенные, снимается "after" -
// в итоге depth-diff содержит только пиксели целевой сущности, без мусора от остальных
public final class EntityRenderInterceptor {

    private static final EntityRenderInterceptor INSTANCE = new EntityRenderInterceptor();

    // защита от повторного перехвата во время replayAll
    public static boolean skipInterception = false;

    // если true - EntityRendererMixin и MixinPlayerEntityRenderer не рисуют неймтеги,
    // чтобы они не попадали в маску. включается только во время replayAllForMask
    public static boolean suppressLabels = false;

    private final List<PendingRender> pending = new ArrayList<>(2);

    private EntityRenderInterceptor() {}

    public static EntityRenderInterceptor getInstance() {
        return INSTANCE;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /** Store a deferred entity render (called from EntityRenderManagerMixin). */
    public void store(EntityRenderState state, CameraRenderState camera,
                      double x, double y, double z,
                      MatrixStack matrices, OrderedRenderCommandQueue queue) {
        pending.add(new PendingRender(state, camera, x, y, z, matrices, queue));
    }

    /** True when at least one entity render is pending replay. */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Реплеит все отложенные рендеры, на время выставляя skipInterception. */
    public void replayAll(EntityRenderManager entityRenderManager) {
        skipInterception = true;
        try {
            for (PendingRender r : pending) {
                entityRenderManager.render(
                        r.state, r.camera,
                        r.x, r.y, r.z,
                        r.matrices, r.queue
                );
            }
        } finally {
            skipInterception = false;
        }
    }

    // реплей с подавлением того, что засоряет depth и портит маску:
    // - onFire: пламя рисуется больше модели (1.4x) и всегда повёрнуто к камере,
    //   вылезает за силуэт и даёт лишние пиксели в маске
    // - leashDatas: поводок пишет в depth по диагонали, похоже на ноги армор-стенда -
    //   ставим null а не пустой список, т.к. EntityRenderer.render() проверяет именно на null
    // оба поля сохраняются и восстанавливаются, состояние не мутируется навсегда
    public void replayAllForMask(EntityRenderManager entityRenderManager) {
        skipInterception = true;
        suppressLabels   = true;
        try {
            for (PendingRender r : pending) {
                boolean savedFire = r.state.onFire;
                List<EntityRenderState.LeashData> savedLeash = r.state.leashDatas;
                r.state.onFire     = false;
                r.state.leashDatas = null;
                try {
                    entityRenderManager.render(
                            r.state, r.camera,
                            r.x, r.y, r.z,
                            r.matrices, r.queue
                    );
                } finally {
                    r.state.onFire     = savedFire;
                    r.state.leashDatas = savedLeash;
                }
            }
        } finally {
            suppressLabels   = false;
            skipInterception = false;
        }
    }

    // реплей с невидимым телом и без тени - остаются только неймтеги.
    // вызывать ПОСЛЕ композита Charms, чтобы подписи были без контура
    public void replayAllForLabels(EntityRenderManager entityRenderManager) {
        skipInterception = true;
        try {
            for (PendingRender r : pending) {
                boolean savedInvisible = r.state.invisible;
                float   savedShadow    = r.state.shadowRadius;
                r.state.invisible    = true;
                r.state.shadowRadius = 0f;
                try {
                    entityRenderManager.render(
                            r.state, r.camera,
                            r.x, r.y, r.z,
                            r.matrices, r.queue
                    );
                } finally {
                    r.state.invisible    = savedInvisible;
                    r.state.shadowRadius = savedShadow;
                }
            }
        } finally {
            skipInterception = false;
        }
    }

    /** Discard all pending renders (call after replayAll + dispatch). */
    public void clear() {
        pending.clear();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private record PendingRender(
            EntityRenderState state,
            CameraRenderState camera,
            double x, double y, double z,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue
    ) {}
}
