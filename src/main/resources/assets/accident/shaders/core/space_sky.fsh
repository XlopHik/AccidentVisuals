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

// Everything below works on a world-space view direction rather than on screen
// coordinates. The previous version shaded straight from texCoord, so the sky
// was a picture pasted on the display: turning the camera slid the aurora and
// the stars across the screen instead of leaving them where they belong.

float hash31(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

// Value noise straight on the sphere. The nebula and the aurora used to be
// sampled through atan(dir.z, dir.x), and that angle jumps from +pi to -pi at
// one bearing - the noise either side of the jump is unrelated, so it tore into
// a hard vertical line hanging in the world. Sampling the direction vector in
// three dimensions has no such wrap to fall off.
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

// Three octaves, not four: with the 0.48 falloff the fourth octave adds about
// 5% of the total amplitude, below what's visible once haze and blending land
// on top - and this runs on every screen pixel with no depth test, so every
// octave removed is another eight hash evaluations saved per pixel per frame.
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

// Two octaves, for the secondary/detail field in each noise pair (the one
// that only adds variation on top of a shape another fbm3 call already
// decided). Half the cost of fbm3 for a field nobody looks at directly.
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

// Stars live in a 3D grid of directions, so they hold still on the sky sphere
// while the player turns and they never bunch up at the poles the way a
// latitude/longitude mapping does.
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

    // No depth test and no scene sampling. This pass runs inside the vanilla sky
    // pass, before the terrain, the weather or anything else is drawn, so every
    // pixel here is sky by definition and everything the world draws afterwards
    // simply covers it. The old pass ran at the end of the frame and had to guess
    // from the depth buffer what was sky - which is why rain, whose quads write
    // depth, punched a square hole of old sky through the new one.

    // Screen pixel -> world-space view ray, straight out of the inverse of the
    // matrices the world itself was drawn with. Reconstructing this from yaw and
    // pitch by hand meant matching Minecraft's angle conventions exactly, and two
    // attempts at that were both subtly wrong - once mirrored, once rotating with
    // the view. The matrix cannot disagree with the engine.
    vec2 ndc = uv * 2.0 - 1.0;

    vec4 nearP = u_invViewProj * vec4(ndc, -1.0, 1.0);
    vec4 farP  = u_invViewProj * vec4(ndc,  1.0, 1.0);
    vec3 dir = normalize(farP.xyz / farP.w - nearP.xyz / nearP.w);

    float height = dir.y;                       // -1 straight down, +1 straight up
    float horizon = 1.0 - clamp(abs(height), 0.0, 1.0);

    float nightPhase = smoothstep(0.20, 0.30, u_sunAngle) - smoothstep(0.70, 0.80, u_sunAngle);
    nightPhase = clamp(nightPhase, 0.0, 1.0);

    // Deep space gradient: near black overhead, a cold blue bloom toward the
    // horizon rather than the old red.
    vec3 base = u_skyColor.rgb;
    int style = int(u_style + 0.5);

    vec3 sky;

    // --- shared ingredients -------------------------------------------------
    float h = clamp(height * 0.5 + 0.5, 0.0, 1.0);
    float aboveHorizon = smoothstep(-0.10, 0.10, height);

    // Only the star field is genuinely shared. The nebula and curtain noise
    // used to be computed here for every pixel of every style, but Galaxy
    // never reads the curtains and Aurora never reads the nebula - so most
    // styles threw away a full fbm3 field (dozens of hash evaluations per
    // pixel) every frame. They are now built inside the branches that
    // actually use them.
    float stars = starField(dir, 70.0, 0.075) * 1.0
                + starField(dir, 140.0, 0.055) * 0.6
                + starField(dir, 260.0, 0.040) * 0.35;
    stars = clamp(stars, 0.0, 1.5);
    vec3 starTint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.95, 0.85), hash31(floor(dir * 70.0)));

    vec3 nebP = dir * 2.4;
    vec3 bandP = vec3(dir.x, dir.y * 3.2, dir.z) * 3.0;

    if (style == 3 || style == 4) {
        // --- Borealis (green) / Solstice (violet): wide soft curtains
        // radiating from near the zenith. Integer-frequency sine waves
        // around the azimuth angle are exactly periodic, so they wrap with
        // zero seam and give the rays real linear structure that a raw noise
        // threshold can't - fbm noise has no preferred direction, so
        // thresholding it just carves out blobs, not rays. A wide smoothstep
        // (not a steep pow) is what keeps the bands soft and feathered
        // instead of hardening into thin lines. Colors are fixed per style,
        // not derived from the sky color setting - mixing in a bright base
        // color at a multiplier above 1.0 was what blew the whole curtain
        // out to flat white regardless of its shape, the real cause of every
        // "just noise/mush/lines" attempt before this one.
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
        // --- Tempest: dense, low, storm-purple cloud cover. No radiating
        // rays here - real clouds are blobs, not rays from a pole - and no
        // stars, since this is meant to read as an overcast sky the sun can
        // still be behind, not a clear night.
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
        // --- Galaxy: a bright band of the milky way across the sphere ------
        vec3 zenith = base * 0.05;
        sky = mix(base * 0.30, zenith, smoothstep(0.30, 0.95, h));

        // Distance from a great circle tilted off the horizon.
        vec3 axis = normalize(vec3(0.35, 0.82, 0.45));
        float d = abs(dot(dir, axis));
        float bandMask = 1.0 - smoothstep(0.02, 0.34, d);

        float dust = fbm3(dir * 5.5 + vec3(u_time * 0.004, 0.0, 0.0));
        float core = bandMask * (0.45 + 0.55 * dust);

        vec3 bandCol = mix(base * 1.6, vec3(1.0, 0.93, 0.82), dust * 0.55);
        sky += bandCol * core * 0.85;

        // Dark dust lanes cutting through it.
        sky *= 1.0 - bandMask * smoothstep(0.55, 0.85, fbm3Cheap(dir * 9.0 + 3.3)) * 0.55;

        sky += starTint * stars * aboveHorizon * u_starBright * (1.0 + core * 0.8);
    }
    else if (style == 1) {
        // --- Aurora: clear dark sky, curtains doing all the work -----------
        sky = mix(base * 0.55, base * 0.06, smoothstep(0.25, 0.95, h));

        float ribbon  = fbm3(bandP + vec3(u_time * 0.05, -u_time * 0.02, 0.0));
        float ribbon2 = fbm3Cheap(bandP * 2.2 + vec3(-u_time * 0.03, 2.1, 0.0));

        float shape = smoothstep(0.40, 0.92, ribbon * 0.65 + ribbon2 * 0.35);
        float band_h = smoothstep(-0.05, 0.32, height) * (1.0 - smoothstep(0.38, 0.95, height));
        float curtain = shape * band_h;

        // A second, higher and fainter set for depth.
        float shape2 = smoothstep(0.55, 0.95, fbm3Cheap(bandP * 1.4 + vec3(u_time * 0.02, 5.5, 0.0)));
        curtain += shape2 * smoothstep(0.15, 0.55, height) * (1.0 - smoothstep(0.6, 1.0, height)) * 0.5;

        vec3 lowCol  = vec3(0.15, 1.00, 0.62);
        vec3 highCol = vec3(0.35, 0.45, 1.00);
        sky += mix(lowCol, highCol, smoothstep(0.0, 0.55, height)) * curtain * 1.6 * (0.35 + 0.65 * nightPhase);

        sky += starTint * stars * aboveHorizon * u_starBright;
    }
    else {
        // --- Space: nebula clouds, faint curtains, dense stars -------------
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

    // Haze hugging the horizon, in the style's own tone.
    float haze = pow(horizon, 3.0) * u_fogDensity;
    sky = mix(sky, sky * 1.15 + base * 0.12, clamp(haze, 0.0, 0.8));

    // Alpha carries the opacity so the pipeline blends this over whatever the
    // vanilla sky already put down.
    fragColor = vec4(sky, u_opacity);
}
