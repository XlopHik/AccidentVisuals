#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;

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

struct SDFResult { float dist; vec2 normal; float aspect; int index; };

vec2 screenToUV(vec2 screen, vec2 res) {
    return (screen.xy - 0.5 * res.xy) / res.y;
}

vec3 sdgBox(in vec2 p, in vec2 b, vec4 ra) {
    ra.xy = (p.x > 0.0) ? ra.xy : ra.zw;
    float r = (p.y > 0.0) ? ra.x : ra.y;
    vec2 w = abs(p) - (b - r);
    vec2 s = vec2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float g = max(w.x, w.y);
    vec2 q = max(w, 0.0);
    float l = length(q);
    float dist = (g > 0.0) ? l - r : g - r;
    vec2 n = (g > 0.0) ? (q / max(l, 1e-6)) : ((w.x > w.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
    return vec3(dist, s * n);
}

SDFResult opSmoothUnion(in SDFResult a, in SDFResult b, in float k) {
    if (k == 0.0) return (a.dist < b.dist) ? a : b;
    float h = clamp(0.5 + 0.5 * (a.dist - b.dist) / k, 0.0, 1.0);
    float d = mix(a.dist, b.dist, h) - k * h * (1.0 - h);
    vec2 n = normalize(mix(a.normal, b.normal, h));
    float aspect = mix(a.aspect, b.aspect, h);
    int index = (a.dist < b.dist) ? a.index : b.index;
    return SDFResult(d, n, aspect, index);
}

SDFResult opHardUnion(SDFResult a, SDFResult b) { return (a.dist < b.dist) ? a : b; }

SDFResult opHardSubtract(SDFResult a, SDFResult b) {
    float d = max(a.dist, -b.dist);
    if (d == a.dist) return a;
    return SDFResult(d, -b.normal, a.aspect, a.index);
}

vec4 sampleBlur(int idx, vec2 uv) {
    if (idx <= 0) return texture(Sampler1, uv);
    if (idx == 1) return texture(Sampler2, uv);
    if (idx == 2) return texture(Sampler3, uv);
    if (idx == 3) return texture(Sampler4, uv);
    return texture(Sampler5, uv);
}

SDFResult fieldWidgets(vec2 p, vec2 inSize, vec2 fragCoord) {
    int n = int(Count + 0.5);
    if (n == 0) return SDFResult(1e6, vec2(0.0), 1.0, -1);

    SDFResult pos = SDFResult(1e6, vec2(0.0), 1.0, -1);
    bool hasPos = false;

    for (int i = 0; i < MAX_WIDGETS; i++) {
        if (i >= n) break;
        if (Smoothings[i].x < 0.0) continue;

        vec4 sc = ScissorRects[i];
        if (fragCoord.x < sc.x || fragCoord.y < sc.y || fragCoord.x > sc.z || fragCoord.y > sc.w) continue;

        vec4 rc = Rects[i];
        vec4 rr = Rads[i];
        vec2 cPx = vec2(rc.x + 0.5 * rc.z, rc.y + 0.5 * rc.w);
        vec2 c = screenToUV(cPx, inSize);
        vec2 b = 0.5 * vec2(rc.z, rc.w) / inSize.y;
        vec4 rad = rr / inSize.y;

        vec3 g = sdgBox(p - c, b, rad);

        vec4 extra = Extra0[i];
        float scaleOff = (HoverScalePx * extra.y + FocusScalePx * extra.z) / inSize.y;
        float dist = g.x - scaleOff;

        float aspect = min(rc.z, rc.w) / max(rc.z, rc.w);
        SDFResult s = SDFResult(dist, g.yz, aspect, i);

        if (!hasPos) { pos = s; hasPos = true; }
        else { pos = opSmoothUnion(pos, s, Smoothings[i].x); }
    }

    SDFResult f = pos;

    for (int i = 0; i < MAX_WIDGETS; i++) {
        if (i >= n) break;
        if (Smoothings[i].x >= 0.0) continue;

        vec4 sc = ScissorRects[i];
        if (fragCoord.x < sc.x || fragCoord.y < sc.y || fragCoord.x > sc.z || fragCoord.y > sc.w) continue;

        vec4 rc = Rects[i];
        vec4 rr = Rads[i];
        vec2 cPx = vec2(rc.x + 0.5 * rc.z, rc.y + 0.5 * rc.w);
        vec2 c = screenToUV(cPx, inSize);
        vec2 b = 0.5 * vec2(rc.z, rc.w) / inSize.y;
        vec4 rad = rr / inSize.y;

        vec3 g = sdgBox(p - c, b, rad);

        vec4 extra = Extra0[i];
        float scaleOff = (HoverScalePx * extra.y + FocusScalePx * extra.z) / inSize.y;
        float dist = g.x - scaleOff;

        float aspect = min(rc.z, rc.w) / max(rc.z, rc.w);
        SDFResult s = SDFResult(dist, g.yz, aspect, i);

        float repulsion = -Smoothings[i].x;
        SDFResult se = SDFResult(s.dist - repulsion, s.normal, s.aspect, s.index);
        f = opHardSubtract(f, se);
        f = opHardUnion(f, s);
    }

    return f;
}

bool isInAnyWidgetScissor(vec2 coord) {
    int n = int(Count + 0.5);
    for (int i = 0; i < MAX_WIDGETS; i++) {
        if (i >= n) break;
        vec4 sc = ScissorRects[i];
        if (coord.x >= sc.x && coord.x <= sc.z && coord.y >= sc.y && coord.y <= sc.w) {
            return true;
        }
    }
    return false;
}

void main() {
    vec2 inSize = InSize;
    if (inSize.x <= 0.0 || inSize.y <= 0.0) inSize = vec2(textureSize(Sampler0, 0));

    vec2 coord = gl_FragCoord.xy;
    vec2 uv = coord / inSize;
    vec3 base = texture(Sampler0, uv).rgb;

    bool inWidget = isInAnyWidgetScissor(coord);

    if (inWidget) {
        vec3 result = base * vec3(0.8, 1.0, 0.8);
        fragColor = vec4(result, 1.0);
    } else {
        fragColor = vec4(base, 1.0);
    }

}