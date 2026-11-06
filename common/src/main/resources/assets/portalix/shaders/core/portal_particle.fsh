#version 330
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;

in vec4 projected;
in vec3 worldPosition;
out vec4 fragColor;
#moj_import <portalix:portal_particle_depth.glsl>
#moj_import <portalix:color_space.glsl>
void main() {
    gl_FragDepth = portalParticleDepth(worldPosition);
    vec4 texel = texture(Sampler0, texCoord);
#ifdef PORTALIX_TINTED_PARTICLE
    // Additive textures store coverage in RGB; keep it separate from the portal tint.
    float coverage = max(texel.r, max(texel.g, texel.b));
    fragColor = vec4(linearToSrgb(clamp(vertexColor.rgb, 0.0, 1.0)), clamp(texel.a * vertexColor.a * coverage, 0.0, 1.0));
#else
    fragColor = vec4(linearToSrgb(clamp(srgbToLinear(texel.rgb) * vertexColor.rgb, 0.0, 1.0)), clamp(texel.a * vertexColor.a, 0.0, 1.0));
#endif
}
