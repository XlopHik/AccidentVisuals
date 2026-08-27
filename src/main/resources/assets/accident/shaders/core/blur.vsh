#version 150

layout(std140) uniform BlurData {
    vec4 rect;
    vec4 screen;
    vec4 framebufferSize;
    vec4 radii;
    vec4 color;
    // Gaussian taps worked out on the CPU: three vec4s holding up to six
    // (offset, weight) pairs, so the fragment stage does no exp() at all.
    vec4 taps0;
    vec4 taps1;
    vec4 taps2;
};

out vec2 fragCoord;
out vec2 pixelCoord;
out vec2 texCoord;
out vec2 rectSize;
out vec4 cornerRadii;
out float guiScale;
out float blurRadius;
out vec2 texelSize;
out vec4 tintColor;
out vec2 resolution;
out vec4 tapA;
out vec4 tapB;
out vec4 tapC;
out float tapCount;
out float invTotalWeight;

void main() {
    vec2 positions[6] = vec2[](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
    );

    vec2 pos = positions[gl_VertexID];

    vec2 screenPos = rect.xy + pos * rect.zw;
    vec2 ndcPos = (screenPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    fragCoord = pos;
    pixelCoord = pos * rect.zw;
    rectSize = rect.zw;
    cornerRadii = radii;
    guiScale = screen.z;
    blurRadius = screen.w;
    resolution = framebufferSize.xy;
    texelSize = 1.0 / resolution;
    tintColor = color;
    tapCount = framebufferSize.z;
    invTotalWeight = framebufferSize.w;
    tapA = taps0;
    tapB = taps1;
    tapC = taps2;

    vec2 fbPos = screenPos * guiScale;
    texCoord = vec2(fbPos.x / resolution.x, 1.0 - (fbPos.y / resolution.y));
}