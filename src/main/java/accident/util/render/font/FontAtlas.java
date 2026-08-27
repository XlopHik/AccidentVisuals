package accident.util.render.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class FontAtlas {

    private static final Logger LOGGER = LoggerFactory.getLogger("accident/Font");

    private final Identifier jsonId;
    private final Identifier textureId;
    private final Map<Integer, Glyph> glyphs;
    private float atlasWidth = 512;
    private float atlasHeight = 512;
    private float fontSize = 32;
    private float lineHeight = 40;
    private float distanceRange = 4;
    private boolean yOriginBottom = false;
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    public FontAtlas(Identifier jsonId, Identifier textureId) {
        this.jsonId = jsonId;
        this.textureId = textureId;
        this.glyphs = new HashMap<>();
    }

    public void forceLoad() {
        if (loaded.get()) return;
        synchronized (this) {
            if (loaded.get()) return;
            doLoad();
        }
    }

    public void ensureLoaded() {
        if (loaded.get()) return;
        synchronized (this) {
            if (loaded.get()) return;
            doLoad();
        }
    }

    private void doLoad() {
        try {
            Optional<Resource> resourceOpt = MinecraftClient.getInstance()
                    .getResourceManager().getResource(jsonId);

            if (resourceOpt.isEmpty()) {
                loaded.set(true);
                return;
            }

            try (InputStream is = resourceOpt.get().getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                parseJson(root);
                loaded.set(true);
            }
        } catch (Exception e) {
            loaded.set(true);
        }
    }

    private void parseJson(JsonObject root) {
        float emSize = 1.0f;

        if (root.has("atlas")) {
            JsonObject atlas = root.getAsJsonObject("atlas");
            atlasWidth = getFloat(atlas, "width", 512);
            atlasHeight = getFloat(atlas, "height", 512);
            fontSize = getFloat(atlas, "size", 32);
            distanceRange = getFloat(atlas, "distanceRange", 4);

            if (atlas.has("yOrigin")) {
                String origin = atlas.get("yOrigin").getAsString();
                yOriginBottom = origin.equalsIgnoreCase("bottom");
            }
        }

        if (root.has("metrics")) {
            JsonObject metrics = root.getAsJsonObject("metrics");
            emSize = getFloat(metrics, "emSize", 1.0f);
            float normalizedLineHeight = getFloat(metrics, "lineHeight", 1.2f);
            lineHeight = normalizedLineHeight * fontSize;
        }

        if (root.has("chars")) {
            JsonArray charsArray = root.getAsJsonArray("chars");
            for (JsonElement elem : charsArray) {
                JsonObject g = elem.getAsJsonObject();
                parseBmfontGlyph(g);
            }
        }

        if (root.has("common")) {
            JsonObject common = root.getAsJsonObject("common");
            atlasWidth = getFloat(common, "scaleW", atlasWidth);
            atlasHeight = getFloat(common, "scaleH", atlasHeight);
            lineHeight = getFloat(common, "lineHeight", lineHeight);
        }

        if (root.has("info")) {
            JsonObject info = root.getAsJsonObject("info");
            fontSize = getFloat(info, "size", fontSize);
        }

        if (root.has("glyphs")) {
            JsonArray glyphsArray = root.getAsJsonArray("glyphs");
            for (JsonElement elem : glyphsArray) {
                JsonObject g = elem.getAsJsonObject();
                parseMsdfGlyph(g, emSize);
            }
        }
    }

    private void parseBmfontGlyph(JsonObject g) {
        int unicode = -1;
        if (g.has("id")) {
            unicode = g.get("id").getAsInt();
        }
        if (unicode < 0) return;

        float x = getFloat(g, "x", 0);
        float y = getFloat(g, "y", 0);
        float w = getFloat(g, "width", 0);
        float h = getFloat(g, "height", 0);
        float xOffset = getFloat(g, "xoffset", 0);
        float yOffset = getFloat(g, "yoffset", 0);
        float xAdvance = getFloat(g, "xadvance", 0);

        glyphs.put(unicode, new Glyph(unicode, x, y, w, h, xOffset, yOffset, xAdvance, atlasWidth, atlasHeight));
    }

    private void parseMsdfGlyph(JsonObject g, float emSize) {
        int unicode = -1;

        if (g.has("unicode")) {
            unicode = g.get("unicode").getAsInt();
        } else if (g.has("char")) {
            String charStr = g.get("char").getAsString();
            if (!charStr.isEmpty()) {
                unicode = charStr.codePointAt(0);
            }
        } else if (g.has("id")) {
            unicode = g.get("id").getAsInt();
        } else if (g.has("index")) {
            unicode = g.get("index").getAsInt();
        }

        if (unicode < 0) return;

        float advance = getFloat(g, "advance", 0) * fontSize;
        if (advance == 0) {
            advance = getFloat(g, "xadvance", 0);
        }

        float x = 0, y = 0, w = 0, h = 0;
        float xOffset = 0, yOffset = 0;

        if (g.has("atlasBounds")) {
            JsonObject bounds = g.getAsJsonObject("atlasBounds");
            float left = getFloat(bounds, "left", 0);
            float bottom = getFloat(bounds, "bottom", 0);
            float right = getFloat(bounds, "right", 0);
            float top = getFloat(bounds, "top", 0);

            x = left;
            w = right - left;
            h = top - bottom;

            if (yOriginBottom) {
                y = atlasHeight - top;
            } else {
                y = bottom;
            }
        } else if (g.has("x") && g.has("y") && g.has("width") && g.has("height")) {
            x = getFloat(g, "x", 0);
            y = getFloat(g, "y", 0);
            w = getFloat(g, "width", 0);
            h = getFloat(g, "height", 0);
        }

        if (g.has("planeBounds")) {
            JsonObject plane = g.getAsJsonObject("planeBounds");
            float pLeft = getFloat(plane, "left", 0);
            float pBottom = getFloat(plane, "bottom", 0);
            float pRight = getFloat(plane, "right", 0);
            float pTop = getFloat(plane, "top", 0);

            xOffset = pLeft * fontSize;
            float ascender = 0.95f;
            yOffset = (ascender - pTop) * fontSize;
        } else if (g.has("xoffset") && g.has("yoffset")) {
            xOffset = getFloat(g, "xoffset", 0);
            yOffset = getFloat(g, "yoffset", 0);
        }

        glyphs.put(unicode, new Glyph(unicode, x, y, w, h, xOffset, yOffset, advance, atlasWidth, atlasHeight));
    }

    private float getFloat(JsonObject obj, String key, float def) {
        return obj.has(key) ? obj.get(key).getAsFloat() : def;
    }

    public Glyph getGlyph(int codePoint) {
        return glyphs.get(codePoint);
    }

    public boolean hasGlyph(int codePoint) {
        return glyphs.containsKey(codePoint);
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public float getFontSize() {
        return fontSize;
    }

    public float getLineHeight() {
        return lineHeight;
    }

    public float getAtlasWidth() {
        return atlasWidth;
    }

    public float getAtlasHeight() {
        return atlasHeight;
    }

    public float getDistanceRange() {
        return distanceRange;
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    public int getGlyphCount() {
        return glyphs.size();
    }
}