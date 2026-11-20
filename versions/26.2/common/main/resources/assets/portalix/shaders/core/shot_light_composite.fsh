#version 330
uniform sampler2D Background;
uniform sampler2D Light;
out vec4 fragColor;
#moj_import <portalix:color_space.glsl>
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 background = texelFetch(Background, pixel, 0);
    vec4 light = texelFetch(Light, pixel, 0);
    fragColor = vec4(linearToSrgb(srgbToLinear(background.rgb) + light.rgb), clamp(background.a + light.a, 0.0, 1.0));
}
