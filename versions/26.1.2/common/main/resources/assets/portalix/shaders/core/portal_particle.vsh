#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 texCoord;
out vec4 vertexColor;
out vec4 projected;
out vec3 worldPosition;
void main() {
    vec3 world = (TextureMat * vec4(Position * TextureMat[3].w, 0)).xyz + ModelOffset;
    worldPosition = world;
    gl_Position = ProjMat * ModelViewMat * vec4(world, 1);
    projected = gl_Position;
    texCoord = UV0;
    vertexColor = Color * ColorModulator;
}
