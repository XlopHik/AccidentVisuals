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
    vec4 columnParams;      // unused here
    mat4 invViewProj;
    vec4 ringPosRadius[16]; // xyz = blast centre (world), w = current radius
    vec4 ringMeta[16];      // x = shell width, y = envelope, z = refraction amount
};

// A spherical shockwave, as opposed to the dome liquid_ring.fsh lays on the
// ground. Nothing here touches the floor: the ray is intersected with a sphere
// around the blast, so the wave reads the same whether it goes off above,
// below or level with the camera.
//
// The perpendicular distance from the blast centre to the view ray gives, for
// free, how far across the sphere's disc this pixel is looking - the same
// quantity the dome got from a plane hit, but valid from any direction.
void main() {
    float rawDepth = texture(DepthSampler, texCoord).r;
    bool hasScene = rawDepth < 0.9999;

    vec4 clip = vec4(texCoord * 2.0 - 1.0, rawDepth * 2.0 - 1.0, 1.0);
    vec4 unprojected = invViewProj * clip;
    vec3 rel = unprojected.xyz / unprojected.w;

    float sceneDist = hasScene ? length(rel) : 1e9;
    vec3 rayDir = normalize(rel);

    float aspect = params.x / max(params.y, 1.0);
    int count = int(params.w + 0.5);

    vec2 offset = vec2(0.0);
    float effectPresence = 0.0;
    float rimGlow = 0.0;

    for (int i = 0; i < 16; i++) {
        if (i >= count) break;

        vec3 centerRel = ringPosRadius[i].xyz - camPosNear.xyz;
        float radius = ringPosRadius[i].w;
        float shellWidth = max(ringMeta[i].x, 1e-4);
        float env = ringMeta[i].y;
        float amp = ringMeta[i].z;
        if (env <= 0.001 || radius <= 0.001) continue;

        // Closest approach of the ray to the centre.
        float along = dot(centerRel, rayDir);
        vec3 perp = rayDir * along - centerRel;
        float dist = length(perp);

        // Behind the camera, or the sphere is entirely off this ray.
        if (along <= 0.0 && dist > radius) continue;

        // Where the wave front sits along the ray, so solid geometry in front
        // of it can hide it.
        if (dist < radius) {
            float half = sqrt(max(radius * radius - dist * dist, 0.0));
            float tFront = along - half;
            if (tFront > 0.0 && sceneDist < tFront - 0.25) continue;
        } else if (sceneDist < along - 0.25) {
            continue;
        }

        // Outward direction on screen, taken from the camera basis so it holds
        // at any viewing angle rather than only from above.
        vec2 screenDir = vec2(dot(perp, camRight.xyz), dot(perp, camUp.xyz));
        float screenLen = length(screenDir);
        screenDir = screenLen > 1e-5 ? screenDir / screenLen : vec2(0.0);
        screenDir.x /= aspect;

        // Refraction through the shell. Sight lines near the silhouette graze
        // the surface and bend hardest; straight through the middle passes
        // almost undisturbed - which is what makes it read as an expanding
        // shell of compressed air rather than as a filled bubble.
        if (dist < radius) {
            float r = dist / radius;
            float grazing = r / max(sqrt(max(1.0 - r * r, 0.0)), 0.18);
            offset += screenDir * grazing * amp;
            effectPresence = max(effectPresence, env * r);
        }

        // The front itself: a bright band at the silhouette, falling away to
        // both sides.
        float rim = exp(-abs(dist - radius) / shellWidth) * env;
        rimGlow = max(rimGlow, rim);
        effectPresence = max(effectPresence, rim);
    }

    if (effectPresence < 0.002) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 col = texture(SceneSampler, clamp(texCoord + offset, vec2(0.001), vec2(0.999))).rgb;

    if (rimGlow > 0.002) {
        float core = rimGlow * rimGlow * rimGlow;
        vec3 rimCol = mix(tintColorAmount.rgb, vec3(1.0), core);
        col += rimCol * rimGlow * tintColorAmount.a * 1.6;
    }

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
