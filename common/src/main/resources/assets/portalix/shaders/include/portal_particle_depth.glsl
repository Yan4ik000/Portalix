#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Basis columns keep unit length; their w values hold depth range, UV scale, and aperture state.
// The last column holds the camera-relative portal center and opening scale.
float portalParticleDepth(vec3 position) {
    vec3 plane = TextureMat[3].xyz;
    vec3 normal = TextureMat[2].xyz;
    float planeDistance = dot(plane, normal);
    float particleDistance = dot(position, normal);
    if (TextureMat[2].w < 0.5 || planeDistance >= 0.0 || particleDistance >= planeDistance) {
        return gl_FragCoord.z;
    }

    vec3 onPlane = position * (planeDistance / particleDistance);
    vec3 offset = onPlane - plane;
    vec2 aperture = vec2(dot(offset, TextureMat[0].xyz) * 2.0, dot(offset, TextureMat[1].xyz)) * TextureMat[1].w;
    if (dot(aperture, aperture) > TextureMat[3].w * TextureMat[3].w) {
        return gl_FragCoord.z;
    }

    // The wall remains in the main depth buffer even where the portal cuts through it.
    float bias = dot(ModelOffset - plane, normal);
    onPlane *= max(0.5, 1.0 - bias / -planeDistance);
    vec4 projectedPlane = ProjMat * ModelViewMat * vec4(onPlane, 1.0);
    float depth = projectedPlane.z / projectedPlane.w;
    depth = clamp(TextureMat[0].w > 0.5 ? depth : depth * 0.5 + 0.5, 0.0, 1.0);
    return max(gl_FragCoord.z, min(1.0, uintBitsToFloat(floatBitsToUint(depth) + 8u)));
}
