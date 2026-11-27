#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
#moj_import <portalix:color_space.glsl>
void main() {
    vec4 texel = texture(Sampler0, texCoord0);
#if SOURCE_SPRITECARD == 1

    vec3 light = srgbToLinear(texel.rgb) * float(SOURCE_OVERBRIGHT);
#if SOURCE_TINTED == 1

    light = clamp(light, 0.0, 1.0);
#endif
    vec4 color = vec4(light, texel.a) * vertexColor * ColorModulator;
    color.rgb = clamp(color.rgb, 0.0, 1.0);
#if SOURCE_LINEAR_OUTPUT == 1
    fragColor = vec4(color.rgb, clamp(color.a, 0.0, 1.0));
#else
    fragColor = vec4(linearToSrgb(color.rgb), clamp(color.a, 0.0, 1.0));
#endif
#else
    vec4 color = texel * vertexColor * ColorModulator;
    color.rgb *= float(SOURCE_OVERBRIGHT);
    fragColor = color;
#endif
}
