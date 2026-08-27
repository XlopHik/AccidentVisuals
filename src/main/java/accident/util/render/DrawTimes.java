package accident.util.render;

// временная инструментация - сколько времени тратится в каждом 2D-пайплайне
public final class DrawTimes {

    private DrawTimes() {}

    private static long rect, outline, texture, blur, text;
    private static long stepWrite, stepPass;
    private static int frames;
    private static long lastReport = System.currentTimeMillis();

    public static long start() { return System.nanoTime(); }

    public static void rect(long t0) { rect += System.nanoTime() - t0; }
    public static void outline(long t0) { outline += System.nanoTime() - t0; }
    public static void texture(long t0) { texture += System.nanoTime() - t0; }
    public static void blur(long t0) { blur += System.nanoTime() - t0; }
    public static void text(long t0) { text += System.nanoTime() - t0; }

    public static void stepWrite(long t0) { stepWrite += System.nanoTime() - t0; }
    public static void stepPass(long t0) { stepPass += System.nanoTime() - t0; }

    public static void frame() {
        frames++;

        long now = System.currentTimeMillis();
        if (now - lastReport < 1000L || frames == 0) return;

        float f = frames * 1_000_000f; // nanoseconds to milliseconds per frame
        System.out.printf(
                "[drawtimes] ms/frame: rect=%.2f outline=%.2f texture=%.2f text=%.2f blur=%.2f | sum=%.2f%n",
                rect / f, outline / f, texture / f, text / f, blur / f,
                (rect + outline + texture + text + blur) / f);
        System.out.printf("[drawtimes]   rect split: encoder+write=%.2f renderpass=%.2f%n", stepWrite / f, stepPass / f);

        rect = outline = texture = blur = text = 0;
        stepWrite = stepPass = 0;
        frames = 0;
        lastReport = now;
    }
}
