#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
#ifdef PORTALIX_MARKER_OCCLUSION
uniform sampler2D PortalSurfaceDepth;
#endif
#ifdef PORTALIX_IRIS_HAND
uniform sampler2D PortalWorldDepth;
uniform sampler2D PortalHandDepth;
#endif
in vec2 texCoord0;
out vec4 fragColor;

void main() {
#if defined(PORTALIX_MARKER_OCCLUSION) || defined(PORTALIX_IRIS_HAND)
    ivec2 pixel = ivec2(gl_FragCoord.xy);
#endif
#ifdef PORTALIX_MARKER_OCCLUSION
    if (texelFetch(PortalSurfaceDepth, pixel, 0).r > gl_FragCoord.z) discard;
#endif
#ifdef PORTALIX_IRIS_HAND
    if (texelFetch(PortalHandDepth, pixel, 0).r > texelFetch(PortalWorldDepth, pixel, 0).r) discard;
#endif
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a == 0.0) discard;
    fragColor = color * ColorModulator;
}
