#include <portalix:portal_particle_depth.glsl>

#if defined(OIT_TRANSMITTANCE) || defined(OIT_ACCUMULATE)
#include <minecraft:oit_common.glsl>
#include <minecraft:oit_depth_sample.glsl>

float normalizePortalParticleDepth() {
    return normalizeDepth(portalParticleDepth(worldPosition));
}

// Use the same depth for OIT sampling and for the particle's depth test at the aperture.
#define normalizeDepth(rasterDepth) normalizePortalParticleDepth()
#endif
#include <minecraft:oit.glsl>
#undef normalizeDepth

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void writePortalParticle(vec4 color) {
    float depth = portalParticleDepth(worldPosition);
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(depth, color.a);
#elif defined(OIT_ACCUMULATE)
    fragColor = sampleColorForAccumulation(color);
#else
    fragColor = color;
#endif
    gl_FragDepth = depth;
}
