#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;

// Vulkan packs the inputs, so projected reserves the location before worldPosition.
layout(location = 2) in vec4 projected;
layout(location = 3) in vec3 worldPosition;
#include <portalix:portal_particle_output.glsl>
#include <portalix:color_space.glsl>
void main() {
    vec4 texel = texture(Sampler0, texCoord);
    vec3 color = vertexColor.rgb;
    float alpha = texel.a * vertexColor.a;
#ifdef PORTALIX_TINTED_PARTICLE
    // Additive textures store coverage in RGB.
    alpha *= max(texel.r, max(texel.g, texel.b));
#else
    color = srgbToLinear(texel.rgb) * color;
#endif
    writePortalParticle(vec4(linearToSrgb(clamp(color, 0.0, 1.0)), clamp(alpha, 0.0, 1.0)));
}
