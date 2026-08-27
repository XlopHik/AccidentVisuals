#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;

layout(std140) uniform HandsData {
    vec4 resolutionTimeAlpha;
    vec4 customColorData;
    vec4 gradient1Data;
    vec4 gradient2Data;
    vec4 gradient3Data;
    vec4 gradient4Data;
    vec4 params;
};

float getMask(vec2 uv) {
    vec2 texelSize = 1.0 / vec2(textureSize(DepthTexture, 0));
    float centerDepth = texture(DepthTexture, uv).r;
    float minDepth = min(centerDepth, texture(DepthTexture, uv + vec2(texelSize.x, 0.0)).r);
    minDepth = min(minDepth, texture(DepthTexture, uv - vec2(texelSize.x, 0.0)).r);
    minDepth = min(minDepth, texture(DepthTexture, uv + vec2(0.0, texelSize.y)).r);
    minDepth = min(minDepth, texture(DepthTexture, uv - vec2(0.0, texelSize.y)).r);
    return smoothstep(0.99, 0.98, minDepth);
}

vec3 getBaseColor(vec4 originalColor) {
    return params.y > 0.5 ? originalColor.rgb : vec3(0.0);
}

void main() {
    vec4 originalColor = texture(ColorTexture, texCoord);
    float mask = getMask(texCoord);
    if (mask < 0.01) {
        discard;
    }

    vec2 st = texCoord * 2.0 - 1.0;
    st.x *= resolutionTimeAlpha.x / resolutionTimeAlpha.y;

    for (float i = 1.0; i < 8.0; i++) {
        st.x += 0.6 / i * cos(i * 2.5 * st.y + resolutionTimeAlpha.z);
        st.y += 0.6 / i * cos(i * 1.5 * st.x + resolutionTimeAlpha.z);
    }

    vec3 waveColor = customColorData.rgb * 0.1 / abs(sin(resolutionTimeAlpha.z - st.y - st.x));
    waveColor = clamp(waveColor, 0.0, 2.0);
    if (customColorData.a > 0.5) {
        waveColor *= originalColor.rgb;
    }

    vec3 finalHandColor = mix(getBaseColor(originalColor), waveColor, resolutionTimeAlpha.w);
    fragColor = vec4(finalHandColor, mask);
}
