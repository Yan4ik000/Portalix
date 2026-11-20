#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;
layout(location = 0) out vec3 fromIndicator;
layout(location = 1) out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * (TextureMat * vec4(Position, 1.0));
    // Keep the light pass on the same depth as the shell across shader pipelines.
    gl_Position.z += 0.000002 * gl_Position.w;
    fromIndicator = Position - ModelOffset;
    texCoord = UV0;
}
