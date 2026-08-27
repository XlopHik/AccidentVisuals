#version 150

uniform BackgroundData {
    vec4 data;
};

out vec4 fragColor;

float orb(vec3 p, float t) {
    return length(p - vec3(
        sin(sin(t * 2.0) + t * 4.0) * 6.0,
        1.0 + sin(sin(t * 5.0) + t * 2.0) * 4.0,
        12.0 + cos(t * 3.0) * 8.0
    ));
}

void main() {
    float screenW = data.x;
    float screenH = data.y;
    float iTime   = data.z * 0.4;

    vec2 uv = (2.0 * gl_FragCoord.xy - vec2(screenW, screenH)) / screenH;
    uv += vec2(cos(iTime * 0.1) * 0.3, cos(iTime * 0.3) * 0.1);

    vec4  o = vec4(0.0);
    float d = 0.0;

    for (float i = 0.0; i < 128.0; i++) {
        vec3  p = vec3(uv * d, d + iTime);
        float e = orb(p, iTime) - 0.1;

        float ang = 0.1 * iTime + p.z / 8.0;
        float ca = cos(ang), sa = sin(ang);
        p.xy = mat2(ca, -sa, sa, ca) * p.xy;

        float s = 4.0 - abs(p.y);
        float a = 0.8;
        for (; a < 32.0; a += a) {
            p += cos(0.7 * iTime + p.yzx) * 0.2;
            s -= abs(dot(sin(0.1 * iTime + p * a), vec3(0.6))) / a;
        }

        float step = max(min(0.03 + 0.2 * abs(s), e), 0.01);
        d += step;
        o += 1.0 / (abs(s) + abs(e) * 3.0 + 0.01);
    }

    o = o / 80.0;

    vec3 col = tanh(o.rgb);

    col *= vec3(0.85, 0.90, 1.0);

    col += col * col * 0.4;

    fragColor = vec4(col, 1.0);
}
