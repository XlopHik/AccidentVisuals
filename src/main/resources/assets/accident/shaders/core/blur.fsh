#version 150

in vec2 fragCoord;
in vec2 pixelCoord;
in vec2 texCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in float guiScale;
in float blurRadius;
in vec2 texelSize;
in vec4 tintColor;
in vec2 resolution;
in vec4 tapA;
in vec4 tapB;
in vec4 tapC;
in float tapCount;
in float invTotalWeight;

out vec4 fragColor;

uniform sampler2D Sampler0;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x = (p.y > 0.0) ? r.y : r.x;

    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

// Second half of a separable Gaussian. Sampler0 here is not the framebuffer
// copy but the output of blur_h.fsh, already blurred along X, so this only has
// to sweep Y. The nested loop this replaces cost (2n+1)^2 taps - 441 at radius
// 10 - against 2n+1 here, and the two passes together reproduce it exactly,
// because a 2D Gaussian is the product of two 1D ones.
// Second half of the separable Gaussian. Sampler0 here is not the framebuffer
// but the output of blur_h.fsh, already blurred along X, so this only sweeps Y.
// Offsets and weights are precomputed on the CPU and paired for bilinear
// fetches, exactly as in that shader - no exp() and no divisions per pixel.
vec4 tapPair(vec2 uv, float offset, float weight) {
    vec2 step = vec2(0.0, offset) * texelSize;
    vec4 a = texture(Sampler0, clamp(uv + step, vec2(0.001), vec2(0.999)));
    vec4 b = texture(Sampler0, clamp(uv - step, vec2(0.001), vec2(0.999)));
    return (a + b) * weight;
}

vec4 gaussianBlur(vec2 uv, float radius) {
    vec4 col = texture(Sampler0, uv);

    if (tapCount > 0.5) col += tapPair(uv, tapA.x, tapA.y);
    if (tapCount > 1.5) col += tapPair(uv, tapA.z, tapA.w);
    if (tapCount > 2.5) col += tapPair(uv, tapB.x, tapB.y);
    if (tapCount > 3.5) col += tapPair(uv, tapB.z, tapB.w);
    if (tapCount > 4.5) col += tapPair(uv, tapC.x, tapC.y);
    if (tapCount > 5.5) col += tapPair(uv, tapC.z, tapC.w);

    return col * invTotalWeight;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;

    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 rRadii = min(cornerRadii, vec4(maxRadius));

    float dist = roundedBoxSDF(center, halfSize, rRadii);

    float pixelWidth = fwidth(dist);
    float smoothing = max(pixelWidth, 0.5 / guiScale);
    float alpha = 1.0 - smoothstep(-smoothing, smoothing, dist);

    if (alpha < 0.01) {
        discard;
    }

    vec4 blurred = gaussianBlur(texCoord, blurRadius);

    vec3 finalColor = mix(blurred.rgb, tintColor.rgb, tintColor.a);

    fragColor = vec4(finalColor, alpha);
}