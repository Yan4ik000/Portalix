#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D WorldDepth;
layout(location = 0) out vec4 fragColor;
void main() {
    gl_FragDepth = texelFetch(WorldDepth, ivec2(gl_FragCoord.xy), 0).r;
    fragColor = vec4(0.0);
}
