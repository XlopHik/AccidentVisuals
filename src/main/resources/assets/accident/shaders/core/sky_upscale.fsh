#version 150

in  vec2 texCoord;
out vec4 fragColor;

uniform sampler2D Sampler0;

layout(std140) uniform UpscaleData {
    vec4 reserved;
};

// The sky was rendered at reduced resolution because its own math, not the
// blend or the pass itself, was what cost the frame time (confirmed by
// swapping it for a flat fill and watching the FPS drop disappear). Nebula
// and gradient detail is low-frequency enough that nobody notices the
// downsample; a linear sampler here is the entire cost of putting it back
// on screen.
void main() {
    fragColor = texture(Sampler0, texCoord);
}
