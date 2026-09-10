#version 150

in  vec2 texCoord;
out vec4 fragColor;

layout(std140) uniform SkyData {
    float u_time;
    float u_sunAngle;
    float u_fogDensity;
    float u_starBright;
    float u_width;
    float u_height;
    float u_opacity;
    float u_style;
    mat4  u_invViewProj;
    vec4  u_skyColor;
};

// Ниже всё считается по направлению взгляда в мире, а не по координатам экрана.
// Раньше небо рисовалось прямо из texCoord, поэтому выглядело как картинка,
// приклеенная к экрану: при повороте камеры сияние и звёзды ехали вместе с ней.

float hash31(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

// Шум берётся прямо по сфере. Раньше туманность и сияние вычислялись через
// atan(dir.z, dir.x): на границе от +pi к -pi появлялся резкий шов. Трёхмерное
// направление взгляда не имеет такой границы, поэтому небо остаётся цельным.
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

// Трёх слоёв шума достаточно: четвёртый почти не влияет на картинку после тумана
// и смешивания цветов, зато ощутимо нагружает каждый пиксель кадра.
float fbm3(vec3 p) {
    float val = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        val += noise3(p) * amp;
        p = p * 2.1 + vec3(11.3, 7.7, 3.1);
        amp *= 0.48;
    }
    return val;
}

// Два слоя для вспомогательного шума: он лишь добавляет неровности к уже готовой
// форме, поэтому не требует полной точности основного fbm3.
float fbm3Cheap(vec3 p) {
    float val = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 2; i++) {
        val += noise3(p) * amp;
        p = p * 2.1 + vec3(11.3, 7.7, 3.1);
        amp *= 0.48;
    }
    return val;
}

