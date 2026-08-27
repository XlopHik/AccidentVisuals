#version 150

uniform sampler2D iChannel0Sampler;
uniform sampler2D iChannel1Sampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform WidgetInfo {
    float Count;
    vec4 ScissorRects[64];
};

in vec2 texCoord;
out vec4 fragColor;

vec3 blendScreen(vec3 a, vec3 b) {
    return 1.0 - (1.0 - a) * (1.0 - b);
}

bool isInAnyWidget(vec2 screenCoord) {
    for (int i = 0; i < int(Count + 0.5); i++) {
        vec4 sc = ScissorRects[i];
        if (screenCoord.x >= sc.x && screenCoord.x <= sc.z &&
            screenCoord.y >= sc.y && screenCoord.y <= sc.w) {
            return true;
        }
    }
    return false;
}

void main() {
    vec2 screenCoord = gl_FragCoord.xy;
    vec3 base = texture(iChannel0Sampler, texCoord).rgb;

    if (isInAnyWidget(screenCoord)) {
        vec3 blurred = texture(iChannel1Sampler, texCoord).rgb;
        float threshold = 0.2;
        float intensity = 1.0;
        vec3 hi = clamp(blurred - threshold, 0.0, 1.0) * (1.0 / (1.0 - threshold)) * intensity;
        fragColor = vec4(blendScreen(base, hi), 1.0);
    } else {
        fragColor = vec4(base, 1.0);
    }
}