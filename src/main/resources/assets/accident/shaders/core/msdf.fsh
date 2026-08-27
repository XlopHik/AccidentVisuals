#version 150

in vec2 texCoord;
in vec4 charColor;
in float outlineWidth;
in vec4 outColor;
in float pxRange;
in vec2 atlasSize;
in float glowWidth;

out vec4 fragColor;

uniform sampler2D Sampler0;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

float screenPxRange() {
    vec2 unitRange = vec2(pxRange) / atlasSize;
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {
    vec3 msd = texture(Sampler0, texCoord).rgb;
    float sd = median(msd.r, msd.g, msd.b);

    float screenPxDist = screenPxRange() * (sd - 0.5);
    float opacity = clamp(screenPxDist + 0.5, 0.0, 1.0);
    vec4 fill = vec4(charColor.rgb, charColor.a * opacity);

    // A real soft glow, not another hard-edged ring: intensity decays
    // exponentially with distance outside the glyph, the way light actually
    // falls off, instead of the outline's flat band that stops dead at a
    // fixed radius and reads as a crooked border around the letters.
    vec4 glow = vec4(0.0);
    if (glowWidth > 0.0) {
        float outside = max(0.0, -screenPxDist);
        float glowFactor = exp(-outside / glowWidth) * (1.0 - opacity);
        glow = vec4(outColor.rgb, outColor.a * glowFactor);
    }

    vec4 outline = vec4(0.0);
    if (outlineWidth > 0.0) {
        float outlineDist = screenPxDist + outlineWidth;
        float outlineOpacity = clamp(outlineDist + 0.5, 0.0, 1.0);
        outline = vec4(outColor.rgb, outColor.a * max(0.0, outlineOpacity - opacity));
    }

    vec4 result = glow;
    result = outline + result * (1.0 - outline.a);
    result = fill + result * (1.0 - fill.a);
    fragColor = result;

    if (fragColor.a < 0.004) {
        discard;
    }
}