// Небо Vibe строится на отдельном двумерном искажённом поле. Используем проекцию
// направления в мире, а не texCoord, чтобы детали оставались на своих местах
// при повороте камеры.
float hash21Vibe(vec2 p) {
    p = fract(p * vec2(443.8975, 397.2973));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float smoothNoise2DVibe(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21Vibe(i);
    float b = hash21Vibe(i + vec2(1.0, 0.0));
    float c = hash21Vibe(i + vec2(0.0, 1.0));
    float d = hash21Vibe(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2Vibe(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < 5; i++) {
        value += amplitude * smoothNoise2DVibe(p * frequency);
        frequency *= 2.1;
        amplitude *= 0.48;
    }
    return value;
}

vec2 warpVibe(vec2 p, float t) {
    vec2 q = vec2(
        fbm2Vibe(p + vec2(0.0, 0.0) + t * 0.12),
        fbm2Vibe(p + vec2(5.2, 1.3) + t * 0.09)
    );
    vec2 r = vec2(
        fbm2Vibe(p + 4.0 * q + vec2(1.7, 9.2) + t * 0.06),
        fbm2Vibe(p + 4.0 * q + vec2(8.3, 2.8) + t * 0.07)
    );
    return p + 1.8 * r;
}

// Объёмный складчатый фрактал из переданного шейдера Cosmos. Луч уже задан в
// мировых координатах, поэтому объём окружает игрока, а не приклеивается к экрану.
vec3 cosmosSky(vec3 worldDir, float time) {
    const int ITERATIONS = 17;
    const int VOL_STEPS = 20;
    const float FORMULA_PARAM = 0.53;
    const float STEP_SIZE = 0.10;
    const float TILE = 0.850;
    const float BRIGHTNESS = 0.0015;
    const float DARK_MATTER = 0.300;
    const float DIST_FADING = 0.730;

    float angle1 = 0.5 + time * 0.1;
    float angle2 = 0.8 + time * 0.15;
    mat2 rotation1 = mat2(cos(angle1), sin(angle1), -sin(angle1), cos(angle1));
    mat2 rotation2 = mat2(cos(angle2), sin(angle2), -sin(angle2), cos(angle2));

    vec3 ray = worldDir;
    ray.xz *= rotation1;
    ray.xy *= rotation2;

    vec3 from = vec3(1.0, 0.5, 0.5) + vec3(time * 2.0, time, -2.0);
    from.xz *= rotation1;
    from.xy *= rotation2;

    float distanceAlongRay = 0.1;
    float fade = 1.0;
    vec3 volume = vec3(0.0);

    for (int r = 0; r < VOL_STEPS; r++) {
        vec3 p = from + distanceAlongRay * ray * 0.5;
        p = abs(vec3(TILE) - mod(p, vec3(TILE * 2.0)));

        float previousLength = 0.0;
        float variation = 0.0;
        for (int i = 0; i < ITERATIONS; i++) {
            p = abs(p) / dot(p, p) - FORMULA_PARAM;
            variation += abs(length(p) - previousLength);
            previousLength = length(p);
        }

        float darkMatter = max(0.0, DARK_MATTER - variation * variation * 0.001);
        variation *= variation * variation;
        if (r > 6) fade *= 1.0 - darkMatter;
        volume += fade;
        volume += vec3(distanceAlongRay, distanceAlongRay * distanceAlongRay,
                       distanceAlongRay * distanceAlongRay * distanceAlongRay * distanceAlongRay)
                  * variation * BRIGHTNESS * fade;
        fade *= DIST_FADING;
        distanceAlongRay += STEP_SIZE;
    }

    volume = mix(vec3(length(volume)), volume, 1.0);
    return clamp(volume * 0.015, 0.0, 1.0);
}

// -------------------------------------------------------------------------
// Набор небес Crown — пять перенесённых шейдеров. Названия получили префикс
// Crown, потому что Aurora и Galaxy уже есть среди стилей клиента.

// Реализация находится ниже среди общих функций. Компилятору NVIDIA нужно это
// объявление заранее, потому что функции Crown вызывают starField раньше.
float starField(vec3 dir, float density, float sharpness);

float crownFbm(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    mat2 rotation = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        value += amplitude * smoothNoise2DVibe(p);
        p = rotation * p;
        amplitude *= 0.5;
    }
    return value;
}

float crownBoltMask(vec2 uv, vec2 origin, float seed, float width) {
    float distanceToBolt = 1e9;
    float x = origin.x;
    const float segmentLength = 0.06;
    for (int i = 0; i < 14; i++) {
        float y = origin.y - float(i) * segmentLength;
        float wobble = (fract(sin(seed + float(i) * 7.31) * 43758.5453) - 0.5) * 0.09;
        float nextX = x + wobble * float(i) * 0.4;
        vec2 a = vec2(x, y);
        vec2 b = vec2(nextX, y - segmentLength);
        vec2 ba = b - a;
        float t = clamp(dot(uv - a, ba) / dot(ba, ba), 0.0, 1.0);
        distanceToBolt = min(distanceToBolt, length(uv - a - ba * t));
        x = nextX;
    }
    return exp(-distanceToBolt * distanceToBolt / (2.0 * width * width));
}

float crownCrystalCells(vec2 p, out float edge) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    float nearest = 1e9;
    float secondNearest = 1e9;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbour = vec2(float(x), float(y));
            vec2 point = neighbour + vec2(
                hash21Vibe(cell + neighbour),
                hash21Vibe(cell + neighbour + 19.7)
            ) - local;
            float distanceSquared = dot(point, point);
            if (distanceSquared < nearest) {
                secondNearest = nearest;
                nearest = distanceSquared;
            } else if (distanceSquared < secondNearest) {
                secondNearest = distanceSquared;
            }
        }
    }
    edge = smoothstep(0.0, 0.05, secondNearest - nearest);
    return nearest;
}

vec3 crownAuroraSky(vec3 dir, float time, vec3 base, float starBrightness) {
    float up = clamp(dir.y, 0.0, 1.0);
    vec3 sky = mix(base * 0.45, base * 0.06, up);
    vec2 p = vec2(dir.x, dir.y * 2.2 + dir.z * 0.3);
    p.x += time * 0.03;
    float bands = 0.0;
    for (int i = 0; i < 4; i++) {
        float layer = float(i);
        float wave = crownFbm(vec2(p.x * 1.3 + layer * 3.7, time * 0.08 + layer), 6) * 0.35;
        float curtain = crownFbm(vec2(p.x * 4.0 + layer * 11.0, time * 0.15), 6);
        float distanceFromBand = abs((p.y * 0.5 + 0.5) - (0.25 + layer * 0.18) - wave);
        bands += exp(-distanceFromBand * distanceFromBand * 60.0) * (0.5 + 0.5 * curtain);
    }
    bands = clamp(bands, 0.0, 1.5);
    float rays = smoothstep(0.4, 0.9, crownFbm(vec2(p.x * 8.0, time * 0.05), 6));
    bands *= (0.6 + 0.6 * rays) * (0.85 + 0.15 * sin(time * 0.7 + p.x * 20.0));
    vec3 aurora = mix(vec3(0.10, 1.00, 0.46), vec3(0.82, 0.18, 0.95),
                       0.5 + 0.5 * sin(time * 0.1 + p.x * 2.0 + bands * 2.0));
    float stars = starField(dir, 180.0, 0.052) * (0.4 + 0.6 * up);
    sky += aurora * bands + aurora * exp(-bands * 2.0) * bands * 0.3;
    return sky + vec3(0.82, 0.92, 1.0) * stars * starBrightness;
}

