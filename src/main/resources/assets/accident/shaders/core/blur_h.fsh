#version 150

// First half of a separable Gaussian: blurs along X only and writes the raw
// result, with no rounded-rect mask and no tint - those belong to the second
// pass, which finishes the job along Y.
//
// A 2D Gaussian factorises exactly: exp(-(x*x + y*y) / 2s^2) equals
// exp(-x*x / 2s^2) * exp(-y*y / 2s^2), and the normalising sums factor the same
// way. Two 1D passes therefore reproduce the 2D result exactly.
//
// Taps also come in pairs. With linear filtering one fetch placed between two
// texels returns their blend, so putting it where the ratio of distances
// matches the ratio of their Gaussian weights makes that one fetch equal to
// two - again exactly. Offsets and weights arrive precomputed, so this stage
// runs no exp() and no divisions per pixel.

in vec2 texCoord;
in vec2 texelSize;
in vec4 tapA;
in vec4 tapB;
in vec4 tapC;
in float tapCount;
in float invTotalWeight;

out vec4 fragColor;

uniform sampler2D Sampler0;

vec4 tapPair(float offset, float weight) {
    vec2 step = vec2(offset, 0.0) * texelSize;
    vec4 a = texture(Sampler0, clamp(texCoord + step, vec2(0.001), vec2(0.999)));
    vec4 b = texture(Sampler0, clamp(texCoord - step, vec2(0.001), vec2(0.999)));
    return (a + b) * weight;
}

void main() {
    vec4 col = texture(Sampler0, texCoord);

    if (tapCount > 0.5) col += tapPair(tapA.x, tapA.y);
    if (tapCount > 1.5) col += tapPair(tapA.z, tapA.w);
    if (tapCount > 2.5) col += tapPair(tapB.x, tapB.y);
    if (tapCount > 3.5) col += tapPair(tapB.z, tapB.w);
    if (tapCount > 4.5) col += tapPair(tapC.x, tapC.y);
    if (tapCount > 5.5) col += tapPair(tapC.z, tapC.w);

    fragColor = col * invTotalWeight;
}
