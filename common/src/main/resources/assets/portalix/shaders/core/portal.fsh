#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D PortalScene;
#if defined(PORTALIX_IRIS_HAND) && !defined(PORTALIX_DEPTH_APERTURE)
uniform sampler2D PortalWorldDepth;
uniform sampler2D PortalHandDepth;
#endif
#ifdef PORTALIX_IRIS_HAND
uniform sampler2D PortalOcclusionDepth;
#endif
in vec2 portalUv;
in vec3 worldPosition;
in vec3 tangentPosition;
in vec3 bitangentPosition;
in vec3 screenPosition;
in vec4 noiseUv;
in vec2 portalDepth;
out vec4 fragColor;
mat4 viewProjection;
#moj_import <portalix:color_space.glsl>
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
    viewProjection = ProjMat * ModelViewMat;
    vec2 fragmentUv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));

    vec3 parameters = vec3(ModelOffset.x, 1.0 - ModelOffset.y, TextureMat[3].y > 0.5 ? 1.0 : 4.0);
    vec4 hole = sourcePortalStage1(portalUv, tangentPosition, bitangentPosition, worldPosition, screenPosition, noiseUv, parameters);
#ifdef PORTALIX_DEPTH_APERTURE
    if (TextureMat[3].x <= 0.5 || hole.a < 1.0) discard;
    gl_FragDepth = 0.0;
    fragColor = vec4(0.0);
    return;
#endif
    vec4 color = TextureMat[3].y > 0.5
        ? sourcePortalStage5(portalUv, tangentPosition, bitangentPosition, worldPosition, screenPosition, noiseUv, parameters)
        : sourcePortalStage2(portalUv, tangentPosition, bitangentPosition, worldPosition, screenPosition, noiseUv, parameters);
    float aperture = hole.a;
    vec3 base = srgbToLinear(texture(Sampler0, fragmentUv).rgb);
    bool hasBase = false;
    if (TextureMat[3].x > 0.5 && aperture == 1.0) {
        base = srgbToLinear(texture(PortalScene, fragmentUv * TextureMat[2].xy + TextureMat[2].zw).rgb);
        hasBase = true;
    } else if (ModelOffset.x < 1.0) {
        vec4 refracted = sourcePortalStage0(portalUv, tangentPosition, bitangentPosition, worldPosition, screenPosition, noiseUv, parameters);
        if (refracted.a > 0.5) {
            base = refracted.rgb;
            hasBase = true;
        }
    }
    if (!hasBase && color.a <= 1.0 / 255.0) discard;
    fragColor = vec4(linearToSrgb(clamp(mix(base, color.rgb, clamp(color.a, 0.0, 1.0)), 0.0, 1.0)), 1.0);
}
