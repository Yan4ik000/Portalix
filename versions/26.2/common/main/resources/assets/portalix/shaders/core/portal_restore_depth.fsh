#version 330
uniform sampler2D WorldDepth;
out vec4 fragColor;
void main() {
    gl_FragDepth = texelFetch(WorldDepth, ivec2(gl_FragCoord.xy), 0).r;
    fragColor = vec4(0.0);
}
