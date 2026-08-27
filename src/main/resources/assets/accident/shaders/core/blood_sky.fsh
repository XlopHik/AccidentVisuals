#version 150

in  vec2 texCoord;
out vec4 fragColor;

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SkyData {
    float u_time;
    float u_sunAngle;
    float u_fogDensity;
    float u_starBright;
    float u_width;
    float u_height;
    float u_opacity;
    float _pad;
};


float hash21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float val = 0.0;
    float amp = 0.5;
    float freq = 1.0;
    for (int i = 0; i < 6; i++) {
        val  += noise(p * freq) * amp;
        freq *= 2.1;
        amp  *= 0.48;
    }
    return val;
}


float star(vec2 uv, float density) {
    vec2 cell  = floor(uv * density);
    vec2 local = fract(uv * density) - 0.5;
    vec2 jitter = (vec2(hash21(cell), hash21(cell + 37.3)) - 0.5) * 0.7;
    float dist = length(local - jitter);
    float twinkleSpeed = 0.5 + hash21(cell + 99.1) * 2.0;
    float twinkle = 0.7 + 0.3 * sin(u_time * twinkleSpeed + hash21(cell) * 6.28);
    float size = 0.015 + hash21(cell + 7.7) * 0.02;
    return smoothstep(size, 0.0, dist) * twinkle;
}


void main() {
    vec2 uv = texCoord;

    float depth = texture(DepthSampler, uv).r;
    float isSky = step(0.9999, depth);

    vec4 scene = texture(SceneSampler, uv);
    if (isSky < 0.5) {
        fragColor = scene;
        return;
    }

    float nightPhase = smoothstep(0.22, 0.28, u_sunAngle) - smoothstep(0.72, 0.78, u_sunAngle);
    float dayFactor  = 1.0 - nightPhase;

    vec3 colorHorizon = vec3(0.72, 0.04, 0.02);
    vec3 colorZenith  = vec3(0.18, 0.01, 0.01);
    float horizon = pow(1.0 - uv.y, 2.5);
    vec3 skyBase = mix(colorZenith, colorHorizon, horizon);
    skyBase = mix(skyBase * 0.25, skyBase, dayFactor);

    vec2 cloudUV = uv * vec2(2.5, 1.8);

    vec2 center = vec2(0.5, 0.35);
    vec2 delta  = uv - center;
    float dist  = length(delta);
    float swirl = u_time * 0.06 + dist * 1.5;
    cloudUV += vec2(cos(swirl), sin(swirl)) * dist * 0.45;

    cloudUV.x += u_time * 0.025;
    cloudUV.y += u_time * 0.008;

    float cloud1 = fbm(cloudUV);
    float cloud2 = fbm(cloudUV * 1.7 + vec2(3.3, 1.1) + u_time * 0.01);
    float cloudMask = smoothstep(0.38, 0.75, cloud1) * smoothstep(0.3, 0.6, cloud2);

    float cloudGrad  = fbm(cloudUV * 0.5 + 1.3);
    float detail     = fbm(cloudUV * 3.0 + vec2(u_time * 0.04, u_time * 0.015));
    vec3 cloudLight  = vec3(0.85, 0.10, 0.05);
    vec3 cloudDark   = vec3(0.20, 0.01, 0.01);
    vec3 cloudColor  = mix(cloudDark, cloudLight, cloudGrad);
    cloudColor = mix(cloudColor, cloudColor * 1.45, detail * 0.5);

    vec3 skyWithClouds = mix(skyBase, cloudColor, cloudMask * 0.85);

    vec2 fogUV1 = vec2(uv.x * 2.8 + u_time * 0.018, uv.y * 1.5 + u_time * 0.006);
    vec2 fogUV2 = vec2(uv.x * 1.9 - u_time * 0.012, uv.y * 2.1 + u_time * 0.009);
    float fogNoise = fbm(fogUV1) * 0.6 + fbm(fogUV2) * 0.4;

    float fogBase     = pow(max(0.0, 1.0 - uv.y * 2.2), 3.0);
    float fogStrength = u_fogDensity * fogBase * (0.5 + 0.5 * fogNoise);
    fogStrength = clamp(fogStrength, 0.0, 0.85);

    vec3 fogColor    = vec3(0.60, 0.03, 0.01);
    vec3 skyWithFog  = mix(skyWithClouds, fogColor, fogStrength);

    float starDrift = u_time * 0.003;
    vec2 starUV = vec2(
        uv.x + starDrift,
        uv.y
    );

    float starMask = smoothstep(0.2, 0.5, uv.y) * (0.25 + 0.75 * nightPhase);

    float stars  = star(starUV,          28.0) * 1.0;
    stars += star(starUV + 0.3,  55.0) * 0.7;
    stars += star(starUV + 0.7, 100.0) * 0.4;
    stars = clamp(stars, 0.0, 1.0);

    vec3 starColor = vec3(1.0, 0.88, 0.85);
    vec3 skyFinal  = skyWithFog + starColor * stars * starMask * u_starBright;

    vec2 vUV = uv * 2.0 - 1.0;
    float vignette = pow(clamp(1.0 - dot(vUV * vec2(0.4, 0.6), vUV * vec2(0.4, 0.6)), 0.0, 1.0), 0.5);
    skyFinal *= (0.75 + 0.25 * vignette);

    vec3 finalColor = mix(scene.rgb, skyFinal, u_opacity);
    fragColor = vec4(finalColor, scene.a);
}
