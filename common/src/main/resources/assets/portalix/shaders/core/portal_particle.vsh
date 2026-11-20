#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec4 projected;
layout(location = 3) out vec3 worldPosition;
void main() {
    vec3 world = (TextureMat * vec4(Position * TextureMat[3].w, 0)).xyz + ModelOffset;
    worldPosition = world;
    gl_Position = ProjMat * ModelViewMat * vec4(world, 1);
    projected = gl_Position;
    texCoord = UV0;
    vertexColor = Color * ColorModulator;
}
