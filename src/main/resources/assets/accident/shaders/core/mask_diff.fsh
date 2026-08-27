#version 150

in vec2 texCoord;

out vec4 fragColor;

uniform sampler2D BeforeSampler;
uniform sampler2D AfterSampler;
uniform sampler2D DepthBeforeSampler;
uniform sampler2D DepthAfterSampler;

layout(std140) uniform MaskData {
    vec4 params; // x=width, y=height, z=near, w=far
};

// Raw depth-buffer samples are 0..1 window depth, which maps hyperbolically
// (not linearly) onto view-space distance: the same fixed epsilon that
// separates two surfaces a few cm apart right in front of the camera shrinks
// towards nothing as those surfaces get farther away. A grazing view angle
// (looking sharply down at a mob from above, e.g. after gaining altitude)
// stretches a mob's feet across a wide band of near-identical raw depth
// against the ground for exactly that reason, so a raw-depth epsilon starts
// rejecting real geometry there - the mask erodes from the feet upward the
// higher/steeper the camera gets. Comparing in linear (view-space, in
// blocks) distance instead keeps one epsilon meaningful at any distance.
float linearDepth(float d, float near, float far) {
    float ndcZ = d * 2.0 - 1.0;
    return (2.0 * near * far) / (far + near - ndcZ * (far - near));
}

void main() {
    float near = params.z;
    float far  = params.w;

    float depthBefore = linearDepth(texture(DepthBeforeSampler, texCoord).r, near, far);
    float depthAfter  = linearDepth(texture(DepthAfterSampler,  texCoord).r, near, far);
    float depthDiff = depthBefore - depthAfter;

    // Standard case: entity is in front of background (a few cm counts as real).
    bool isEntity = depthDiff > 0.03;

    // Coplanar fallback: entity geometry at the exact same depth as background
    // (e.g. player feet touching the ground — depthDiff ~= 0).
    // Shadows also hit this path, so we require a depth-detected entity neighbor
    // to accept the pixel: feet pixels are adjacent to the lower leg which IS
    // depth-detected, but shadow pixels outside the entity silhouette have no
    // such neighbor and are therefore excluded.
    if (!isEntity && depthDiff > -0.03) {
        vec3 colorBefore = texture(BeforeSampler, texCoord).rgb;
        vec3 colorAfter  = texture(AfterSampler,  texCoord).rgb;
        float colorDiff = dot(abs(colorAfter - colorBefore), vec3(1.0));

        // Moving sky/weather (clouds, rain, fog) changes color between the
        // before/after captures same as real geometry does, and concentrates
        // near the top of the screen - a reference client's version of this
        // shader specifically damps detection up there for exactly that
        // reason. Demanding a much stronger color change near the top, easing
        // to the normal threshold by about a quarter of the way down, closes
        // that loophole without touching entity detection anywhere the sky
        // isn't (which is where this fallback actually matters).
        float topDistance = (params.y - gl_FragCoord.y) / max(params.y, 1.0);
        float colorThreshold = mix(0.12, 0.02, smoothstep(0.0, 0.25, topDistance));

        if (colorDiff > colorThreshold) {
            vec2 ts = 1.0 / vec2(textureSize(DepthBeforeSampler, 0));
            bool hasEntityNeighbor = false;
            for (int dy = -3; dy <= 3 && !hasEntityNeighbor; dy++) {
                for (int dx = -3; dx <= 3 && !hasEntityNeighbor; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    vec2 off = texCoord + vec2(float(dx), float(dy)) * ts;
                    float dB = linearDepth(texture(DepthBeforeSampler, off).r, near, far);
                    float dA = linearDepth(texture(DepthAfterSampler,  off).r, near, far);
                    hasEntityNeighbor = (dB - dA) > 0.03;
                }
            }
            isEntity = hasEntityNeighbor;
        }
    }

    float result = isEntity ? 1.0 : 0.0;
    fragColor = vec4(result, result, result, 1.0);
}