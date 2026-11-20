#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Background;
uniform sampler2D Light;
layout(location = 0) out vec4 fragColor;
#include <portalix:color_space.glsl>
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 background = texelFetch(Background, pixel, 0);
    vec4 light = texelFetch(Light, pixel, 0);
    fragColor = vec4(linearToSrgb(srgbToLinear(background.rgb) + light.rgb), clamp(background.a + light.a, 0.0, 1.0));
}
