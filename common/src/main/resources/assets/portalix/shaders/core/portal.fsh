#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec2 portalUv;
in vec2 portalDepth;
out vec4 fragColor;

#ifndef PORTALIX_DEPTH_APERTURE
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D PortalScene;
in vec3 tangentPosition;
in vec3 bitangentPosition;
in vec3 screenPosition;
in vec4 noiseUv;
mat4 viewProjection;
#moj_import <portalix:color_space.glsl>
#ifdef PORTALIX_IRIS_HAND
uniform sampler2D PortalWorldDepth;
uniform sampler2D PortalHandDepth;
#endif
#endif
#ifdef PORTALIX_IRIS_HAND
uniform sampler2D PortalOcclusionDepth;
#endif

#moj_import <portalix:source_portal.glsl>
void main() {
#if defined(PORTALIX_IRIS_HAND) && !defined(PORTALIX_DEPTH_APERTURE)
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    if (texelFetch(PortalHandDepth, pixel, 0).r > texelFetch(PortalWorldDepth, pixel, 0).r) discard;
#endif
    if (ModelOffset.x <= 0.0) discard;
    float depth = portalDepth.x / portalDepth.y;
    depth = clamp(TextureMat[3].z > 0.5 ? depth : depth * 0.5 + 0.5, 0.0, 1.0);

    float depthBias = max(abs(dFdx(depth)), abs(dFdy(depth)));
    gl_FragDepth = min(1.0, uintBitsToFloat(floatBitsToUint(depth + depthBias) + 8u));
#ifdef PORTALIX_IRIS_HAND
    if (TextureMat[3].x > 0.5 && gl_FragDepth < texelFetch(PortalOcclusionDepth, ivec2(gl_FragCoord.xy), 0).r) discard;
#endif
    float aperture = sourcePortalAperture(portalUv, ModelOffset.x);
#ifdef PORTALIX_DEPTH_APERTURE
    if (TextureMat[3].x <= 0.5 || aperture < 1.0) discard;
    gl_FragDepth = 0.0;
    fragColor = vec4(0.0);
#else
    viewProjection = ProjMat * ModelViewMat;
    vec2 fragmentUv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    vec2 rim = sourcePortalRim(portalUv, noiseUv, ModelOffset.x, 1.0 - ModelOffset.y);
    float gradient = rim.x * rim.y;
    vec3 rimColor = TextureMat[3].y > 0.5
        ? gradient * ColorModulator.rgb
        : srgbToLinear(texture(Sampler2, vec2(gradient)).rgb) * 4.0;
    vec3 base;
    if (TextureMat[3].x > 0.5 && aperture == 1.0) {
        base = srgbToLinear(texture(PortalScene, fragmentUv * TextureMat[2].xy + TextureMat[2].zw).rgb);
    } else {
        vec4 refracted = vec4(0.0);
        if (ModelOffset.x < 1.0)
            refracted = sourcePortalRefraction(portalUv, tangentPosition, bitangentPosition, screenPosition, ModelOffset.x);
        if (refracted.a > 0.5) base = refracted.rgb;
        else {
            if (rim.x <= 1.0 / 255.0) discard;
            base = srgbToLinear(texture(Sampler0, fragmentUv).rgb);
        }
    }
    fragColor = vec4(linearToSrgb(clamp(mix(base, rimColor, clamp(rim.x, 0.0, 1.0)), 0.0, 1.0)), 1.0);
#endif
}
