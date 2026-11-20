#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif
#include <portalix:color_space.glsl>
#include <minecraft:oit.glsl>

vec4 particleColor() {
    vec4 texel = texture(Sampler0, texCoord0);
#if SOURCE_SPRITECARD == 1

    vec3 light = srgbToLinear(texel.rgb) * float(SOURCE_OVERBRIGHT);
#if SOURCE_TINTED == 1

    light = clamp(light, 0.0, 1.0);
#endif
    vec4 color = vec4(light, texel.a) * vertexColor * ColorModulator;
    color.rgb = clamp(color.rgb, 0.0, 1.0);
#if SOURCE_LINEAR_OUTPUT == 1
    return vec4(color.rgb, clamp(color.a, 0.0, 1.0));
#else
    return vec4(linearToSrgb(color.rgb), clamp(color.a, 0.0, 1.0));
#endif
#else
    vec4 color = texel * vertexColor * ColorModulator;
    color.rgb *= float(SOURCE_OVERBRIGHT);
    return color;
#endif
}

void main() {
    vec4 color = particleColor();
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, min(0.99, color.a));
#elif defined(OIT_ACCUMULATE)
#if SOURCE_LINEAR_OUTPUT == 1
    // Store linear light separately while using the same transmittance as world geometry.
    float transmittance = sampleTransmittance(ivec2(gl_FragCoord.xy), normalizeDepth(gl_FragCoord.z), 1.0);
    fragColor = vec4(color.rgb, color.a * transmittance);
#else
    color.a = min(0.99, color.a);
    fragColor = sampleColorForAccumulation(color);
#endif
#else
    fragColor = color;
#endif
}
