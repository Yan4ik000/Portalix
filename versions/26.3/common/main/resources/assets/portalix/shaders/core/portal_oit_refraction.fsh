#version 330
#extension GL_ARB_separate_shader_objects : require
#include <portalix:portal_particle_depth.glsl>
#include <portalix:color_space.glsl>
#include <minecraft:oit_common.glsl>
#include <minecraft:oit_depth_sample.glsl>
#include <minecraft:oit_sample.glsl>

uniform sampler2D Sampler0;
uniform sampler2D OpaqueColor;
uniform sampler2D OpaqueDepth;
uniform sampler2D CompositedColor;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;
layout(location = 3) in vec3 worldPosition;
layout(location = 0) out vec4 fragColor;

bool inFront(float sceneDepth, float waveDepth) {
    return sceneDepth > waveDepth;
}

vec3 sampleBehind(vec2 uv, float waveDepth, bool foreground, bool opaqueOnly, vec3 original) {
    ivec2 extent = textureSize(OpaqueDepth, 0);
    ivec2 lower = ivec2(floor(uv * vec2(extent) - 0.5));
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            ivec2 pixel = clamp(lower + ivec2(x, y), ivec2(0), extent - 1);
            if (inFront(texelFetch(OpaqueDepth, pixel, 0).r, waveDepth)) return original;
            if (!opaqueOnly && inFront(texelFetch(DepthBoundsSampler, pixel, 0).b, waveDepth)) {
                if (!foreground) return original;
                opaqueOnly = true;
            }
        }
    }
    return srgbToLinear(opaqueOnly ? texture(OpaqueColor, uv).rgb : texture(CompositedColor, uv).rgb);
}

void main() {
    vec4 normal = texture(Sampler0, texCoord);
    float alpha = normal.a * vertexColor.a;
    float depth = portalParticleDepth(worldPosition);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    if (alpha <= 0.0 || inFront(texelFetch(OpaqueDepth, pixel, 0).r, depth)) discard;

    vec4 bounds = texelFetch(DepthBoundsSampler, pixel, 0);
    bool foreground = inFront(bounds.b, depth);
    // A mixed pixel has one RGB sum. Only its opaque contribution can be replaced independently.
    bool opaqueOnly = foreground && bounds.g > deviceToLinearDepth(depth);
    float visibility = 1.0;
    if (foreground) {
        visibility = sampleTransmittance(pixel, 100000.0, 1.0);
        if (visibility < OIT_FULLY_OPAQUE_TOTAL_TRANSMITTANCE) discard;
    }
    vec3 scene = texelFetch(CompositedColor, pixel, 0).rgb;
    vec3 originalColor = foreground ? texelFetch(OpaqueColor, pixel, 0).rgb : scene;
    vec3 original = srgbToLinear(originalColor);
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(OpaqueDepth, 0));
    uv += (normal.xy * 2.0 - 1.0) * vec2(1, -1) * normal.a * 0.5 * vertexColor.a;
    vec3 refracted = sampleBehind(uv + vec2(0.001953125, 0.0009765625), depth, foreground, opaqueOnly, original) * 0.222222194;
    refracted += sampleBehind(uv + vec2(-0.0009765625, 0.0009765625), depth, foreground, opaqueOnly, original) * 0.444444388;
    refracted += sampleBehind(uv + vec2(-0.0009765625, -0.001953125), depth, foreground, opaqueOnly, original) * 0.222222194;
    refracted += sampleBehind(uv + vec2(0.001953125, -0.001953125), depth, foreground, opaqueOnly, original) * 0.111111097;
    vec3 displaced = linearToSrgb(refracted * vertexColor.rgb);
    vec3 color = foreground ? scene + (displaced - originalColor) * visibility : displaced;
    fragColor = vec4(color, alpha);
}