vec3 crownGalaxySky(vec3 dir, float time, vec3 base, float starBrightness) {
    float up = clamp(dir.y, 0.0, 1.0);
    vec3 sky = mix(base * 0.42, base * 0.05, up);
    vec3 axis = normalize(vec3(0.35, 0.55, -0.75));
    float band = exp(-pow(abs(dot(dir, axis)), 2.0) * 8.0);
    vec2 galacticUv = vec2(atan(dir.z, dir.x) * 1.5, dir.y * 3.0) + vec2(time * 0.01, 0.0);
    float dust = crownFbm(galacticUv * 1.2, 5);
    float dustFine = crownFbm(galacticUv * 3.5 + vec2(4.2, 1.1), 5);
    float milkyWay = band * (0.5 + 0.5 * dust) * (0.6 + 0.4 * dustFine);
    vec3 nebula = mix(vec3(0.22, 0.70, 1.0), vec3(0.78, 0.18, 0.92),
                       0.5 + 0.5 * sin(dust * 6.0 + time * 0.05)) * milkyWay;
    float stars = starField(dir, 220.0, 0.050) + starField(dir, 374.0, 0.036) * 0.7
                + starField(dir, 132.0, 0.075) * 1.2;
    return sky + nebula + vec3(0.22, 0.70, 1.0) * milkyWay * 0.15
         + vec3(0.95, 0.95, 1.0) * clamp(stars, 0.0, 1.0) * starBrightness;
}

vec3 crownSunsetSky(vec3 dir, float time, vec3 base) {
    float up = clamp(dir.y, 0.0, 1.0);
    vec3 sunDirection = normalize(vec3(0.3, 0.18, -0.9));
    float sunDot = clamp(dot(dir, sunDirection), 0.0, 1.0);
    vec3 sky = mix(vec3(0.95, 0.22, 0.08), base * 0.18, pow(up, 0.6));
    sky += vec3(1.0, 0.68, 0.22) * pow(sunDot, 8.0) * 0.6;
    vec2 p = dir.xz / max(dir.y + 0.35, 0.05) * 0.6 + vec2(time * 0.02, time * 0.008);
    float clouds = smoothstep(0.35, 0.85, crownFbm(p * 1.3, 6) * 0.7
                             + crownFbm(p * 3.7 + vec2(5.1, 2.3), 6) * 0.3);
    clouds *= 1.0 - smoothstep(0.0, 0.6, dir.y);
    float shade = crownFbm(p * 6.0 + 10.0, 6);
    vec3 cloudColor = mix(vec3(0.15, 0.02, 0.16), vec3(1.0, 0.28, 0.06), pow(sunDot, 2.0));
    cloudColor = mix(cloudColor, vec3(1.0, 0.72, 0.26), pow(sunDot, 12.0) * 0.5) * (0.75 + 0.4 * shade);
    return sky + cloudColor * clouds + vec3(1.0, 0.72, 0.26) * smoothstep(0.9985, 0.9995, sunDot) * 3.0;
}

vec3 crownStormSky(vec3 dir, float time, vec3 base) {
    vec2 p = dir.xz / max(dir.y + 0.3, 0.05) * 0.5 + vec2(time * 0.015, time * 0.01);
    float detail = crownFbm(p * 3.0 + vec2(3.3, 1.1), 6);
    float clouds = smoothstep(0.3, 0.9, crownFbm(p * 1.1, 6) * 0.65 + detail * 0.35);
    float cloudMask = 1.0 - smoothstep(-0.1, 0.7, dir.y);
    clouds *= cloudMask;
    vec3 sky = mix(base * 0.26, base * 0.04, clamp(dir.y, 0.0, 1.0));
    sky += mix(vec3(0.035, 0.055, 0.10), vec3(0.10, 0.20, 0.38), detail) * clouds;
    float flashTime = floor(time * 0.45);
    float flashSeed = fract(sin(flashTime * 12.9898) * 43758.5453);
    float flashActive = step(0.75, flashSeed);
    float flashPulse = exp(-fract(time * 0.45) * 18.0);
    vec2 origin = vec2((fract(sin(flashTime * 3.71) * 43758.5453) - 0.5) * 1.4, 0.9);
    float bolt = crownBoltMask(vec2(dir.x, dir.y) * 1.2, origin, flashTime * 5.13, 0.02) * flashActive * flashPulse;
    vec3 lightning = vec3(0.72, 0.88, 1.0);
    return sky + lightning * (bolt * 4.0 + flashActive * flashPulse * 0.6 * cloudMask);
}

