#version 330
// Refraction sampling adapted from Portal 2's refract_ps20b (Valve); see NOTICE.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
in vec4 vertexColor;
in vec4 projected;
in vec3 worldPosition;
out vec4 fragColor;
#moj_import <portalix:portal_particle_depth.glsl>
#moj_import <portalix:color_space.glsl>
void main() {
    gl_FragDepth = portalParticleDepth(worldPosition);
    vec4 normal = texture(Sampler0, texCoord);
    vec2 uv = projected.xy / projected.w * 0.5 + 0.5;

    uv += (normal.xy * 2.0 - 1.0) * vec2(1, -1) * normal.a * 0.5 * vertexColor.a;
    vec3 refracted = srgbToLinear(texture(Sampler1, uv + vec2(0.001953125, 0.0009765625)).rgb) * 0.222222194;
    refracted += srgbToLinear(texture(Sampler1, uv + vec2(-0.0009765625, 0.0009765625)).rgb) * 0.444444388;
    refracted += srgbToLinear(texture(Sampler1, uv + vec2(-0.0009765625, -0.001953125)).rgb) * 0.222222194;
    refracted += srgbToLinear(texture(Sampler1, uv + vec2(0.001953125, -0.001953125)).rgb) * 0.111111097;
    fragColor = vec4(linearToSrgb(refracted * vertexColor.rgb), normal.a * vertexColor.a);
}
