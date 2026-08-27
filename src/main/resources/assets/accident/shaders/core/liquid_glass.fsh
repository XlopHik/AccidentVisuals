#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform CustomUniforms {
    float Time;
    vec4 Mouse;
    float ScreenWantsBlur;
    vec3 RIM_LIGHT_VEC;
    vec4 RIM_LIGHT_COLOR;
    float EPS_PIX;
    float DebugStep;
    float Pixelated;
    float PixelGridSize;
    float HoverScalePx;
    float FocusScalePx;
    float FocusBorderWidthPx;
    float FocusBorderIntensity;
    float FocusBorderSpeed;
};

#define MAX_WIDGETS 64
layout(std140) uniform WidgetInfo {
    float Count;
    vec4 Rects[MAX_WIDGETS];
    vec4 Rads[MAX_WIDGETS];
    vec4 Tints[MAX_WIDGETS];
    vec4 Optics0[MAX_WIDGETS];
    vec4 Optics1[MAX_WIDGETS];
    vec4 Optics2[MAX_WIDGETS];
    vec4 Smoothings[MAX_WIDGETS];
    vec4 ScissorRects[MAX_WIDGETS];
    vec4 Shadow0[MAX_WIDGETS];
    vec4 ShadowColor[MAX_WIDGETS];
    vec4 Extra0[MAX_WIDGETS];
};

layout(std140) uniform BgConfig {
    float ShadowExpand;
    float ShadowFactor;
    vec2 ShadowOffset;
};

out vec4 fragColor;

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
    vec2 uv = texCoord;

    vec3 original = texture(Sampler0, uv).rgb;

    if (isInAnyWidget(screenCoord)) {
        vec3 blurred = texture(Sampler2, uv).rgb;
        vec3 bloom = texture(Sampler3, uv).rgb;

        vec3 result = mix(original, blurred, 0.3);
        result += bloom * 0.2;

        fragColor = vec4(result, 1.0);
    } else {
        fragColor = vec4(original, 1.0);
    }
}