vec3 crownCrystalSky(vec3 dir, float time, vec3 base, float starBrightness) {
    float up = clamp(dir.y, 0.0, 1.0);
    vec2 sphereUv = vec2(atan(dir.z, dir.x), acos(clamp(dir.y, -1.0, 1.0))) * vec2(2.5, 3.0)
                  + vec2(time * 0.01, -time * 0.006);
    float edge;
    float cell = crownCrystalCells(sphereUv * 3.5, edge);
    float mist = crownFbm(sphereUv * 0.8 + time * 0.02, 5);
    float mistFine = crownFbm(sphereUv * 1.7 - time * 0.015, 5);
    vec3 sky = mix(base * 0.28, base * 0.04, up);
    vec3 mistColor = mix(vec3(0.18, 0.72, 1.0), vec3(0.70, 0.40, 1.0),
                         0.5 + 0.5 * sin(mist * 5.0 + time * 0.08)) * (0.3 + 0.5 * mistFine);
    float shard = (1.0 - edge) * smoothstep(0.0, 0.15, cell);
    float facet = exp(-cell * 10.0);
    vec3 shardGlow = vec3(0.75, 0.95, 1.0) * (shard * 0.8 + facet * 1.5)
                   * (0.7 + 0.3 * sin(time * 0.6 + cell * 20.0));
    float stars = starField(dir, 300.0, 0.040);
    return sky + mistColor + shardGlow * (0.4 + 0.6 * up) + vec3(0.75, 0.95, 1.0) * stars * starBrightness;
}

// Звёзды распределены в трёхмерной сетке направлений. Поэтому они не смещаются
// при повороте игрока и не скапливаются у полюсов, как при широте и долготе.
float starField(vec3 dir, float density, float sharpness) {
    vec3 p = dir * density;
    vec3 cell = floor(p);
    vec3 local = fract(p) - 0.5;

    float rnd = hash31(cell);
    if (rnd < 0.90) return 0.0;

    vec3 jitter = vec3(hash31(cell + 3.1), hash31(cell + 7.7), hash31(cell + 11.3)) - 0.5;
    float dist = length(local - jitter * 0.6);

    float twinkleSpeed = 0.6 + hash31(cell + 23.4) * 2.2;
    float twinkle = 0.65 + 0.35 * sin(u_time * twinkleSpeed + rnd * 6.28318);

    return smoothstep(sharpness, 0.0, dist) * twinkle;
}

