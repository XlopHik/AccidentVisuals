#version 150

// One draw call covers a run of rectangles. Each contributes six vertices, so
// the vertex id says both which rectangle this is and which of its corners:
// id / 6 and id % 6. Nothing is read from a vertex buffer - the format stays
// empty, exactly as it was when a call drew a single rect.
//
// Everything belonging to one rectangle sits together in ENTRY_SIZE
// consecutive vec4s rather than in parallel arrays. Laid out that way the CPU
// only has to fill the entries actually used: with the arrays split apart, the
// colours began at a fixed offset past all sixty-four slots, so a batch of
// three still meant writing the whole twelve-kilobyte block.
const int MAX_RECTS = 64;
const int ENTRY_SIZE = 12;

layout(std140) uniform RectData {
    vec4 screen;
    vec4 entries[MAX_RECTS * ENTRY_SIZE];
};

out vec2 fragCoord;
out vec2 pixelCoord;
out vec2 rectSize;
out vec4 cornerRadii;
out vec4 fragColors[9];
out float guiScale;
out float innerBlur;

void main() {
    vec2 positions[6] = vec2[](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
    );

    int index = gl_VertexID / 6;
    vec2 pos = positions[gl_VertexID - index * 6];

    int base = index * ENTRY_SIZE;
    vec4 rect = entries[base];

    vec2 screenPos = rect.xy + pos * rect.zw;
    vec2 ndcPos = (screenPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    fragCoord = pos;
    pixelCoord = pos * rect.zw;
    rectSize = rect.zw;
    cornerRadii = entries[base + 1];
    guiScale = screen.z;
    innerBlur = entries[base + 2].x;

    for (int i = 0; i < 9; i++) {
        fragColors[i] = entries[base + 3 + i];
    }
}
