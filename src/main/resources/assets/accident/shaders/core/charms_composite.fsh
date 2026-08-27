#version 150

in vec2 texCoord;
in vec2 texelSize;

out vec4 fragColor;

uniform sampler2D MaskSampler;
uniform sampler2D BlurredMaskSampler;

layout(std140) uniform CharmsData {
    vec4 resolution;     // [width, height, outlineThickness, _]
    vec4 outlineColor;   // [r, g, b, a]
    vec4 fillColor;      // [r, g, b, a]
    vec4 settings;       // [fillAlpha, glowIntensity, _, _]
};

// Returns 1.0 if this pixel is just OUTSIDE the entity but within `thickness` texels of a boundary.
// `center` is the already-sampled mask value at `uv` (avoids a redundant fetch).
float getOutline(vec2 uv, float thickness, float center) {
    if (center > 0.5) return 0.0; // inside entity — not part of outline

    float tx = texelSize.x;
    float ty = texelSize.y;

    // Sample a ring at each integer step up to thickness (max 4 to keep GPU happy)
    for (int i = 1; i <= 4; i++) {
        if (float(i) > thickness) break;
        float s  = float(i);
        float mx = 0.0;
        mx = max(mx, texture(MaskSampler, uv + vec2( s*tx,  0.0 )).r);
        mx = max(mx, texture(MaskSampler, uv + vec2(-s*tx,  0.0 )).r);
        mx = max(mx, texture(MaskSampler, uv + vec2( 0.0,  s*ty )).r);
        mx = max(mx, texture(MaskSampler, uv + vec2( 0.0, -s*ty )).r);
        mx = max(mx, texture(MaskSampler, uv + vec2( s*tx,  s*ty)).r);
        mx = max(mx, texture(MaskSampler, uv + vec2(-s*tx,  s*ty)).r);
        mx = max(mx, texture(MaskSampler, uv + vec2( s*tx, -s*ty)).r);
        mx = max(mx, texture(MaskSampler, uv + vec2(-s*tx, -s*ty)).r);
        if (mx > 0.5) return 1.0;
    }
    return 0.0;
}

void main() {
    float maskValue   = texture(MaskSampler,        texCoord).r;
    float blurredMask = texture(BlurredMaskSampler, texCoord).r;

    float outlineThickness = resolution.z;
    float fillAlpha        = settings.x;
    float glowIntensity    = settings.y;

    // Default: fully transparent — do not touch the framebuffer
    vec4 result = vec4(0.0, 0.0, 0.0, 0.0);

    // ── 1. Soft glow expanding outward from the entity ──────────────────────────
    if (maskValue < 0.5 && blurredMask > 0.004 && glowIntensity > 0.001) {
        float glowAlpha = clamp(blurredMask * glowIntensity, 0.0, 0.9) * outlineColor.a;
        result = vec4(outlineColor.rgb, glowAlpha);
    }

    // ── 2. Solid outline at the entity boundary ──────────────────────────────────
    // Early-out on blurredMask: the Kawase kernel's reach (≥6 px even at minimum
    // radius/iterations) always covers the ≤4 px outline band, so a pixel whose
    // blurred mask quantized to zero cannot have a silhouette boundary within
    // `thickness` texels.  Skips the up-to-32-tap ring search on the vast
    // majority of screen pixels.
    if (outlineThickness > 0.5 && outlineColor.a > 0.001 && blurredMask > 0.0009) {
        float outline = getOutline(texCoord, outlineThickness, maskValue);
        if (outline > 0.5) {
            result = vec4(outlineColor.rgb, outlineColor.a);
        }
    }

    // ── 3. Light translucent fill inside the entity silhouette ───────────────────
    if (maskValue > 0.5 && fillAlpha > 0.001 && fillColor.a > 0.001) {
        result = vec4(fillColor.rgb, fillAlpha * fillColor.a);
    }

    fragColor = result;
}
