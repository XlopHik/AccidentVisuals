package accident.modules.impl.hud;

import accident.client.draggables.AbstractHudElement;
import accident.screens.clickgui.dropdown.ThemesColumn;
import accident.util.animations.Direction;
import accident.util.lang.LanguageManager;
import accident.util.render.Render2D;
import accident.util.render.font.Fonts;
import accident.util.render.shader.Scissor;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.io.ByteArrayInputStream;
import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import accident.modules.module.setting.implement.BooleanSetting;
import accident.modules.module.setting.implement.SelectSetting;

public class Media extends AbstractHudElement {

    public static final SelectSetting displayMode = new SelectSetting("accident.module.media.setting.mode.name", "accident.module.media.setting.mode.desc")
            .value("Default", "Mini")
            .selected("Default");

    public static final BooleanSetting showLyrics = new BooleanSetting("accident.module.media.setting.lyrics.name", "accident.module.media.setting.lyrics.desc")
            .setValue(false);

    public static final BooleanSetting worldLyrics = new BooleanSetting("accident.module.media.setting.worldlyrics.name", "accident.module.media.setting.worldlyrics.desc")
            .setValue(false)
            .visible(showLyrics::isValue);


    private String title = LanguageManager.get("accident.media.notrack");
    private String artist = LanguageManager.get("accident.media.waiting");
    private int duration = 0;
    private int position = 0;

    // плеер опрашивается раз в секунду, поэтому позицию докручиваем сами между опросами
    private long positionSampledAt = System.currentTimeMillis();

    // скролл строки лирики отдельно от скролла заголовка - он завязан на воспроизведение, а не на таймер
    private String lyricScrollFor;
    private float lyricScrollOffset;

    // Where the world-space line is pinned, and which chunk it was pinned for.
    private static final float WORLD_DISTANCE = 11f;
    private static final float WORLD_HEIGHT = 2.2f;
    private static final float WORLD_SPAN = 4.5f;
    private static final float WORLD_FONT_SIZE = 40f;

    // показываем по чуть-чуть слов и перепиниваем, иначе строка уедет за спину при повороте
    private static final int WORLD_CHUNK_CHARS = 18;

    // target - куда едем, world - где рисуем сейчас; разделены, чтобы смена чанка плавно ехала, а не телепортировалась
    private Vec3d worldAnchor;
    private Vec3d worldRight;
    private Vec3d targetAnchor;
    private Vec3d targetRight;
    private String worldAnchorFor;

    private float chunkFade = 0f;
    private long lastWorldFrame = 0L;

    // если новая точка дальше этого - значит игрок развернулся, тогда не едем туда, а просто перескакиваем с фейдом
    private static final double ANCHOR_SLIDE_LIMIT = 14.0;
    private byte[] artworkBytes;
    private byte[] lastArtworkBytes;
    private Identifier artworkTexture;
    private long lastUpdate = System.currentTimeMillis();
    // плеер отдаёт позицию только целыми секундами, опрос 4 раза в секунду ловит момент смены точнее
    private static final long UPDATE_INTERVAL = 250;
    private volatile long lastTickMs = 0L;
    private Thread pollerThread;
    private boolean sessionActive;
    private String playerName = LanguageManager.get("accident.media.nosource");

    private static final float FONT_SIZE = 6f;
    private static final float ARTWORK_SIZE = 45;
    private static final float PROGRESS_BAR_HEIGHT = 2f;
    private static final float CORNER_RADIUS = 6f;
    private static final float FIXED_WIDTH = 180f;

    private float animatedHeight = 45;
    private long lastUpdateTime = System.currentTimeMillis();
    private static final float ANIMATION_SPEED = 8.0f;

    // Dynamic Island: пилюля, ширина по тексту, а не фиксированная как в Default
    private static final float MINI_HEIGHT = 18f;
    private static final float MINI_ARTWORK_SIZE = 13f;
    private static final float MINI_FONT_SIZE = 6f;
    private static final float MINI_PAD = 3f;
    private static final float MINI_TEXT_GAP = 5f;
    private static final float MINI_TOP_MARGIN = 3f;
    // при наведении остров разворачивается в карточку - выше, шире, углы не круглые
    private static final float MINI_EXPAND_HEIGHT = 44f;
    private static final float MINI_EXPAND_WIDTH = 96f;
    private static final float MINI_EXPAND_RADIUS = 8f;
    private static final float MINI_DETAIL_FONT_SIZE = 5.5f;

    private float hoverAnim = 0f;

    private static final float MINI_MAX_TEXT = 150f;
    private static final float MINI_EMPTY_WIDTH = 70f;

    // Бегущая строка
    private static final float SCROLL_SPEED = 30f; // пикселей в секунду
    private static final long SCROLL_PAUSE_MS = 6500L;
    private static final float SCROLL_GAP = 20f;

    // Title scroll
    private float titleScrollOffset = 0f;
    private long titleScrollPauseUntil = 0L;
    private long titleScrollLastTime = 0L;

