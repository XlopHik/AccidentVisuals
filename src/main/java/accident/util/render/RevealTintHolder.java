package accident.util.render;

// хранит цвет подмены для невидимой сущности между заполнением render state и её отрисовкой
public interface RevealTintHolder {

    int NO_TINT = -1;

    int accident$getRevealTint();

    void accident$setRevealTint(int tint);
}
