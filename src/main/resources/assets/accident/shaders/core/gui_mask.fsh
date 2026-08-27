#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D SourceSampler;

layout(std140) uniform GuiMaskData {
    vec4 params; // x = brightness multiplier, yzw unused
};

// Brightens the captured GUI layer on its way into a ghost texture.
//
// The panels' glass blurs whatever the framebuffer held behind them, and
// while the menu is open that framebuffer already carries ClickGUI's
// full-screen dim - correctly, since the panel is sitting on that dimmed
// world. Hung in the world afterwards the same pixels sit against an
// undimmed world instead, so they read as too dark. Undoing the dim at
// capture time keeps the live menu untouched and fixes only the echo.
//
// Alpha is passed through: the layer's transparency is what makes the ghost
// a floating panel rather than a rectangle, and scaling it would eat that.
void main() {
    vec4 src = texture(SourceSampler, texCoord);
    fragColor = vec4(src.rgb * params.x, src.a);
}