    // Artist scroll
    private float artistScrollOffset = 0f;
    private long artistScrollPauseUntil = 0L;
    private long artistScrollLastTime = 0L;

    public Media() {
        super("Media", 0, 0, (int) FIXED_WIDTH, 45, true);
        settings(displayMode, showLyrics, worldLyrics);
        stopAnimation();
    }

    // Mini всегда прибит к верхнему центру, драг тут только мешал бы
    @Override
    public boolean isDraggable() {
        return !displayMode.isSelected("Mini");
    }

    private int getPriority(String owner) {
        owner = owner.toLowerCase();
        if (owner.contains("spotify")) return 0;
        if (owner.contains("яндекс музыка")) return 1;
        if (owner.contains("yandex")) return 2;
        if (owner.contains("chrome")) return 3;
        if (owner.contains("edge") || owner.contains("microsoft edge")) return 4;
        if (owner.contains("ayugram")) return 5;
        if (owner.contains("telegram")) return 6;
        if (owner.contains("vk") || owner.contains("vkontakte")) return 7;
        return 99;
    }

    private String formatDuration(long duration) {
        long seconds = duration % 60;
        long minutes = duration / 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private float lerp(float current, float target, float deltaTime) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * ANIMATION_SPEED));
        return current + (target - current) * factor;
    }


    private float updateScroll(float currentOffset, long pauseUntil, long lastTime,
                               float textWidth, float maxWidth) {
        long now = System.currentTimeMillis();

        if (textWidth <= maxWidth) {
            return 0f;
        }

        float offset = currentOffset;
        float cycleDistance = textWidth + SCROLL_GAP;

        // Если сейчас пауза — стоим на месте
        if (now < pauseUntil) {
            return offset;
        }

        // Вычисляем dt в секундах
        float dt;
        if (lastTime == 0L) {
            dt = 1f / 60f;
        } else {
            dt = (now - lastTime) / 1000f;
            dt = Math.min(dt, 0.1f); // Ограничиваем максимум
        }

        // Движение
        offset += SCROLL_SPEED * dt;

        // Конец цикла — сброс в начало и пауза
        if (offset >= cycleDistance) {
            offset = 0f;
            pauseUntil = now + SCROLL_PAUSE_MS;
        }

        // Сохраняем lastTime (нужно вернуть через поле, но для простоты обновим напрямую)
        return offset;
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        // опрос идёт из одного живого потока, а не нового на каждый тик - WinRT завязан на COM apartment
        // потока, а пересоздавать поток 4 раза в секунду значит пересоздавать и apartment
        lastTickMs = System.currentTimeMillis();
        ensurePoller();

        boolean hasMedia = !LanguageManager.get("accident.media.notrack").equals(title) && !LanguageManager.get("accident.media.waiting").equals(title) && duration > 0;
        boolean inChat = isChat(mc.currentScreen);

        if (hasMedia || inChat) {
            startAnimation();
        } else {
            stopAnimation();
        }

        // анимация размера живёт в рендере, а не тут - tick() всего 20 раз в секунду, на 144Hz будет видно ступеньки
        if (!displayMode.isSelected("Mini")) {
            setWidth((int) FIXED_WIDTH);
        }
    }

    private void ensurePoller() {
        if (pollerThread != null) return;

        pollerThread = new Thread(() -> {
            while (true) {
                // молчит, если элемент перестал тикать (выключен / вышли из мира), не долбит апи впустую
                if (System.currentTimeMillis() - lastTickMs < 2000L) {
                    try {
                        pollOnce();
                    } catch (Throwable ignored) {
                    }
                }

                try {
                    Thread.sleep(UPDATE_INTERVAL);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "accident-media-poller");

        pollerThread.setDaemon(true);
        pollerThread.start();
    }

    private void pollOnce() {
        try {

            List<IMediaSession> sessions = MediaPlayerInfo.Instance.getMediaSessions();
            sessionActive = sessions == null || sessions.isEmpty();

            // каждый getMedia()/getOwner() - вызов в нативную dll, раньше спрашивали сессию по несколько раз,
            // теперь спрашиваем раз и переиспользуем ответ
            IMediaSession session = null;
            MediaInfo media = null;
            int bestPriority = Integer.MAX_VALUE;

            if (sessions != null) {
                for (IMediaSession candidate : sessions) {
                    MediaInfo info = candidate.getMedia();
                    if (info == null) continue;

                    boolean hasAnything =
                            (info.getTitle() != null && !info.getTitle().isEmpty())
                                    || (info.getArtist() != null && !info.getArtist().isEmpty())
                                    || info.getDuration() > 0
                                    || (info.getArtworkPng() != null && info.getArtworkPng().length > 0);
                    if (!hasAnything) continue;

                    int priority = getPriority(candidate.getOwner());
                    if (priority < bestPriority) {
                        bestPriority = priority;
                        session = candidate;
                        media = info;
                    }
                }
            }

            if (!sessionActive && session != null) {
                String newTitle = (media.getTitle() == null || media.getTitle().isEmpty()) ? "..." : media.getTitle();
                String newArtist = (media.getArtist() == null || media.getArtist().isEmpty()) ? "..." : media.getArtist();

                if (!newTitle.equals(title)) {
                    titleScrollOffset = 0f;
                    titleScrollPauseUntil = System.currentTimeMillis() + SCROLL_PAUSE_MS;
                    titleScrollLastTime = System.currentTimeMillis();
                }
                if (!newArtist.equals(artist)) {
                    artistScrollOffset = 0f;
                    artistScrollPauseUntil = System.currentTimeMillis() + SCROLL_PAUSE_MS;
                    artistScrollLastTime = System.currentTimeMillis();
                }

                title = newTitle;
                artist = newArtist;
                duration = (int) media.getDuration();
                int newPosition = (int) media.getPosition();
                // ловим момент именно смены позиции - так граница секунды точнее, лирика не отстаёт
                if (newPosition != position) {
                    positionSampledAt = System.currentTimeMillis();
                }
                position = newPosition;
                artworkBytes = media.getArtworkPng();

                String owner = session.getOwner().toLowerCase();
                if (owner.contains("spotify")) playerName = "Spotify";
                else if (owner.contains("яндекс музыка")) playerName = "Ya. Music";
                else if (owner.contains("yandex")) playerName = "Yandex";
                else if (owner.contains("chrome")) playerName = "Chrome";
                else if (owner.contains("edge") || owner.contains("microsoft edge")) playerName = "Edge";
                else if (owner.contains("ayugram")) playerName = "Ayugram";
                else if (owner.contains("telegram")) playerName = "Telegram";
                else if (owner.contains("vk") || owner.contains("vkontakte")) playerName = "VK";
                else playerName = "Ya. Music";
            } else {
                title = LanguageManager.get("accident.media.waiting");
                artist = "";
                duration = 0;
                position = 0;
                artworkBytes = null;
                playerName = LanguageManager.get("accident.media.noconnection");
            }
        } catch (Exception e) {
            title = LanguageManager.get("accident.media.trackerror");
            artist = "—";
            duration = 0;
            position = 0;
            artworkBytes = null;
            playerName = LanguageManager.get("accident.media.sourceerror");
        }
    }

    /** Per-frame sizing, so the open/close easing is as smooth as the frame rate. */
    private void updateMiniLayout() {
        boolean mini = displayMode.isSelected("Mini");

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        updateHover(mini, deltaTime);

        float targetHeight = mini ? MINI_HEIGHT + MINI_EXPAND_HEIGHT * hoverAnim : 45f;

        animatedHeight = lerp(animatedHeight, targetHeight, deltaTime);
        if (Math.abs(animatedHeight - targetHeight) < 0.3f) animatedHeight = targetHeight;

        setHeight((int) Math.ceil(animatedHeight));

        if (mini) {
            setWidth((int) Math.ceil(miniWidth() + MINI_EXPAND_WIDTH * hoverAnim));
            // всегда по центру, не двигаем руками - пересчитываем при ресайзе окна и смене текста
            setX((mc.getWindow().getScaledWidth() - getWidth()) / 2);
            setY((int) MINI_TOP_MARGIN);
            snapRender();
        }
    }

    // остров реагирует на курсор только когда открыт экран - в игре мышь зажата на прицеле
    private void updateHover(boolean mini, float deltaTime) {
        boolean hovered = false;

        if (mini && hasMedia() && mc.currentScreen != null) {
            double scale = mc.getWindow().getScaleFactor();
            double mouseX = mc.mouse.getX() / scale;
            double mouseY = mc.mouse.getY() / scale;

            hovered = mouseX >= getX() && mouseX <= getX() + getWidth()
                    && mouseY >= getY() && mouseY <= getY() + getHeight();
        }

        float target = hovered ? 1f : 0f;
        hoverAnim += (target - hoverAnim) * Math.min(1f, deltaTime * 12f);
        if (Math.abs(target - hoverAnim) < 0.005f) hoverAnim = target;
    }

    // null пока показываем заголовок - чтобы не свипать подсветку по не-лирике
    private LyricsProvider.Current currentLyric() {
        if (!showLyrics.isValue()) return null;

        LyricsProvider.Current line = LyricsProvider.currentLine(
                title, artist, duration, smoothPositionMs());
        if (line == null || line.text() == null || line.text().isEmpty()) return null;
        return line;
    }

    /** Last polled position carried forward by real time since it was sampled. */
    private int smoothPositionMs() {
        long elapsed = System.currentTimeMillis() - positionSampledAt;
        // клэмп, чтобы зависание опроса (пауза, разрыв сессии) не утащило подсветку за конец строки
        elapsed = Math.max(0, Math.min(elapsed, 1500));
        return position * 1000 + (int) elapsed;
    }

    private String miniText() {
        LyricsProvider.Current line = currentLyric();
        return line != null ? line.text() : title;
    }

    private float miniWidth() {
        boolean hasMedia = hasMedia();
        if (!hasMedia) return MINI_EMPTY_WIDTH;

        float textW = Math.min(Fonts.TEST.getWidth(miniText(), MINI_FONT_SIZE), MINI_MAX_TEXT);
        return MINI_PAD + MINI_ARTWORK_SIZE + MINI_TEXT_GAP + textW + MINI_PAD * 2.5f;
    }

    private boolean hasMedia() {
        return !LanguageManager.get("accident.media.notrack").equals(title)
                && !LanguageManager.get("accident.media.waiting").equals(title)
                && duration > 0;
    }

    private void renderArtwork(DrawContext context, float x, float y, float size, int alpha, float cornerRadius) {
        if (artworkBytes != null && artworkBytes.length > 0) {
            if (lastArtworkBytes == null || !Arrays.equals(artworkBytes, lastArtworkBytes)) {
                try {
                    if (artworkTexture != null) {
                        mc.getTextureManager().destroyTexture(artworkTexture);
                    }
                    NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(artworkBytes));
                    NativeImageBackedTexture dynamicTexture = new NativeImageBackedTexture(
                            () -> "artwork_" + System.currentTimeMillis(),
                            nativeImage
                    );
                    artworkTexture = Identifier.of("media_hud", "artwork_" + System.currentTimeMillis());
                    mc.getTextureManager().registerTexture(artworkTexture, dynamicTexture);
                    lastArtworkBytes = artworkBytes.clone();
                } catch (Exception e) {
                    e.printStackTrace();
                    artworkTexture = null;
                }
            }

            if (artworkTexture != null) {
                Render2D.texture(artworkTexture, x, y, size, size, 0, 0, 1, 1,
                        (alpha << 24) | 0xFFFFFF, cornerRadius);
                return;
            }
        }
        Render2D.rect(x, y, size, size, (alpha << 24) | 0x202020, cornerRadius);
    }

    private int interpolateColor(int color1, int color2, float factor) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        return ((int)(r1 + factor * (r2 - r1)) << 16) | ((int)(g1 + factor * (g2 - g1)) << 8) | (int)(b1 + factor * (b2 - b1));
    }

    private int getMixedColor(float alphaFactor) {
        ThemesColumn.Theme theme = ThemesColumn.getCurrentGlobalTheme();
        if (theme == null) theme = ThemesColumn.Theme.DEFAULT;
        int[] palette = theme.palette;

        if (palette == null || palette.length == 0) {
            int alpha = Math.max(0, Math.min(255, (int)(alphaFactor * 255)));
            return (alpha << 24) | 0xFFFFFF;
        }
        if (palette.length == 1) {
            int alpha = Math.max(0, Math.min(255, (int)(alphaFactor * 255)));
            return (alpha << 24) | (palette[0] & 0xFFFFFF);
        }

        long timeMs = System.currentTimeMillis();
        float indexProgress = (float)((timeMs % 6000L) / 6000.0 * palette.length);
        int index1 = (int) indexProgress % palette.length;
        int index2 = (index1 + 1) % palette.length;
        float fraction = indexProgress - (int) indexProgress;

        int mixed = interpolateColor(palette[index1], palette[index2], fraction);
        int alpha = Math.max(0, Math.min(255, (int)(alphaFactor * 255)));
        return (alpha << 24) | (mixed & 0xFFFFFF);
    }

    private void renderMiniMode(DrawContext context, int alpha) {
        renderIsland(context, alpha, getX(), getY(), getWidth(), getHeight());
    }

    // остров можно нарисовать где угодно - чтобы HUD-элемент и меню юзали одну реализацию, а не дублировали
    public void renderInMenu(DrawContext context, float centreX, float topY, float alpha) {
        // меню не тикает этот элемент, поэтому будим поллер отсюда сами
        lastTickMs = System.currentTimeMillis();
        ensurePoller();

        if (!hasMedia()) return;

        float width = miniWidth();
        renderIsland(context, (int) (alpha * 255f), centreX - width / 2f, topY, width, MINI_HEIGHT);
    }

    private void renderIsland(DrawContext context, int alpha, float x, float y, float width, float height) {
        float alphaFactor = alpha / 255.0f;

        // свёрнуто - пилюля (радиус = половина высоты), развёрнуто - плавно едет к обычному скруглению углов
        float pillRadius = MINI_HEIGHT / 2f;
        float radius = pillRadius + (MINI_EXPAND_RADIUS - pillRadius) * hoverAnim;
        if (hoverAnim < 0.01f) radius = height / 2f;

        int glassAlpha = (int)(alphaFactor * 60);
        Render2D.blur(x, y, width, height, 10f, radius, (glassAlpha << 24) | 0x0F121F);
        Scissor.enable(x, y, width, height);

        if (hasMedia()) {
            // свёрнуто всё центрируется по пилюле, развёрнуто заголовок остаётся на месте, а деталка растёт снизу
            float mainCenterY = y + MINI_HEIGHT / 2f;

            float artY = mainCenterY - MINI_ARTWORK_SIZE / 2f;
            renderArtwork(context, x + MINI_PAD, artY, MINI_ARTWORK_SIZE,
                    (int)(255 * alphaFactor), MINI_ARTWORK_SIZE / 2f);

            LyricsProvider.Current lyric = currentLyric();
            String text = lyric != null ? lyric.text() : title;
            float textX = x + MINI_PAD + MINI_ARTWORK_SIZE + MINI_TEXT_GAP;
            float maxTextWidth = width - (textX - x) - MINI_PAD * 2.5f;
            float textW = Fonts.TEST.getWidth(text, MINI_FONT_SIZE);
            float textY = mainCenterY - MINI_FONT_SIZE / 2f;

            // у 5-аргументной перегрузки последний параметр - это scale, а не радиус; 0 схлопывал scissor
            Scissor.enable(textX, y, maxTextWidth, MINI_HEIGHT);

            if (lyric != null) {
                // лирику скроллим только до места пения, без зацикливания как у бегущего заголовка
                float offset = lyricScroll(text, lyric.progress(), textW, maxTextWidth);
                drawLyricText(text, textX - offset, textY, alphaFactor, lyric);
            } else {
                advanceTitleScroll(textW, maxTextWidth);
                if (textW <= maxTextWidth) {
                    drawLyricText(text, textX, textY, alphaFactor, null);
                } else {
                    float cycle = textW + SCROLL_GAP;
                    drawLyricText(text, textX - titleScrollOffset, textY, alphaFactor, null);
                    drawLyricText(text, textX - titleScrollOffset + cycle, textY, alphaFactor, null);
                }
            }
            Scissor.disable();

            if (hoverAnim > 0.01f) {
                renderMiniDetails(x, y, width, alphaFactor);
            }
        } else {
            String message = LanguageManager.get("accident.media.nomedia");
            float messageWidth = Fonts.TEST.getWidth(message, MINI_FONT_SIZE);
            Fonts.TEST.draw(message, x + (width - messageWidth) / 2f,
                    y + (height - MINI_FONT_SIZE) / 2f, MINI_FONT_SIZE,
                    (int)(150 * alphaFactor) << 24 | 0xFFFFFF);
        }

        Scissor.disable();
    }

    // строка лирики висит в мире, а не на HUD - каждый глиф ставится по прямой в мировых координатах
    // и проецируется отдельно, перспектива сама даёт наклон и сужение при взгляде под углом
    @accident.events.api.EventHandler
    public void onDrawWorldLyrics(accident.events.impl.DrawEvent event) {
        if (!isState() || !showLyrics.isValue() || !worldLyrics.isValue()) return;
        if (mc.player == null || mc.world == null || !hasMedia()) return;

        LyricsProvider.Current lyric = currentLyric();
        if (lyric == null) return;

        List<String> chunks = chunk(lyric.text());
        if (chunks.isEmpty()) return;

        int index = Math.min(chunks.size() - 1, (int) (lyric.progress() * chunks.size()));
        String text = chunks.get(index);

        // прогресс внутри текущего чанка, а не всей строки
        float span = 1f / chunks.size();
        float chunkProgress = Math.max(0f, Math.min(1f,
                (lyric.progress() - index * span) / span));

        anchorFor(lyric.text() + "#" + index);
        if (worldAnchor == null) return;

        float alphaFactor = scaleAnimation.getOutput().floatValue() * chunkFade;
        if (alphaFactor <= 0.01f) return;

        float totalWidth = Fonts.BOLD.getWidth(text, WORLD_FONT_SIZE);
        if (totalWidth <= 0f) return;

        // центры символов вдоль right-вектора, отмасштабированы так, чтобы вся строка влезла в WORLD_SPAN блоков
        float unitsPerPixel = WORLD_SPAN / totalWidth;
        float cursor = -totalWidth / 2f;

        int count = text.length();
        Vec3d[] screen = new Vec3d[count + 1];
        float[] centers = new float[count + 1];

        for (int i = 0; i <= count; i++) {
            centers[i] = cursor;
            screen[i] = accident.util.math.Projection.worldSpaceToScreenSpace(
                    worldAnchor.add(worldRight.multiply(cursor * unitsPerPixel)));
            if (i < count) {
                cursor += Fonts.BOLD.getWidth(String.valueOf(text.charAt(i)), WORLD_FONT_SIZE);
            }
        }

        float litWidth = totalWidth * chunkProgress;

        for (int i = 0; i < count; i++) {
            char c = text.charAt(i);
            if (c == ' ') continue;

            Vec3d a = screen[i];
            Vec3d b = screen[i + 1];
            if (a == null || b == null) continue;
            if (a.z <= 0 || a.z >= 1 || b.z <= 0 || b.z >= 1) continue;

            float dx = (float) (b.x - a.x);
            float dy = (float) (b.y - a.y);
            float gap = (float) Math.sqrt(dx * dx + dy * dy);
            if (gap < 0.01f) continue;

            // размер шага одного символа на экране говорит, каким рисовать глиф, чтобы строка не рвалась на любой дистанции
            float charWidth = Fonts.BOLD.getWidth(String.valueOf(c), WORLD_FONT_SIZE);
            if (charWidth <= 0f) continue;
            float size = WORLD_FONT_SIZE * (gap / charWidth);
            if (size < 0.5f || size > 400f) continue;

            float rotation = (float) Math.toDegrees(Math.atan2(dy, dx));
            boolean lit = centers[i] - centers[0] < litWidth;

            int alpha = clampByte((int) (alphaFactor * (lit ? 255 : 110)));
            int color = (alpha << 24) | 0xFFFFFF;
            int shadow = (clampByte((int) (alphaFactor * (lit ? 140 : 60))) << 24) | 0x000000;

            // тень рисуем обычной копией, а не через glow - у glow нет угла поворота и на этом размере хало вылезало за глиф
            String glyph = String.valueOf(c);
            float drawY = (float) a.y - size / 2f;
            fontRenderer().drawText(Fonts.BOLD.getName(), glyph,
                    (float) a.x + size * 0.045f, drawY + size * 0.045f, size, shadow, rotation);
            fontRenderer().drawText(Fonts.BOLD.getName(), glyph,
                    (float) a.x, drawY, size, color, rotation);
        }
    }

    private static accident.util.render.font.FontRenderer fontRenderer() {
        return accident.Initialization.getInstance().getManager().getRenderCore().getFontRenderer();
    }

    // прибивает чанк перед игроком и плавно едет к нему, чтобы соседние чанки не дёргались
    private void anchorFor(String text) {
        long now = System.currentTimeMillis();
        float dt = lastWorldFrame == 0L ? 1f / 60f
                : Math.min((now - lastWorldFrame) / 1000f, 0.1f);
        lastWorldFrame = now;

        if (!text.equals(worldAnchorFor)) {
            Vec3d eye = mc.player.getEyePos();
            double rad = Math.toRadians(mc.player.getYaw());
            Vec3d forward = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));

            targetAnchor = eye.add(forward.multiply(WORLD_DISTANCE)).add(0, WORLD_HEIGHT, 0);

            // лицом на юг (+Z) правая рука игрока смотрит на запад (-X) - если перепутать знак, строка зеркалится
            targetRight = new Vec3d(-forward.z, 0, forward.x);
            worldAnchorFor = text;
            chunkFade = 0f;

            if (worldAnchor == null || worldAnchor.distanceTo(targetAnchor) > ANCHOR_SLIDE_LIMIT) {
                worldAnchor = targetAnchor;
                worldRight = targetRight;
            }
        }

        if (targetAnchor == null) return;

        // экспоненциальный easing по реальному времени - едет одинаково при любом фреймрейте
        float ease = 1f - (float) Math.exp(-9.0 * dt);
        worldAnchor = worldAnchor.add(targetAnchor.subtract(worldAnchor).multiply(ease));
        worldRight = worldRight.add(targetRight.subtract(worldRight).multiply(ease)).normalize();

        chunkFade = Math.min(1f, chunkFade + dt * 4.5f);
    }

    // режет строку на куски по несколько слов, разрыв только между словами
    private static List<String> chunk(String line) {
        List<String> chunks = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : line.trim().split("\\s+")) {
            if (word.isEmpty()) continue;

            if (current.length() > 0 && current.length() + 1 + word.length() > WORLD_CHUNK_CHARS) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }

        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void advanceTitleScroll(float textW, float maxTextWidth) {
        long now = System.currentTimeMillis();

        if (textW <= maxTextWidth) {
            titleScrollOffset = 0f;
            titleScrollPauseUntil = 0L;
            titleScrollLastTime = 0L;
            return;
        }

        if (now >= titleScrollPauseUntil) {
            float dt = titleScrollLastTime == 0L
                    ? 1f / 60f
                    : Math.min((now - titleScrollLastTime) / 1000f, 0.1f);
            titleScrollOffset += SCROLL_SPEED * dt;
            if (titleScrollOffset >= textW + SCROLL_GAP) {
                titleScrollOffset = 0f;
                titleScrollPauseUntil = now + SCROLL_PAUSE_MS;
            }
        }
        titleScrollLastTime = now;
    }

    // скролл лирики влево завязан на прогресс пения, а не на таймер - подсветка и скролл не расходятся
    private float lyricScroll(String text, float progress, float textW, float window) {
        if (!text.equals(lyricScrollFor)) {
            lyricScrollFor = text;
            lyricScrollOffset = 0f;
        }
        if (textW <= window) return 0f;

        float target = Math.max(0f, Math.min(textW - window, textW * progress - window * 0.35f));
        lyricScrollOffset += (target - lyricScrollOffset) * 0.15f;
        return lyricScrollOffset;
    }

    // заголовок рисуется просто, лирика - дважды (тускло + ярко поверх, обрезано по прогрессу пения), как караоке
    private void drawLyricText(String text, float textX, float textY,
                               float alphaFactor, LyricsProvider.Current lyric) {
        int bright = (int)(255 * alphaFactor) << 24 | 0xFFFFFF;

        if (lyric == null) {
            Fonts.TEST.draw(text, textX, textY, MINI_FONT_SIZE, bright);
            return;
        }

        int dim = (int)(110 * alphaFactor) << 24 | 0xFFFFFF;
        Fonts.TEST.draw(text, textX, textY, MINI_FONT_SIZE, dim);

        float textW = Fonts.TEST.getWidth(text, MINI_FONT_SIZE);
        float litW = textW * lyric.progress();
        if (litW <= 0.01f) return;

        // обрезаем яркий проход scissor'ом, а не резкой строки - глифы не сдвигаются при движении подсветки
        Scissor.enable(textX, textY - MINI_FONT_SIZE, litW, MINI_FONT_SIZE * 3f);
        Fonts.TEST.draw(text, textX, textY, MINI_FONT_SIZE, bright);
        Scissor.disable();
    }

    // строка под заголовком при наведении: исполнитель слева, время справа, полоска прогресса между ними
    private void renderMiniDetails(float x, float y, float width, float alphaFactor) {
        float fade = hoverAnim * alphaFactor;

        float left = x + MINI_PAD + 2f;
        float right = x + width - MINI_PAD - 2f;
        if (right - left < 20f) return;

        float rowY = y + MINI_HEIGHT + 3f;
        float lineStep = MINI_DETAIL_FONT_SIZE + 4f;

        int bright = (clampByte((int) (fade * 225)) << 24) | 0xFFFFFF;
        int dim = (clampByte((int) (fade * 150)) << 24) | 0xFFFFFF;

        Scissor.enable(left, rowY - 2f, right - left, lineStep * 2f + 2f);
        String by = artist == null || artist.isEmpty() || artist.equals("...") ? "-" : artist;
        Fonts.TEST.draw(labelled("accident.media.label.artist", by), left, rowY,
                MINI_DETAIL_FONT_SIZE, bright);
        Fonts.TEST.draw(labelled("accident.media.label.source", playerName), left, rowY + lineStep,
                MINI_DETAIL_FONT_SIZE, dim);
        Scissor.disable();

        // Bottom row: elapsed and total on the ends, with the progress bar
        // spanning between them.
        float barY = y + MINI_HEIGHT + MINI_EXPAND_HEIGHT - 12f;
        String elapsed = formatDuration(position);
        String total = formatDuration(duration);
        float totalW = Fonts.TEST.getWidth(total, MINI_DETAIL_FONT_SIZE);

        Fonts.TEST.draw(elapsed, left, barY + 4f, MINI_DETAIL_FONT_SIZE, dim);
        Fonts.TEST.draw(total, right - totalW, barY + 4f, MINI_DETAIL_FONT_SIZE, dim);

        if (duration > 0) {
            float progress = Math.max(0f, Math.min(1f, (float) position / duration));
            Render2D.rect(left, barY, right - left, 1.5f,
                    (clampByte((int) (fade * 45)) << 24) | 0xFFFFFF, 0.75f);
            Render2D.rect(left, barY, (right - left) * progress, 1.5f,
                    getMixedColor(fade * 0.85f), 0.75f);
        }
    }

    private static String labelled(String key, String value) {
        String label = LanguageManager.get(key);
        return label == null || label.isEmpty() ? value : label + ": " + value;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;
        if (mc.player == null) return;

        updateMiniLayout();

        if (displayMode.isSelected("Mini")) {
            renderMiniMode(context, alpha);
        } else {
            drawDefaultMode(context, alpha);
        }
    }

    private void drawDefaultMode(DrawContext context, int alpha) {
        float alphaFactor = alpha / 255.0f;
        int finalCustomColor = getMixedColor(alphaFactor);

        float x = getRenderX();
        float y = getRenderY();
        float width = getWidth();
        float height = getHeight();

        boolean hasMedia = !LanguageManager.get("accident.media.notrack").equals(title) && !LanguageManager.get("accident.media.waiting").equals(title) && duration > 0;

        Render2D.blur(x, y, width, height, 10f, 4f, ((int)(alphaFactor * 60) << 24) | 0x0F121F);
        Scissor.enable(x, y, width, height, 2);

        if (hasMedia) {
            renderArtwork(context, x, y, ARTWORK_SIZE, (int)(255 * alphaFactor), CORNER_RADIUS);

            float textStartX = x + ARTWORK_SIZE + 8;
            float playerNameWidth = Fonts.TEST.getWidth(playerName, FONT_SIZE);
            float maxTextWidth = width - ARTWORK_SIZE - 8 - playerNameWidth - 8;

            Fonts.TEST.draw(playerName, x + width - playerNameWidth - 6, y + 6, FONT_SIZE,
                    (int)(200 * alphaFactor) << 24 | 0xFFFFFF);

            long now = System.currentTimeMillis();

            // --- Бегущая строка title ---
            float titleW = Fonts.TEST.getWidth(title, FONT_SIZE);
            if (titleW > maxTextWidth) {
                if (now >= titleScrollPauseUntil) {
                    float dt;
                    if (titleScrollLastTime == 0L) {
                        dt = 1f / 60f;
                    } else {
                        dt = (now - titleScrollLastTime) / 1000f;
                        dt = Math.min(dt, 0.1f);
                    }
                    titleScrollOffset += SCROLL_SPEED * dt;
                    float cycleDistance = titleW + SCROLL_GAP;
                    if (titleScrollOffset >= cycleDistance) {
                        titleScrollOffset = 0f;
                        titleScrollPauseUntil = now + SCROLL_PAUSE_MS;
                    }
                }
                titleScrollLastTime = now;
            } else {
                titleScrollOffset = 0f;
                titleScrollPauseUntil = 0L;
                titleScrollLastTime = 0L;
            }

            Scissor.enable(textStartX, y + 4, maxTextWidth, 12f);
            if (titleW <= maxTextWidth) {
                Fonts.TEST.draw(title, textStartX, y + 8, FONT_SIZE,
                        (int)(255 * alphaFactor) << 24 | 0xFFFFFF);
            } else {
                float titleCycle = titleW + SCROLL_GAP;
                Fonts.TEST.draw(title, textStartX - titleScrollOffset, y + 8, FONT_SIZE,
                        (int)(255 * alphaFactor) << 24 | 0xFFFFFF);
                Fonts.TEST.draw(title, textStartX - titleScrollOffset + titleCycle, y + 8, FONT_SIZE,
                        (int)(255 * alphaFactor) << 24 | 0xFFFFFF);
            }
            Scissor.disable();

            // --- Бегущая строка artist ---
            float artistW = Fonts.TEST.getWidth(artist, FONT_SIZE);
            if (artistW > maxTextWidth) {
                if (now >= artistScrollPauseUntil) {
                    float dt;
                    if (artistScrollLastTime == 0L) {
                        dt = 1f / 60f;
                    } else {
                        dt = (now - artistScrollLastTime) / 1000f;
                        dt = Math.min(dt, 0.1f);
                    }
                    artistScrollOffset += SCROLL_SPEED * dt;
                    float cycleDistance = artistW + SCROLL_GAP;
                    if (artistScrollOffset >= cycleDistance) {
                        artistScrollOffset = 0f;
                        artistScrollPauseUntil = now + SCROLL_PAUSE_MS;
                    }
                }
                artistScrollLastTime = now;
            } else {
                artistScrollOffset = 0f;
                artistScrollPauseUntil = 0L;
                artistScrollLastTime = 0L;
            }

            Scissor.enable(textStartX, y + 14, maxTextWidth, 12f);
            if (artistW <= maxTextWidth) {
                Fonts.TEST.draw(artist, textStartX, y + 18, FONT_SIZE,
                        (int)(200 * alphaFactor) << 24 | 0xFFFFFF);
            } else {
                float artistCycle = artistW + SCROLL_GAP;
                Fonts.TEST.draw(artist, textStartX - artistScrollOffset, y + 18, FONT_SIZE,
                        (int)(200 * alphaFactor) << 24 | 0xFFFFFF);
                Fonts.TEST.draw(artist, textStartX - artistScrollOffset + artistCycle, y + 18, FONT_SIZE,
                        (int)(200 * alphaFactor) << 24 | 0xFFFFFF);
            }
            Scissor.disable();

            // Прогресс-бар
            if (duration > 0) {
                float progress = Math.min((float) position / duration, 1f);
                float barWidth = width - ARTWORK_SIZE - 16;
                float barX = textStartX;
                float barY = y + height - 15;

                Render2D.rect(barX, barY, barWidth, PROGRESS_BAR_HEIGHT, (int)(30 * alphaFactor) << 24 | 0xFFFFFF, 1);
                Render2D.rect(barX, barY, barWidth * progress, PROGRESS_BAR_HEIGHT, (int)(255 * alphaFactor) << 24 | finalCustomColor, 1);

                String timeText = formatDuration(position);
                String timeTextAll = formatDuration(duration);
                Fonts.REGULARNEW.draw(timeText, textStartX, y + height - 11, FONT_SIZE,
                        (int)(170 * alphaFactor) << 24 | 0xFFFFFF);
                Fonts.REGULARNEW.draw(timeTextAll, x + width - Fonts.REGULARNEW.getWidth(timeTextAll, FONT_SIZE) - 6,
                        y + height - 11, FONT_SIZE, (int)(170 * alphaFactor) << 24 | 0xFFFFFF);
            }
        } else {
            String message = LanguageManager.get("accident.media.noplayer");
            float messageWidth = Fonts.REGULARNEW.getWidth(message, FONT_SIZE);
            Fonts.TEST.draw(message, x + (width - messageWidth) / 2 + 0.5f, y + height / 2 - 3 + 0.5f, FONT_SIZE,
                    (int)(30 * alphaFactor) << 24 | 0x000000);
            Fonts.TEST.draw(message, x + (width - messageWidth) / 2, y + height / 2 - 3, FONT_SIZE,
                    (int)(150 * alphaFactor) << 24 | 0xFFFFFF);
        }

        Scissor.disable();
    }
}