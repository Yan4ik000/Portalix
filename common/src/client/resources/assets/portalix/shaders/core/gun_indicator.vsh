#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
out vec3 fromIndicator;
out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * (TextureMat * vec4(Position, 1.0));
    // Keep the light pass on the same depth as the shell across shader pipelines.
    gl_Position.z += 0.000002 * gl_Position.w;
    fromIndicator = Position - ModelOffset;
    texCoord = UV0;
}
