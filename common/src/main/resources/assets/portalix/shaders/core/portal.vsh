#version 330
// Adapts Portal 2's portal_refract_vs20 (Valve); see NOTICE.
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;

in vec4 Color;
in vec2 UV0;
out vec2 portalUv;
out vec3 worldPosition;
out vec3 tangentPosition;
out vec3 bitangentPosition;
out vec3 screenPosition;
out vec4 noiseUv;
out vec2 portalDepth;
vec4 portalix_UnclippedPosition;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1);
    portalix_UnclippedPosition = gl_Position;
    // Change depth along the viewing ray while keeping the aperture at its original position.
    portalDepth = (ProjMat * ModelViewMat * vec4(Position * ColorModulator.w, 1)).zw;

    gl_Position.z = gl_Position.w;
    worldPosition = Position;

    tangentPosition = Position + TextureMat[0].xyz;
    bitangentPosition = Position + TextureMat[1].xyz;
    screenPosition = vec3(gl_Position.xy * 0.5 + gl_Position.ww * 0.5, gl_Position.w);
    portalUv = (UV0 - 0.5) * TextureMat[3].w + 0.5;
    vec2 scaledUv = ((UV0 - 0.5) / clamp(ModelOffset.x + 0.001, 0.001, 1.0) + 0.5) * 0.3;
    noiseUv = vec4(scaledUv + vec2(ModelOffset.z * 0.0275, 0), scaledUv + vec2(ModelOffset.z * -0.0275, 0));
}
