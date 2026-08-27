#version 150

in vec2 texCoord;
in vec2 texelSize;

out vec4 fragColor;

uniform sampler2D SceneSampler;
uniform sampler2D BlurSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform GlassData {
    vec4 resolution;  // x, y = размер, z = saturation, w = doReflect
    vec4 tintColor;
    vec4 settings;    // x = tintIntensity, y = edgeGlowIntensity, z = time, w = waveIntensity
    vec4 styleParams; // x = style (0=Стекло 1=Огонь 2=Вода 3=Каустика 4=Туманность 5=Плазма 6=Блум)
};

vec3 adjustSaturation(vec3 color, float saturation) {
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    return mix(vec3(gray), color, saturation);
}

float getEdge(vec2 uv) {
    float center = texture(MaskSampler, uv).r;
    float edge = 0.0;

    edge += abs(center - texture(MaskSampler, uv + vec2(texelSize.x, 0.0)).r);
    edge += abs(center - texture(MaskSampler, uv - vec2(texelSize.x, 0.0)).r);
    edge += abs(center - texture(MaskSampler, uv + vec2(0.0, texelSize.y)).r);
    edge += abs(center - texture(MaskSampler, uv - vec2(0.0, texelSize.y)).r);

    return clamp(edge * 2.0, 0.0, 1.0);
}

// Плавный шум на основе синусов
float smoothNoise(vec2 uv, float time) {
    float n = 0.0;
    n += sin(uv.x * 3.1 + time * 0.7) * cos(uv.y * 2.7 - time * 0.5);
    n += sin(uv.x * 5.3 - time * 0.4 + uv.y * 1.9) * 0.5;
    n += cos(uv.x * 1.7 + uv.y * 4.1 + time * 0.6) * 0.3;
    return n;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Classic value noise + fbm - drives every procedural style below (rising
// embers, lightning veins, drifting nebula, crystal facets).
float noise2(vec2 uv) {
    vec2 i = floor(uv);
    vec2 f = fract(uv);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// 6 octaves with a shifting offset each step, matching the sky shader's
// fbm3 - the previous 4-octave version with no offset was noticeably
// coarser and left every style looking flat rather than richly textured.
float fbm2(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
        v += noise2(p) * amp;
        p = p * 2.1 + vec2(11.3, 7.7);
        amp *= 0.48;
    }
    return v;
}

// --- Water / Caustic / Nebula / Plasma / Bloom -----------------------------
// Ported straight from a reference client's standalone "shader fog" effects
// (classic shadertoy-style ray-marched tunnels + 3D fbm volumes). Those run
// on a real world-space camera ray so the pattern holds still as you turn;
// hands are screen-locked, not world-locked, so here the "ray" is just a
// fixed pseudo-direction built from screen position - dropping the camera
// rotation is correct for this use, not a simplification that lost anything.

float hash31(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);

    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));

    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float fbm3(vec3 p) {
    float val = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        val += noise3(p) * amp;
        p = p * 2.1 + vec3(11.3, 7.7, 3.1);
        amp *= 0.48;
    }
    return val;
}

vec3 pseudoRay(vec2 uv, vec2 aspectRes) {
    vec2 sp = uv * 2.0 - 1.0;
    sp.x *= aspectRes.x / aspectRes.y;
    return normalize(vec3(sp, 1.0));
}

// The water/caustic "tunnel": interference of a handful of orbiting points,
// same shape family as the classic shadertoy "Star Nest" tunnel.
vec3 tunnelColor(vec3 p, float time, float timeScale, float intensity, vec3 tint) {
    vec3 i = p;
    float c = 1.0;
    for (int n = 0; n < 4; n++) {
        float t = time * timeScale * (11.0 - (3.0 / float(n + 1)));
        i = p + vec3(
            cos(t - i.x) + sin(t + i.y),
            sin(t - i.y) + cos(t + i.z),
            cos(t - i.z) + sin(t + i.x)
        );
        c += 1.0 / length(vec3(
            p.x / (sin(i.x + t) / intensity),
            p.y / (cos(i.y + t) / intensity),
            p.z / (sin(i.z + t) / intensity)
        ));
    }
    c /= 4.0;
    c = 1.5 - sqrt(max(c, 0.0));
    float brightness = c * c * c * c;
    return tint * brightness + tint * 0.15;
}

