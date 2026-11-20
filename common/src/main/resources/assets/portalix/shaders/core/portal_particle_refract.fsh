#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec4 projected;
layout(location = 3) in vec3 worldPosition;
#include <portalix:portal_particle_output.glsl>
#include <portalix:color_space.glsl>
void main() {
    vec4 normal = texture(Sampler0, texCoord);
    float alpha = normal.a * vertexColor.a;
#ifdef OIT_ALPHA_ONLY
    writePortalParticle(vec4(0.0, 0.0, 0.0, alpha));
#else
    vec2 uv = projected.xy / projected.w * 0.5 + 0.5;

    uv += (normal.xy * 2.0 - 1.0) * vec2(1, -1) * normal.a * 0.5 * vertexColor.a;
    vec3 refracted = srgbToLinear(texture(Sampler1, uv + vec2(0.001953125, 0.0009765625)).rgb) * 0.222222194;
    refracted += srgbToLinear(texture(Sampler1, uv + vec2(-0.0009765625, 0.0009765625)).rgb) * 0.444444388;
    refracted += srgbToLinear(texture(Sampler1, uv + vec2(-0.0009765625, -0.001953125)).rgb) * 0.222222194;
    refracted += srgbToLinear(texture(Sampler1, uv + vec2(0.001953125, -0.001953125)).rgb) * 0.111111097;
    writePortalParticle(vec4(linearToSrgb(refracted * vertexColor.rgb), alpha));
#endif
}