void main() {
    vec2 uv = texCoord;

    // Здесь нет проверки глубины и чтения сцены. Проход запускается вместе с
    // ванильным небом, до мира и погоды: всё, что будет нарисовано позже, просто
    // закроет его. Раньше проход шёл в конце кадра и угадывал небо по буферу
    // глубины, из-за чего дождь иногда оставлял квадраты старого неба.

    // Превращаем пиксель экрана в луч взгляда в мире через обратную матрицу того
    // же рендера, которым нарисован сам мир. Так не нужно вручную повторять углы
    // Minecraft и получать отражённое либо вращающееся вместе с камерой небо.
    vec2 ndc = uv * 2.0 - 1.0;

    vec4 nearP = u_invViewProj * vec4(ndc, -1.0, 1.0);
    vec4 farP  = u_invViewProj * vec4(ndc,  1.0, 1.0);
    vec3 dir = normalize(farP.xyz / farP.w - nearP.xyz / nearP.w);

    float height = dir.y;                       // -1 — вниз, +1 — вверх
    float horizon = 1.0 - clamp(abs(height), 0.0, 1.0);

    float nightPhase = smoothstep(0.20, 0.30, u_sunAngle) - smoothstep(0.70, 0.80, u_sunAngle);
    nightPhase = clamp(nightPhase, 0.0, 1.0);

    // Градиент глубокого космоса: почти чёрный вверху и холодное синее свечение
    // у горизонта вместо прежнего красного оттенка.
    vec3 base = u_skyColor.rgb;
    int style = int(u_style + 0.5);

    vec3 sky;

    // --- Общие составляющие -------------------------------------------------
    float h = clamp(height * 0.5 + 0.5, 0.0, 1.0);
    float aboveHorizon = smoothstep(-0.10, 0.10, height);

    // По-настоящему общим остаётся только звёздное поле. Раньше туманность и
    // шум сияния считались для каждого стиля, хотя Galaxy не нужны шторы, а
    // Aurora — туманность. Теперь тяжёлые поля строятся только там, где нужны.
    float stars = starField(dir, 70.0, 0.075) * 1.0
                + starField(dir, 140.0, 0.055) * 0.6
                + starField(dir, 260.0, 0.040) * 0.35;
    stars = clamp(stars, 0.0, 1.5);
    vec3 starTint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.95, 0.85), hash31(floor(dir * 70.0)));

    vec3 nebP = dir * 2.4;
    vec3 bandP = vec3(dir.x, dir.y * 3.2, dir.z) * 3.0;

    if (style == 12) {
        sky = crownCrystalSky(dir, u_time, base, u_starBright);
    }
    else if (style == 11) {
        sky = crownStormSky(dir, u_time, base);
    }
    else if (style == 10) {
        sky = crownSunsetSky(dir, u_time, base);
    }
    else if (style == 9) {
        sky = crownGalaxySky(dir, u_time, base, u_starBright);
    }
    else if (style == 8) {
        sky = crownAuroraSky(dir, u_time, base, u_starBright);
    }
    else if (style == 7) {
        // --- Cosmos: анимированный объёмный складчатый фрактал -------------
        sky = cosmosSky(dir, u_time * 0.010 + 0.25);
    }
    else if (style == 6) {
        // --- Vibe: текучие контрастные розово-оранжевые космические облака ---
        // Эффект перенесён из переданного шейдера. Палитра начинается с цвета
        // неба из настроек, поэтому стиль не выбивается из выбранной темы.
        float anim = u_time;
        vec2 p = dir.xz * 1.35 + vec2(0.0, dir.y * 0.20);
        p += vec2(anim * 0.040, anim * 0.025);
        vec2 warped = warpVibe(p, anim * 0.045);

        float n1 = fbm2Vibe(warped * 0.9);
        float n2 = fbm2Vibe(warped * 1.5 + vec2(2.7, 4.3));
        float n3 = fbm2Vibe(warped * 0.5 + vec2(-1.8, 3.1));
        float n4 = fbm2Vibe(warped * 2.2 + vec2(-3.0, 1.2));
        float juice = smoothstep(0.15, 0.85, n1 * 0.40 + n2 * 0.25 + n3 * 0.20 + n4 * 0.15);

        float heightMask = 0.70 + 0.30 * smoothstep(0.0, 0.60, max(height, 0.0));
        float flicker = (0.80 + 0.20 * sin(anim * 0.50 + dir.x * 14.0 + dir.z * 10.0))
                      * (0.90 + 0.10 * sin(anim * 0.90 + dir.y * 18.0));
        juice *= heightMask * flicker;

        vec3 nebulaPrimary = mix(base * 0.75, vec3(1.0, 0.20, 0.42), 0.48);
        vec3 nebulaSecondary = mix(vec3(0.72, 0.08, 0.38), vec3(1.0, 0.64, 0.12), 0.45);
        float paletteWave1 = 0.5 + 0.5 * sin(anim * 0.06 + p.x * 1.2 + p.y * 0.8 + juice * 1.5);
        float paletteWave2 = 0.5 + 0.5 * cos(anim * 0.08 + p.x * 0.9 - p.y * 1.1 + juice * 2.0);
        sky = mix(nebulaPrimary, nebulaSecondary, paletteWave1);
        sky = mix(sky, vec3(1.0, 0.90, 0.80), paletteWave2 * 0.30);

        float hot = exp(-abs(juice - 0.40) * 6.0) * 0.60;
        sky += vec3(1.0, 0.95, 0.70) * hot;

        // Редкая звёздная пыль в трёхмерной сетке направлений: в отличие от
        // экранных блёсток, она не скользит по экрану при осмотре вокруг.
        float glitter = starField(dir + vec3(anim * 0.002, 0.0, -anim * 0.001), 42.0, 0.090)
                      + starField(dir + vec3(0.0, anim * 0.001, 0.0), 86.0, 0.060) * 0.60;
        sky += vec3(1.0, 0.83, 0.35) * glitter * u_starBright * 0.80;

        float bloom = exp(-juice * 3.0) * 0.20;
        sky += vec3(0.90, 0.80, 1.0) * bloom;
        sky *= juice * 1.30 + hot * 0.30 + glitter * 0.40;
        sky += base * (0.025 + 0.075 * h);
    }
    else if (style == 3 || style == 4) {
        // --- Borealis (зелёный) / Solstice (фиолетовый): широкие мягкие
        // шторы, расходящиеся от зенита. Целые частоты синуса замыкаются без
        // шва и дают лучам направление, которого нет у обычного шума. Широкий
        // smoothstep сохраняет мягкие края, а фиксированные цвета стилей не
        // дают эффекту превращаться в сплошной белый фон.
        vec2 azDir = normalize(dir.xz + vec2(1e-4, 0.0));
        float angle = atan(azDir.y, azDir.x);

        float rayNoise = fbm3Cheap(dir * 2.0 + vec3(u_time * 0.012, 0.0, -u_time * 0.008));

        float raysRaw  = sin(angle * 3.0 + rayNoise * 4.0 + u_time * 0.08) * 0.5 + 0.5;
        float rays     = smoothstep(0.10, 0.80, raysRaw);

        float rays2Raw = sin(angle * 5.0 - rayNoise * 3.0 + u_time * 0.11) * 0.5 + 0.5;
        float rays2    = smoothstep(0.15, 0.85, rays2Raw);

        float curtain = max(rays * 0.85, rays2 * 0.65);
        float verticalMask = smoothstep(-0.2, 0.55, height) * (1.0 - smoothstep(0.55, 1.0, height));
        curtain *= verticalMask;

        vec3 lowCol, highCol;
        if (style == 3) {
            lowCol  = vec3(0.12, 0.95, 0.55);
            highCol = vec3(0.05, 0.55, 0.45);
        } else {
            lowCol  = vec3(0.55, 0.30, 0.95);
            highCol = vec3(0.25, 0.15, 0.65);
        }

        sky = mix(vec3(0.02, 0.03, 0.05), vec3(0.0), smoothstep(0.1, 0.9, h));
        sky += mix(lowCol, highCol, smoothstep(0.0, 0.7, height)) * curtain * (0.75 + 0.25 * nightPhase);

        sky += starTint * stars * aboveHorizon * u_starBright * 0.85;
    }
    else if (style == 5) {
        // --- Tempest: плотная низкая фиолетовая грозовая облачность. Здесь нет
        // лучей и звёзд: это пасмурное небо, за которым всё ещё может быть солнце.
        vec3 cloud1P = dir * 1.3;
        vec3 cloud2P = dir * 2.7;
        float cloud1 = fbm3(cloud1P + vec3(u_time * 0.02, u_time * 0.01, 0.0));
        float cloud2 = fbm3Cheap(cloud2P + vec3(-u_time * 0.015, 0.0, u_time * 0.012));
        float cloudMask = smoothstep(0.30, 0.75, cloud1 * 0.65 + cloud2 * 0.35);

        vec3 deepPurple = vec3(0.075, 0.035, 0.115);
        vec3 midPurple  = vec3(0.18, 0.09, 0.24);
        vec3 highlight  = vec3(0.48, 0.30, 0.58);

        sky = mix(deepPurple, midPurple, smoothstep(0.1, 0.9, h));
        sky = mix(sky, highlight, cloudMask * 0.55);

        float rim = smoothstep(0.45, 0.62, cloud1) * (1.0 - smoothstep(0.62, 0.82, cloud1));
        sky += highlight * rim * 0.35;
    }
    else if (style == 2) {
        // --- Galaxy: яркая полоса Млечного Пути через всю небесную сферу -----
        vec3 zenith = base * 0.05;
        sky = mix(base * 0.30, zenith, smoothstep(0.30, 0.95, h));

        // Расстояние до большой окружности, наклонённой относительно горизонта.
        vec3 axis = normalize(vec3(0.35, 0.82, 0.45));
        float d = abs(dot(dir, axis));
        float bandMask = 1.0 - smoothstep(0.02, 0.34, d);

        float dust = fbm3(dir * 5.5 + vec3(u_time * 0.004, 0.0, 0.0));
        float core = bandMask * (0.45 + 0.55 * dust);

        vec3 bandCol = mix(base * 1.6, vec3(1.0, 0.93, 0.82), dust * 0.55);
        sky += bandCol * core * 0.85;

        // Тёмные полосы космической пыли, пересекающие полосу галактики.
        sky *= 1.0 - bandMask * smoothstep(0.55, 0.85, fbm3Cheap(dir * 9.0 + 3.3)) * 0.55;

        sky += starTint * stars * aboveHorizon * u_starBright * (1.0 + core * 0.8);
    }
    else if (style == 1) {
        // --- Aurora: чистое тёмное небо, где всё настроение создают шторы ----
        sky = mix(base * 0.55, base * 0.06, smoothstep(0.25, 0.95, h));

        float ribbon  = fbm3(bandP + vec3(u_time * 0.05, -u_time * 0.02, 0.0));
        float ribbon2 = fbm3Cheap(bandP * 2.2 + vec3(-u_time * 0.03, 2.1, 0.0));

        float shape = smoothstep(0.40, 0.92, ribbon * 0.65 + ribbon2 * 0.35);
        float band_h = smoothstep(-0.05, 0.32, height) * (1.0 - smoothstep(0.38, 0.95, height));
        float curtain = shape * band_h;

        // Второй, более высокий и тусклый слой добавляет глубину.
        float shape2 = smoothstep(0.55, 0.95, fbm3Cheap(bandP * 1.4 + vec3(u_time * 0.02, 5.5, 0.0)));
        curtain += shape2 * smoothstep(0.15, 0.55, height) * (1.0 - smoothstep(0.6, 1.0, height)) * 0.5;

        vec3 lowCol  = vec3(0.15, 1.00, 0.62);
        vec3 highCol = vec3(0.35, 0.45, 1.00);
        sky += mix(lowCol, highCol, smoothstep(0.0, 0.55, height)) * curtain * 1.6 * (0.35 + 0.65 * nightPhase);

        sky += starTint * stars * aboveHorizon * u_starBright;
    }
    else {
        // --- Space: туманность, слабые шторы и плотное звёздное поле ---------
        vec3 zenith  = base * 0.09;
        vec3 mid     = base * 0.34;
        vec3 horizonC= base;

        sky = mix(horizonC, mid, smoothstep(0.35, 0.62, h));
        sky = mix(sky, zenith, smoothstep(0.60, 0.95, h));

        float neb1 = fbm3(nebP + vec3(u_time * 0.010, u_time * 0.004, 0.0));
        float neb2 = fbm3Cheap(nebP * 1.9 + vec3(-u_time * 0.006, u_time * 0.003, 4.7));
        float ribbon  = fbm3(bandP + vec3(u_time * 0.05, -u_time * 0.02, 0.0));
        float ribbon2 = fbm3Cheap(bandP * 2.2 + vec3(-u_time * 0.03, 2.1, 0.0));

        float nebMask = smoothstep(0.42, 0.85, neb1) * smoothstep(0.30, 0.70, neb2);
        nebMask *= smoothstep(-0.25, 0.45, height);
        sky = mix(sky, mix(base * 0.75, min(base * 2.6 + 0.10, vec3(1.0)), neb2), nebMask * 0.55);

        float shape = smoothstep(0.45, 0.95, ribbon * 0.65 + ribbon2 * 0.35);
        float band_h = smoothstep(-0.05, 0.28, height) * (1.0 - smoothstep(0.30, 0.85, height));
        sky += mix(vec3(0.10, 0.85, 0.80), vec3(0.25, 0.45, 1.00), smoothstep(0.0, 0.5, height))
             * shape * band_h * 1.35 * (0.35 + 0.65 * nightPhase);

        sky += starTint * stars * aboveHorizon * u_starBright * (0.25 + 0.75 * nightPhase);
    }

    // Дымка у горизонта в оттенке выбранного стиля.
    float haze = pow(horizon, 3.0) * u_fogDensity;
    sky = mix(sky, sky * 1.15 + base * 0.12, clamp(haze, 0.0, 0.8));

    // В альфа-канале хранится прозрачность, чтобы пайплайн правильно наложил
    // эффект поверх ванильного неба.
    fragColor = vec4(sky, u_opacity);
}
