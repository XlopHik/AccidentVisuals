package accident.util.render.shader;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class MaskCommandCollector {

    private static MaskCommandCollector instance;

    public record MaskEntry(MatrixStack.Entry matricesEntry, Model<?> model, Object state) {}

    @Getter
    private final List<MaskEntry> entries = new ArrayList<>();

    private boolean collecting = false;

    public MaskCommandCollector() {
        instance = this;
    }

    public static MaskCommandCollector getInstance() { return instance; }

    public void beginFrame() {
        entries.clear();
        collecting = true;
    }

    public void endFrame() {
        collecting = false;
    }

    public boolean isCollecting() { return collecting; }

    public <S> void submit(Model<? super S> model, S state, MatrixStack.Entry matricesEntry) {
        if (!collecting) return;
        entries.add(new MaskEntry(matricesEntry, (Model<?>) model, state));
    }
}