vec2 waveDistortion(vec2 uv, float time, float intensity) {
    float noise1 = smoothNoise(uv * 1.2, time);
    float noise2 = smoothNoise(uv * 0.8 + vec2(1.7, 3.1), time * 1.3);

    // Медленные большие волны
    float bigWaveX = sin(uv.y * 4.0 + time * 0.8) * cos(uv.x * 2.0 + time * 0.3);
    float bigWaveY = cos(uv.x * 3.5 - time * 0.6) * sin(uv.y * 2.5 + time * 0.4);

    // Мелкая рябь поверх
    float rippleX = sin(uv.y * 12.0 + time * 2.1 + noise1) * 0.3;
    float rippleY = cos(uv.x * 10.0 - time * 1.8 + noise2) * 0.3;

    vec2 distort;
    distort.x = (bigWaveX + rippleX + noise1 * 0.4) * intensity;
    distort.y = (bigWaveY + rippleY + noise2 * 0.4) * intensity;

    return distort;
}

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    float maskValue = texture(MaskSampler, texCoord).r;

    // Leave everything outside the mask completely alone. This pass covers
    // the whole screen, and SceneSampler is the capture taken *before* the
    // subject was drawn - so writing it back for unmasked pixels means any
    // gap in the mask erases whatever was rendered there. On entities that
    // wiped out every mob and player on screen. Emitting zero alpha (and
    // blending instead of replacing) makes an imperfect mask cost only a
    // missing effect, never destroyed geometry.
    if (maskValue < 0.5) {
        fragColor = vec4(0.0);
        return;
    }

    int style              = int(styleParams.x + 0.5);
    float saturation        = resolution.z;
    float doReflect         = resolution.w;
    float tintIntensity     = settings.x;
    float edgeGlowIntensity = settings.y;
    float time              = settings.z;
    float waveIntensity     = settings.w > 0.001 ? settings.w : 0.004;
    float edge               = getEdge(texCoord);

    // Screen-space texCoord (0..1) is far too coarse to drive noise with -
    // hands only cover a small patch of the window, so a "* 7.0" frequency
    // barely spans a fraction of one noise cell there and everything reads
    // as one flat, textureless blob. Sampling in actual pixels instead gives
    // every style real, visible detail regardless of how big the hands are
    // on screen.
    vec2 px = texCoord * resolution.xy;

    // Огонь (style 1) не обрабатывается здесь: он рисуется отдельным
    // пайплайном (HandTrailPipeline + HandFirePipeline) с накопительным
    // следом за рукой, и GlassHandsRenderer уходит на него раньше, чем
    // дело доходит до этого шейдера. См. renderFireEffect().

    // Общий "тинт" для стилей 2-6: пользовательский оттенок, если включён,
    // иначе фирменный цвет каждого стиля (как glowColor у Огня).
    vec3 fogTint = tintColor.a > 0.01 ? tintColor.rgb : vec3(0.15, 0.55, 0.95);
    float fogIntensity = mix(0.010, 0.045, clamp(edgeGlowIntensity, 0.0, 1.0));

    // Вода: интерференционный "туннель" - тот же приём, что классический
    // шейдертоевский Star Nest.
    if (style == 2) {
        vec3 p = pseudoRay(texCoord, resolution.xy) * 5.0;
        // The inner loop already multiplies time by 8-10x, so a scale of 1.0
        // ran the ripple at roughly ten times real time - fast enough to read
        // as a painful strobe rather than water. Caustic's 0.09 is calm, so
        // keep water in that neighbourhood, just slightly apart from it so
        // the two styles still move differently.
        vec3 water = tunnelColor(p, time, 0.12, fogIntensity, fogTint);
        water += edge * fogTint * max(edgeGlowIntensity, 0.6);
        fragColor = vec4(clamp(water, 0.0, 1.0), 1.0);
        return;
    }

    // Каустика: тот же туннель, но с другим множителем времени и чуть иным
    // балансом яркости - рябь получается мельче и быстрее.
    if (style == 3) {
        vec3 p = pseudoRay(texCoord, resolution.xy) * 5.0;
        vec3 caustic = tunnelColor(p, time, 0.09, fogIntensity, fogTint) * 1.5 + fogTint * 0.05;
        caustic += edge * fogTint * max(edgeGlowIntensity, 0.6);
        fragColor = vec4(clamp(caustic, 0.0, 1.0), 1.0);
        return;
    }

    // Туманность: объёмный fbm с ядром свечения внутри облака.
    if (style == 4) {
        vec3 p = pseudoRay(texCoord, resolution.xy) * (5.0 * 0.3 + 0.15);
        float t = time * 0.04;
        vec3 q = vec3(fbm3(p + vec3(0.0, t, 0.0)),
                       fbm3(p + vec3(1.7, 0.4, t * 0.8)),
                       fbm3(p + vec3(2.4, t * 1.2, 0.5)));
        float density = fbm3(p + q * 1.3);
        float innerCore = fbm3(p * 1.6 + q * 2.0 - vec3(0.0, t * 0.5, 0.0));

        vec3 glowColor2 = mix(vec3(fogTint.b, fogTint.r, fogTint.g), vec3(0.2, 0.8, 1.0), 0.35);
        vec3 voidDark = vec3(0.001, 0.001, 0.004);
        float cloudShape = pow(clamp(density, 0.0, 1.0), 2.2);
        vec3 nebula = mix(voidDark, fogTint * 0.45, cloudShape);
        float corePower = pow(clamp(innerCore * density, 0.0, 1.0), 2.0);
        nebula += glowColor2 * corePower * (1.8 + fogIntensity * 16.0);
        nebula += edge * fogTint * max(edgeGlowIntensity, 0.6);
        fragColor = vec4(clamp(nebula, 0.0, 1.0), 1.0);
        return;
    }

    // Плазма: домен-warp по fbm, три канала смещения дают перетекающие
    // цветные жилы, как в лава-лампе.
    if (style == 5) {
        vec3 p = pseudoRay(texCoord, resolution.xy) * (5.0 * 0.3 + 0.5);
        float t = time * 0.3;
        vec3 q = vec3(fbm3(p + vec3(0.0, t * 0.4, 0.0)),
                       fbm3(p + vec3(1.3, 0.8, t * 0.3)),
                       fbm3(p + vec3(2.8, t * 0.5, 1.1)));
        vec3 r = vec3(fbm3(p + 1.2 * q + vec3(1.7, 9.2, t * 0.6)),
                       fbm3(p + 1.2 * q + vec3(8.3, 2.8, t * 0.4)),
                       fbm3(p + 1.2 * q + vec3(3.2, 1.4, t * 0.5)));
        float f = fbm3(p + r * 1.8);

        vec3 color1 = fogTint;
        vec3 color2 = vec3(fogTint.b, fogTint.r, fogTint.g);
        vec3 color3 = vec3(1.0) - fogTint * 0.5;
        vec3 plasma = mix(color1, color2, clamp(f * f * 4.0, 0.0, 1.0));
        plasma = mix(plasma, color3, clamp(length(q), 0.0, 1.0));
        plasma = mix(plasma, color1 * 1.5, clamp(length(r.xy), 0.0, 1.0));
        plasma *= (f * 1.8 + fogIntensity * 12.0);
        plasma += edge * fogTint * max(edgeGlowIntensity, 0.6);
        fragColor = vec4(clamp(plasma, 0.0, 1.0), 1.0);
        return;
    }

    // Блум: изгибающиеся светящиеся нити на тёмном фоне.
    if (style == 6) {
        vec3 rayDir = pseudoRay(texCoord, resolution.xy);
        vec3 p = rayDir * (5.0 * 0.4 + 0.8);
        float t = time * 0.4;
        vec3 warp = vec3(noise3(p + vec3(0.0, t * 0.8, 0.0)),
                          noise3(p + vec3(t * 0.6, 12.4, t * 0.4)),
                          noise3(p + vec3(27.1, 0.0, t * 0.7))) * 1.2;
        vec3 distortedRay = normalize(rayDir + warp * 0.45);

        float lineBloom = 0.0;
        float lineCore = 0.0;
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float wave = sin(distortedRay.x * 2.5 + t + fi * 1.8) *
                         cos(distortedRay.z * 2.5 - t * 0.6 + fi * 0.7) * 0.45;
            float organicBend = (noise3(distortedRay * 2.0 + vec3(t * 0.3, fi * 4.0, t * 0.2)) - 0.5) * 0.6;
            float targetY = (fi - 1.5) * 0.3 + wave + organicBend;
            float dist = abs(rayDir.y - targetY);
            lineCore += exp(-110.0 * dist);
            lineBloom += exp(-11.0 * dist);
        }

        vec3 brightColor = fogTint * (2.0 + fogIntensity * 20.0);
        vec3 bloomColor = fogTint * (0.6 + fogIntensity * 10.0);
        vec3 bloom = fogTint * 0.02;
        bloom += bloomColor * lineBloom;
        bloom += brightColor * lineCore;
        bloom += edge * fogTint * max(edgeGlowIntensity, 0.6);
        fragColor = vec4(clamp(bloom, 0.0, 1.0), 1.0);
        return;
    }

    // Волновое смещение UV (используется только стеклом).
    vec2 distortion = waveDistortion(texCoord, time, waveIntensity);
    vec2 distortedUV = texCoord + distortion;
    distortedUV = clamp(distortedUV, vec2(0.0), vec2(1.0));

    // Проверяем маску на смещённых UV — не выходим за границы силуэта.
    float maskDistorted = texture(MaskSampler, distortedUV).r;
    vec2 blurUV = maskDistorted > 0.5 ? distortedUV : texCoord;

    if (doReflect > 0.5) {
        vec2 center = vec2(0.5, 0.5);
        vec2 offset = blurUV - center;
        blurUV = center - offset * 0.3 + offset;
    }

    vec4 blur = texture(BlurSampler, blurUV);

    // Стекло (по умолчанию): исходное поведение без изменений.
    vec3 glassColor = blur.rgb;

    glassColor = adjustSaturation(glassColor, saturation);

    if (tintIntensity > 0.001) {
        glassColor = mix(glassColor, tintColor.rgb, tintIntensity);
    }

    // Лёгкое мерцание от волн — имитация преломления света
    float waveLuma = length(distortion) / waveIntensity * 0.015;
    glassColor += waveLuma * mix(vec3(1.0), tintColor.rgb, 0.5);

    if (edgeGlowIntensity > 0.001) {
        vec3 glowColor = tintColor.rgb;
        if (tintColor.a < 0.01) {
            glowColor = vec3(1.0);
        }
        glassColor += edge * glowColor * edgeGlowIntensity;
    }

    float fresnel = pow(edge, 2.0) * 0.3;
    glassColor += fresnel * 0.1;

    glassColor = clamp(glassColor, vec3(0.0), vec3(1.0));

    fragColor = vec4(glassColor, 1.0);
}