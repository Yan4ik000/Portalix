#version 330
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;

// Vulkan packs the inputs, so projected reserves the location before worldPosition.
in vec4 projected;
in vec3 worldPosition;
out vec4 fragColor;
#moj_import <portalix:portal_particle_depth.glsl>
#moj_import <portalix:color_space.glsl>
void main() {
    gl_FragDepth = portalParticleDepth(worldPosition);
    vec4 texel = texture(Sampler0, texCoord);
    vec3 color = vertexColor.rgb;
    float alpha = texel.a * vertexColor.a;
#ifdef PORTALIX_TINTED_PARTICLE
    // Additive textures store coverage in RGB.
    alpha *= max(texel.r, max(texel.g, texel.b));
#else
    color = srgbToLinear(texel.rgb) * color;
#endif
    fragColor = vec4(linearToSrgb(clamp(color, 0.0, 1.0)), clamp(alpha, 0.0, 1.0));
}
