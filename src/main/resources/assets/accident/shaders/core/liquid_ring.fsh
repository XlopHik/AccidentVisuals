#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform LiquidRingData {
    vec4 camPosNear;        // xyz = camera world pos, w = near plane
    vec4 params;            // x = width, y = height, z = far plane, w = active ring count
    vec4 camRight;          // xyz = camera right vector, world space
    vec4 camUp;             // xyz = camera up vector, world space
    vec4 tintColorAmount;   // rgb = rim colour, a = how brightly it burns
    vec4 reserved;
    vec4 columnParams;      // x = glow column enabled, y = height, z = width fraction, w = alpha
    mat4 invViewProj;
    vec4 ringPosRadius[16]; // xyz = bubble centre (world), w = current radius
    vec4 ringMeta[16];      // x = rim width, y = envelope, z = refraction amount, w unused
};

// A glass dome lying on the ground.
//
// For each pixel the view ray is intersected with the ground plane the dome
// rests on, which gives the exact point under the glass that pixel is looking
// through. The dome is a lens over that circle rather than a solid the ray
// enters - it deliberately stays flush with the floor and never rises above
// it, since a silhouette standing up out of the ground turned out to read as
// too much rather than as a nicer bubble.
void main() {
    float rawDepth = texture(DepthSampler, texCoord).r;
    bool hasScene = rawDepth < 0.9999;

    vec4 clip = vec4(texCoord * 2.0 - 1.0, rawDepth * 2.0 - 1.0, 1.0);
    vec4 unprojected = invViewProj * clip;
    // Camera-relative, deliberately: Minecraft's view matrix is built that
    // way, and the ray maths below is cleaner with the camera at the origin.
    vec3 rel = unprojected.xyz / unprojected.w;
    vec3 worldPos = rel + camPosNear.xyz;

    float sceneDist = hasScene ? length(rel) : 1e9;
    vec3 rayDir = normalize(rel);

    float aspect = params.x / max(params.y, 1.0);
    int count = int(params.w + 0.5);

    vec2 offset = vec2(0.0);
    float effectPresence = 0.0;
    float rimGlow = 0.0;
    float columnGlow = 0.0;

    for (int i = 0; i < 16; i++) {
        if (i >= count) break;

        vec3 center = ringPosRadius[i].xyz;
        vec3 centerRel = center - camPosNear.xyz;
        float radius = ringPosRadius[i].w;
        float rimWidth = max(ringMeta[i].x, 1e-4);
        float env = ringMeta[i].y;
        float domeAmp = ringMeta[i].z;
        if (env <= 0.001 || radius <= 0.001) continue;

        // Rising glow column, optional: a soft cylinder of light standing over
        // the bubble, shown where scene geometry passes through it.
        if (columnParams.x > 0.5 && hasScene) {
            float colHeight = max(columnParams.y, 0.001);
            float heightAbove = worldPos.y - center.y;
            if (heightAbove > 0.0 && heightAbove < colHeight) {
                float colRadius = max(radius * columnParams.z, 0.05);
                float distXZ = length(worldPos.xz - center.xz);
                float colMask = 1.0 - smoothstep(colRadius * 0.6, colRadius, distXZ);
                colMask *= (1.0 - heightAbove / colHeight) * env;
                columnGlow += colMask;
            }
        }

        if (!hasScene) continue;
        if (abs(rayDir.y) < 1e-4) continue;

        float tPlane = centerRel.y / rayDir.y;
        if (tPlane <= 0.0) continue;

        vec3 hit = rayDir * tPlane;
        // Anything solid in front of the plane hides the dome behind it.
        if (sceneDist * sceneDist < dot(hit, hit) - 0.25) continue;

        vec2 d = hit.xz - centerRel.xz;
        float dist = length(d);
        vec2 screenDir = dist > 1e-5 ? d / dist : vec2(0.0);
        screenDir.x /= aspect;

        // Refraction through the dome. Treating the interior as a hemisphere
        // of the same radius, the glass stands sqrt(1 - r^2) high at this
        // point, and how steeply it is tilted there - r/h - decides how far
        // light passing through gets bent. Flat and clear at the centre where
        // the glass faces straight up, bending hard near the rim where it
        // curves away: that is what magnifies the middle and drags it outward.
        if (dist < radius) {
            float r = dist / radius;
            float h = sqrt(max(1.0 - r * r, 0.0));
            // Floored, because a true hemisphere's slope goes vertical at the
            // rim and the offset would otherwise run away to infinity there.
            float bend = r / max(h, 0.18);
            offset += screenDir * bend * domeAmp;
            effectPresence = max(effectPresence, env);
        }

        // The rim: light gathering along the edge of the glass, falling off
        // smoothly to either side so it reads as a glow rather than a drawn
        // outline.
        float rim = exp(-abs(dist - radius) / rimWidth) * env;
        rimGlow = max(rimGlow, rim);
        effectPresence = max(effectPresence, rim);
    }

    columnGlow = clamp(columnGlow, 0.0, 1.0) * columnParams.w;

    if (effectPresence < 0.002 && columnGlow < 0.002) {
        fragColor = vec4(0.0);
        return;
    }

    // Replacing the pixel with the refracted scene rather than laying a
    // translucent sheet of glass over it - blending a tinted overlay is what
    // washes the image out and costs the effect its edge.
    vec3 col = texture(SceneSampler, clamp(texCoord + offset, vec2(0.001), vec2(0.999))).rgb;

    if (rimGlow > 0.002) {
        // The hottest part of the rim blows out towards white while its
        // shoulders keep the tint colour, the way a bright light source reads
        // against its own halo.
        float core = rimGlow * rimGlow * rimGlow;
        vec3 rimCol = mix(tintColorAmount.rgb, vec3(1.0), core);
        col += rimCol * rimGlow * tintColorAmount.a * 1.6;
    }

    col += tintColorAmount.rgb * columnGlow;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
