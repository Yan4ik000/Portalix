#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 0) out vec2 portalUv;
layout(location = 1) out vec3 tangentPosition;
layout(location = 2) out vec3 bitangentPosition;
layout(location = 3) out vec3 screenPosition;
layout(location = 4) out vec4 noiseUv;
layout(location = 5) out vec2 portalDepth;
vec4 portalix_UnclippedPosition;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1);
    portalix_UnclippedPosition = gl_Position;
    // Change depth along the viewing ray while keeping the aperture at its original position.
    portalDepth = (ProjMat * ModelViewMat * vec4(Position * ColorModulator.w, 1)).zw;

    gl_Position.z = gl_Position.w;

    tangentPosition = Position + TextureMat[0].xyz;
    bitangentPosition = Position + TextureMat[1].xyz;
    screenPosition = vec3(gl_Position.xy * 0.5 + gl_Position.ww * 0.5, gl_Position.w);
    portalUv = (UV0 - 0.5) * TextureMat[3].w + 0.5;
    vec2 scaledUv = ((UV0 - 0.5) / clamp(ModelOffset.x + 0.001, 0.001, 1.0) + 0.5) * 0.3;
    noiseUv = vec4(scaledUv + vec2(ModelOffset.z * 0.0275, 0), scaledUv + vec2(ModelOffset.z * -0.0275, 0));
}
