#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
layout(location = 0) in vec3 fromIndicator;
layout(location = 1) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    float light = 1.0 - smoothstep(0.0, 3.4, length(fromIndicator));
    vec3 albedo = texture(Sampler0, texCoord).rgb;
    fragColor = vec4(albedo * ColorModulator.rgb, 0.42 * light * light);
}
